package com.retrosala.app.emulation

import com.retrosala.app.catalog.GameCatalogItem
import kotlinx.coroutines.flow.StateFlow

data class RemoteSession(
    val sessionId: String,
    val gameId: String,
    val streamingUrl: String,
    val controlEndpoint: String
)

data class VideoFrame(val encodedPayload: ByteArray, val timestampMs: Long)
data class AudioPacket(val encodedPayload: ByteArray, val timestampMs: Long)
data class ControllerInput(val player: Int, val control: String, val pressed: Boolean)

sealed interface RemoteSessionStatus {
    data object Idle : RemoteSessionStatus
    data object Connecting : RemoteSessionStatus
    data class Streaming(val session: RemoteSession) : RemoteSessionStatus
    data object Paused : RemoteSessionStatus
    data class Disconnected(val reason: String) : RemoteSessionStatus
    data class Failed(val message: String) : RemoteSessionStatus
}

/** Contrato del cliente liviano: nunca expone ROMs, BIOS ni ejecución local. */
interface RemoteEmulationSession {
    val status: StateFlow<RemoteSessionStatus>
    suspend fun start(game: GameCatalogItem): RemoteSession
    suspend fun selectGame(gameId: String)
    suspend fun sendInput(input: ControllerInput)
    fun videoFrames(): StateFlow<VideoFrame?>
    fun audioPackets(): StateFlow<AudioPacket?>
    suspend fun pause()
    suspend fun saveGame()
    suspend fun close()
}

/** Implementación visible para probar la interfaz sin API ni servidor remoto. */
class DemoRemoteEmulationSession : RemoteEmulationSession {
    private val _status = kotlinx.coroutines.flow.MutableStateFlow<RemoteSessionStatus>(RemoteSessionStatus.Idle)
    private val _video = kotlinx.coroutines.flow.MutableStateFlow<VideoFrame?>(null)
    private val _audio = kotlinx.coroutines.flow.MutableStateFlow<AudioPacket?>(null)
    override val status: StateFlow<RemoteSessionStatus> = _status
    override suspend fun start(game: GameCatalogItem): RemoteSession {
        _status.value = RemoteSessionStatus.Connecting
        val session = RemoteSession("demo-${game.gameId}", game.gameId, "demo://video", "demo://controls")
        _status.value = RemoteSessionStatus.Streaming(session)
        return session
    }
    override suspend fun selectGame(gameId: String) = Unit
    override suspend fun sendInput(input: ControllerInput) = Unit
    override fun videoFrames(): StateFlow<VideoFrame?> = _video
    override fun audioPackets(): StateFlow<AudioPacket?> = _audio
    override suspend fun pause() { _status.value = RemoteSessionStatus.Paused }
    override suspend fun saveGame() = Unit
    override suspend fun close() { _status.value = RemoteSessionStatus.Disconnected("Sesión de demostración cerrada") }
}
