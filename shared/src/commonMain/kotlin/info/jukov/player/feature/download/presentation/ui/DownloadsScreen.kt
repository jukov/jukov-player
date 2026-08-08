package info.jukov.player.feature.download.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import info.jukov.player.feature.track.presentation.ui.rememberTrackSelectionState
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
    Scaffold(
        topBar = {
            Column {
                if (selectionState.isActive) {
                    TrackSelectionTopAppBar(
                        selectedCount = selectionState.selectedCount,
                        allSelectedFavorite = selectionState.areAllSelectedFavorite(tracks),
                        onClose = selectionState::clear,
                        onFavorite = { selectionState.finish(tracks, viewModel::toggleFavorites) },
                        onDownload = selectionState::clear,
                        onAddToQueue = { selectionState.finish(tracks, onAddToQueue) },
                    )
                } else {
                    AppFlexibleTopAppBar(
                        title = stringResource(Res.string.downloads),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.back))
                            }
                        },
                        actions = { SearchAction(viewModel::openSearch) },
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
                CircularProgressIndicator(Modifier.align(Alignment.Center))
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
                                    OfflineAlbumRow(album, { onAlbumClick(album) }) {
                                        viewModel.removeAlbum(album.album.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
    Scaffold(
        topBar = {
            if (selectionState.isActive) {
                TrackSelectionTopAppBar(
                    selectedCount = selectionState.selectedCount,
                    allSelectedFavorite = selectionState.areAllSelectedFavorite(tracks),
                    onClose = selectionState::clear,
                    onFavorite = { selectionState.finish(tracks, viewModel::toggleFavorites) },
                    onDownload = selectionState::clear,
                    onAddToQueue = { selectionState.finish(tracks, onAddToQueue) },
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
private fun OfflineAlbumRow(album: OfflineAlbum, onClick: () -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            album.album.coverArtUrl?.let { url ->
                AsyncImage(
                    model = rememberArtworkRequest(url, album.album.id, SMALL_ARTWORK_SIZE),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
            }
        },
        headlineContent = { Text(album.album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(album.album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (album.status.state) {
                    DownloadState.Queued, DownloadState.Downloading -> album.status.progress?.let { progress ->
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } ?: run {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    DownloadState.Completed -> Text("✓", color = MaterialTheme.colorScheme.primary)
                    DownloadState.Failed -> Text("!", color = MaterialTheme.colorScheme.error)
                }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(painterResource(Res.drawable.more_vert), stringResource(Res.string.more_actions))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.remove_download)) },
                            onClick = { expanded = false; onRemove() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun EmptyDownloads(text: org.jetbrains.compose.resources.StringResource) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(text)) }
}
