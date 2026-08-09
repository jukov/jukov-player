package info.jukov.player.core.presentation.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ArtworkPalette(
    val primary: Color,
    val secondary: Color,
)

fun interface ArtworkPaletteExtractor {
    suspend fun extract(key: String, url: String): ArtworkPalette?
}

val LocalArtworkPaletteExtractor = staticCompositionLocalOf<ArtworkPaletteExtractor?> { null }
