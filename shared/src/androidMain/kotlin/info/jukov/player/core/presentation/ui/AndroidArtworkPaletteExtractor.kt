package info.jukov.player.core.presentation.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

class AndroidArtworkPaletteExtractor(
    context: Context,
) : ArtworkPaletteExtractor {
    private val appContext = context.applicationContext
    private val cache = LruCache<String, ArtworkPalette>(64)
    private val loadMutex = Mutex()

    override suspend fun extract(key: String, url: String): ArtworkPalette? {
        cache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            loadMutex.withLock {
                cache.get(key)?.let { return@withLock it }
                val thumbnailUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                    "$url${if ('?' in url) '&' else '?'}size=$SAMPLE_SIZE"
                } else {
                    url
                }
                val cacheKey = "artwork-palette:$key"
                val request = ImageRequest.Builder(appContext)
                    .data(thumbnailUrl)
                    .size(Size(SAMPLE_SIZE, SAMPLE_SIZE))
                    .allowHardware(false)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .build()
                val result = SingletonImageLoader.get(appContext).execute(request) as? SuccessResult
                    ?: return@withLock null
                val bitmap = result.image.toBitmap(
                    width = SAMPLE_SIZE,
                    height = SAMPLE_SIZE,
                    config = Bitmap.Config.ARGB_8888,
                )
                extractPalette(bitmap.getPixels()).also { palette ->
                    if (palette != null) {
                        cache.put(key, palette)
                    }
                }
            }
        }
    }

    private fun android.graphics.Bitmap.getPixels(): IntArray = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }

    private fun extractPalette(pixels: IntArray): ArtworkPalette? {
        val buckets = HashMap<Int, Int>()
        pixels.forEach { pixel ->
            if (AndroidColor.alpha(pixel) < 192) {
                return@forEach
            }
            val red = AndroidColor.red(pixel)
            val green = AndroidColor.green(pixel)
            val blue = AndroidColor.blue(pixel)
            val max = maxOf(red, green, blue)
            val min = minOf(red, green, blue)
            if (max < 24 || min > 236 || max - min < 12) {
                return@forEach
            }
            val bucket = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
            buckets[bucket] = (buckets[bucket] ?: 0) + 1 + (max - min) / 32
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

    private fun Color.hue(): Float = FloatArray(3).also {
        AndroidColor.colorToHSV(toArgb(), it)
    }[0]

    private fun Color.shiftHue(degrees: Float): Color {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(toArgb(), hsv)
        hsv[0] = (hsv[0] + degrees) % 360f
        return Color(AndroidColor.HSVToColor(hsv))
    }

    private fun hueDistance(first: Float, second: Float): Float {
        val distance = abs(first - second)
        return minOf(distance, 360f - distance)
    }

    private companion object {
        const val SAMPLE_SIZE = 32
    }
}
