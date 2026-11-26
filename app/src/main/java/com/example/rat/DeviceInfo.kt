package com.example.rat

import android.content.Context
import android.os.Build
import org.json.JSONObject

object DeviceInfo {
    fun get(context: Context): JSONObject = JSONObject()
        .put("brand", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("device", Build.DEVICE)
        .put("hardware", Build.HARDWARE)
        .put("release", Build.VERSION.RELEASE)
        .put("sdk", Build.VERSION.SDK_INT)
        .put("build", Build.ID)
}
