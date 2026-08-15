package info.jukov.player.feature.album.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import info.jukov.player.core.domain.AppError
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.album.presentation.AlbumsViewModel
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.playback.presentation.ui.PlayerBackHandler
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.SearchAction
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.core.presentation.ui.LARGE_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.core.presentation.LoadingOrigin
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import info.jukov.player.core.domain.AlbumSortCriterion
import info.jukov.player.core.domain.SortDirection
import info.jukov.player.core.presentation.ui.SortAction
import info.jukov.player.core.presentation.ui.SortMenuItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    artistId: String?,
    artistName: String?,
    viewModel: AlbumsViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAllTracksClick: () -> Unit,
    onAddToQueue: (List<Track>) -> Unit = {},
    onAddToPlaylist: (List<Track>, () -> Unit) -> Unit = { _, _ -> },
) {
    LaunchedEffect(artistId) { viewModel.load(artistId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchHasMore by viewModel.searchHasMore.collectAsStateWithLifecycle()
    val loadingOrigin by viewModel.loadingOrigin.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val displayedState = if (searchActive && searchQuery.trim().length >= 2) searchState else state
    val albums = displayedState.content.orEmpty()
    val browseGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    LaunchedEffect(searchQuery) {
        if (searchActive) {
            searchGridState.scrollToItem(0)
        }
    }
    val selectionState = rememberAlbumSelectionState(albums, key = artistId)
    PlayerBackHandler(
        enabled = selectionState.isActive,
        onBack = selectionState::clear,
    )
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val artworkUris by viewModel.artworkUris.collectAsStateWithLifecycle()
    val downloadStatuses by viewModel.downloadStatuses.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarError by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarError = it } }
    val snackbarMessage = snackbarError?.localizedMessage()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
        snackbarError = null
    }
    val isRefreshing = loadingOrigin == LoadingOrigin.PullToRefresh

    val refreshState = rememberPullToRefreshState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        canScroll = { refreshState.distanceFraction == 0f },
    )
    val refreshEnabled by remember(scrollBehavior, refreshState) {
        derivedStateOf {
            scrollBehavior.state.collapsedFraction == 0f || refreshState.distanceFraction > 0f
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                if (selectionState.isActive) {
                    AlbumSelectionTopAppBar(
                        selectedCount = selectionState.selectedCount,
                        allSelectedFavorite = selectionState.areAllSelectedFavorite(albums),
                        onClose = selectionState::clear,
                        onFavorite = { selectionState.finish(albums, viewModel::toggleFavorites) },
                        onDownload = { selectionState.finish(albums, viewModel::downloadAlbums) },
                        onAddToQueue = {
                            selectionState.finish(albums) {
                                viewModel.addAlbumsToQueue(it, onAddToQueue)
                            }
                        },
                        onAddToPlaylist = {
                            val selectedAlbums = selectionState.selectedAlbums(albums)
                            viewModel.addAlbumsToQueue(selectedAlbums) { tracks ->
                                onAddToPlaylist(tracks, selectionState::clear)
                            }
                        },
                    )
                } else {
                    AppFlexibleTopAppBar(
                        title = artistName ?: stringResource(Res.string.albums),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    painterResource(Res.drawable.arrow_back),
                                    stringResource(Res.string.back)
                                )
                            }
                        },
                        actions = {
                            if (!searchActive) {
                                val alphaDirections = if (artistId == null) setOf(SortDirection.Ascending) else SortDirection.entries.toSet()
                                SortAction(sort, listOf(
                                    SortMenuItem(AlbumSortCriterion.Title, stringResource(Res.string.sort_title), alphaDirections),
                                    SortMenuItem(AlbumSortCriterion.Artist, stringResource(Res.string.sort_artist), alphaDirections),
                                    SortMenuItem(AlbumSortCriterion.Year, stringResource(Res.string.sort_year)),
                                ), stringResource(Res.string.sort_ascending), stringResource(Res.string.sort_descending)) { option ->
                                    browseGridState.requestScrollToItem(0)
                                    viewModel.updateSort(option)
                                }
                            }
                            SearchAction(viewModel::openSearch)
                        },
                        searchQuery = searchQuery.takeIf { searchActive },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSearchClose = viewModel::closeSearch,
                    )
                }
                if (
                    (loadingOrigin == LoadingOrigin.Automatic || loadingOrigin == LoadingOrigin.Sorting) &&
                    albums.isNotEmpty()
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            state = refreshState,
            enabled = refreshEnabled && !searchActive,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            when {
                displayedState is LoadableState.Loading && albums.isEmpty() && !isRefreshing -> CenteredLoading()
                displayedState is LoadableState.Failure && albums.isEmpty() -> CenteredError(
                    error = (displayedState as LoadableState.Failure).error,
                    onRetry = if (searchActive) viewModel::retrySearch else viewModel::retry,
                )

                albums.isEmpty() && (artistId == null || searchActive) -> Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(if (searchActive && searchQuery.length >= 2) Res.string.nothing_found else Res.string.albums_not_found))
                }

                else -> AlbumsGrid(
                    albums = albums,
                    error = (displayedState as? LoadableState.Failure)?.error,
                    onRetry = if (searchActive) viewModel::retrySearch else viewModel::retry,
                    onAlbumClick = onAlbumClick,
                    pendingIds = pending,
                    onFavoriteClick = viewModel::toggleFavorite,
                    onAllTracksClick = onAllTracksClick.takeIf { artistId != null && !searchActive },
                    showEmptyMessage = albums.isEmpty(),
                    hasMore = if (searchActive) searchHasMore else hasMore,
                    isLoadingMore = if (searchActive) searchState is LoadableState.Loading && albums.isNotEmpty() else loadingOrigin == LoadingOrigin.Pagination,
                    onLoadMore = if (searchActive) viewModel::loadMoreSearch else viewModel::loadMore,
                    gridState = if (searchActive) searchGridState else browseGridState,
                    artworkUris = artworkUris,
                    downloadStatuses = downloadStatuses,
                    selectionMode = selectionState.isActive,
                    selectedIds = selectionState.selectedIds,
                    onSelectionChange = selectionState::setSelected,
                )
            }
        }
    }
}

@Composable
fun AlbumsGrid(
    albums: List<Album>,
    error: AppError?,
    onRetry: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    pendingIds: Set<String> = emptySet(),
    onFavoriteClick: (Album) -> Unit = {},
    onAllTracksClick: (() -> Unit)? = null,
    showEmptyMessage: Boolean = false,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
    artworkUris: Map<String, String> = emptyMap(),
    downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    gridState: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues().withPlayerBottomInset(),
    ) {
        onAllTracksClick?.let { onClick ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(Padding.small)) {
                    AllTracksCard(onClick)
                }
            }
        }
        error?.let {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = onRetry) {
                    Text(it.localizedMessage(), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (showEmptyMessage) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Padding.large),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.albums_not_found))
                }
            }
        }
        itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
            if (hasMore && index >= albums.lastIndex - LOAD_MORE_THRESHOLD) {
                LaunchedEffect(albums.size) { onLoadMore() }
            }
            AlbumCard(
                album = album,
                onClick = { onAlbumClick(album) },
                onFavoriteClick = { onFavoriteClick(album) },
                favoriteEnabled = album.id !in pendingIds,
                artworkUrl = album.coverArtId?.let(artworkUris::get) ?: album.coverArtUrl,
                downloadStatus = downloadStatuses[album.id],
                selectionMode = selectionMode,
                selected = album.id in selectedIds,
                onSelectedChange = { onSelectionChange(album.id, it) },
            )
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(Padding.medium),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 12

@Composable
private fun AllTracksCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(Res.string.all_tracks),
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = Padding.medium, horizontal = Padding.large),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    favoriteEnabled: Boolean = true,
    artworkUrl: String? = album.coverArtUrl,
    downloadStatus: DownloadStatus? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
            onClick = {
                if (selectionMode) {
                    onSelectedChange(!selected)
                } else {
                    onClick()
                }
            },
            onLongClick = { onSelectedChange(true) },
        )
            .padding(Padding.small),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = rememberArtworkRequest(
                        url = artworkUrl,
                        albumId = album.id,
                        requestedSize = LARGE_ARTWORK_SIZE,
                    ),
                    contentDescription = stringResource(Res.string.album_cover, album.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.album),
                        contentDescription = stringResource(Res.string.no_cover),
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (selectionMode) {
                AlbumSelectionBadge(
                    selected = selected,
                    title = album.name,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Padding.small),
                )
            }
            if (downloadStatus != null) {
                AlbumDownloadBadge(
                    status = downloadStatus,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Padding.small),
                )
            }
        }
        Spacer(Modifier.height(Padding.small))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    album.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!selectionMode) {
                FavoriteToggleButton(album.isFavorite, onFavoriteClick, favoriteEnabled)
            }
        }
    }
}

@Composable
private fun AlbumDownloadBadge(
    status: DownloadStatus,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(28.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .78f)),
        contentAlignment = Alignment.Center,
    ) {
        when (status.state) {
            DownloadState.Queued, DownloadState.Downloading -> status.progress?.let { progress ->
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } ?: CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            DownloadState.Completed -> Icon(
                painter = painterResource(Res.drawable.arrow_down_bold),
                contentDescription = stringResource(Res.string.downloads),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            DownloadState.Failed -> Text(
                text = "!",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
fun AlbumSelectionBadge(
    selected: Boolean,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(28.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            )
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painterResource(Res.drawable.check),
                contentDescription = stringResource(Res.string.selected_album, title),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumSelectionTopAppBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    allSelectedFavorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    removesDownloads: Boolean = false,
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.selected_albums, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.clear_selection))
            }
        },
        actions = {
            onFavorite?.let { action ->
                IconButton(onClick = action) {
                    Icon(
                        painterResource(
                            if (allSelectedFavorite) Res.drawable.heart_outline else Res.drawable.heart,
                        ),
                        stringResource(
                            if (allSelectedFavorite) {
                                Res.string.remove_from_favorites
                            } else {
                                Res.string.favorite_selected_albums
                            },
                        ),
                    )
                }
            }
            IconButton(onClick = onDownload) {
                Icon(
                    painterResource(
                        if (removesDownloads) {
                            Res.drawable.download_off
                        } else {
                            Res.drawable.download
                        },
                    ),
                    stringResource(
                        if (removesDownloads) {
                            Res.string.remove_selected_downloads
                        } else {
                            Res.string.download_selected_albums
                        },
                    ),
                )
            }
            IconButton(onClick = onAddToQueue) {
                Icon(
                    painterResource(Res.drawable.playlist_play),
                    stringResource(Res.string.add_selected_albums_to_queue),
                )
            }
            onAddToPlaylist?.let { action ->
                IconButton(onClick = action) {
                    Icon(painterResource(Res.drawable.playlist_plus), stringResource(Res.string.add_to_playlist))
                }
            }
        },
    )
}

@Composable
private fun CenteredLoading() = Box(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.Center,
) {
    LoadingIndicator(Modifier.size(96.dp))
}

@Composable
private fun CenteredError(error: AppError, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Padding.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error.localizedMessage(), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(Padding.medium))
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}
