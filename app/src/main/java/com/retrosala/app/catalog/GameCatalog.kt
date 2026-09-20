package com.retrosala.app.catalog

enum class Platform(val label: String) { GBA("Game Boy Advance"), DS("Nintendo DS") }

data class GameCatalogItem(
    val gameId: String,
    val title: String,
    val platform: Platform,
    val language: String,
    val emulationServer: String,
    val streamingAvailable: Boolean,
    val players: Int,
    val description: String
)

/** Reemplazable por una API HTTP. No contiene URL de ROM ni de descarga. */
interface CatalogApi {
    suspend fun listGames(): List<GameCatalogItem>
}

class DemoCatalogApi : CatalogApi {
    override suspend fun listGames() = listOf(
        GameCatalogItem("gba-demo-adventure", "Aventura GBA", Platform.GBA, "es", "demo-gba-01", true, 1, "Sesión remota de prueba."),
        GameCatalogItem("gba-demo-racing", "Carreras GBA", Platform.GBA, "es", "demo-gba-01", true, 2, "Sesión remota multijugador de prueba."),
        GameCatalogItem("ds-demo-farm", "Granja DS", Platform.DS, "es", "demo-ds-01", true, 1, "Sesión remota de prueba."),
        GameCatalogItem("ds-demo-puzzle", "Puzzle DS", Platform.DS, "es", "demo-ds-01", true, 2, "Sesión remota multijugador de prueba.")
    )
}
