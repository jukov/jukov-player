package info.jukov.player.core.presentation.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.CoreGraphics.CGImageAlphaInfo
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosArtworkPaletteExtractor : ArtworkPaletteExtractor {
    private val cache = LinkedHashMap<String, ArtworkPalette>()
    private val mutex = Mutex()

    override suspend fun extract(key: String, url: String): ArtworkPalette? {
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                cache[key]?.let { return@withLock it }
                val imageUrl = NSURL.URLWithString(url) ?: return@withLock null
                val data = NSData.create(contentsOfURL = imageUrl) ?: return@withLock null
                val image = UIImage.imageWithData(data) ?: return@withLock null
                val palette = image.CGImage?.toArgbPixels()?.let(::extractArtworkPalette)
                if (palette != null) {
                    cache[key] = palette
                    while (cache.size > CACHE_SIZE) {
                        cache.remove(cache.keys.first())
                    }
                }
                palette
            }
        }
    }

    private fun kotlinx.cinterop.CPointer<cnames.structs.CGImage>.toArgbPixels(): IntArray {
        val bytes = ByteArray(SAMPLE_SIZE * SAMPLE_SIZE * BYTES_PER_PIXEL)
        val colorSpace = platform.CoreGraphics.CGColorSpaceCreateDeviceRGB()
        try {
            bytes.usePinned { pinned ->
                val context = CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = SAMPLE_SIZE.toULong(),
                    height = SAMPLE_SIZE.toULong(),
                    bitsPerComponent = 8u,
                    bytesPerRow = (SAMPLE_SIZE * BYTES_PER_PIXEL).toULong(),
                    space = colorSpace,
                    bitmapInfo = kCGBitmapByteOrder32Big or
                        CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
                ) ?: return IntArray(0)
                try {
                    CGContextDrawImage(
                        context,
                        CGRectMake(0.0, 0.0, SAMPLE_SIZE.toDouble(), SAMPLE_SIZE.toDouble()),
                        this,
                    )
                } finally {
                    CGContextRelease(context)
                }
            }
        } finally {
            platform.CoreGraphics.CGColorSpaceRelease(colorSpace)
        }
        return IntArray(SAMPLE_SIZE * SAMPLE_SIZE) { index ->
            val offset = index * BYTES_PER_PIXEL
            val red = bytes[offset].toInt() and 0xFF
            val green = bytes[offset + 1].toInt() and 0xFF
            val blue = bytes[offset + 2].toInt() and 0xFF
            val alpha = bytes[offset + 3].toInt() and 0xFF
            alpha shl 24 or (red shl 16) or (green shl 8) or blue
        }
    }

    private companion object {
        const val SAMPLE_SIZE = 32
        const val BYTES_PER_PIXEL = 4
        const val CACHE_SIZE = 64
    }
}
