package com.retrosala.app.catalog

import com.retrosala.app.emulation.SessionApi

enum class Platform(val label: String) { GBA("Game Boy Advance"), NDS("Nintendo DS"), PS1("PlayStation 1"), PSP("PlayStation Portable") }

data class GameCatalogItem(
    val gameId: String,
    val title: String,
    val platform: Platform,
    val language: String,
    val emulationServer: String,
    val available: Boolean,
    val players: Int,
    val description: String,
    val coverUrl: String? = null
)

fun gamesForPlatform(games: List<GameCatalogItem>, platform: Platform): List<GameCatalogItem> =
    games.filter { it.platform == platform && it.available }

/** Reemplazable por una API HTTP. No contiene URL de ROM ni de descarga. */
interface CatalogApi {
    suspend fun listGames(): List<GameCatalogItem>
}

class DemoCatalogApi : CatalogApi {
    override suspend fun listGames() = listOf(
        GameCatalogItem("gba-demo-classic", "Biblioteca GBA", Platform.GBA, "es", "demo-gba-01", true, 1, "Juego de demostración disponible."),
        GameCatalogItem("nds-demo-classic", "Biblioteca Nintendo DS", Platform.NDS, "es", "demo-ds-01", true, 1, "Juego de demostración disponible.")
    )
}

/** Biblioteca privada entregada por la API; nunca contiene rutas ni enlaces de ROM. */
class RemoteCatalogApi(private val sessionApi: SessionApi) : CatalogApi {
    override suspend fun listGames() = sessionApi.listCatalog().map { game ->
        GameCatalogItem(
            gameId = game.gameId,
            title = game.title,
            platform = when (game.platform) {
                "ds" -> Platform.NDS
                "ps1" -> Platform.PS1
                "psp" -> Platform.PSP
                else -> Platform.GBA
            },
            language = game.language,
            emulationServer = game.emulationServer,
            available = game.available,
            players = game.players,
            description = game.description,
            coverUrl = game.coverUrl
        )
    }
}
