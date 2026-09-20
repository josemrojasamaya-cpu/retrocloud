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
        server = ControllerServer(port, onControllerConnected = {
            val player = nextPlayer++
            _controllers.value = _controllers.value + ConnectedController(player, "Jugador $player")
            player
        }, onClose = { player ->
            _controllers.value = _controllers.value.filterNot { it.player == player }
        }, onInput = { input ->
            _inputs.tryEmit(input)
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
    private val onControllerConnected: () -> Int,
    private val onClose: (Int) -> Unit,
    private val onInput: (ControllerInput) -> Unit
) : NanoWSD(port) {
    override fun openWebSocket(handshake: IHTTPSession): WebSocket = object : WebSocket(handshake) {
        private var player = 0
        override fun onOpen() { player = onControllerConnected(); send("player:$player") }
        override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) { if (player != 0) onClose(player) }
        override fun onMessage(message: WebSocketFrame) {
            parseControllerMessage(player, message.textPayload)?.let(onInput)
        }
        override fun onPong(pong: WebSocketFrame) = Unit
        override fun onException(exception: IOException) = Unit
    }

    override fun serveHttp(session: IHTTPSession): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", controllerPage())

    private fun controllerPage() = """<!doctype html><html lang=es><head><meta name=viewport content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'><meta name=theme-color content='#0a0a14'><style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent;touch-action:none}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#0a0a14;color:#f5f7ff;font-family:'Segoe UI',system-ui,sans-serif}body{background:linear-gradient(135deg,#1a0a2e 0%,#0d1117 40%,#0a1628 100%)}.app{height:100%;padding:12px;display:grid;grid-template-rows:42px 1fr 36px;gap:8px}.bar{display:flex;align-items:center;justify-content:space-between;font-weight:bold}.brand{background:linear-gradient(90deg,#22d3ee,#c084fc);-webkit-background-clip:text;-webkit-text-fill-color:transparent;letter-spacing:2px;font-size:15px}.state{font-size:12px;color:#a1a5be}.dot{display:inline-block;width:8px;height:8px;border-radius:99px;background:#ec4899;margin-right:5px;box-shadow:0 0 6px #ec489966}.online .dot{background:#22d3ee;box-shadow:0 0 8px #22d3ee88}.layout{display:grid;grid-template-columns:1fr .75fr 1fr;gap:12px;align-items:center}.zone{height:100%;display:flex;align-items:center;justify-content:center}.stick{width:min(35vw,180px);aspect-ratio:1;border-radius:50%;background:radial-gradient(circle,#2d2854 0 27%,#0f0f1a 28%);border:2px solid #8b5cf6;box-shadow:0 0 30px #8b5cf644;position:relative}.thumb{width:38%;aspect-ratio:1;border-radius:50%;background:linear-gradient(135deg,#22d3ee,#8b5cf6);position:absolute;left:31%;top:31%;box-shadow:0 5px 18px #0008}.center{display:flex;flex-direction:column;align-items:center;gap:12px}.mode{display:flex;background:#161b34;border:1px solid #8b5cf644;border-radius:14px;padding:3px}.mode button,.system button{border:0;border-radius:11px;padding:9px 12px;background:transparent;color:#a1a5be;font-weight:bold;font-size:13px}.mode button.active{background:#8b5cf6;color:white;box-shadow:0 0 12px #8b5cf644}.system{display:flex;gap:7px;flex-wrap:wrap;justify-content:center}.system button{background:#1d2341;border:1px solid #8b5cf633}.system button.exit{background:#4c1d3b;border-color:#ec4899}.face{display:grid;grid-template-columns:repeat(3,62px);grid-template-rows:repeat(3,62px);gap:8px}.face button{border:2px solid;border-radius:50%;font-size:22px;font-weight:bold;color:white;box-shadow:0 4px 0 #080a15,0 0 15px var(--glow)}.face button:active{transform:translateY(3px);box-shadow:0 1px 0 #080a15}.face .a{background:#7c3aed;border-color:#a78bfa;--glow:#7c3aed55}.face .b{background:#be185d;border-color:#f472b6;--glow:#be185d55}.face .x{background:#0891b2;border-color:#22d3ee;--glow:#0891b255}.face .y{background:#059669;border-color:#34d399;--glow:#05966955}.shoulders{position:absolute;inset:54px 16px auto;display:flex;justify-content:space-between;pointer-events:none}.shoulders button{pointer-events:auto;width:100px;padding:11px;border:1px solid #8b5cf644;border-radius:14px;background:#1e1b4b;color:white;font-weight:bold;box-shadow:0 0 10px #8b5cf622}.touch{display:none;width:100%;height:100%;border:2px dashed #8b5cf688;border-radius:20px;background:linear-gradient(135deg,#0f0f1a,#1e1b4b);align-items:center;justify-content:center;color:#a1a5be}.touch.on{display:flex}.hidden{display:none!important}.hint{font-size:11px;color:#6b7280;text-align:center;letter-spacing:1px}@media(max-height:420px){.app{padding:7px;grid-template-rows:28px 1fr 24px}.face{grid-template-columns:repeat(3,50px);grid-template-rows:repeat(3,50px);gap:6px}.shoulders{inset:38px 12px auto}.shoulders button{padding:7px}}
</style></head><body><main class=app><header class=bar><span class=brand>AMAYOMI RETRO</span><span id=state class=state><i class=dot></i>Conectando</span><span id=player>Jugador —</span></header><div class=shoulders><button data-c=L>L</button><button data-c=R>R</button></div><section class=layout><div id=normalLeft class='zone'><div id=stick class=stick><div id=thumb class=thumb></div></div></div><div class=center><div class=mode><button id=normalMode class=active>Control</button><button id=touchMode>DS táctil</button></div><div class=system><button data-c=Select>Select</button><button data-c=Start>Start</button><button data-c=Pausa>⏸ Pausa</button><button data-c=Salir class=exit>Salir</button></div><div id=touch class=touch>Panel táctil DS</div></div><div id=normalRight class=zone><div class=face><span></span><button class=x data-c=X>X</button><span></span><button class=y data-c=Y>Y</button><span></span><button class=a data-c=A>A</button><span></span><button class=b data-c=B>B</button><span></span></div></div></section><footer class=hint>CREADO Y FUNDADO POR JOSÉ AMAYA</footer></main><script>
let ws,player=0;const state=document.getElementById('state'),playerEl=document.getElementById('player');function connect(){ws=new WebSocket('ws://'+location.host);ws.onopen=()=>{state.className='state online';state.innerHTML='<i class=dot></i>Conectado'};ws.onclose=()=>{state.className='state';state.innerHTML='<i class=dot></i>Reconectando';setTimeout(connect,1200)};ws.onmessage=e=>{if(e.data.indexOf('player:')===0){player=e.data.split(':')[1];playerEl.textContent='Jugador '+player}}}connect();function send(v){if(ws&&ws.readyState===1)ws.send(v)}function buzz(){if(navigator.vibrate)navigator.vibrate(8)}document.querySelectorAll('[data-c]').forEach(b=>{const c=b.dataset.c,down=e=>{e.preventDefault();b.setPointerCapture&&b.setPointerCapture(e.pointerId);send(c+':down');buzz()},up=e=>{e.preventDefault();send(c+':up')};b.addEventListener('pointerdown',down);b.addEventListener('pointerup',up);b.addEventListener('pointercancel',up);b.addEventListener('pointerleave',e=>{if(e.buttons)up(e)})});const stick=document.getElementById('stick'),thumb=document.getElementById('thumb');function moveStick(e){const r=stick.getBoundingClientRect(),x=Math.max(-1,Math.min(1,(e.clientX-r.left-r.width/2)/(r.width/2))),y=Math.max(-1,Math.min(1,(e.clientY-r.top-r.height/2)/(r.height/2)));thumb.style.left=(31+x*25)+'%';thumb.style.top=(31+y*25)+'%';send('joystick:'+x.toFixed(3)+':'+y.toFixed(3))}stick.addEventListener('pointerdown',e=>{stick.setPointerCapture(e.pointerId);moveStick(e)});stick.addEventListener('pointermove',e=>{if(e.buttons)moveStick(e)});['pointerup','pointercancel'].forEach(n=>stick.addEventListener(n,()=>{thumb.style.left='31%';thumb.style.top='31%';send('joystick:0:0')}));const touch=document.getElementById('touch');function touchPoint(e,phase){const r=touch.getBoundingClientRect(),x=Math.max(0,Math.min(1,(e.clientX-r.left)/r.width)),y=Math.max(0,Math.min(1,(e.clientY-r.top)/r.height));send('ds-touch:'+phase+':'+x.toFixed(4)+':'+y.toFixed(4))}touch.addEventListener('pointerdown',e=>{touch.setPointerCapture(e.pointerId);touchPoint(e,'down')});touch.addEventListener('pointermove',e=>{if(e.buttons)touchPoint(e,'move')});['pointerup','pointercancel'].forEach(n=>touch.addEventListener(n,e=>touchPoint(e,'up')));function setMode(ds){touch.classList.toggle('on',ds);document.getElementById('normalLeft').classList.toggle('hidden',ds);document.getElementById('normalRight').classList.toggle('hidden',ds);document.getElementById('normalMode').classList.toggle('active',!ds);document.getElementById('touchMode').classList.toggle('active',ds)}document.getElementById('normalMode').onclick=()=>setMode(false);document.getElementById('touchMode').onclick=()=>setMode(true);
</script></body></html>"""
}
