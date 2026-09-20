package com.retrosala.app

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.retrosala.app.catalog.DemoCatalogApi
import com.retrosala.app.catalog.GameCatalogItem
import com.retrosala.app.controller.DemoMobileControllerGateway
import com.retrosala.app.emulation.ControllerInput
import com.retrosala.app.emulation.DemoRemoteEmulationSession
import com.retrosala.app.emulation.RemoteSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { RetroSalaScreen() } }
    }
}

class RetroSalaViewModel : ViewModel() {
    private val catalog = DemoCatalogApi()
    private val session = DemoRemoteEmulationSession()
    private val controller = DemoMobileControllerGateway()
    private val _games = MutableStateFlow<List<GameCatalogItem>>(emptyList())
    val games: StateFlow<List<GameCatalogItem>> = _games
    val status = session.status
    val controllerUrl = controller.controllerUrl
    private val _lastControl = MutableStateFlow("Ningún control recibido")
    val lastControl: StateFlow<String> = _lastControl

    init { viewModelScope.launch { _games.value = catalog.listGames() } }

    fun start(game: GameCatalogItem) = viewModelScope.launch { session.start(game) }
    fun press(control: String) = viewModelScope.launch {
        _lastControl.value = "Jugador 1: $control"
        session.sendInput(ControllerInput(1, control, true))
        controller.publish(ControllerInput(1, control, true))
    }
    fun pause() = viewModelScope.launch { session.pause() }
    fun close() = viewModelScope.launch { session.close() }
}

@Composable
private fun RetroSalaScreen(vm: RetroSalaViewModel = viewModel()) {
    val games by vm.games.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val url by vm.controllerUrl.collectAsStateWithLifecycle()
    val lastControl by vm.lastControl.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<GameCatalogItem?>(null) }
    Row(Modifier.fillMaxSize().background(Color(0xFF10111A)).padding(32.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("RetroSala", color = Color.White, fontSize = 36.sp)
            Text("Cliente de streaming remoto · modo demostración", color = Color(0xFFB9B8C5), fontSize = 16.sp)
            Text("Biblioteca", color = Color.White, fontSize = 24.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(games) { game ->
                    GameCard(game, selected?.gameId == game.gameId) { selected = game }
                }
            }
            Text("Estado: ${status.label()}", color = Color(0xFFA9F5C5), fontSize = 18.sp)
            Text(lastControl, color = Color.White, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = selected != null, onClick = { selected?.let(vm::start) }) { Text("Iniciar demostración") }
                Button(onClick = vm::pause) { Text("Pausar") }
                Button(onClick = vm::close) { Text("Cerrar sesión") }
            }
            DemoDisplay(status, lastControl)
        }
        Spacer(Modifier.width(32.dp))
        PairingPanel(url)
    }
}

@Composable
private fun GameCard(game: GameCatalogItem, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(180.dp).height(145.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(game.title, fontSize = 18.sp)
            Text(game.platform.label, color = Color(0xFF665C80))
            Text("${game.players} jugador(es) · ${game.language}", fontSize = 13.sp)
            if (selected) Text("Seleccionado", color = Color(0xFF5332D4))
        }
    }
}

@Composable
private fun DemoDisplay(status: RemoteSessionStatus, lastControl: String) {
    Card(Modifier.fillMaxWidth().height(155.dp)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF202232)).padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text("Transmisión remota simulada", color = Color.White, fontSize = 24.sp)
            Text(status.label(), color = Color(0xFFB9B8C5))
            Text(lastControl, color = Color(0xFFA9F5C5))
        }
    }
}

@Composable
private fun PairingPanel(url: String) {
    val qr = remember(url) { createQr(url) }
    Column(Modifier.width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Conecta tu mando", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(14.dp))
        Image(qr.asImageBitmap(), "Código QR para mando", Modifier.width(220.dp).height(220.dp).background(Color.White))
        Spacer(Modifier.height(12.dp))
        Text("Modo demostración", color = Color(0xFFA9F5C5))
        Text(url, color = Color(0xFFB9B8C5), fontSize = 12.sp)
    }
}

private fun createQr(value: String): Bitmap {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 220, 220)
    return Bitmap.createBitmap(220, 220, Bitmap.Config.RGB_565).also { bitmap ->
        for (x in 0 until 220) for (y in 0 until 220) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}

private fun RemoteSessionStatus.label() = when (this) {
    RemoteSessionStatus.Idle -> "Sin sesión"
    RemoteSessionStatus.Connecting -> "Conectando al servidor remoto…"
    is RemoteSessionStatus.Streaming -> "Transmitiendo sesión ${session.sessionId}"
    RemoteSessionStatus.Paused -> "Sesión pausada"
    is RemoteSessionStatus.Disconnected -> reason
    is RemoteSessionStatus.Failed -> "Error: $message"
}
