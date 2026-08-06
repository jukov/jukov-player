package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.core.presentation.ui.LARGE_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerHost(
    viewModel: PlayerViewModel,
    content: @Composable () -> Unit,
) {
    val loadable by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = loadable.content ?: PlayerUiState()
    var showPlayer by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        if (snapshot.currentTrack != null) {
            MiniPlayer(
                snapshot = snapshot,
                onOpen = { showPlayer = true },
                onPlayPause = viewModel::playPause,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    if (showPlayer && snapshot.currentTrack != null) {
        ModalBottomSheet(onDismissRequest = { showPlayer = false }) {
            FullPlayer(
                snapshot = snapshot,
                error = (loadable as? LoadableState.Failure)?.error,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun MiniPlayer(
    snapshot: PlayerUiState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = snapshot.currentTrack ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = track.coverArtUrl,
            albumId = track.albumId,
            requestedSize = SMALL_ARTWORK_SIZE,
            description = stringResource(Res.string.track_cover, track.title),
            modifier = Modifier.size(48.dp),
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
        PlayPauseButton(snapshot.isPlaying, onPlayPause)
    }
}

@Composable
private fun FullPlayer(
    snapshot: PlayerUiState,
    error: AppError?,
    viewModel: PlayerViewModel,
) {
    val track = snapshot.currentTrack ?: return
    var isDragging by remember(track.id) { mutableStateOf(false) }
    var sliderPosition by remember(track.id) { mutableFloatStateOf(snapshot.positionMs.toFloat()) }
    LaunchedEffect(snapshot.positionMs, isDragging) {
        if (!isDragging) sliderPosition = snapshot.positionMs.toFloat()
    }
    val duration = snapshot.durationMs.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            url = track.coverArtUrl,
            albumId = track.albumId,
            requestedSize = LARGE_ARTWORK_SIZE,
            description = stringResource(Res.string.track_cover, track.title),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(1f),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artist,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
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
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(sliderPosition.toLong()), style = MaterialTheme.typography.bodySmall)
            Text(formatDuration(snapshot.durationMs), style = MaterialTheme.typography.bodySmall)
        }
        error?.let {
            Text(
                text = it.localizedMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(
                resource = Res.drawable.skip_previous,
                description = stringResource(Res.string.previous_track),
                enabled = snapshot.hasPrevious,
                onClick = viewModel::previous,
            )
            PlayPauseButton(snapshot.isPlaying, viewModel::playPause, Modifier.size(64.dp))
            PlayerIconButton(
                resource = Res.drawable.skip_next,
                description = stringResource(Res.string.next_track),
                enabled = snapshot.hasNext,
                onClick = viewModel::next,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PlayerIconButton(
                Res.drawable.shuffle,
                stringResource(Res.string.shuffle),
                enabled = false,
                onClick = {},
            )
            PlayerIconButton(
                Res.drawable.repeat,
                stringResource(Res.string.repeat),
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun Artwork(
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

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PlayerIconButton(
        resource = if (isPlaying) Res.drawable.pause else Res.drawable.play_arrow,
        description = stringResource(if (isPlaying) Res.string.pause else Res.string.play),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun PlayerIconButton(
    resource: DrawableResource,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(painterResource(resource), contentDescription = description, modifier = Modifier.fillMaxSize(0.7f))
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
