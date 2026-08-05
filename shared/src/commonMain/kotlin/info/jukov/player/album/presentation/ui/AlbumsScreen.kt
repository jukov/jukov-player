package info.jukov.player.album.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import info.jukov.player.album.domain.Album
import info.jukov.player.album.presentation.AlbumsViewModel
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.arrow_back
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    artistId: String?,
    viewModel: AlbumsViewModel,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
) {
    LaunchedEffect(artistId) { viewModel.load(artistId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val albums = state.content.orEmpty()
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppFlexibleTopAppBar(
                title = "Альбомы",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back),
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = refreshRequested && state is LoadableState.Loading,
            onRefresh = {
                refreshRequested = true
                viewModel.retry()
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
            when {
                state is LoadableState.Loading && albums.isEmpty() -> CenteredLoading()
                state is LoadableState.Failure && albums.isEmpty() -> CenteredError(
                    message = (state as LoadableState.Failure).message,
                    onRetry = viewModel::retry,
                )
                albums.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Альбомы не найдены")
                }
                else -> AlbumsGrid(
                    albums = albums,
                    error = (state as? LoadableState.Failure)?.message,
                    onRetry = viewModel::retry,
                    onAlbumClick = onAlbumClick,
                    pendingIds = pending,
                    onFavoriteClick = viewModel::toggleFavorite,
                )
            }
        }
    }
}

@Composable
fun AlbumsGrid(
    albums: List<Album>,
    error: String?,
    onRetry: () -> Unit,
    onAlbumClick: (String) -> Unit,
    pendingIds: Set<String> = emptySet(),
    onFavoriteClick: (Album) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Padding.small),
        horizontalArrangement = Arrangement.spacedBy(Padding.small),
        verticalArrangement = Arrangement.spacedBy(Padding.medium),
    ) {
        error?.let {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = onRetry) { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        items(albums, key = { it.id }) { album ->
            AlbumCard(
                album = album,
                onClick = { onAlbumClick(album.id) },
                onFavoriteClick = { onFavoriteClick(album) },
                favoriteEnabled = album.id !in pendingIds,
            )
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    favoriteEnabled: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (album.coverArtUrl != null) {
                AsyncImage(
                    model = album.coverArtUrl,
                    contentDescription = "Обложка альбома ${album.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("Нет обложки", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            FavoriteToggleButton(album.isFavorite, onFavoriteClick, favoriteEnabled)
        }
    }
}

@Composable
private fun CenteredLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
}

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Padding.large), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(Padding.medium))
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}
