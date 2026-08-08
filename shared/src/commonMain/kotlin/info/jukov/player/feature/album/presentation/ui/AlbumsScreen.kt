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
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.core.presentation.ui.LARGE_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.core.presentation.LoadingOrigin
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    val loadingOrigin by viewModel.loadingOrigin.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val albums = state.content.orEmpty()
    val selectionState = rememberAlbumSelectionState(albums, key = artistId)
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val artworkUris by viewModel.artworkUris.collectAsStateWithLifecycle()
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
                    )
                }
                if (loadingOrigin == LoadingOrigin.Automatic && albums.isNotEmpty()) {
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
            when {
                state is LoadableState.Loading && albums.isEmpty() && !isRefreshing -> CenteredLoading()
                state is LoadableState.Failure && albums.isEmpty() -> CenteredError(
                    error = (state as LoadableState.Failure).error,
                    onRetry = viewModel::retry,
                )

                albums.isEmpty() && artistId == null -> Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.albums_not_found))
                }

                else -> AlbumsGrid(
                    albums = albums,
                    error = (state as? LoadableState.Failure)?.error,
                    onRetry = viewModel::retry,
                    onAlbumClick = onAlbumClick,
                    pendingIds = pending,
                    onFavoriteClick = viewModel::toggleFavorite,
                    onAllTracksClick = onAllTracksClick.takeIf { artistId != null },
                    showEmptyMessage = albums.isEmpty(),
                    hasMore = hasMore,
                    isLoadingMore = loadingOrigin == LoadingOrigin.Pagination,
                    onLoadMore = viewModel::loadMore,
                    artworkUris = artworkUris,
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
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Padding.small).withPlayerBottomInset(),
        horizontalArrangement = Arrangement.spacedBy(Padding.small),
        verticalArrangement = Arrangement.spacedBy(Padding.small),
    ) {
        onAllTracksClick?.let { onClick ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                AllTracksCard(onClick)
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
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = {
                if (selectionMode) {
                    onSelectedChange(!selected)
                } else {
                    onClick()
                }
            },
            onLongClick = { onSelectedChange(true) },
        ),
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
                Text(
                    stringResource(Res.string.no_cover),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectionMode) {
                AlbumSelectionBadge(
                    selected = selected,
                    title = album.name,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Padding.small),
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
private fun AlbumSelectionBadge(
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
private fun AlbumSelectionTopAppBar(
    selectedCount: Int,
    allSelectedFavorite: Boolean,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.selected_albums, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onFavorite) {
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
            IconButton(onClick = onDownload) {
                Icon(
                    painterResource(Res.drawable.download_circle),
                    stringResource(Res.string.download_selected_albums),
                )
            }
            IconButton(onClick = onAddToQueue) {
                Icon(
                    painterResource(Res.drawable.playlist_play),
                    stringResource(Res.string.add_selected_albums_to_queue),
                )
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(painterResource(Res.drawable.playlist_plus), stringResource(Res.string.add_to_playlist))
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
