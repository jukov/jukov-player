package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.LocalPlayerBottomInset
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.PlayPauseButton
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.core.presentation.ui.LARGE_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerHost(
    viewModel: PlayerViewModel,
    content: @Composable () -> Unit,
) {
    val loadable by viewModel.state.collectAsStateWithLifecycle()
    val favoritePending by viewModel.favoritePending.collectAsStateWithLifecycle()
    val snapshot = loadable.content ?: PlayerUiState()
    val track = snapshot.currentTrack

    if (track == null) {
        content()
        return
    }

    val density = LocalDensity.current
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val peekHeight = MINI_PLAYER_CONTENT_INSET + navigationBarHeight
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var queueVisible by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalPlayerBottomInset provides peekHeight) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetDragHandle = null,
            sheetContent = {
                PlayerSheetContent(
                    snapshot = snapshot,
                    error = (loadable as? LoadableState.Failure)?.error,
                    viewModel = viewModel,
                    favoriteEnabled = track.id !in favoritePending,
                    peekHeight = peekHeight,
                    sheetOffset = { runCatching { sheetState.requireOffset() }.getOrNull() },
                    onExpand = { scope.launch { sheetState.expand() } },
                    onOpenQueue = { queueVisible = true },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
        if (queueVisible) {
            ModalBottomSheet(
                onDismissRequest = { queueVisible = false },
                sheetState = queueSheetState,
                modifier = Modifier.fillMaxSize(),
            ) {
                QueueScreen(
                    state = snapshot,
                    onPlayAt = viewModel::playAt,
                    onPlayPause = viewModel::playPause,
                    onMove = viewModel::moveQueueItem,
                    onRemove = viewModel::removeQueueItem,
                    onRemoveSelected = viewModel::removeQueueItems,
                    onMoveSelectedToTop = viewModel::moveQueueItemsToTop,
                )
            }
        }
    }
}

private val MINI_PLAYER_HEIGHT = 64.dp
private val MINI_PLAYER_VERTICAL_PADDING = 8.dp
private val MINI_PLAYER_CONTENT_INSET = MINI_PLAYER_HEIGHT + MINI_PLAYER_VERTICAL_PADDING * 2

@Composable
private fun PlayerSheetContent(
    snapshot: PlayerUiState,
    error: AppError?,
    viewModel: PlayerViewModel,
    favoriteEnabled: Boolean,
    peekHeight: Dp,
    sheetOffset: () -> Float?,
    onExpand: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
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
            error = error,
            viewModel = viewModel,
            favoriteEnabled = favoriteEnabled,
            onOpenQueue = onOpenQueue,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = expansionProgress }
                .zIndex(expansionProgress),
        )
        MiniPlayer(
            snapshot = snapshot,
            onOpen = onExpand,
            onPlayPause = viewModel::playPause,
            onFavorite = viewModel::toggleFavorite,
            favoriteEnabled = favoriteEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = MINI_PLAYER_VERTICAL_PADDING)
                .graphicsLayer { alpha = 1f - expansionProgress }
                .zIndex(1f - expansionProgress),
        )
    }
}

@Composable
private fun MiniPlayer(
    snapshot: PlayerUiState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    favoriteEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = snapshot.currentTrack ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_PLAYER_HEIGHT)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp),
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
            onClick = onPlayPause,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun FullPlayer(
    snapshot: PlayerUiState,
    error: AppError?,
    viewModel: PlayerViewModel,
    favoriteEnabled: Boolean,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = snapshot.currentTrack ?: return
    var isDragging by remember(track.id) { mutableStateOf(false) }
    var sliderPosition by remember(track.id) { mutableFloatStateOf(snapshot.positionMs.toFloat()) }
    LaunchedEffect(snapshot.positionMs, isDragging) {
        if (!isDragging) sliderPosition = snapshot.positionMs.toFloat()
    }
    val duration = snapshot.durationMs.coerceAtLeast(1)

    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Artwork(
            url = track.coverArtUrl,
            albumId = track.albumId,
            requestedSize = LARGE_ARTWORK_SIZE,
            description = stringResource(Res.string.track_cover, track.title),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
        )
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
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PlayerIconButton(
                Res.drawable.shuffle,
                stringResource(Res.string.shuffle),
                enabled = false,
                onClick = {},
            )
            FavoriteToggleButton(
                isFavorite = track.isFavorite,
                onClick = viewModel::toggleFavorite,
                enabled = favoriteEnabled,
            )
            PlayerIconButton(
                Res.drawable.repeat,
                stringResource(Res.string.repeat),
                enabled = false,
                onClick = {},
            )
            PlayerIconButton(
                Res.drawable.playlist_play,
                stringResource(Res.string.open_queue),
                onClick = onOpenQueue,
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
