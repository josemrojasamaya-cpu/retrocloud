package com.retrosala.app.controller

import com.retrosala.app.emulation.ControllerInput

/** Protocolo compatible con `control:down` y ampliado para joystick y panel táctil DS. */
fun parseControllerMessage(player: Int, payload: String): ControllerInput? {
    val parts = payload.split(":")
    return when (parts.firstOrNull()) {
        "joystick" -> parseCoordinates(player, "joystick", true, parts.drop(1), -1f..1f)
        "ds-touch" -> {
            if (parts.size != 4 || parts[1] !in setOf("down", "move", "up")) null
            else parseCoordinates(player, "ds-touch-${parts[1]}", parts[1] != "up", parts.drop(1), 0f..1f)
        }
        else -> if (parts.size == 2 && parts[1] in setOf("down", "up")) {
            ControllerInput(player, parts[0], parts[1] == "down")
        } else null
    }
}

private fun parseCoordinates(
    player: Int,
    control: String,
    pressed: Boolean,
    parts: List<String>,
    range: ClosedFloatingPointRange<Float>
): ControllerInput? {
    val offset = if (parts.firstOrNull() == "down" || parts.firstOrNull() == "move" || parts.firstOrNull() == "up") 1 else 0
    val x = parts.getOrNull(offset)?.toFloatOrNull()
    val y = parts.getOrNull(offset + 1)?.toFloatOrNull()
    return if (x != null && y != null && x in range && y in range) ControllerInput(player, control, pressed, x, y) else null
}
