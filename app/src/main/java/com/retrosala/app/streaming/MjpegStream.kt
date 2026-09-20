package com.retrosala.app.streaming

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MjpegStreamReader(private val streamUrl: String) {
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    @Volatile private var running = false

    suspend fun start() = withContext(Dispatchers.IO) {
        running = true
        while (running && isActive) {
            try {
                val connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 10000
                }
                _connected.value = true
                val input = BufferedInputStream(connection.inputStream, 65536)
                val buffer = ByteArrayOutputStream(65536)
                var prev = 0

                while (running && isActive) {
                    val b = input.read()
                    if (b == -1) break
                    buffer.write(b)

                    if (prev == 0xFF && b == 0xD9) {
                        val jpeg = buffer.toByteArray()
                        val start = findJpegStart(jpeg)
                        if (start >= 0) {
                            val bmp = BitmapFactory.decodeByteArray(jpeg, start, jpeg.size - start)
                            if (bmp != null) _frame.value = bmp
                        }
                        buffer.reset()
                        prev = 0
                    } else {
                        prev = b
                    }
                }
                connection.disconnect()
            } catch (_: Exception) {
                _connected.value = false
                if (running) kotlinx.coroutines.delay(1000)
            }
        }
        _connected.value = false
    }

    fun stop() { running = false }

    private fun findJpegStart(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) return i
        }
        return -1
    }
}
