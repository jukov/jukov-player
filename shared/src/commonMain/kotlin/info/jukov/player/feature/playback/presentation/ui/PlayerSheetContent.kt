package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.presentation.ui.rememberArtworkPalette
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.track.domain.Track

@Composable
internal fun PlayerSheetContent(
    snapshot: PlayerUiState,
    error: AppError?,
    viewModel: PlayerViewModel,
    favoriteEnabled: Boolean,
    downloadStatus: DownloadStatus?,
    peekHeight: Dp,
    sheetOffset: () -> Float?,
    onExpand: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onArtistClick: (Track) -> Unit,
    onAlbumClick: (Track) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val track = snapshot.currentTrack ?: return@BoxWithConstraints
        val paletteKey = track.coverArtId ?: track.coverArtUrl ?: track.albumId
        val palette = rememberArtworkPalette(
            key = paletteKey,
            url = track.coverArtUrl,
        )
        val density = LocalDensity.current
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val peekHeightPx = with(density) { peekHeight.toPx() }
        val expansionProgress by remember(containerHeightPx, peekHeightPx) {
            derivedStateOf {
                val offset = sheetOffset() ?: (containerHeightPx - peekHeightPx)
                ((containerHeightPx - offset - peekHeightPx) /
                    (containerHeightPx - peekHeightPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
        }

        FullPlayer(
            snapshot = snapshot,
            palette = palette,
            error = error,
            viewModel = viewModel,
            favoriteEnabled = favoriteEnabled,
            downloadStatus = downloadStatus,
            onOpenQueue = onOpenQueue,
            onAddToPlaylist = onAddToPlaylist,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = expansionProgress }
                .zIndex(expansionProgress),
        )
        MiniPlayer(
            snapshot = snapshot,
            palette = palette,
            containerHeight = peekHeight,
            onOpen = onExpand,
            onPlayPause = viewModel::playPause,
            onFavorite = viewModel::toggleFavorite,
            favoriteEnabled = favoriteEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f - expansionProgress },
        )
    }
}
