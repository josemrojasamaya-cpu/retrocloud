package com.retrosala.app.streaming

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.retrosala.app.emulation.ControllerInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GameWebSocket(
    private val apiUrl: String,
    private val token: String
) {
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _players = MutableStateFlow<List<Int>>(emptyList())
    val players: StateFlow<List<Int>> = _players
    val message = MutableStateFlow("Conectando video…")

    @Volatile private var running = true
    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }

    fun connect() {
        running = true
        val wsUrl = apiUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/ws?token=$token"

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _connected.value = true
                message.value = "Video conectado (WS)"
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                BitmapFactory.decodeByteArray(data, 0, data.size, opts)?.let {
                    _frame.value = it
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    if (json.optString("type") != "controllers") return@runCatching
                    val array = json.getJSONArray("players")
                    _players.value = (0 until array.length()).map { array.getInt(it) }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                message.value = "Reconectando video: ${t.message ?: "sin señal"}"
                _frame.value = null
                if (running) scheduleReconnect()
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connected.value = false
                _frame.value = null
                if (running) scheduleReconnect()
            }
        })
    }

    fun sendControl(input: ControllerInput) {
        val ws = webSocket ?: return
        val json = JSONObject()
            .put("control", input.control)
            .put("pressed", input.pressed)
        input.normalizedX?.let { json.put("x", it) }
        input.normalizedY?.let { json.put("y", it) }
        ws.send(json.toString())
    }

    fun stop() {
        running = false
        webSocket?.close(1000, "closing")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        _connected.value = false
        _frame.value = null
    }

    private fun scheduleReconnect() {
        Thread {
            Thread.sleep(800)
            if (running) connect()
        }.start()
    }
}
