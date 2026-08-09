package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.util.Logger
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.PlayPauseButton
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.delete
import jukovplayer.shared.generated.resources.drag_handle
import jukovplayer.shared.generated.resources.format_vertical_align_top
import jukovplayer.shared.generated.resources.move_selected_to_top
import jukovplayer.shared.generated.resources.move_track
import jukovplayer.shared.generated.resources.queue
import jukovplayer.shared.generated.resources.queue_empty
import jukovplayer.shared.generated.resources.remove_selected_tracks
import jukovplayer.shared.generated.resources.remove_track_from_queue
import jukovplayer.shared.generated.resources.select_track
import jukovplayer.shared.generated.resources.selected_tracks
import jukovplayer.shared.generated.resources.track_cover
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    state: PlayerUiState,
    onPlayAt: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onRemoveSelected: (Set<Int>) -> Unit,
    onMoveSelectedToTop: (Set<Int>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    val visibleItems = state.queue.drop(state.currentIndex.coerceAtLeast(0))
    val validFutureIds = state.queue.drop(state.currentIndex + 1).mapTo(mutableSetOf()) { it.uiId }
    LaunchedEffect(validFutureIds) { selectedIds = selectedIds intersect validFutureIds }
    val selectedIndices = state.queue.mapIndexedNotNull { index, item ->
        index.takeIf { index > state.currentIndex && item.uiId in selectedIds }
    }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedIds.isEmpty()) stringResource(Res.string.queue)
                        else stringResource(Res.string.selected_tracks, selectedIds.size),
                    )
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = {
                            onMoveSelectedToTop(selectedIndices)
                            selectedIds = emptySet()
                        }) {
                            Icon(
                                painterResource(Res.drawable.format_vertical_align_top),
                                stringResource(Res.string.move_selected_to_top),
                            )
                        }
                        IconButton(onClick = {
                            onRemoveSelected(selectedIndices)
                            selectedIds = emptySet()
                        }) {
                            Icon(
                                painterResource(Res.drawable.delete),
                                stringResource(Res.string.remove_selected_tracks),
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        if (visibleItems.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(contentPadding)
                    .padding(androidx.compose.foundation.layout.PaddingValues().withPlayerBottomInset()),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(Res.string.queue_empty)) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Padding.small)
                    .withPlayerBottomInset(),
                verticalArrangement = Arrangement.spacedBy(Padding.xSmall),
            ) {
                itemsIndexed(
                    items = visibleItems,
                    key = { _, item -> item.uiId },
                ) { visibleIndex, item ->
                    val track = item.track
                    val absoluteIndex = state.currentIndex + visibleIndex
                    val isCurrent = visibleIndex == 0
                    if (isCurrent) {
                        QueueTrackRow(
                            track = track,
                            isCurrent = true,
                            selected = false,
                            onSelectedChange = {},
                            onClick = { onPlayAt(absoluteIndex) },
                            modifier = Modifier.animateItem(),
                            dragHandle = {
                                PlayPauseButton(
                                    isPlaying = state.isPlaying,
                                    onClick = onPlayPause,
                                    modifier = Modifier.size(48.dp),
                                )
                            },
                        )
                    } else {
                        DismissibleQueueTrackRow(
                            track = track,
                            absoluteIndex = absoluteIndex,
                            currentIndex = state.currentIndex,
                            lastIndex = state.queue.lastIndex,
                            selected = item.uiId in selectedIds,
                            onSelectedChange = { selected ->
                                selectedIds = if (selected) {
                                    selectedIds + item.uiId
                                } else {
                                    selectedIds - item.uiId
                                }
                            },
                            onClick = { onPlayAt(absoluteIndex) },
                            onMove = onMove,
                            onDraggingChange = { dragging ->
                                draggedItemId = item.uiId.takeIf { dragging }
                            },
                            onRemove = {
                                selectedIds -= item.uiId
                                onRemove(absoluteIndex)
                            },
                            modifier = if (draggedItemId == item.uiId) {
                                Modifier.animateItem(placementSpec = null)
                            } else {
                                Modifier.animateItem()
                            },
                        )
                    }
                }
                if (visibleItems.size == 1) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(Padding.large),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(Res.string.queue_empty)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleQueueTrackRow(
    track: Track,
    absoluteIndex: Int,
    currentIndex: Int,
    lastIndex: Int,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var removalHandled by remember(track.id) { mutableStateOf(false) }
    var isDragging by remember(track.id) { mutableStateOf(false) }
    var measuredRowHeightPx by remember(track.id) { mutableIntStateOf(0) }
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart && !removalHandled) {
            removalHandled = true
            onRemove()
        }
    }
    SwipeToDismissBox(
        modifier = modifier
            .onSizeChanged { measuredRowHeightPx = it.height }
            .zIndex(if (isDragging) 1f else 0f),
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(
                    if (isDragging) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.errorContainer,
                )
                    .padding(horizontal = Padding.large),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (!isDragging) {
                    Icon(
                        painterResource(Res.drawable.delete),
                        stringResource(Res.string.remove_track_from_queue, track.title),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
    ) {
        var dragOffset by remember(track.id) { mutableFloatStateOf(0f) }
        var dragIndex by remember(track.id) { mutableIntStateOf(absoluteIndex) }
        LaunchedEffect(absoluteIndex, isDragging) {
            if (!isDragging) dragIndex = absoluteIndex
        }
        QueueTrackRow(
            track = track,
            isCurrent = false,
            selected = selected,
            onSelectedChange = onSelectedChange,
            onClick = onClick,
            dragHandle = {
                Icon(
                    painter = painterResource(Res.drawable.drag_handle),
                    contentDescription = stringResource(Res.string.move_track, track.title),
                    modifier = Modifier.size(48.dp).padding(12.dp).pointerInput(track.id) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                onDraggingChange(true)
                                dragIndex = absoluteIndex
                            },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                                val itemStridePx = measuredRowHeightPx + Padding.xSmall.toPx()
                                if (itemStridePx > 0f) {
                                    val moveThreshold = itemStridePx / 2f
                                    while (dragOffset > moveThreshold && dragIndex < lastIndex) {
                                        onMove(dragIndex, dragIndex + 1)
                                        dragIndex += 1
                                        dragOffset -= itemStridePx
                                    }
                                    while (
                                        dragOffset < -moveThreshold &&
                                        dragIndex > currentIndex + 1
                                    ) {
                                        onMove(dragIndex, dragIndex - 1)
                                        dragIndex -= 1
                                        dragOffset += itemStridePx
                                    }
                                }
                            },
                            onDragEnd = {
                                dragOffset = 0f
                                isDragging = false
                                onDraggingChange(false)
                            },
                            onDragCancel = {
                                dragOffset = 0f
                                isDragging = false
                                onDraggingChange(false)
                            },
                        )
                    },
                )
            },
            modifier = Modifier.graphicsLayer { translationY = dragOffset },
        )
    }
}

@Composable
private fun QueueTrackRow(
    track: Track,
    isCurrent: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = Padding.small, vertical = Padding.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Spacer(Modifier.size(48.dp))
        } else {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = Modifier.size(48.dp),
            )
        }
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            track.coverArtUrl?.let { url ->
                AsyncImage(
                    model = rememberArtworkRequest(url, track.albumId, SMALL_ARTWORK_SIZE),
                    contentDescription = stringResource(Res.string.track_cover, track.title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(Padding.medium))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        dragHandle?.invoke()
    }
}
