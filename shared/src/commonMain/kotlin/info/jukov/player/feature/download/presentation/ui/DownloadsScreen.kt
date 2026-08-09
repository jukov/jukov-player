package info.jukov.player.feature.download.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.SearchAction
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.feature.download.domain.*
import info.jukov.player.feature.download.presentation.DownloadsTab
import info.jukov.player.feature.download.presentation.DownloadsViewModel
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.presentation.ui.TracksList
import info.jukov.player.feature.track.presentation.ui.TrackSelectionTopAppBar
import info.jukov.player.feature.track.presentation.ui.TrackTrailingAction
import info.jukov.player.feature.track.presentation.ui.DownloadPlayButton
import info.jukov.player.feature.track.presentation.ui.rememberTrackSelectionState
import info.jukov.player.feature.playback.presentation.ui.PlayerBackHandler
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit,
    onAlbumClick: (OfflineAlbum) -> Unit,
    onPlayClick: (List<Track>, Int) -> Unit,
    onActiveTrackClick: () -> Unit,
    activeTrackId: String?,
    isPlaying: Boolean,
    onAddToQueue: (List<Track>) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var confirmRemoveAll by remember { mutableStateOf(false) }
    val library = state.content ?: OfflineLibrary()
    val tracks = library.tracks.map { it.track }
    val browseTracksListState = rememberLazyListState()
    val searchTracksListState = rememberLazyListState()
    val browseAlbumsListState = rememberLazyListState()
    val searchAlbumsListState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = tab.ordinal,
        pageCount = { DownloadsTab.entries.size },
    )
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab.ordinal) {
            pagerState.animateScrollToPage(tab.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.selectTab(DownloadsTab.entries[page])
        }
    }
    LaunchedEffect(searchQuery) {
        if (searchActive) {
            searchTracksListState.scrollToItem(0)
            searchAlbumsListState.scrollToItem(0)
        }
    }
    val selectionState = rememberTrackSelectionState(
        tracks = tracks,
        active = tab == DownloadsTab.Tracks,
    )
    PlayerBackHandler(
        enabled = selectionState.isActive,
        onBack = selectionState::clear,
    )
    Scaffold(
        topBar = {
            Column {
                if (selectionState.isActive) {
                    TrackSelectionTopAppBar(
                        selectedCount = selectionState.selectedCount,
                        allSelectedFavorite = selectionState.areAllSelectedFavorite(tracks),
                        onClose = selectionState::clear,
                        onFavorite = { selectionState.finish(tracks, viewModel::toggleFavorites) },
                        onDownload = { selectionState.finish(tracks, viewModel::removeTracks) },
                        onAddToQueue = { selectionState.finish(tracks, onAddToQueue) },
                        removesDownloads = true,
                    )
                } else {
                    AppFlexibleTopAppBar(
                        title = stringResource(Res.string.downloads),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.back))
                            }
                        },
                        actions = {
                            SearchAction(viewModel::openSearch)
                            IconButton(onClick = { confirmRemoveAll = true }) {
                                Icon(
                                    painterResource(Res.drawable.download_off),
                                    stringResource(Res.string.remove_all_downloads),
                                )
                            }
                        },
                        searchQuery = searchQuery.takeIf { searchActive },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSearchClose = viewModel::closeSearch,
                    )
                }
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    DownloadsTab.entries.forEach { item ->
                        Tab(
                            selected = item == tab,
                            onClick = { viewModel.selectTab(item) },
                            text = { Text(stringResource(if (item == DownloadsTab.Tracks) Res.string.tracks else Res.string.albums)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state is LoadableState.Loading && state.content == null) {
                LoadingIndicator(Modifier.align(Alignment.Center).size(96.dp))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    when (DownloadsTab.entries[page]) {
                        DownloadsTab.Tracks -> {
                            if (library.tracks.isEmpty()) EmptyDownloads(if (searchActive && searchQuery.isNotBlank()) Res.string.nothing_found else Res.string.no_downloaded_tracks)
                            else {
                                TracksList(
                                    tracks = tracks,
                                    error = null,
                                    onPlayClick = onPlayClick,
                                    onActiveTrackClick = onActiveTrackClick,
                                    activeTrackId = activeTrackId,
                                    isPlaying = isPlaying,
                                    downloadStatuses = library.tracks.associate { it.track.id to it.status },
                                    onCancelDownload = viewModel::removeTrack,
                                    onRetryDownload = viewModel::retryTrack,
                                    onFavoriteClick = viewModel::toggleFavorite,
                                    selectionMode = selectionState.isActive,
                                    selectedIds = selectionState.selectedIds,
                                    onSelectionChange = selectionState::setSelected,
                                    trailingAction = TrackTrailingAction.RemoveDownload,
                                    listState = if (searchActive) searchTracksListState else browseTracksListState,
                                )
                            }
                        }
                        DownloadsTab.Albums -> {
                            if (library.albums.isEmpty()) EmptyDownloads(if (searchActive && searchQuery.isNotBlank()) Res.string.nothing_found else Res.string.no_downloaded_albums)
                            else LazyColumn(
                                state = if (searchActive) searchAlbumsListState else browseAlbumsListState,
                                contentPadding = PaddingValues(Padding.small).withPlayerBottomInset(),
                            ) {
                                items(library.albums, key = { it.album.id }) { album ->
                                    OfflineAlbumRow(
                                        album = album,
                                        onClick = { onAlbumClick(album) },
                                        onRemove = { viewModel.removeAlbum(album.album.id) },
                                        onPlay = {
                                            if (album.tracks.any { it.track.id == activeTrackId }) {
                                                onActiveTrackClick()
                                            } else if (album.tracks.isNotEmpty()) {
                                                onPlayClick(album.tracks.map { it.track }, 0)
                                            }
                                        },
                                        isPlaying = isPlaying &&
                                            album.tracks.any { it.track.id == activeTrackId },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            title = { Text(stringResource(Res.string.remove_all_downloads_title)) },
            text = { Text(stringResource(Res.string.remove_all_downloads_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveAll = false
                        viewModel.removeAll()
                    },
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAll = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
fun OfflineAlbumTracksScreen(
    albumId: String,
    albumName: String,
    viewModel: DownloadsViewModel,
    onBack: () -> Unit,
    onPlayClick: (List<Track>, Int) -> Unit,
    onActiveTrackClick: () -> Unit,
    activeTrackId: String?,
    isPlaying: Boolean,
    onAddToQueue: (List<Track>) -> Unit,
) {
    val offlineTracks by viewModel.albumTracks(albumId).collectAsStateWithLifecycle(emptyList())
    val tracks = offlineTracks.map { it.track }
    val selectionState = rememberTrackSelectionState(tracks, key = albumId)
    PlayerBackHandler(
        enabled = selectionState.isActive,
        onBack = selectionState::clear,
    )
    Scaffold(
        topBar = {
            if (selectionState.isActive) {
                TrackSelectionTopAppBar(
                    selectedCount = selectionState.selectedCount,
                    allSelectedFavorite = selectionState.areAllSelectedFavorite(tracks),
                    onClose = selectionState::clear,
                    onFavorite = { selectionState.finish(tracks, viewModel::toggleFavorites) },
                    onDownload = { selectionState.finish(tracks, viewModel::removeTracks) },
                    onAddToQueue = { selectionState.finish(tracks, onAddToQueue) },
                    removesDownloads = true,
                )
            } else {
                AppFlexibleTopAppBar(
                    title = albumName,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.back))
                        }
                    },
                )
            }
        },
    ) { padding ->
        TracksList(
            tracks = tracks,
            error = null,
            onPlayClick = onPlayClick,
            onActiveTrackClick = onActiveTrackClick,
            activeTrackId = activeTrackId,
            isPlaying = isPlaying,
            downloadStatuses = offlineTracks.associate { it.track.id to it.status },
            onCancelDownload = viewModel::removeTrack,
            onRetryDownload = viewModel::retryTrack,
            onFavoriteClick = viewModel::toggleFavorite,
            selectionMode = selectionState.isActive,
            selectedIds = selectionState.selectedIds,
            onSelectionChange = selectionState::setSelected,
            trailingAction = TrackTrailingAction.RemoveDownload,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun OfflineAlbumRow(
    album: OfflineAlbum,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPlay: () -> Unit,
    isPlaying: Boolean,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(
                modifier = Modifier.size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val artworkUrl = album.album.coverArtUrl
                if (artworkUrl != null) {
                    AsyncImage(
                        model = rememberArtworkRequest(artworkUrl, album.album.id, SMALL_ARTWORK_SIZE),
                        contentDescription = stringResource(Res.string.album_cover, album.album.name),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.album),
                        contentDescription = stringResource(Res.string.no_cover),
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        headlineContent = { Text(album.album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(album.album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(Res.drawable.delete),
                        contentDescription = stringResource(Res.string.remove_download),
                    )
                }
                DownloadPlayButton(
                    isPlaying = isPlaying,
                    onClick = onPlay,
                    status = album.status,
                )
            }
        },
    )
}

@Composable
private fun EmptyDownloads(text: org.jetbrains.compose.resources.StringResource) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(text)) }
}
