package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.PlayPauseButton
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.feature.playback.presentation.PlayerUiState
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.delete
import jukovplayer.shared.generated.resources.format_vertical_align_top
import jukovplayer.shared.generated.resources.move_selected_to_top
import jukovplayer.shared.generated.resources.queue
import jukovplayer.shared.generated.resources.queue_empty
import jukovplayer.shared.generated.resources.remove_selected_tracks
import jukovplayer.shared.generated.resources.selected_tracks
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
    PlayerBackHandler(
        enabled = selectedIds.isNotEmpty(),
        onBack = { selectedIds = emptySet() },
    )
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    val visibleItems = remember(state.queue, state.currentIndex) {
        state.queue.drop(state.currentIndex.coerceAtLeast(0))
    }
    val validFutureIds = remember(state.queue, state.currentIndex) {
        buildSet {
            for (index in state.currentIndex + 1 until state.queue.size) {
                add(state.queue[index].uiId)
            }
        }
    }
    LaunchedEffect(validFutureIds) {
        selectedIds = selectedIds intersect validFutureIds
    }
    val selectedIndices = remember(state.queue, state.currentIndex, selectedIds) {
        buildSet {
            state.queue.forEachIndexed { index, item ->
                if (index > state.currentIndex && item.uiId in selectedIds) {
                    add(index)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedIds.isEmpty()) {
                            stringResource(Res.string.queue)
                        } else {
                            stringResource(Res.string.selected_tracks, selectedIds.size)
                        },
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
            ) {
                Text(stringResource(Res.string.queue_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Padding.small)
                    .withPlayerBottomInset(),
                verticalArrangement = Arrangement.spacedBy(Padding.small),
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
                                    isLoading = state.isLoading,
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
                        ) {
                            Text(stringResource(Res.string.queue_empty))
                        }
                    }
                }
            }
        }
    }
}
