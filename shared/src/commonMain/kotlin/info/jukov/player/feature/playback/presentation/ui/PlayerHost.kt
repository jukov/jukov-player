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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import info.jukov.player.core.presentation.ui.ArtworkPalette
import info.jukov.player.core.presentation.ui.LocalArtworkPaletteExtractor
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerHost(
    viewModel: PlayerViewModel,
    onAddToPlaylist: (List<Track>) -> Unit,
    onArtistClick: (Track) -> Unit,
    onAlbumClick: (Track) -> Unit,
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
                    onAddToPlaylist = onAddToPlaylist,
                    onArtistClick = { selectedTrack ->
                        scope.launch {
                            sheetState.partialExpand()
                            onArtistClick(selectedTrack)
                        }
                    },
                    onAlbumClick = { selectedTrack ->
                        scope.launch {
                            sheetState.partialExpand()
                            onAlbumClick(selectedTrack)
                        }
                    },
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

    PlayerBackHandler(
        enabled = sheetState.currentValue == SheetValue.Expanded && !queueVisible,
    ) {
        scope.launch { sheetState.partialExpand() }
    }
}

private val Track.artistAndYear: String
    get() = year?.let { "$artist · $it" } ?: artist

private val MINI_PLAYER_HEIGHT = 64.dp
private val MINI_PLAYER_CONTENT_INSET = MINI_PLAYER_HEIGHT
@Composable
private fun rememberArtworkPalette(
    key: String?,
    url: String?,
): ArtworkPalette {
    val extractor = LocalArtworkPaletteExtractor.current
    val hash = key.orEmpty().fold(17) { result, char -> result * 31 + char.code }
    val hue = ((hash.toLong() and 0x7fffffff) % 360).toFloat()
    val fallback = ArtworkPalette(
        primary = Color.hsv(hue, .45f, .68f),
        secondary = Color.hsv((hue + 42f) % 360f, .35f, .55f),
    )
    var extracted by remember(key) { mutableStateOf<ArtworkPalette?>(null) }
    LaunchedEffect(key, url, extractor) {
        extracted = if (key != null && url != null) extractor?.extract(key, url) else null
    }
    val target = extracted ?: fallback
    val primary by animateColorAsState(target.primary)
    val secondary by animateColorAsState(target.secondary)
    return ArtworkPalette(primary, secondary)
}

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
                .graphicsLayer { alpha = 1f - expansionProgress }
        )
    }
}

@Composable
private fun MiniPlayer(
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
        modifier = Modifier.fillMaxWidth().height(MINI_PLAYER_HEIGHT).padding(horizontal = 24.dp),
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
      Box(
          Modifier.align(Alignment.TopStart).padding(top = MINI_PLAYER_HEIGHT - 3.dp)
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

@Composable
private fun FullPlayer(
    snapshot: PlayerUiState,
    palette: ArtworkPalette,
    error: AppError?,
    viewModel: PlayerViewModel,
    favoriteEnabled: Boolean,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onArtistClick: (Track) -> Unit,
    onAlbumClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = snapshot.currentTrack ?: return
    var isDragging by remember(track.id) { mutableStateOf(false) }
    var sliderPosition by remember(track.id) { mutableFloatStateOf(snapshot.positionMs.toFloat()) }
    LaunchedEffect(snapshot.positionMs, isDragging) {
        if (!isDragging) sliderPosition = snapshot.positionMs.toFloat()
    }
    val duration = snapshot.durationMs.coerceAtLeast(1)
    val titleLineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.headlineSmall.lineHeight.toDp()
    }
    val titleMinHeight = titleLineHeight * 2 + 4.dp
    val titleMaxHeight = titleLineHeight * 4 + 4.dp
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    0f to palette.primary.copy(alpha = .72f),
                    .48f to palette.secondary.copy(alpha = .35f),
                    1f to MaterialTheme.colorScheme.surface,
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
            text = track.artistAndYear,
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
            Text(formatDuration(sliderPosition.toLong()), style = MaterialTheme.typography.bodySmall)
            Text(formatDuration(snapshot.durationMs), style = MaterialTheme.typography.bodySmall)
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
            horizontalArrangement = Arrangement.spacedBy(
                space = 16.dp,
                alignment = Alignment.CenterHorizontally,
            ),
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
                resource = Res.drawable.playlist_plus,
                description = stringResource(Res.string.add_to_playlist),
                onClick = { onAddToPlaylist(listOf(track)) },
            )
            PlayerIconButton(
                Res.drawable.playlist_play,
                stringResource(Res.string.open_queue),
                onClick = onOpenQueue,
            )
            PlayerIconButton(
                Res.drawable.repeat,
                stringResource(Res.string.repeat),
                enabled = false,
                onClick = {},
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PlayerArtworkPager(
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
