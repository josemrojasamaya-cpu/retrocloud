package com.retrosala.app.config

import com.retrosala.app.BuildConfig

/** LOCAL_PC is the private Windows host on the same Wi-Fi as the projector. */
enum class ServerMode { DEMO, LOCAL, LOCAL_PC, REMOTE }

data class ServerConfiguration(
    val mode: ServerMode,
    val apiUrl: String,
    val signalingUrl: String,
    val streamingUrl: String,
    val sessionToken: String
) {
    val usesRemoteSessionApi: Boolean get() = mode != ServerMode.DEMO && apiUrl.isNotBlank()

    fun validated(): ServerConfiguration {
        if (mode == ServerMode.REMOTE) require(apiUrl.startsWith("https://")) { "El servidor remoto requiere HTTPS" }
        if (signalingUrl.isNotBlank()) require(
            signalingUrl.startsWith("wss://") || mode == ServerMode.LOCAL || mode == ServerMode.LOCAL_PC
        ) { "La señalización remota requiere WSS" }
        return this
    }
}

object RuntimeServerConfiguration {
    fun fromBuildConfig() = ServerConfiguration(
        mode = runCatching { ServerMode.valueOf(BuildConfig.RETROSALA_SERVER_MODE.uppercase()) }.getOrDefault(ServerMode.DEMO),
        apiUrl = BuildConfig.RETROSALA_API_URL.trimEnd('/'),
        signalingUrl = BuildConfig.RETROSALA_SIGNALING_URL.trimEnd('/'),
        streamingUrl = BuildConfig.RETROSALA_STREAMING_URL.trimEnd('/'),
        sessionToken = BuildConfig.RETROSALA_SESSION_TOKEN
    ).validated()
}
