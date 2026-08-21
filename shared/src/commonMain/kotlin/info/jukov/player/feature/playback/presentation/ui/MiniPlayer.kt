package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import info.jukov.player.core.presentation.ui.ArtworkPalette
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.PlayPauseButton
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.feature.playback.presentation.PlayerUiState
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.track_cover
import org.jetbrains.compose.resources.stringResource

internal val MiniPlayerHeight = 64.dp
internal val MiniPlayerContentInset = MiniPlayerHeight

@Composable
internal fun MiniPlayer(
    snapshot: PlayerUiState,
    palette: ArtworkPalette,
    containerHeight: Dp,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    favoriteEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = snapshot.currentTrack ?: return
    Box(
        modifier = modifier.fillMaxWidth().height(containerHeight)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        palette.primary.copy(alpha = .44f),
                        palette.secondary.copy(alpha = .22f),
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ),
            )
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(MiniPlayerHeight).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                url = track.coverArtUrl,
                albumId = track.albumId,
                requestedSize = SMALL_ARTWORK_SIZE,
                description = stringResource(Res.string.track_cover, track.title),
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(2.dp)),
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FavoriteToggleButton(
                isFavorite = track.isFavorite,
                onClick = onFavorite,
                enabled = favoriteEnabled,
            )
            Spacer(Modifier.width(Padding.small))
            PlayPauseButton(
                isPlaying = snapshot.isPlaying,
                isLoading = snapshot.isLoading,
                onClick = onPlayPause,
                modifier = Modifier.size(48.dp),
            )
        }
        Box(
            Modifier.align(Alignment.TopStart).padding(top = MiniPlayerHeight - 3.dp)
                .fillMaxWidth().height(3.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)),
        ) {
            val progress = if (snapshot.durationMs > 0) {
                (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                Modifier.fillMaxWidth(progress).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}
