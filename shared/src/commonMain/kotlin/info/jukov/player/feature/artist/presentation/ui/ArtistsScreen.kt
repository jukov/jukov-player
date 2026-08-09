package info.jukov.player.feature.artist.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
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
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    viewModel: ArtistsViewModel,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAllAlbumsClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchHasMore by viewModel.searchHasMore.collectAsStateWithLifecycle()
    val loadingOrigin by viewModel.loadingOrigin.collectAsStateWithLifecycle()
    val displayedState = if (searchActive && searchQuery.trim().length >= 2) searchState else state
    val artists = displayedState.content.orEmpty()
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
                ArtistsTopAppBar(
                    onLogout = onLogout,
                    onBack = onBack,
                    onAllAlbumsClick = onAllAlbumsClick,
                    onSearchClick = viewModel::openSearch,
                    searchQuery = searchQuery.takeIf { searchActive },
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSearchClose = viewModel::closeSearch,
                    scrollBehavior = topAppBarScrollBehavior,
                )
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
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ArtistsTopAppBar(
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onAllAlbumsClick: () -> Unit,
    onSearchClick: () -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuExpanded by remember { mutableStateOf(false) }

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
            IconButton(
                onClick = { menuExpanded = true },
            ) {
                Icon(
                    painter = painterResource(Res.drawable.more_vert),
                    contentDescription = stringResource(Res.string.more),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.all_albums)) },
                    onClick = {
                        menuExpanded = false
                        onAllAlbumsClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.sign_out)) },
                    onClick = {
                        menuExpanded = false
                        onLogout()
                    },
                )
            }
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
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(artist.name, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(stringResource(Res.string.albums_count, artist.albumCount)) },
        trailingContent = {
            FavoriteToggleButton(artist.isFavorite, onFavoriteClick, favoriteEnabled)
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
