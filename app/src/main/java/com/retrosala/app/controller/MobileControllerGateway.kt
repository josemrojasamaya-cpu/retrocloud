package com.retrosala.app.controller

import com.retrosala.app.emulation.ControllerInput
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.net.NetworkInterface

data class ConnectedController(val player: Int, val label: String)

/**
 * Puente futuro para la página web del mando. La implementación de producción
 * abrirá un WebSocket local y entregará ControllerInput a la sesión remota.
 */
interface MobileControllerGateway {
    val controllerUrl: StateFlow<String>
    val connectedControllers: StateFlow<List<ConnectedController>>
    val inputs: SharedFlow<ControllerInput>
    suspend fun start()
    suspend fun stop()
    fun publish(input: ControllerInput)
}

class DemoMobileControllerGateway : MobileControllerGateway {
    private val _url = kotlinx.coroutines.flow.MutableStateFlow("http://retrosala.local/demo")
    private val _controllers = kotlinx.coroutines.flow.MutableStateFlow(listOf(ConnectedController(1, "Mando de demostración")))
    private val _inputs = MutableSharedFlow<ControllerInput>()
    override val controllerUrl: StateFlow<String> = _url
    override val connectedControllers: StateFlow<List<ConnectedController>> = _controllers
    override val inputs: SharedFlow<ControllerInput> = _inputs
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun publish(input: ControllerInput) = Unit
}

/** Servidor local del mando; sólo existe en la red Wi-Fi de la sala. */
class LocalWebSocketControllerGateway(private val port: Int = 8090) : MobileControllerGateway {
    private val _url = MutableStateFlow("Preparando mando…")
    private val _controllers = MutableStateFlow<List<ConnectedController>>(emptyList())
    private val _inputs = MutableSharedFlow<ControllerInput>(extraBufferCapacity = 32)
    private var server: ControllerServer? = null
    private var nextPlayer = 1

    override val controllerUrl: StateFlow<String> = _url
    override val connectedControllers: StateFlow<List<ConnectedController>> = _controllers
    override val inputs: SharedFlow<ControllerInput> = _inputs

    override suspend fun start() {
        if (server != null) return
        val host = localIpv4() ?: "127.0.0.1"
        server = ControllerServer(port, onOpen = {
            val player = nextPlayer++
            _controllers.value = _controllers.value + ConnectedController(player, "Jugador $player")
            player
        }, onClose = { player ->
            _controllers.value = _controllers.value.filterNot { it.player == player }
        }, onInput = { player, control, pressed ->
            _inputs.tryEmit(ControllerInput(player, control, pressed))
        }).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        _url.value = "http://$host:$port"
    }

    override suspend fun stop() { server?.stop(); server = null; _controllers.value = emptyList() }
    override fun publish(input: ControllerInput) { _inputs.tryEmit(input) }

    private fun localIpv4(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            if (!network.isUp || network.isLoopback) continue
            val addresses = network.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) return address.hostAddress
            }
        }
        return null
    }
}

private class ControllerServer(
    port: Int,
    private val onOpen: () -> Int,
    private val onClose: (Int) -> Unit,
    private val onInput: (Int, String, Boolean) -> Unit
) : NanoWSD(port) {
    override fun openWebSocket(handshake: IHTTPSession): WebSocket = object : WebSocket(handshake) {
        private var player = 0
        override fun onOpen() { player = onOpen() }
        override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) { if (player != 0) onClose(player) }
        override fun onMessage(message: WebSocketFrame) {
            val parts = message.textPayload.split(":")
            if (parts.size == 2) onInput(player, parts[0], parts[1] == "down")
        }
        override fun onPong(pong: WebSocketFrame) = Unit
        override fun onException(exception: IOException) = Unit
    }

    override fun serveHttp(session: IHTTPSession): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", controllerPage())

    private fun controllerPage() = """<!doctype html><html><meta name=viewport content='width=device-width,initial-scale=1'><style>body{background:#10111a;color:#fff;font-family:sans-serif;text-align:center}button{font-size:24px;margin:8px;padding:20px;border-radius:12px}.d{display:grid;grid-template-columns:repeat(3,1fr);max-width:360px;margin:auto}</style><h1>RetroSala</h1><p id=s>Conectando…</p><div class=d><span></span><button data-c=cima>↑</button><span></span><button data-c=izquierda>←</button><button data-c=abajo>↓</button><button data-c=derecha>→</button></div><p><button data-c=A>A</button><button data-c=B>B</button><button data-c=Start>Start</button><button data-c=Select>Select</button><button data-c=Pausa>Pausa</button></p><script>const w=new WebSocket('ws://'+location.host);w.onopen=()=>s.textContent='Conectado';document.querySelectorAll('button').forEach(b=>{let f=x=>w.readyState===1&&w.send(b.dataset.c+':'+(x?'down':'up'));b.onpointerdown=()=>f(1);b.onpointerup=b.onpointerleave=()=>f(0)})</script></html>"""
}
