package info.jukov.player.feature.favorite.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.AppError
import info.jukov.player.feature.album.presentation.ui.AlbumsGrid
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.presentation.ui.ArtistRow
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoritesTab
import info.jukov.player.feature.favorite.presentation.FavoritesViewModel
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.presentation.ui.TracksList
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
) {
    LaunchedEffect(viewModel) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarError by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarError = it } }
    val snackbarMessage = snackbarError?.localizedMessage()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
        snackbarError = null
    }
    var refreshRequested by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state !is LoadableState.Loading) refreshRequested = false
    }
    val refreshState = rememberPullToRefreshState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
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
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = refreshRequested && state is LoadableState.Loading,
            onRefresh = {
                refreshRequested = true
                viewModel.refresh()
            },
            state = refreshState,
            enabled = refreshEnabled,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = refreshRequested && state is LoadableState.Loading,
                    state = refreshState,
                )
            },
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            val favorites = state.content
            if (state is LoadableState.Loading && favorites == null) {
                Centered { CircularProgressIndicator() }
            } else if (state is LoadableState.Failure && favorites == null) {
                Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            (state as LoadableState.Failure).error.localizedMessage(),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = viewModel::refresh) { Text(stringResource(Res.string.retry)) }
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
                            pendingIds = pending.filterIsInstance<FavoriteTarget.Track>()
                                .mapTo(mutableSetOf()) { it.id },
                            onFavoriteClick = {
                                viewModel.toggleFavorite(
                                    FavoriteTarget.Track(it.id),
                                    it.isFavorite,
                                )
                            },
                            modifier = Modifier,
                        )
                        FavoritesTab.Albums -> if (content.albums.isEmpty()) {
                            Empty(stringResource(Res.string.no_favorite_albums))
                        } else AlbumsGrid(
                            albums = content.albums,
                            error = (state as? LoadableState.Failure)?.error,
                            onRetry = viewModel::refresh,
                            onAlbumClick = onAlbumClick,
                            pendingIds = pending.filterIsInstance<FavoriteTarget.Album>()
                                .mapTo(mutableSetOf()) { it.id },
                            onFavoriteClick = {
                                viewModel.toggleFavorite(
                                    FavoriteTarget.Album(it.id),
                                    it.isFavorite,
                                )
                            },
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

@Composable
private fun Empty(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = { content() })
}
