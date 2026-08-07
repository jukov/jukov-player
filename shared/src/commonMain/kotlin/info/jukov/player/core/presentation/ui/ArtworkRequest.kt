package info.jukov.player.core.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

@Composable
fun rememberArtworkRequest(
    url: String,
    albumId: String?,
    requestedSize: Int,
): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, url, albumId, requestedSize) {
        val isRemote = url.startsWith("http://") || url.startsWith("https://")
        ImageRequest.Builder(context)
            .data(if (isRemote) url.withCoverArtSize(requestedSize) else url)
            .apply {
                albumId?.takeIf { isRemote }?.let {
                    val server = url.substringBefore("/rest/").trimEnd('/')
                    val cacheKey = "$server:$it:$requestedSize"
                    memoryCacheKey(cacheKey)
                    diskCacheKey(cacheKey)
                }
            }
            .build()
    }
}

private fun String.withCoverArtSize(size: Int): String =
    "$this${if ('?' in this) '&' else '?'}size=$size"

const val SMALL_ARTWORK_SIZE = 192
const val LARGE_ARTWORK_SIZE = 512
