package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import info.jukov.player.core.presentation.ui.LARGE_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.track_cover
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerArtworkPager(
    snapshot: PlayerUiState,
    viewModel: PlayerViewModel,
) {
    val pagerState = rememberPagerState(
        initialPage = snapshot.currentIndex,
        pageCount = { snapshot.queue.size },
    )
    val currentIndex by rememberUpdatedState(snapshot.currentIndex)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                when (page) {
                    currentIndex - 1 -> viewModel.previous()
                    currentIndex + 1 -> viewModel.next()
                    else -> {
                        if (page > currentIndex) {
                            viewModel.playAt(page)
                        }
                    }
                }
            }
    }
    LaunchedEffect(snapshot.currentIndex) {
        if (pagerState.currentPage != snapshot.currentIndex) {
            pagerState.animateScrollToPage(snapshot.currentIndex)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(maxWidth - 72.dp),
            key = { page -> snapshot.queue[page].uiId },
        ) { page ->
            val displayedTrack = snapshot.queue[page].track
            Artwork(
                url = displayedTrack.coverArtUrl,
                albumId = displayedTrack.albumId,
                requestedSize = LARGE_ARTWORK_SIZE,
                description = stringResource(Res.string.track_cover, displayedTrack.title),
                modifier = Modifier
                    .padding(horizontal = 36.dp)
                    .fillMaxSize()
                    .graphicsLayer {
                        shadowElevation = 28.dp.toPx()
                        shape = RoundedCornerShape(24.dp)
                        clip = true
                    },
            )
        }
    }
}

@Composable
internal fun Artwork(
    url: String?,
    albumId: String?,
    requestedSize: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url != null) {
            AsyncImage(
                model = rememberArtworkRequest(url, albumId, requestedSize),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
