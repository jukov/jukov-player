package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.PlayPauseButton
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.playback.domain.RepeatMode
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerControls(
    snapshot: PlayerUiState,
    track: Track,
    error: AppError?,
    viewModel: PlayerViewModel,
    favoriteEnabled: Boolean,
    downloadStatus: DownloadStatus?,
    onAddToPlaylist: (List<Track>) -> Unit,
    onOpenQueue: () -> Unit,
) {
    var isDragging by remember(track.id) { mutableStateOf(false) }
    var sliderPosition by remember(track.id) { mutableFloatStateOf(snapshot.positionMs.toFloat()) }
    LaunchedEffect(snapshot.positionMs, isDragging) {
        if (!isDragging) {
            sliderPosition = snapshot.positionMs.toFloat()
        }
    }
    val duration = snapshot.durationMs.coerceAtLeast(1)

    Slider(
        value = sliderPosition.coerceIn(0f, duration.toFloat()),
        onValueChange = {
            isDragging = true
            sliderPosition = it
        },
        onValueChangeFinished = {
            viewModel.seekTo(sliderPosition.toLong())
            isDragging = false
        },
        valueRange = 0f..duration.toFloat(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(formatPlaybackDuration(sliderPosition.toLong()), style = MaterialTheme.typography.bodySmall)
        Text(formatPlaybackDuration(snapshot.durationMs), style = MaterialTheme.typography.bodySmall)
    }
    error?.let {
        Text(
            text = it.localizedMessage(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(
            space = 32.dp,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIconButton(
            resource = Res.drawable.skip_previous,
            description = stringResource(Res.string.previous_track),
            enabled = snapshot.hasPrevious,
            onClick = viewModel::previous,
            modifier = Modifier.size(56.dp),
        )
        PlayPauseButton(
            isPlaying = snapshot.isPlaying,
            isLoading = snapshot.isLoading,
            onClick = viewModel::playPause,
            modifier = Modifier.size(64.dp),
            iconSize = 36.dp,
        )
        PlayerIconButton(
            resource = Res.drawable.skip_next,
            description = stringResource(Res.string.next_track),
            enabled = snapshot.hasNext,
            onClick = viewModel::next,
            modifier = Modifier.size(56.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PlayerIconButton(
            resource = Res.drawable.shuffle,
            description = stringResource(Res.string.shuffle),
            active = snapshot.isShuffleEnabled,
            onClick = viewModel::toggleShuffle,
        )
        FavoriteToggleButton(
            isFavorite = track.isFavorite,
            onClick = viewModel::toggleFavorite,
            enabled = favoriteEnabled,
        )
        PlayerDownloadButton(downloadStatus, viewModel::toggleCurrentTrackDownload)
        PlayerIconButton(
            resource = Res.drawable.playlist_plus,
            description = stringResource(Res.string.add_to_playlist),
            onClick = { onAddToPlaylist(listOf(track)) },
        )
        PlayerIconButton(
            resource = Res.drawable.playlist_play,
            description = stringResource(Res.string.open_queue),
            onClick = onOpenQueue,
        )
        PlayerIconButton(
            resource = Res.drawable.repeat,
            description = when (snapshot.repeatMode) {
                RepeatMode.Off -> stringResource(Res.string.repeat_off)
                RepeatMode.All -> stringResource(Res.string.repeat_all)
                RepeatMode.One -> stringResource(Res.string.repeat_one)
            },
            active = snapshot.repeatMode != RepeatMode.Off,
            badge = "1".takeIf { snapshot.repeatMode == RepeatMode.One },
            onClick = viewModel::cycleRepeatMode,
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PlayerDownloadButton(status: DownloadStatus?, onClick: () -> Unit) {
    Box {
        PlayerIconButton(
            resource = Res.drawable.download,
            description = stringResource(Res.string.download_track),
            enabled = status?.state != DownloadState.Completed,
            onClick = onClick,
        )
        when (status?.state) {
            DownloadState.Queued, DownloadState.Downloading -> status.progress?.let { progress ->
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(14.dp).align(Alignment.BottomEnd),
                    strokeWidth = 2.dp,
                )
            } ?: CircularProgressIndicator(
                modifier = Modifier.size(14.dp).align(Alignment.BottomEnd),
                strokeWidth = 2.dp,
            )
            DownloadState.Completed -> Icon(
                painterResource(Res.drawable.check),
                contentDescription = null,
                modifier = Modifier.size(14.dp).align(Alignment.BottomEnd),
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> Unit
        }
    }
}

@Composable
private fun PlayerIconButton(
    resource: DrawableResource,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    badge: String? = null,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(resource),
                contentDescription = description,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.fillMaxSize(0.7f),
            )
            badge?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

internal fun formatPlaybackDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
