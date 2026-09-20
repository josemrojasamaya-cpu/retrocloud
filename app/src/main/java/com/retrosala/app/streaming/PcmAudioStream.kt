package com.retrosala.app.streaming

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.net.HttpURLConnection
import java.net.URL

/** Output-only PCM playback; no microphone, ROM, or emulator on Android. */
class PcmAudioStream(private val url: String, private val token: String) {
    val message = MutableStateFlow("Conectando audio…")
    @Volatile private var stopped = false
    @Volatile private var active: HttpURLConnection? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        while (!stopped && isActive) {
            val connection = URL(url).openConnection() as HttpURLConnection
            active = connection
            var track: AudioTrack? = null
            try {
                connection.connectTimeout = 4000; connection.readTimeout = 3000
                connection.setRequestProperty("Authorization", "Bearer $token")
                check(connection.responseCode == 200) { "Audio HTTP ${connection.responseCode}" }
                check(connection.getHeaderField("x-audio-encoding") == "pcm_s16le") { "Formato de audio incompatible" }
                val rate = connection.getHeaderField("x-audio-rate").toInt()
                val channels = connection.getHeaderField("x-audio-channels").toInt()
                require(rate in 8000..96000 && channels in 1..2)
                val channelMask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                val minimum = AudioTrack.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0) { "Este dispositivo no admite el formato de audio" }
                val player = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(channelMask).build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(minimum, rate * channels * 2 / 20))
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
                track = player
                check(player.state == AudioTrack.STATE_INITIALIZED)
                player.play()
                DataInputStream(connection.inputStream).use { input ->
                    val buffer = ByteArray((rate / 100) * channels * 2)
                    while (!stopped && isActive) {
                        input.readFully(buffer)
                        var offset = 0
                        while (offset < buffer.size && !stopped && isActive) {
                            val written = player.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_BLOCKING)
                            check(written > 0) { "No se pudo reproducir audio ($written)" }
                            offset += written
                        }
                        message.value = "Audio conectado"
                    }
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { message.value = "Reconectando audio: ${error.message ?: "sin señal"}"
            } finally {
                connection.disconnect(); active = null
                track?.let { runCatching { it.stop(); it.flush() }; it.release() }
            }
            if (!stopped && isActive) delay(1000)
        }
    }
    fun stop() { stopped = true; active?.disconnect() }
}
