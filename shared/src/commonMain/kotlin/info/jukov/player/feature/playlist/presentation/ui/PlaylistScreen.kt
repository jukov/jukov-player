package info.jukov.player.feature.playlist.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.feature.favorite.domain.favoriteStateForSelection
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playlist.presentation.PlaylistViewModel
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.presentation.ui.TracksList
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    id: String,
    title: String,
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onPlay: (List<Track>, Int, PlaybackOrigin) -> Unit,
    onActiveTrackClick: () -> Unit,
    activeTrackId: String?,
    isPlaying: Boolean,
    loadingTrackId: String? = null,
    onAddToQueue: (List<Track>) -> Unit,
) {
    LaunchedEffect(id) { viewModel.load(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val downloadStatuses by viewModel.downloadStatuses.collectAsStateWithLifecycle()
    val artworkUris by viewModel.artworkUris.collectAsStateWithLifecycle()
    val playlist = state.content
    val tracks = playlist?.tracks.orEmpty()
    var selected by remember(id) { mutableStateOf<Set<String>>(emptySet()) }
    var editing by remember(id) { mutableStateOf(false) }
    var confirmDelete by remember(id) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var error by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { error = it } }
    val errorMessage = error?.localizedMessage()
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbar.showSnackbar(it) }
        error = null
    }
    val chosen = selected.mapNotNull { it.toIntOrNull()?.let(tracks::getOrNull) }
    val editable = playlist?.let(viewModel::isEditable) == true
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected.isEmpty()) {
                            playlist?.name ?: title
                        } else {
                            stringResource(Res.string.selected_tracks, selected.size)
                        },
                    )
                },
                navigationIcon = {
                    PlaylistBackButton(
                        if (selected.isEmpty()) onBack else { { selected = emptySet() } },
                    )
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        PlaylistSelectionActions(
                            tracks = chosen,
                            editable = editable,
                            onFavorite = {
                                viewModel.toggleFavorites(chosen)
                                selected = emptySet()
                            },
                            onDownload = {
                                viewModel.download(chosen)
                                selected = emptySet()
                            },
                            onAddToQueue = {
                                onAddToQueue(chosen)
                                selected = emptySet()
                            },
                            onRemove = {
                                viewModel.remove(selected.mapNotNull(String::toInt))
                                selected = emptySet()
                            },
                        )
                    } else if (playlist != null) {
                        PlaylistActions(
                            tracks = tracks,
                            editable = editable,
                            onDownload = { viewModel.download(tracks) },
                            onAddToQueue = { onAddToQueue(tracks) },
                            onEdit = { editing = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state is LoadableState.Loading && playlist == null -> PlaylistCentered {
                LoadingIndicator()
            }
            state is LoadableState.Failure && playlist == null -> PlaylistCentered {
                Button(onClick = { viewModel.load(id, forceRefresh = true) }) {
                    Text(stringResource(Res.string.retry))
                }
            }
            tracks.isEmpty() -> PlaylistCentered {
                Text(stringResource(Res.string.tracks_not_found))
            }
            else -> TracksList(
                tracks = tracks,
                error = (state as? LoadableState.Failure)?.error,
                onPlayClick = { list, index ->
                    onPlay(list, index, PlaybackOrigin.Playlist(id))
                },
                onActiveTrackClick = onActiveTrackClick,
                activeTrackId = activeTrackId,
                isPlaying = isPlaying,
                loadingTrackId = loadingTrackId,
                downloadStatuses = downloadStatuses,
                onDownloadClick = viewModel::download,
                onCancelDownload = viewModel::cancelDownload,
                onRetryDownload = viewModel::retryDownload,
                artworkUris = artworkUris,
                selectionMode = selected.isNotEmpty(),
                selectedIds = selected,
                onSelectionChange = { key, value ->
                    selected = if (value) selected + key else selected - key
                },
                selectionKey = { index, _ -> index.toString() },
                modifier = Modifier.padding(padding),
                reorderEnabled = true,
                onMove = viewModel::moveTrack,
            )
        }
    }
    if (editing && playlist != null) {
        EditPlaylistDialog(
            playlist = playlist,
            pending = pending,
            onDismiss = { editing = false },
            onSave = { name, isPublic ->
                viewModel.update(name, isPublic) { editing = false }
            },
            onDelete = { confirmDelete = true },
        )
    }
    if (confirmDelete) {
        DeletePlaylistDialog(
            name = playlist?.name ?: title,
            pending = pending,
            onDismiss = { confirmDelete = false },
            onConfirm = { viewModel.delete(onDeleted) },
        )
    }
}

@Composable
private fun PlaylistSelectionActions(
    tracks: List<Track>,
    editable: Boolean,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: () -> Unit,
) {
    IconButton(onClick = onFavorite) {
        Icon(
            painterResource(
                if (favoriteStateForSelection(tracks)) Res.drawable.heart
                else Res.drawable.heart_outline,
            ),
            stringResource(Res.string.add_to_favorites),
        )
    }
    IconButton(onClick = onDownload) {
        Icon(painterResource(Res.drawable.download), stringResource(Res.string.download))
    }
    IconButton(onClick = onAddToQueue) {
        Icon(
            painterResource(Res.drawable.playlist_play),
            stringResource(Res.string.add_selected_to_queue),
        )
    }
    if (editable) {
        IconButton(onClick = onRemove) {
            Icon(
                painterResource(Res.drawable.playlist_remove),
                stringResource(Res.string.remove_from_playlist),
            )
        }
    }
}

@Composable
private fun PlaylistActions(
    tracks: List<Track>,
    editable: Boolean,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    onEdit: () -> Unit,
) {
    IconButton(onClick = onDownload, enabled = tracks.isNotEmpty()) {
        Icon(
            painterResource(Res.drawable.download),
            stringResource(Res.string.download_playlist),
        )
    }
    IconButton(onClick = onAddToQueue, enabled = tracks.isNotEmpty()) {
        Icon(
            painterResource(Res.drawable.playlist_play),
            stringResource(Res.string.add_playlist_to_queue),
        )
    }
    if (editable) {
        IconButton(onClick = onEdit) {
            Icon(painterResource(Res.drawable.edit), stringResource(Res.string.edit_playlist))
        }
    }
}
