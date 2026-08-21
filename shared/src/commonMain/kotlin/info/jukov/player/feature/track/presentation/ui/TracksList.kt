package info.jukov.player.feature.track.presentation.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.drag_handle
import jukovplayer.shared.generated.resources.move_track
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TracksList(
    tracks: List<Track>,
    showArtwork: Boolean = true,
    showTrackNumber: Boolean = false,
    error: AppError?,
    onPlayClick: (List<Track>, Int) -> Unit,
    onActiveTrackClick: () -> Unit,
    activeTrackId: String?,
    isPlaying: Boolean,
    loadingTrackId: String? = null,
    pendingIds: Set<String> = emptySet(),
    onFavoriteClick: (Track) -> Unit = {},
    downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    onDownloadClick: (Track) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onRetryDownload: (String) -> Unit = {},
    artworkUris: Map<String, String> = emptyMap(),
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    selectionKey: (Int, Track) -> String = { _, track -> track.id },
    itemKey: (Int, Track) -> Any = selectionKey,
    trailingAction: TrackTrailingAction = TrackTrailingAction.Favorite,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    reorderEnabled: Boolean = false,
    onMove: (Int, Int) -> Unit = { _, _ -> },
) {
    var draggedItemKey by remember { mutableStateOf<Any?>(null) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Padding.medium)
            .withPlayerBottomInset(),
        verticalArrangement = Arrangement.spacedBy(Padding.small),
    ) {
        error?.let { message ->
            item { Text(message.localizedMessage(), color = MaterialTheme.colorScheme.error) }
        }
        itemsIndexed(tracks, key = itemKey) { index, track ->
            val key = itemKey(index, track)
            var dragOffset by remember(key) { mutableFloatStateOf(0f) }
            var dragIndex by remember(key) { mutableIntStateOf(index) }
            var measuredRowHeightPx by remember(key) { mutableIntStateOf(0) }
            val isDragging = draggedItemKey == key
            LaunchedEffect(index, isDragging) {
                if (!isDragging) {
                    dragIndex = index
                }
            }
            if (hasMore && index >= tracks.lastIndex - LOAD_MORE_THRESHOLD) {
                LaunchedEffect(tracks.size) { onLoadMore() }
            }
            TrackRow(
                track = track,
                showArtwork = showArtwork,
                showTrackNumber = showTrackNumber,
                onPlayClick = {
                    if (track.id == activeTrackId) {
                        onActiveTrackClick()
                    } else {
                        onPlayClick(tracks, index)
                    }
                },
                isPlaying = track.id == activeTrackId && isPlaying,
                isLoading = track.id == loadingTrackId,
                favoriteEnabled = track.id !in pendingIds,
                onFavoriteClick = { onFavoriteClick(track) },
                downloadStatus = downloadStatuses[track.id],
                onDownloadClick = { onDownloadClick(track) },
                onCancelDownload = { onCancelDownload(track.id) },
                onRetryDownload = { onRetryDownload(track.id) },
                artworkUrl = track.coverArtId?.let(artworkUris::get) ?: track.coverArtUrl,
                selectionMode = selectionMode,
                selected = selectionKey(index, track) in selectedIds,
                onSelectedChange = { onSelectionChange(selectionKey(index, track), it) },
                trailingAction = trailingAction,
                modifier = if (!reorderEnabled) {
                    Modifier
                } else if (isDragging) {
                    Modifier.animateItem(placementSpec = null)
                        .graphicsLayer { translationY = dragOffset }
                        .onSizeChanged { measuredRowHeightPx = it.height }
                        .zIndex(1f)
                } else {
                    Modifier.animateItem()
                        .onSizeChanged { measuredRowHeightPx = it.height }
                },
                dragHandle = if (reorderEnabled && !selectionMode) {
                    {
                        Icon(
                            painterResource(Res.drawable.drag_handle),
                            stringResource(Res.string.move_track, track.title),
                            Modifier.size(48.dp).padding(12.dp).pointerInput(key) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        draggedItemKey = key
                                        dragIndex = index
                                    },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val itemStridePx = measuredRowHeightPx + Padding.small.toPx()
                                        if (itemStridePx > 0f) {
                                            val moveThreshold = itemStridePx / 2f
                                            while (dragOffset > moveThreshold && dragIndex < tracks.lastIndex) {
                                                onMove(dragIndex, dragIndex + 1)
                                                dragIndex += 1
                                                dragOffset -= itemStridePx
                                            }
                                            while (dragOffset < -moveThreshold && dragIndex > 0) {
                                                onMove(dragIndex, dragIndex - 1)
                                                dragIndex -= 1
                                                dragOffset += itemStridePx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragOffset = 0f
                                        draggedItemKey = null
                                    },
                                    onDragCancel = {
                                        dragOffset = 0f
                                        draggedItemKey = null
                                    },
                                )
                            },
                        )
                    }
                } else {
                    null
                },
            )
        }
        if (isLoadingMore) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(Padding.medium),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 12
