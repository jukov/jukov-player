package info.jukov.player.feature.artist.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.AppError
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.artist.presentation.ArtistsViewModel
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.localizedMessage
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    viewModel: ArtistsViewModel,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAllAlbumsClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val artists = state.content.orEmpty()
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

    val isRefreshing = refreshRequested && state is LoadableState.Loading
    val pullToRefreshState = rememberPullToRefreshState()
    val canScrollTopAppBar = remember(pullToRefreshState) {
        { pullToRefreshState.distanceFraction == 0f }
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
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
            ArtistsTopAppBar(onLogout, onBack, onAllAlbumsClick, topAppBarScrollBehavior)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                refreshRequested = true
                viewModel.retry()
            },
            state = pullToRefreshState,
            enabled = isPullToRefreshEnabled,
            modifier = Modifier.fillMaxSize().padding(it),
        ) {
            when {
                state is LoadableState.Loading && artists.isEmpty() -> LoadingContent()
                state is LoadableState.Failure && artists.isEmpty() ->
                    ErrorContent((state as LoadableState.Failure).error, viewModel::retry)
                artists.isEmpty() -> EmptyContent()
                else -> ArtistsContent(
                    artists = artists,
                    error = (state as? LoadableState.Failure)?.error,
                    onRetry = viewModel::retry,
                    onArtistClick = onArtistClick,
                    pendingIds = pending,
                    onFavoriteClick = viewModel::toggleFavorite,
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
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    AppFlexibleTopAppBar(
        title = stringResource(Res.string.artists),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_back),
                    contentDescription = stringResource(Res.string.back),
                )
            }
        },
        actions = {
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
    onArtistClick: (String) -> Unit,
    pendingIds: Set<String> = emptySet(),
    onFavoriteClick: (Artist) -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        error?.let {
            TextButton(onClick = onRetry) {
                Text(it.localizedMessage(), color = MaterialTheme.colorScheme.error)
            }
        }
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Padding.small),
                verticalArrangement = Arrangement.spacedBy(Padding.xSmall),
            ) {
                items(artists, key = { it.id }) { artist ->
                    ArtistRow(
                        artist = artist,
                        onClick = { onArtistClick(artist.id) },
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(error: AppError, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Padding.large), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error.localizedMessage(), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(Padding.medium))
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.artists_not_found))
    }
}
