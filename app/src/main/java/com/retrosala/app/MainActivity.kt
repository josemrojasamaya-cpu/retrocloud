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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val Ink = Color(0xFF090B18)
private val Panel = Color(0xFF141A31)
private val Violet = Color(0xFF8D72FF)
private val Lime = Color(0xFFB9FF3B)
private val Soft = Color(0xFFC6C9D8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GranZRetroScreen() } }
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

    fun start(game: GameCatalogItem) = viewModelScope.launch { session.start(game) }
    fun pause() = viewModelScope.launch { session.pause() }
    fun close() = viewModelScope.launch { session.close() }
}

@Composable
private fun GranZRetroScreen(vm: RetroSalaViewModel = viewModel()) {
    val games by vm.games.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val url by vm.controllerUrl.collectAsStateWithLifecycle()
    val controllers by vm.connectedControllers.collectAsStateWithLifecycle()
    val lastControl by vm.lastControl.collectAsStateWithLifecycle()
    var platform by remember { mutableStateOf<Platform?>(null) }
    var selectedGame by remember { mutableStateOf<GameCatalogItem?>(null) }
    val library = platform?.let { gamesForPlatform(games, it) }.orEmpty()

    Row(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF17133A), Ink))).padding(30.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header(platform, onBack = { platform = null; selectedGame = null })
            if (platform == null) {
                Text("Elige tu plataforma", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                PlatformRow(onSelect = { platform = it }, onUpcoming = {})
                PremiumStatus(status, lastControl)
            } else {
                Text(platform!!.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Biblioteca disponible", color = Soft, fontSize = 17.sp)
                if (library.isEmpty()) EmptyLibrary(platform!!)
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(library) { game -> GameCard(game, selectedGame?.gameId == game.gameId) { selectedGame = game } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = selectedGame != null,
                        onClick = { selectedGame?.let(vm::start) },
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)
                    ) { Text("Iniciar sesión", fontWeight = FontWeight.Bold) }
                    Button(onClick = vm::pause, colors = ButtonDefaults.buttonColors(containerColor = Panel)) { Text("Pausar") }
                    Button(onClick = vm::close, colors = ButtonDefaults.buttonColors(containerColor = Panel)) { Text("Salir") }
                }
                PremiumStatus(status, lastControl)
            }
            Spacer(Modifier.weight(1f))
            AboutGranZRetro()
        }
        PairingPanel(url, controllers.size)
    }
}

@Composable
private fun Header(platform: Platform?, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Image(painterResource(R.drawable.gran_z_retro_logo), "Logo Gran Z Retro", Modifier.width(260.dp).height(70.dp))
        if (platform != null) Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Panel)) { Text("Plataformas") }
    }
}

@Composable
private fun PlatformRow(onSelect: (Platform) -> Unit, onUpcoming: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PlatformCard("Game Boy Advance", "GBA", Violet, true) { onSelect(Platform.GBA) }
        PlatformCard("Nintendo DS", "NDS", Color(0xFF00A8C7), true) { onSelect(Platform.NDS) }
        PlatformCard("PlayStation 1", "PRÓXIMAMENTE", Color(0xFF7A5AAE), false, onUpcoming)
        PlatformCard("PlayStation 2", "PRÓXIMAMENTE", Color(0xFF8D4F78), false, onUpcoming)
    }
}

@Composable
private fun PlatformCard(title: String, subtitle: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(190.dp).height(185.dp).alpha(if (enabled) 1f else .58f).clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("◆", color = accent, fontSize = 38.sp)
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
            Text(game.platform.label, color = Lime, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
            Text("Sala remota", color = Lime, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            Text("$controllerCount jugador(es) conectado(s)", color = Lime, fontWeight = FontWeight.Bold)
            Text(url, color = Soft, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AboutGranZRetro() {
    Text("GRAN Z RETRO · Creado y fundado por José Amaya", color = Soft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
