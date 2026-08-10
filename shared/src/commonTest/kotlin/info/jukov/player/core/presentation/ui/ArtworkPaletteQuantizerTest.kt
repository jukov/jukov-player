package info.jukov.player.core.presentation.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ArtworkPaletteQuantizerTest {
    @Test
    fun selectsDominantAndContrastingColors() {
        val red = 0xFFFF2020.toInt()
        val blue = 0xFF2040FF.toInt()
        val pixels = IntArray(100) { index ->
            if (index < 70) {
                red
            } else {
                blue
            }
        }

        val palette = assertNotNull(extractArtworkPalette(pixels))

        assertNotEquals(palette.primary, palette.secondary)
        assertEquals(1f, palette.primary.alpha)
    }

    @Test
    fun ignoresTransparentAndNeutralPixels() {
        val pixels = intArrayOf(
            0x00112233,
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF808080.toInt(),
        )

        assertEquals(null, extractArtworkPalette(pixels))
    }
}
