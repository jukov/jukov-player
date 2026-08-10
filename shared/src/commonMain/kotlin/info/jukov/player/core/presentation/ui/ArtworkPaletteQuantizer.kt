package info.jukov.player.core.presentation.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal fun extractArtworkPalette(pixels: IntArray): ArtworkPalette? {
    val buckets = HashMap<Int, Int>()
    pixels.forEach { pixel ->
        val alpha = pixel ushr 24 and 0xFF
        if (alpha < 192) {
            return@forEach
        }
        val red = pixel ushr 16 and 0xFF
        val green = pixel ushr 8 and 0xFF
        val blue = pixel and 0xFF
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        if (maximum < 24 || minimum > 236 || maximum - minimum < 12) {
            return@forEach
        }
        val bucket = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
        buckets[bucket] = (buckets[bucket] ?: 0) + 1 + (maximum - minimum) / 32
    }
    val ranked = buckets.entries.sortedByDescending(Map.Entry<Int, Int>::value)
    val primary = ranked.firstOrNull()?.key?.toColor() ?: return null
    val primaryHue = primary.hue()
    val secondary = ranked.asSequence()
        .map { it.key.toColor() }
        .firstOrNull { candidate -> hueDistance(primaryHue, candidate.hue()) >= 28f }
        ?: primary.shiftHue(42f)
    return ArtworkPalette(primary = primary, secondary = secondary)
}

private fun Int.toColor(): Color = Color(
    red = (((this shr 8) and 0xF) * 17) / 255f,
    green = (((this shr 4) and 0xF) * 17) / 255f,
    blue = ((this and 0xF) * 17) / 255f,
)

private fun Color.hue(): Float {
    val red = red
    val green = green
    val blue = blue
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    if (delta == 0f) {
        return 0f
    }
    val raw = when (maximum) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    return if (raw < 0f) {
        raw + 360f
    } else {
        raw
    }
}

private fun Color.shiftHue(degrees: Float): Color {
    val hue = (hue() + degrees) % 360f
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val saturation = if (maximum == 0f) {
        0f
    } else {
        delta / maximum
    }
    val chroma = maximum * saturation
    val x = chroma * (1f - abs((hue / 60f) % 2f - 1f))
    val offset = maximum - chroma
    val (shiftedRed, shiftedGreen, shiftedBlue) = when (hue) {
        in 0f..<60f -> Triple(chroma, x, 0f)
        in 60f..<120f -> Triple(x, chroma, 0f)
        in 120f..<180f -> Triple(0f, chroma, x)
        in 180f..<240f -> Triple(0f, x, chroma)
        in 240f..<300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    return Color(shiftedRed + offset, shiftedGreen + offset, shiftedBlue + offset)
}

private fun hueDistance(first: Float, second: Float): Float {
    val distance = abs(first - second)
    return min(distance, 360f - distance)
}
