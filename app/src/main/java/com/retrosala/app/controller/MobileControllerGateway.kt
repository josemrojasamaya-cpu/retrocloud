package com.retrosala.app.controller

import com.retrosala.app.emulation.ControllerInput
import kotlinx.coroutines.flow.StateFlow

data class ConnectedController(val player: Int, val label: String)

/**
 * Puente futuro para la página web del mando. La implementación de producción
 * abrirá un WebSocket local y entregará ControllerInput a la sesión remota.
 */
interface MobileControllerGateway {
    val controllerUrl: StateFlow<String>
    val connectedControllers: StateFlow<List<ConnectedController>>
    suspend fun start()
    suspend fun stop()
    fun publish(input: ControllerInput)
}

class DemoMobileControllerGateway : MobileControllerGateway {
    private val _url = kotlinx.coroutines.flow.MutableStateFlow("http://retrosala.local/demo")
    private val _controllers = kotlinx.coroutines.flow.MutableStateFlow(listOf(ConnectedController(1, "Mando de demostración")))
    override val controllerUrl: StateFlow<String> = _url
    override val connectedControllers: StateFlow<List<ConnectedController>> = _controllers
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun publish(input: ControllerInput) = Unit
}
