package info.jukov.player.feature.artist.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.AppError
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.artist.presentation.ArtistsViewModel
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.SearchAction
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.core.presentation.LoadingOrigin
import info.jukov.player.feature.playback.presentation.ui.PlayerBackHandler
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    viewModel: ArtistsViewModel,
    onBack: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAddToQueue: (List<Track>) -> Unit = {},
    onAddToPlaylist: (List<Track>, () -> Unit) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchHasMore by viewModel.searchHasMore.collectAsStateWithLifecycle()
    val loadingOrigin by viewModel.loadingOrigin.collectAsStateWithLifecycle()
    val displayedState = if (searchActive && searchQuery.trim().length >= 2) searchState else state
    val artists = displayedState.content.orEmpty()
    val selectionState = rememberArtistSelectionState(
        artists,
        key = searchActive to searchQuery,
    )
    PlayerBackHandler(enabled = selectionState.isActive, onBack = selectionState::clear)
    val browseListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    LaunchedEffect(searchQuery) {
        if (searchActive) {
            searchListState.scrollToItem(0)
        }
    }
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarError by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarError = it } }
    val snackbarMessage = snackbarError?.localizedMessage()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
        snackbarError = null
    }
    val isRefreshing = loadingOrigin == LoadingOrigin.PullToRefresh
    val pullToRefreshState = rememberPullToRefreshState()
    val canScrollTopAppBar = remember(pullToRefreshState) {
        { pullToRefreshState.distanceFraction == 0f }
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        canScroll = canScrollTopAppBar,
    )
    val isPullToRefreshEnabled by remember(topAppBarScrollBehavior, pullToRefreshState) {
        derivedStateOf {
            topAppBarScrollBehavior.state.collapsedFraction == 0f ||
                    pullToRefreshState.distanceFraction > 0f
        }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                if (selectionState.isActive) {
                    ArtistSelectionTopAppBar(
                        selectedCount = selectionState.selectedCount,
                        allSelectedFavorite = selectionState.areAllSelectedFavorite(artists),
                        onClose = selectionState::clear,
                        onFavorite = { selectionState.finish(artists, viewModel::toggleFavorites) },
                        onDownload = {
                            viewModel.downloadArtists(selectionState.selectedArtists(artists), selectionState::clear)
                        },
                        onAddToQueue = {
                            viewModel.addArtistsToQueue(selectionState.selectedArtists(artists)) { tracks ->
                                onAddToQueue(tracks)
                                selectionState.clear()
                            }
                        },
                        onAddToPlaylist = {
                            viewModel.addArtistsToQueue(selectionState.selectedArtists(artists)) { tracks ->
                                onAddToPlaylist(tracks, selectionState::clear)
                            }
                        },
                    )
                } else {
                    ArtistsTopAppBar(
                        onBack = onBack,
                        onSearchClick = viewModel::openSearch,
                        searchQuery = searchQuery.takeIf { searchActive },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSearchClose = viewModel::closeSearch,
                        scrollBehavior = topAppBarScrollBehavior,
                    )
                }
                if (loadingOrigin == LoadingOrigin.Automatic && artists.isNotEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
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
            modifier = Modifier.fillMaxSize().padding(it),
        ) {
            when {
                displayedState is LoadableState.Loading && artists.isEmpty() && !isRefreshing -> LoadingContent()
                displayedState is LoadableState.Failure && artists.isEmpty() ->
                    ErrorContent((displayedState as LoadableState.Failure).error, if (searchActive) viewModel::retrySearch else viewModel::retry)
                artists.isEmpty() -> if (searchActive && searchQuery.length >= 2) SearchEmptyContent() else EmptyContent()
                else -> ArtistsContent(
                    artists = artists,
                    error = (displayedState as? LoadableState.Failure)?.error,
                    onRetry = if (searchActive) viewModel::retrySearch else viewModel::retry,
                    onArtistClick = onArtistClick,
                    pendingIds = pending,
                    onFavoriteClick = viewModel::toggleFavorite,
                    hasMore = searchActive && searchHasMore,
                    onLoadMore = viewModel::loadMoreSearch,
                    listState = if (searchActive) searchListState else browseListState,
                    selectionMode = selectionState.isActive,
                    selectedIds = selectionState.selectedIds,
                    onSelectionChange = selectionState::setSelected,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ArtistsTopAppBar(
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    AppFlexibleTopAppBar(
        title = stringResource(Res.string.artists),
        scrollBehavior = scrollBehavior,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onSearchClose = onSearchClose,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_back),
                    contentDescription = stringResource(Res.string.back),
                )
            }
        },
        actions = {
            SearchAction(onSearchClick)
        },
    )
}

@Composable
fun ArtistsContent(
    artists: List<Artist>,
    error: AppError?,
    onRetry: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    pendingIds: Set<String> = emptySet(),
    onFavoriteClick: (Artist) -> Unit = {},
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
) {
    Column(Modifier.fillMaxSize()) {
        error?.let {
            TextButton(onClick = onRetry) {
                Text(it.localizedMessage(), color = MaterialTheme.colorScheme.error)
            }
        }
        LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Padding.small).withPlayerBottomInset(),
                verticalArrangement = Arrangement.spacedBy(Padding.xSmall),
            ) {
                itemsIndexed(artists, key = { _, it -> it.id }) { index, artist ->
                    if (hasMore && index >= artists.lastIndex - 8) {
                        LaunchedEffect(artists.size) { onLoadMore() }
                    }
                    ArtistRow(
                        artist = artist,
                        onClick = { onArtistClick(artist) },
                        onFavoriteClick = { onFavoriteClick(artist) },
                        favoriteEnabled = artist.id !in pendingIds,
                        selectionMode = selectionMode,
                        selected = artist.id in selectedIds,
                        onSelectedChange = { onSelectionChange(artist.id, it) },
                    )
                    HorizontalDivider()
                }
            }
    }
}

@Composable
fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    favoriteEnabled: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = { if (selectionMode) onSelectedChange(!selected) else onClick() },
            onLongClick = { onSelectedChange(!selected) },
        ),
        leadingContent = if (selectionMode) {
            { Checkbox(checked = selected, onCheckedChange = onSelectedChange) }
        } else null,
        headlineContent = { Text(artist.name, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(stringResource(Res.string.albums_count, artist.albumCount)) },
        trailingContent = {
            FavoriteToggleButton(artist.isFavorite, onFavoriteClick, favoriteEnabled)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistSelectionTopAppBar(
    selectedCount: Int,
    allSelectedFavorite: Boolean,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.selected_artists, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onFavorite) {
                Icon(
                    painterResource(if (allSelectedFavorite) Res.drawable.heart_outline else Res.drawable.heart),
                    stringResource(
                        if (allSelectedFavorite) Res.string.remove_from_favorites
                        else Res.string.favorite_selected_artists,
                    ),
                )
            }
            IconButton(onClick = onDownload) {
                Icon(painterResource(Res.drawable.download), stringResource(Res.string.download_selected_artists))
            }
            IconButton(onClick = onAddToQueue) {
                Icon(painterResource(Res.drawable.playlist_play), stringResource(Res.string.add_selected_artists_to_queue))
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(painterResource(Res.drawable.playlist_plus), stringResource(Res.string.add_to_playlist))
            }
        },
    )
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(96.dp))
    }
}

@Composable
private fun ErrorContent(error: AppError, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Padding.large), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error.localizedMessage(), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(Padding.medium))
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.artists_not_found))
    }
}

@Composable
private fun SearchEmptyContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.nothing_found))
    }
}
