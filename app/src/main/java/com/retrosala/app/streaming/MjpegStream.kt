package com.retrosala.app.streaming

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MjpegStreamReader(private val streamUrl: String, private val token: String = "") {
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    val message = MutableStateFlow("Conectando video…")
    @Volatile private var running = true
    @Volatile private var active: HttpURLConnection? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        while (running && isActive) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 4000
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                active = connection
                check(connection.responseCode == 200) { "Video HTTP ${connection.responseCode}" }
                val parser = JpegParser()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16384)
                    while (running && isActive) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        parser.accept(buffer, count) { jpeg ->
                            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let {
                                _frame.value = it; _connected.value = true
                                message.value = "Video conectado"
                            }
                        }
                    }
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                message.value = "Reconectando video: ${error.message ?: "sin señal"}"
            } finally {
                connection?.disconnect(); active = null; _connected.value = false; _frame.value = null
            }
            if (running && isActive) kotlinx.coroutines.delay(1000)
        }
        _connected.value = false
    }

    fun stop() { running = false; active?.disconnect(); _frame.value = null; _connected.value = false }

    private fun findJpegStart(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) return i
        }
        return -1
    }
}
