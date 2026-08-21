package info.jukov.player.feature.track.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.TrackSortCriterion
import info.jukov.player.core.presentation.LoadingOrigin
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.PlayPauseButton
import info.jukov.player.core.presentation.ui.SearchAction
import info.jukov.player.core.presentation.ui.SortAction
import info.jukov.player.core.presentation.ui.SortMenuItem
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.rememberAppCollapsingTopAppBarState
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playback.presentation.ui.PlayerBackHandler
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.track.presentation.TracksViewModel
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    filter: TracksFilter,
    albumName: String? = null,
    artistName: String? = null,
    albumArtistId: String? = null,
    albumYear: Int? = null,
    coverArtUrl: String? = null,
    coverArtId: String? = null,
    albumIsFavorite: Boolean = false,
    viewModel: TracksViewModel,
    onBack: () -> Unit,
    onPlayClick: (List<Track>, Int, PlaybackOrigin) -> Unit = { _, _, _ -> },
    onActiveTrackClick: () -> Unit = {},
    activeTrackId: String? = null,
    isPlaying: Boolean = false,
    isPlaybackLoading: Boolean = false,
    loadingTrackId: String? = null,
    activeOrigin: PlaybackOrigin = PlaybackOrigin.TrackList,
    onAddToQueue: (List<Track>) -> Unit = {},
    onAddToPlaylist: (List<Track>, () -> Unit) -> Unit = { _, _ -> },
    onArtistClick: (String, String) -> Unit = { _, _ -> },
) {
    LaunchedEffect(filter, albumIsFavorite) { viewModel.load(filter, albumIsFavorite) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchHasMore by viewModel.searchHasMore.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val loadingOrigin by viewModel.loadingOrigin.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val currentAlbumIsFavorite by viewModel.albumIsFavorite.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val downloadStatuses by viewModel.downloadStatuses.collectAsStateWithLifecycle()
    val albumDownloadStatuses by viewModel.albumDownloadStatuses.collectAsStateWithLifecycle()
    val artworkUris by viewModel.artworkUris.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarError by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarError = it } }
    val snackbarMessage = snackbarError?.localizedMessage()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
        snackbarError = null
    }
    val displayedState = if (searchActive && searchQuery.trim().length >= 2) searchState else state
    val tracks = displayedState.content.orEmpty()
    val browseListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    LaunchedEffect(searchQuery) {
        if (searchActive) {
            searchListState.scrollToItem(0)
        }
    }
    val selectionState = rememberTrackSelectionState(tracks, key = filter)
    PlayerBackHandler(
        enabled = selectionState.isActive,
        onBack = selectionState::clear,
    )
    val collectionOrigin = when (filter) {
        is TracksFilter.ByAlbum -> PlaybackOrigin.Album(filter.albumId)
        is TracksFilter.ByArtist -> PlaybackOrigin.Artist(filter.artistId)
        TracksFilter.All -> null
    }
    val isCollectionActive = collectionOrigin != null && activeOrigin == collectionOrigin
    val onCollectionPlayClick: () -> Unit = {
        if (isCollectionActive) {
            onActiveTrackClick()
        } else if (collectionOrigin != null) {
            onPlayClick(tracks, 0, collectionOrigin)
        }
    }
    val albumHeader = if (filter is TracksFilter.ByAlbum && albumName != null) {
        AlbumHeader(
            albumName, artistName.orEmpty(),
            coverArtId?.let(artworkUris::get) ?: coverArtUrl,
            coverArtId, filter.albumId, albumArtistId, albumYear,
        )
    } else null
    val pullToRefreshState = rememberPullToRefreshState()
    val canScrollAppBar = remember(pullToRefreshState) {
        { pullToRefreshState.distanceFraction == 0f }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        canScroll = canScrollAppBar,
    )
    val albumAppBarState = rememberAppCollapsingTopAppBarState(
        canScroll = canScrollAppBar,
    )
    val isRefreshing = loadingOrigin == LoadingOrigin.PullToRefresh
    val isPullToRefreshEnabled by remember(
        albumHeader,
        albumAppBarState,
        scrollBehavior,
        pullToRefreshState,
    ) {
        derivedStateOf {
            val isAppBarExpanded = if (albumHeader == null) {
                scrollBehavior.state.collapsedFraction == 0f
            } else {
                albumAppBarState.collapsedFraction == 0f
            }
            isAppBarExpanded || pullToRefreshState.distanceFraction > 0f
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(
            albumHeader?.let { albumAppBarState.nestedScrollConnection }
                ?: scrollBehavior.nestedScrollConnection,
        ),
        topBar = {
            Column {
                if (selectionState.isActive) {
                    TrackSelectionTopAppBar(
                        selectedCount = selectionState.selectedCount,
                        allSelectedFavorite = selectionState.areAllSelectedFavorite(tracks),
                        onClose = selectionState::clear,
                        onFavorite = { selectionState.finish(tracks, viewModel::toggleFavorites) },
                        onDownload = { selectionState.finish(tracks, viewModel::downloadTracks) },
                        onAddToQueue = { selectionState.finish(tracks, onAddToQueue) },
                        onAddToPlaylist = {
                            onAddToPlaylist(selectionState.selectedTracks(tracks), selectionState::clear)
                        },
                    )
                } else if (albumHeader == null) {
                    AppFlexibleTopAppBar(
                        title = artistName ?: stringResource(Res.string.tracks),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack) },
                        actions = {
                            if (filter is TracksFilter.ByArtist && !searchActive) {
                                SortAction(sort, listOf(
                                    SortMenuItem(TrackSortCriterion.Title, stringResource(Res.string.sort_title)),
                                    SortMenuItem(TrackSortCriterion.Artist, stringResource(Res.string.sort_artist)),
                                ), stringResource(Res.string.sort_ascending), stringResource(Res.string.sort_descending)) { option ->
                                    browseListState.requestScrollToItem(0)
                                    viewModel.updateSort(option)
                                }
                            }
                            if (filter !is TracksFilter.ByAlbum) {
                                SearchAction(viewModel::openSearch)
                            }
                            if (filter is TracksFilter.ByArtist) {
                                PlayPauseButton(
                                    isPlaying = isCollectionActive && isPlaying,
                                    isLoading = isCollectionActive && isPlaybackLoading,
                                    onClick = onCollectionPlayClick,
                                    enabled = tracks.isNotEmpty(),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                        },
                        searchQuery = searchQuery.takeIf { searchActive },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSearchClose = viewModel::closeSearch,
                    )
                } else {
                    AlbumTracksTopAppBar(
                        header = albumHeader, tracks = tracks, appBarState = albumAppBarState,
                        onBack = onBack,
                        onPlayClick = onCollectionPlayClick,
                        isPlaying = isCollectionActive && isPlaying,
                        isLoading = isCollectionActive && isPlaybackLoading,
                        isFavorite = currentAlbumIsFavorite,
                        favoriteEnabled = albumHeader.albumId !in pending,
                        onFavoriteClick = { viewModel.toggleAlbumFavorite(albumHeader.albumId) },
                        onAddToPlaylist = { onAddToPlaylist(tracks, {}) },
                        onAddToQueue = { onAddToQueue(tracks) },
                        downloadStatus = albumDownloadStatuses[albumHeader.albumId],
                        onDownloadClick = {
                            val status = albumDownloadStatuses[albumHeader.albumId]
                            if (status?.state == DownloadState.Queued || status?.state == DownloadState.Downloading) {
                                viewModel.cancelAlbumDownload(albumHeader.albumId)
                            } else if (status?.state != DownloadState.Completed) {
                                viewModel.downloadAlbum(
                                    Album(
                                        id = albumHeader.albumId,
                                        name = albumHeader.name,
                                        artist = albumHeader.artist,
                                        artistId = null,
                                        year = albumHeader.year,
                                        coverArtId = albumHeader.coverArtId,
                                        coverArtUrl = albumHeader.coverArtUrl,
                                        isFavorite = currentAlbumIsFavorite,
                                    ),
                                )
                            }
                        },
                        onArtistClick = onArtistClick,
                    )
                }
                if (loadingOrigin == LoadingOrigin.Automatic && tracks.isNotEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            enabled = isPullToRefreshEnabled && !searchActive,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            when {
                displayedState is LoadableState.Loading && tracks.isEmpty() && !isRefreshing -> CenteredLoading()

                displayedState is LoadableState.Failure && tracks.isEmpty() -> CenteredError(
                    error = (displayedState as LoadableState.Failure).error,
                    onRetry = if (searchActive) viewModel::retrySearch else viewModel::retry,
                )

                tracks.isEmpty() -> Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(if (searchActive && searchQuery.length >= 2) Res.string.nothing_found else Res.string.tracks_not_found))
                }

                else -> TracksList(
                    tracks = tracks,
                    showArtwork = filter !is TracksFilter.ByAlbum,
                    showTrackNumber = filter is TracksFilter.ByAlbum,
                    error = (displayedState as? LoadableState.Failure)?.error,
                    onPlayClick = { queue, index ->
                        if (searchActive) {
                            onPlayClick(listOf(queue[index]), 0, PlaybackOrigin.TrackList)
                        } else {
                            onPlayClick(
                                queue,
                                index,
                                collectionOrigin ?: PlaybackOrigin.TrackList,
                            )
                        }
                    },
                    onActiveTrackClick = onActiveTrackClick,
                    activeTrackId = activeTrackId,
                    isPlaying = isPlaying,
                    loadingTrackId = loadingTrackId,
                    pendingIds = pending,
                    onFavoriteClick = viewModel::toggleFavorite,
                    downloadStatuses = downloadStatuses,
                    onDownloadClick = viewModel::downloadTrack,
                    onCancelDownload = viewModel::cancelTrackDownload,
                    onRetryDownload = viewModel::retryTrackDownload,
                    selectionMode = selectionState.isActive,
                    selectedIds = selectionState.selectedIds,
                    onSelectionChange = selectionState::setSelected,
                    artworkUris = artworkUris,
                    hasMore = if (searchActive) searchHasMore else hasMore,
                    isLoadingMore = if (searchActive) searchState is LoadableState.Loading && tracks.isNotEmpty() else loadingOrigin == LoadingOrigin.Pagination,
                    onLoadMore = if (searchActive) viewModel::loadMoreSearch else viewModel::loadMore,
                    listState = if (searchActive) searchListState else browseListState,
                )
            }
        }
    }
}

@Composable
private fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(Modifier.size(96.dp))
    }
}

@Composable
private fun CenteredError(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error.localizedMessage(), color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}
