package info.jukov.player.core.presentation.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

data class ArtworkPalette(
    val primary: Color,
    val secondary: Color,
)

fun interface ArtworkPaletteExtractor {
    suspend fun extract(key: String, url: String): ArtworkPalette?
}

val LocalArtworkPaletteExtractor = staticCompositionLocalOf<ArtworkPaletteExtractor?> { null }

@Composable
fun rememberArtworkPalette(
    key: String?,
    url: String?,
): ArtworkPalette {
    val extractor = LocalArtworkPaletteExtractor.current
    val hash = key.orEmpty().fold(17) { result, char -> result * 31 + char.code }
    val hue = ((hash.toLong() and 0x7fffffff) % 360).toFloat()
    val fallback = ArtworkPalette(
        primary = Color.hsv(hue, .45f, .68f),
        secondary = Color.hsv((hue + 42f) % 360f, .35f, .55f),
    )
    val extracted = remember(key, url, extractor) { mutableStateOf<ArtworkPalette?>(null) }
    LaunchedEffect(key, url, extractor) {
        extracted.value = if (key != null && url != null) {
            extractor?.extract(key, url)
        } else {
            null
        }
    }
    val target = extracted.value ?: fallback
    val primary by animateColorAsState(target.primary)
    val secondary by animateColorAsState(target.secondary)
    return ArtworkPalette(primary, secondary)
}

fun ArtworkPalette.playerGradientColors(surface: Color): List<Color> {
    return listOf(
        primary.copy(alpha = .72f).compositeOver(surface),
        secondary.copy(alpha = .35f).compositeOver(surface),
        surface,
    )
}
