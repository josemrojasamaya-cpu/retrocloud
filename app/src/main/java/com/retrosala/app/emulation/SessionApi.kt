package com.retrosala.app.emulation

import com.retrosala.app.config.ServerConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SessionBootstrap(val sessionId: String, val status: String)

interface SessionApi {
    suspend fun createSession(gameId: String): SessionBootstrap
    suspend fun sendControl(sessionId: String, input: ControllerInput)
    suspend fun pause(sessionId: String)
    suspend fun save(sessionId: String)
    suspend fun close(sessionId: String)
}

class HttpSessionApi(private val configuration: ServerConfiguration) : SessionApi {
    override suspend fun createSession(gameId: String) = request("POST", "/v1/sessions", JSONObject().put("gameId", gameId)).let {
        SessionBootstrap(it.getString("id"), it.getString("status"))
    }
    override suspend fun sendControl(sessionId: String, input: ControllerInput) {
        request("POST", "/v1/sessions/$sessionId/controls", JSONObject().put("control", input.control).put("pressed", input.pressed))
    }
    override suspend fun pause(sessionId: String) { request("POST", "/v1/sessions/$sessionId/pause", JSONObject()) }
    override suspend fun save(sessionId: String) { request("POST", "/v1/sessions/$sessionId/save", JSONObject()) }
    override suspend fun close(sessionId: String) { request("POST", "/v1/sessions/$sessionId/close", JSONObject()) }

    private suspend fun request(method: String, path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        require(configuration.apiUrl.isNotBlank()) { "RETROSALA_API_URL no está configurada" }
        val connection = (URL("${configuration.apiUrl}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 8_000; readTimeout = 12_000
            setRequestProperty("content-type", "application/json")
            if (configuration.sessionToken.isNotBlank()) setRequestProperty("authorization", "Bearer ${configuration.sessionToken}")
            doOutput = true
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299) throw SessionApiException(connection.responseCode, responseText.ifBlank { "Error de sesión" })
        JSONObject(responseText)
    }
}

class SessionApiException(val status: Int, message: String) : Exception(message)
