package com.retrosala.app.emulation

import com.retrosala.app.catalog.GameCatalogItem
import com.retrosala.app.config.ServerConfiguration
import kotlinx.coroutines.flow.StateFlow

data class RemoteSession(
    val sessionId: String,
    val gameId: String,
    val streamingUrl: String,
    val controlEndpoint: String
)

data class VideoFrame(val encodedPayload: ByteArray, val timestampMs: Long)
data class AudioPacket(val encodedPayload: ByteArray, val timestampMs: Long)
data class ControllerInput(
    val player: Int,
    val control: String,
    val pressed: Boolean,
    val normalizedX: Float? = null,
    val normalizedY: Float? = null
)

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

/** Cliente de una sesión ejecutada únicamente en el servidor de RetroSala. */
class ApiRemoteEmulationSession(
    private val api: SessionApi,
    private val configuration: ServerConfiguration
) : RemoteEmulationSession {
    private val _status = kotlinx.coroutines.flow.MutableStateFlow<RemoteSessionStatus>(RemoteSessionStatus.Idle)
    private val _video = kotlinx.coroutines.flow.MutableStateFlow<VideoFrame?>(null)
    private val _audio = kotlinx.coroutines.flow.MutableStateFlow<AudioPacket?>(null)
    private var activeSessionId: String? = null
    override val status: StateFlow<RemoteSessionStatus> = _status

    override suspend fun start(game: GameCatalogItem): RemoteSession {
        _status.value = RemoteSessionStatus.Connecting
        return try {
            val bootstrap = api.createSession(game.gameId)
            activeSessionId = bootstrap.sessionId
            RemoteSession(bootstrap.sessionId, game.gameId, configuration.streamingUrl, configuration.signalingUrl).also {
                _status.value = RemoteSessionStatus.Streaming(it)
            }
        } catch (error: Exception) {
            _status.value = RemoteSessionStatus.Failed(error.message ?: "No se pudo crear la sesión")
            throw error
        }
    }
    override suspend fun selectGame(gameId: String) = Unit
    override suspend fun sendInput(input: ControllerInput) { activeSessionId?.let { api.sendControl(it, input) } }
    override fun videoFrames(): StateFlow<VideoFrame?> = _video
    override fun audioPackets(): StateFlow<AudioPacket?> = _audio
    override suspend fun pause() { activeSessionId?.let { api.pause(it); _status.value = RemoteSessionStatus.Paused } }
    override suspend fun saveGame() { activeSessionId?.let { sessionId -> api.save(sessionId) } }
    override suspend fun close() {
        activeSessionId?.let { sessionId -> api.close(sessionId) }; activeSessionId = null
        _status.value = RemoteSessionStatus.Disconnected("Sesión remota cerrada")
    }
}
