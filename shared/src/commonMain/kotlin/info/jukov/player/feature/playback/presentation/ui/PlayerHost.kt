package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.LocalPlayerBottomInset
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerHost(
    viewModel: PlayerViewModel,
    expandRequest: Long = 0L,
    onExpandRequestConsumed: () -> Unit = {},
    onAddToPlaylist: (List<Track>) -> Unit,
    onArtistClick: (Track) -> Unit,
    onAlbumClick: (Track) -> Unit,
    content: @Composable () -> Unit,
) {
    val loadable by viewModel.state.collectAsStateWithLifecycle()
    val favoritePending by viewModel.favoritePending.collectAsStateWithLifecycle()
    val downloadStatuses by viewModel.downloadStatuses.collectAsStateWithLifecycle()
    val snapshot = loadable.content ?: PlayerUiState()
    val track = snapshot.currentTrack

    if (track == null) {
        content()
        return
    }

    val density = LocalDensity.current
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val peekHeight = MiniPlayerContentInset + navigationBarHeight
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var queueVisible by remember { mutableStateOf(false) }

    LaunchedEffect(expandRequest) {
        if (expandRequest != 0L) {
            sheetState.expand()
            onExpandRequestConsumed()
        }
    }

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
                    downloadStatus = downloadStatuses[track.id],
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
