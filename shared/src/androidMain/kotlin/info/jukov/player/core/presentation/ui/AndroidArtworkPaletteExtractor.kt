package info.jukov.player.core.presentation.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
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
                    .allowHardware(enable = false)
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
                extractArtworkPalette(bitmap.getPixels()).also { palette ->
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

    private companion object {
        const val SAMPLE_SIZE = 32
    }
}
