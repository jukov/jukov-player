package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.presentation.ui.ArtworkPalette
import info.jukov.player.core.presentation.ui.playerGradientColors
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.track.domain.Track

@Composable
internal fun FullPlayer(
    snapshot: PlayerUiState,
    palette: ArtworkPalette,
    error: AppError?,
    viewModel: PlayerViewModel,
    favoriteEnabled: Boolean,
    downloadStatus: DownloadStatus?,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onArtistClick: (Track) -> Unit,
    onAlbumClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = snapshot.currentTrack ?: return
    val titleLineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.headlineSmall.lineHeight.toDp()
    }
    val titleMinHeight = titleLineHeight * 2 + 4.dp
    val titleMaxHeight = titleLineHeight * 4 + 4.dp
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colorStops = palette.playerGradientColors(
                        surface = MaterialTheme.colorScheme.surface,
                    ).let { colors ->
                        arrayOf(
                            0f to colors[0],
                            .48f to colors[1],
                            1f to colors[2],
                        )
                    },
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        key(snapshot.queue) {
            PlayerArtworkPager(snapshot = snapshot, viewModel = viewModel)
        }
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(min = titleMinHeight, max = titleMaxHeight)
                .clickable(
                    enabled = track.albumId != null,
                    onClick = { onAlbumClick(track) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = track.title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                softWrap = true,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatPlayerArtistAndYear(track.artist, track.year),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clickable(
                    enabled = track.artistId != null,
                    onClick = { onArtistClick(track) },
                ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        PlayerControls(
            snapshot = snapshot,
            track = track,
            error = error,
            viewModel = viewModel,
            favoriteEnabled = favoriteEnabled,
            downloadStatus = downloadStatus,
            onAddToPlaylist = onAddToPlaylist,
            onOpenQueue = onOpenQueue,
        )
    }
}

internal fun formatPlayerArtistAndYear(artist: String, year: Int?): String =
    year?.let { "$artist · $it" } ?: artist
