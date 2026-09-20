package com.retrosala.app

import com.retrosala.app.catalog.GameCatalogItem
import com.retrosala.app.catalog.Platform
import com.retrosala.app.catalog.gamesForPlatform
import com.retrosala.app.controller.parseControllerMessage
import com.retrosala.app.emulation.DemoRemoteEmulationSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAndControllerTest {
    private val gba = GameCatalogItem("gba-1", "GBA", Platform.GBA, "es", "server", true, 1, "")
    private val nds = GameCatalogItem("nds-1", "NDS", Platform.NDS, "es", "server", true, 1, "")
    private val hidden = GameCatalogItem("nds-hidden", "Hidden", Platform.NDS, "es", "server", false, 1, "")

    @Test fun `filters only available GBA games`() {
        assertEquals(listOf("gba-1"), gamesForPlatform(listOf(gba, nds, hidden), Platform.GBA).map { it.gameId })
    }

    @Test fun `filters only available NDS games and hides unavailable`() {
        assertEquals(listOf("nds-1"), gamesForPlatform(listOf(gba, nds, hidden), Platform.NDS).map { it.gameId })
    }

    @Test fun `keeps legacy QR button protocol`() {
        val input = parseControllerMessage(1, "A:down")
        assertNotNull(input)
        assertEquals("A", input!!.control)
        assertTrue(input.pressed)
    }

    @Test fun `parses joystick and DS touch events`() {
        val joystick = parseControllerMessage(1, "joystick:-0.500:0.250")!!
        assertEquals(-.5f, joystick.normalizedX!!)
        assertEquals(.25f, joystick.normalizedY!!)
        val touch = parseControllerMessage(1, "ds-touch:move:0.2500:0.7500")!!
        assertEquals("ds-touch-move", touch.control)
        assertTrue(touch.pressed)
        assertFalse(parseControllerMessage(1, "ds-touch:down:2:0") != null)
    }

    @Test fun `keeps remote session demo compatible`() = runBlocking {
        val session = DemoRemoteEmulationSession()
        val remote = session.start(gba)
        assertEquals("gba-1", remote.gameId)
    }
}
