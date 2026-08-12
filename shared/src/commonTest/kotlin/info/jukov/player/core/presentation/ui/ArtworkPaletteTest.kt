package info.jukov.player.core.presentation.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtworkPaletteTest {
    @Test
    fun playerGradientColorsAreOpaqueAndEndAtSurface() {
        val surface = Color(0xFF101820)
        val palette = ArtworkPalette(
            primary = Color(0xFFCC3300),
            secondary = Color(0xFF0066CC),
        )

        val colors = palette.playerGradientColors(surface)

        assertEquals(3, colors.size)
        assertEquals(1f, colors[0].alpha)
        assertEquals(1f, colors[1].alpha)
        assertEquals(surface, colors[2])
    }
}
