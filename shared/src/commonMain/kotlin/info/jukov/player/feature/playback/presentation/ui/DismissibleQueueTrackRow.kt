package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.delete
import jukovplayer.shared.generated.resources.drag_handle
import jukovplayer.shared.generated.resources.move_track
import jukovplayer.shared.generated.resources.remove_track_from_queue
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DismissibleQueueTrackRow(
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
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isDragging) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
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
            if (!isDragging) {
                dragIndex = absoluteIndex
            }
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
