package info.jukov.player.feature.favorite.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.AppError
import info.jukov.player.feature.album.presentation.ui.AlbumsGrid
import info.jukov.player.feature.album.presentation.ui.AlbumSelectionTopAppBar
import info.jukov.player.feature.album.presentation.ui.rememberAlbumSelectionState
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.presentation.ui.ArtistRow
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoritesTab
import info.jukov.player.feature.favorite.presentation.FavoritesViewModel
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.presentation.ui.TracksList
import info.jukov.player.feature.track.presentation.ui.TrackSelectionTopAppBar
import info.jukov.player.feature.track.presentation.ui.rememberTrackSelectionState
import info.jukov.player.feature.playback.presentation.ui.PlayerBackHandler
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import info.jukov.player.core.presentation.LoadingOrigin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlayClick: (List<Track>, Int) -> Unit,
    onActiveTrackClick: () -> Unit,
    activeTrackId: String?,
    isPlaying: Boolean,
    loadingTrackId: String? = null,
    onAddToQueue: (List<Track>) -> Unit,
    onAddToPlaylist: (List<Track>, () -> Unit) -> Unit = { _, _ -> },
) {
    LaunchedEffect(viewModel) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadingOrigin by viewModel.loadingOrigin.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val pendingTrackIds = remember(pending) {
        pending.filterIsInstance<FavoriteTarget.Track>().mapTo(mutableSetOf()) { it.id }
    }
    val pendingAlbumIds = remember(pending) {
        pending.filterIsInstance<FavoriteTarget.Album>().mapTo(mutableSetOf()) { it.id }
    }
    val downloadStatuses by viewModel.downloadStatuses.collectAsStateWithLifecycle()
    val artworkUris by viewModel.artworkUris.collectAsStateWithLifecycle()
    val visibleTracks = state.content?.tracks.orEmpty()
    val visibleAlbums = state.content?.albums.orEmpty()
    val trackSelectionState = rememberTrackSelectionState(
        tracks = visibleTracks,
        active = selectedTab == FavoritesTab.Tracks,
    )
    val albumSelectionState = rememberAlbumSelectionState(
        albums = visibleAlbums,
        active = selectedTab == FavoritesTab.Albums,
    )
    PlayerBackHandler(
        enabled = trackSelectionState.isActive || albumSelectionState.isActive,
        onBack = {
            trackSelectionState.clear()
            albumSelectionState.clear()
        },
    )
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
    val pagerState = rememberPagerState(
        initialPage = selectedTab.ordinal,
        pageCount = { FavoritesTab.entries.size },
    )
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.selectTab(FavoritesTab.entries[page])
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                if (trackSelectionState.isActive) {
                    TrackSelectionTopAppBar(
                        selectedCount = trackSelectionState.selectedCount,
                        allSelectedFavorite = trackSelectionState.areAllSelectedFavorite(visibleTracks),
                        onClose = trackSelectionState::clear,
                        onFavorite = {
                            trackSelectionState.finish(visibleTracks, viewModel::toggleFavorites)
                        },
                        onDownload = {
                            trackSelectionState.finish(visibleTracks, viewModel::downloadTracks)
                        },
                        onAddToQueue = { trackSelectionState.finish(visibleTracks, onAddToQueue) },
                        onAddToPlaylist = {
                            onAddToPlaylist(
                                trackSelectionState.selectedTracks(visibleTracks),
                                trackSelectionState::clear,
                            )
                        },
                    )
                } else if (albumSelectionState.isActive) {
                    AlbumSelectionTopAppBar(
                        selectedCount = albumSelectionState.selectedCount,
                        allSelectedFavorite = albumSelectionState.areAllSelectedFavorite(visibleAlbums),
                        onClose = albumSelectionState::clear,
                        onFavorite = {
                            albumSelectionState.finish(
                                visibleAlbums,
                                viewModel::toggleFavoriteAlbums,
                            )
                        },
                        onDownload = {
                            albumSelectionState.finish(visibleAlbums, viewModel::downloadAlbums)
                        },
                        onAddToQueue = {
                            albumSelectionState.finish(visibleAlbums) { albums ->
                                viewModel.addAlbumsToQueue(albums, onAddToQueue)
                            }
                        },
                        onAddToPlaylist = {
                            val selectedAlbums = albumSelectionState.selectedAlbums(visibleAlbums)
                            viewModel.addAlbumsToQueue(selectedAlbums) { tracks ->
                                onAddToPlaylist(tracks, albumSelectionState::clear)
                            }
                        },
                    )
                } else {
                    AppFlexibleTopAppBar(
                        title = stringResource(Res.string.favorites),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    painterResource(Res.drawable.arrow_back),
                                    contentDescription = stringResource(Res.string.back),
                                )
                            }
                        },
                    )
                }
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    FavoritesTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab.title()) },
                        )
                    }
                }
                if (loadingOrigin == LoadingOrigin.Automatic && state.content?.isNotEmpty() == true) {
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
            enabled = refreshEnabled,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            val favorites = state.content
            if (state is LoadableState.Loading && favorites?.isEmpty() != false && !isRefreshing) {
                Centered { LoadingIndicator(Modifier.size(96.dp)) }
            } else if (state is LoadableState.Failure && favorites == null) {
                Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            (state as LoadableState.Failure).error.localizedMessage(),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = viewModel::retry) { Text(stringResource(Res.string.retry)) }
                    }
                }
            } else {
                val content = favorites ?: return@PullToRefreshBox
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    when (FavoritesTab.entries[page]) {
                        FavoritesTab.Tracks -> if (content.tracks.isEmpty()) {
                            Empty(stringResource(Res.string.no_favorite_tracks))
                        } else TracksList(
                            tracks = content.tracks,
                            error = (state as? LoadableState.Failure)?.error,
                            onPlayClick = onPlayClick,
                            onActiveTrackClick = onActiveTrackClick,
                            activeTrackId = activeTrackId,
                            isPlaying = isPlaying,
                            loadingTrackId = loadingTrackId,
                            pendingIds = pendingTrackIds,
                            onFavoriteClick = {
                                viewModel.toggleFavorite(
                                    FavoriteTarget.Track(it.id),
                                    it.isFavorite,
                                )
                            },
                            downloadStatuses = downloadStatuses,
                            onDownloadClick = viewModel::downloadTrack,
                            onCancelDownload = viewModel::cancelTrackDownload,
                            onRetryDownload = viewModel::retryTrackDownload,
                            artworkUris = artworkUris,
                            selectionMode = trackSelectionState.isActive,
                            selectedIds = trackSelectionState.selectedIds,
                            onSelectionChange = trackSelectionState::setSelected,
                            modifier = Modifier,
                        )
                        FavoritesTab.Albums -> if (content.albums.isEmpty()) {
                            Empty(stringResource(Res.string.no_favorite_albums))
                        } else AlbumsGrid(
                            albums = content.albums,
                            error = (state as? LoadableState.Failure)?.error,
                            onRetry = viewModel::refresh,
                            onAlbumClick = onAlbumClick,
                            pendingIds = pendingAlbumIds,
                            onFavoriteClick = {
                                viewModel.toggleFavorite(
                                    FavoriteTarget.Album(it.id),
                                    it.isFavorite,
                                )
                            },
                            artworkUris = artworkUris,
                            selectionMode = albumSelectionState.isActive,
                            selectedIds = albumSelectionState.selectedIds,
                            onSelectionChange = albumSelectionState::setSelected,
                            modifier = Modifier,
                        )
                        FavoritesTab.Artists -> if (content.artists.isEmpty()) {
                            Empty(stringResource(Res.string.no_favorite_artists))
                        } else LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = Padding.small)
                                .withPlayerBottomInset(),
                            verticalArrangement = Arrangement.spacedBy(Padding.xSmall),
                        ) {
                            items(content.artists, key = { it.id }) { artist ->
                                ArtistRow(
                                    artist = artist,
                                    onClick = { onArtistClick(artist) },
                                    onFavoriteClick = {
                                        viewModel.toggleFavorite(
                                            FavoriteTarget.Artist(artist.id),
                                            artist.isFavorite,
                                        )
                                    },
                                    favoriteEnabled = FavoriteTarget.Artist(artist.id) !in pending,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun FavoritesTab.title(): String = when (this) {
    FavoritesTab.Tracks -> stringResource(Res.string.tracks)
    FavoritesTab.Albums -> stringResource(Res.string.albums)
    FavoritesTab.Artists -> stringResource(Res.string.artists)
}

private fun Favorites.isEmpty(): Boolean =
    tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()

private fun Favorites.isNotEmpty(): Boolean = !isEmpty()

@Composable
private fun Empty(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) { Text(text) }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
