package com.retrosala.app

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.retrosala.app.catalog.CatalogApi
import com.retrosala.app.catalog.DemoCatalogApi
import com.retrosala.app.catalog.GameCatalogItem
import com.retrosala.app.catalog.Platform
import com.retrosala.app.catalog.RemoteCatalogApi
import com.retrosala.app.catalog.gamesForPlatform
import com.retrosala.app.config.RuntimeServerConfiguration
import com.retrosala.app.controller.LocalWebSocketControllerGateway
import com.retrosala.app.emulation.ApiRemoteEmulationSession
import com.retrosala.app.emulation.ControllerInput
import com.retrosala.app.emulation.DemoRemoteEmulationSession
import com.retrosala.app.emulation.HttpSessionApi
import com.retrosala.app.emulation.RemoteSessionStatus
import com.retrosala.app.streaming.MjpegStreamReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val Ink = Color(0xFF0A0A14)
private val Panel = Color(0xFF161B34)
private val Violet = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF22D3EE)
private val Magenta = Color(0xFFEC4899)
private val Lime = Color(0xFFB9FF3B)
private val Soft = Color(0xFFA1A5BE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AmayomiRetroScreen() } }
    }
}

class RetroSalaViewModel : ViewModel() {
    private val serverConfiguration = RuntimeServerConfiguration.fromBuildConfig()
    private val api = if (serverConfiguration.usesRemoteSessionApi) HttpSessionApi(serverConfiguration) else null
    private val catalog: CatalogApi = api?.let(::RemoteCatalogApi) ?: DemoCatalogApi()
    private val session = if (api != null) ApiRemoteEmulationSession(api, serverConfiguration) else DemoRemoteEmulationSession()
    private val controller = LocalWebSocketControllerGateway()
    private val _games = MutableStateFlow<List<GameCatalogItem>>(emptyList())
    val games: StateFlow<List<GameCatalogItem>> = _games
    val status = session.status
    val controllerUrl = controller.controllerUrl
    val connectedControllers = controller.connectedControllers
    private val _lastControl = MutableStateFlow("Esperando mando móvil")
    val lastControl: StateFlow<String> = _lastControl

    private var mjpegReader: MjpegStreamReader? = null
    private val _streamFrame = MutableStateFlow<Bitmap?>(null)
    val streamFrame: StateFlow<Bitmap?> = _streamFrame
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    init {
        viewModelScope.launch { _games.value = catalog.listGames().filter { it.available } }
        viewModelScope.launch { controller.start() }
        viewModelScope.launch {
            controller.inputs.collect { input ->
                _lastControl.value = if (input.normalizedX != null) "Jugador ${input.player}: ${input.control}" else "Jugador ${input.player}: ${input.control}"
                if (input.pressed && input.control == "Pausa") session.pause()
                else if (input.pressed && input.control == "Salir") session.close()
                else session.sendInput(input)
            }
        }
    }

    fun start(game: GameCatalogItem) = viewModelScope.launch {
        session.start(game)
        if (serverConfiguration.streamingUrl.isNotBlank()) {
            startStream(serverConfiguration.streamingUrl)
        } else if (serverConfiguration.apiUrl.isNotBlank()) {
            startStream("${serverConfiguration.apiUrl}/v1/stream")
        }
    }

    private fun startStream(url: String) {
        mjpegReader?.stop()
        val reader = MjpegStreamReader(url)
        mjpegReader = reader
        _isStreaming.value = true
        viewModelScope.launch {
            reader.frame.collect { frame -> frame?.let { _streamFrame.value = it } }
        }
        viewModelScope.launch { reader.start() }
    }

    fun pause() = viewModelScope.launch { session.pause() }
    fun close() = viewModelScope.launch {
        session.close()
        mjpegReader?.stop()
        mjpegReader = null
        _isStreaming.value = false
        _streamFrame.value = null
    }
}

@Composable
private fun AmayomiRetroScreen(vm: RetroSalaViewModel = viewModel()) {
    val games by vm.games.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val url by vm.controllerUrl.collectAsStateWithLifecycle()
    val controllers by vm.connectedControllers.collectAsStateWithLifecycle()
    val lastControl by vm.lastControl.collectAsStateWithLifecycle()
    val streaming by vm.isStreaming.collectAsStateWithLifecycle()
    val frame by vm.streamFrame.collectAsStateWithLifecycle()
    var platform by remember { mutableStateOf<Platform?>(null) }
    var selectedGame by remember { mutableStateOf<GameCatalogItem?>(null) }
    val library = platform?.let { gamesForPlatform(games, it) }.orEmpty()

    if (streaming && frame != null) {
        StreamingScreen(frame!!, url, controllers.size, lastControl, onClose = vm::close)
    } else {
        CatalogScreen(
            games, status, url, controllers, lastControl,
            platform, selectedGame, library,
            onSelectPlatform = { platform = it },
            onBack = { platform = null; selectedGame = null },
            onSelectGame = { selectedGame = it },
            onStart = { selectedGame?.let(vm::start) },
            onPause = vm::pause,
            onClose = vm::close
        )
    }
}

@Composable
private fun StreamingScreen(
    frame: Bitmap, controllerUrl: String, controllerCount: Int, lastControl: String, onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = "Pantalla del juego",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
        Row(
            Modifier.align(Alignment.TopStart).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC161B34)), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AMAYOMI RETRO", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("·", color = Soft, fontSize = 13.sp)
                    Text(lastControl, color = Soft, fontSize = 12.sp)
                }
            }
        }
        Button(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC4C1D3B))
        ) { Text("Salir", color = Color.White, fontWeight = FontWeight.Bold) }
        Card(
            Modifier.align(Alignment.BottomEnd).padding(16.dp).width(180.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC10162B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val qr = remember(controllerUrl) { createQr(controllerUrl) }
                Image(qr.asImageBitmap(), "QR mando", Modifier.width(100.dp).height(100.dp).background(Color.White))
                Spacer(Modifier.height(6.dp))
                Text("$controllerCount jugador(es)", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    games: List<GameCatalogItem>, status: RemoteSessionStatus, url: String,
    controllers: List<com.retrosala.app.controller.ConnectedController>, lastControl: String,
    platform: Platform?, selectedGame: GameCatalogItem?, library: List<GameCatalogItem>,
    onSelectPlatform: (Platform) -> Unit, onBack: () -> Unit, onSelectGame: (GameCatalogItem) -> Unit,
    onStart: () -> Unit, onPause: () -> Unit, onClose: () -> Unit
) {
    Row(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF1A0A2E), Color(0xFF0D1117), Color(0xFF0A1628)),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(1000f, 800f)
            )
        ).padding(30.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header(platform, onBack)
            if (platform == null) {
                Text("Elige tu plataforma", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                PlatformRow(onSelect = onSelectPlatform, onUpcoming = {})
                PremiumStatus(status, lastControl)
            } else {
                Text(platform.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Biblioteca disponible", color = Soft, fontSize = 17.sp)
                if (library.isEmpty()) EmptyLibrary(platform)
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(library) { game -> GameCard(game, selectedGame?.gameId == game.gameId) { onSelectGame(game) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = selectedGame != null,
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)
                    ) { Text("Iniciar sesión", fontWeight = FontWeight.Bold) }
                    Button(onClick = onPause, colors = ButtonDefaults.buttonColors(containerColor = Panel)) { Text("Pausar") }
                    Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Panel)) { Text("Salir") }
                }
                PremiumStatus(status, lastControl)
            }
            Spacer(Modifier.weight(1f))
            AboutAmayomiRetro()
        }
        PairingPanel(url, controllers.size)
    }
}

@Composable
private fun Header(platform: Platform?, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Image(painterResource(R.drawable.amayomi_retro_logo), "Logo Amayomi Retro", Modifier.width(280.dp).height(90.dp))
        if (platform != null) Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Panel)) { Text("Plataformas") }
    }
}

@Composable
private fun PlatformRow(onSelect: (Platform) -> Unit, onUpcoming: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PlatformCard("Game Boy Advance", "GBA", Violet, true) { onSelect(Platform.GBA) }
        PlatformCard("Nintendo DS", "NDS", Cyan, true) { onSelect(Platform.NDS) }
        PlatformCard("PlayStation 1", "PRÓXIMAMENTE", Magenta, false, onUpcoming)
        PlatformCard("PlayStation 2", "PRÓXIMAMENTE", Color(0xFF6366F1), false, onUpcoming)
    }
}

@Composable
private fun PlatformCard(title: String, subtitle: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(190.dp).height(185.dp).alpha(if (enabled) 1f else .58f).clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("▶", color = accent, fontSize = 34.sp)
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = if (enabled) Lime else Soft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GameCard(game: GameCatalogItem, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.width(230.dp).height(180.dp), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF2A2455) else Panel)
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(game.platform.label, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(game.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text("${game.players} jugador(es) · ${game.language}", color = Soft, fontSize = 14.sp)
            if (selected) Text("Seleccionado", color = Color(0xFFD9CCFF), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyLibrary(platform: Platform) {
    Card(Modifier.fillMaxWidth().height(180.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay juegos disponibles para ${platform.label}.", color = Soft, fontSize = 20.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PremiumStatus(status: RemoteSessionStatus, lastControl: String) {
    Card(Modifier.fillMaxWidth().height(120.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sala remota", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(status.label(), color = Color.White, fontSize = 19.sp)
            Text(lastControl, color = Soft, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PairingPanel(url: String, controllerCount: Int) {
    val qr = remember(url) { createQr(url) }
    Card(Modifier.width(300.dp).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color(0xFF10162B)), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Mando móvil", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Escanea y juega desde tu celular", color = Soft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Image(qr.asImageBitmap(), "Código QR para mando móvil", Modifier.width(220.dp).height(220.dp).background(Color.White))
            Spacer(Modifier.height(16.dp))
            Text("$controllerCount jugador(es) conectado(s)", color = Cyan, fontWeight = FontWeight.Bold)
            Text(url, color = Soft, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AboutAmayomiRetro() {
    Text("AMAYOMI RETRO · Creado y fundado por José Amaya", color = Soft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

private fun createQr(value: String): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 220, 220)
    return Bitmap.createBitmap(220, 220, Bitmap.Config.RGB_565).also { bitmap ->
        for (x in 0 until 220) for (y in 0 until 220) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}

private fun RemoteSessionStatus.label() = when (this) {
    RemoteSessionStatus.Idle -> "Listo para conectar"
    RemoteSessionStatus.Connecting -> "Conectando a la sala remota…"
    is RemoteSessionStatus.Streaming -> "Sesión activa"
    RemoteSessionStatus.Paused -> "Sesión pausada"
    is RemoteSessionStatus.Disconnected -> reason
    is RemoteSessionStatus.Failed -> "Error: $message"
}
