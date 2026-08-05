package info.jukov.player.favorite.presentation.ui

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
import info.jukov.player.album.presentation.ui.AlbumsGrid
import info.jukov.player.artist.presentation.ui.ArtistRow
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.favorite.domain.FavoriteTarget
import info.jukov.player.favorite.presentation.FavoritesTab
import info.jukov.player.favorite.presentation.FavoritesViewModel
import info.jukov.player.track.domain.Track
import info.jukov.player.track.presentation.ui.TracksList
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.arrow_back
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
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
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }
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
                    title = "Избранное",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(Res.drawable.arrow_back),
                                contentDescription = "Назад",
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
                            text = { Text(tab.title) },
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
                            (state as LoadableState.Failure).message,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = viewModel::refresh) { Text("Повторить") }
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
                            Empty("Нет избранных треков")
                        } else TracksList(
                            tracks = content.tracks,
                            error = (state as? LoadableState.Failure)?.message,
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
                            Empty("Нет избранных альбомов")
                        } else AlbumsGrid(
                            albums = content.albums,
                            error = (state as? LoadableState.Failure)?.message,
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
                            Empty("Нет избранных исполнителей")
                        } else LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = Padding.small),
                            verticalArrangement = Arrangement.spacedBy(Padding.xSmall),
                        ) {
                            items(content.artists, key = { it.id }) { artist ->
                                ArtistRow(
                                    artist = artist,
                                    onClick = { onArtistClick(artist.id) },
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


private val FavoritesTab.title: String get() = when (this) {
    FavoritesTab.Tracks -> "Треки"
    FavoritesTab.Albums -> "Альбомы"
    FavoritesTab.Artists -> "Артисты"
}

@Composable
private fun Empty(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = { content() })
}
