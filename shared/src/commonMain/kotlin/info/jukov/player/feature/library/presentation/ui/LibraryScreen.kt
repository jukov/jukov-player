package info.jukov.player.feature.library.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import coil3.compose.AsyncImage
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.*
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.library.presentation.LibraryViewModel
import info.jukov.player.feature.search.domain.LibrarySearchItem
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag
import info.jukov.player.feature.playback.presentation.ui.PlayerBackHandler

private data class LibraryItem(
    val id: String,
    val title: String,
    val icon: DrawableResource,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onLogout: () -> Unit,
    onFavoritesClick: () -> Unit,
    onTracksClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onTrackClick: (Track) -> Unit,
    onAddToQueue: (List<Track>) -> Unit = {},
    onAddToPlaylist: (List<Track>, () -> Unit) -> Unit = { _, _ -> },
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val resultItems = results.content.orEmpty()
    var selectedIds by remember(query) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedItems = remember(resultItems, selectedIds) {
        resultItems.filter { it.id in selectedIds }
    }
    val allSelectedFavorite = selectedItems.all { item ->
        when (item) {
            is LibrarySearchItem.ArtistItem -> item.artist.isFavorite
            is LibrarySearchItem.AlbumItem -> item.album.isFavorite
            is LibrarySearchItem.TrackItem -> item.track.isFavorite
        }
    }
    PlayerBackHandler(enabled = selectedIds.isNotEmpty(), onBack = { selectedIds = emptySet() })
    val browseListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    LaunchedEffect(query) {
        if (searchActive) {
            searchListState.scrollToItem(0)
        }
    }
    val items = listOf(
        LibraryItem("favorites", stringResource(Res.string.favorites), Res.drawable.heart, onFavoritesClick),
        LibraryItem("playlists", stringResource(Res.string.playlists), Res.drawable.playlist_music, onPlaylistsClick),
        LibraryItem("tracks", stringResource(Res.string.tracks), Res.drawable.music_box_multiple, onTracksClick),
        LibraryItem("artists", stringResource(Res.string.artists), Res.drawable.account_multiple, onArtistsClick),
        LibraryItem("albums", stringResource(Res.string.albums), Res.drawable.album, onAlbumsClick),
        LibraryItem("downloads", stringResource(Res.string.downloads), Res.drawable.download, onDownloadsClick),
    )
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var actionError by remember { mutableStateOf<info.jukov.player.core.domain.AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { actionError = it } }
    val actionErrorMessage = actionError?.localizedMessage()
    LaunchedEffect(actionErrorMessage) {
        actionErrorMessage?.let { snackbarHostState.showSnackbar(it) }
        actionError = null
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selectedIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.selected_items, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleFavorites(selectedItems); selectedIds = emptySet() }) {
                            Icon(
                                painterResource(
                                    if (allSelectedFavorite) Res.drawable.heart_outline else Res.drawable.heart,
                                ),
                                stringResource(
                                    if (allSelectedFavorite) Res.string.remove_from_favorites
                                    else Res.string.add_to_favorites,
                                ),
                            )
                        }
                        IconButton(onClick = { viewModel.download(selectedItems) { selectedIds = emptySet() } }) {
                            Icon(painterResource(Res.drawable.download), stringResource(Res.string.downloads))
                        }
                        IconButton(onClick = {
                            viewModel.resolveTracks(selectedItems) { onAddToQueue(it); selectedIds = emptySet() }
                        }) {
                            Icon(painterResource(Res.drawable.playlist_play), stringResource(Res.string.add_selected_to_queue))
                        }
                        IconButton(onClick = {
                            viewModel.resolveTracks(selectedItems) { tracks ->
                                onAddToPlaylist(tracks) { selectedIds = emptySet() }
                            }
                        }) {
                            Icon(painterResource(Res.drawable.playlist_plus), stringResource(Res.string.add_to_playlist))
                        }
                    },
                )
            } else {
                AppFlexibleTopAppBar(
                    title = stringResource(Res.string.app_name),
                    scrollBehavior = scrollBehavior,
                    actions = {
                    SearchAction(viewModel::openSearch)
                    IconButton(onClick = { menuExpanded = true }) {
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
                            text = { Text(stringResource(Res.string.sign_out)) },
                            onClick = {
                                menuExpanded = false
                                confirmLogout = true
                            },
                        )
                    }
                    },
                    searchQuery = query.takeIf { searchActive },
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSearchClose = viewModel::closeSearch,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!searchActive || query.trim().length < 2) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Padding.medium).withPlayerBottomInset(),
                verticalArrangement = Arrangement.spacedBy(Padding.medium),
                horizontalArrangement = Arrangement.spacedBy(Padding.medium),
            ) {
                items(items, key = LibraryItem::title) { item ->
                    val index = items.indexOf(item)
                    val container = when (index % 3) {
                        0 -> MaterialTheme.colorScheme.primaryContainer
                        1 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                    Card(
                        onClick = item.onClick,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.15f)
                            .testTag("library.${item.id}"),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = container),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(Padding.medium),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .55f),
                            ) {
                                Icon(
                                    painterResource(item.icon),
                                    contentDescription = null,
                                    modifier = Modifier.padding(12.dp).size(32.dp),
                                )
                            }
                            Text(item.title, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        } else {
            LibrarySearchResults(
                state = results,
                hasMore = hasMore,
                listState = searchListState,
                modifier = Modifier.fillMaxSize().padding(padding),
                onLoadMore = viewModel::loadMoreSearch,
                onRetry = viewModel::retrySearch,
                onArtistClick = onArtistClick,
                onAlbumClick = onAlbumClick,
                onTrackClick = onTrackClick,
                selectedIds = selectedIds,
                onSelectionChange = { id, selected ->
                    selectedIds = if (selected) selectedIds + id else selectedIds - id
                },
            )
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(Res.string.sign_out_title)) },
            text = { Text(stringResource(Res.string.sign_out_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                ) {
                    Text(stringResource(Res.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LibrarySearchResults(
    state: LoadableState<List<LibrarySearchItem>>,
    hasMore: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onTrackClick: (Track) -> Unit,
    selectedIds: Set<String>,
    onSelectionChange: (String, Boolean) -> Unit,
) {
    val items = state.content.orEmpty()
    when {
        state is LoadableState.Loading && items.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(96.dp)) }
        state is LoadableState.Failure && items.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
        items.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) { Text(stringResource(Res.string.nothing_found)) }
        else -> LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(vertical = Padding.small).withPlayerBottomInset(),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                if (hasMore && index >= items.lastIndex - 8) {
                    LaunchedEffect(items.size) { onLoadMore() }
                }
                when (item) {
                    is LibrarySearchItem.ArtistItem -> SearchRow(
                        title = item.artist.name,
                        subtitle = stringResource(Res.string.artists),
                        imageUrl = null,
                        fallback = Res.drawable.account_multiple,
                        onClick = { onArtistClick(item.artist) },
                        selected = item.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onSelectedChange = { onSelectionChange(item.id, it) },
                    )
                    is LibrarySearchItem.AlbumItem -> SearchRow(
                        title = item.album.name,
                        subtitle = listOfNotNull(item.album.artist.takeIf(String::isNotBlank), item.album.year?.toString()).joinToString(" · "),
                        imageUrl = item.album.coverArtUrl,
                        fallback = Res.drawable.album,
                        onClick = { onAlbumClick(item.album) },
                        selected = item.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onSelectedChange = { onSelectionChange(item.id, it) },
                    )
                    is LibrarySearchItem.TrackItem -> SearchRow(
                        title = item.track.title,
                        subtitle = listOfNotNull(item.track.artist.takeIf(String::isNotBlank), item.track.album).joinToString(" · "),
                        imageUrl = item.track.coverArtUrl,
                        fallback = Res.drawable.music_box_multiple,
                        onClick = { onTrackClick(item.track) },
                        selected = item.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onSelectedChange = { onSelectionChange(item.id, it) },
                    )
                }
            }
            if (state is LoadableState.Loading) {
                item { Box(Modifier.fillMaxWidth().padding(Padding.medium), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            }
        }
    }
}

@Composable
private fun SearchRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    fallback: DrawableResource,
    onClick: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = { if (selectionMode) onSelectedChange(!selected) else onClick() },
            onLongClick = { onSelectedChange(!selected) },
        ),
        headlineContent = { Text(title, maxLines = 1) },
        supportingContent = { if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1) },
        leadingContent = {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                Icon(painterResource(fallback), null, modifier = Modifier.size(40.dp))
            }
        },
        trailingContent = if (selectionMode) {
            { Checkbox(checked = selected, onCheckedChange = onSelectedChange) }
        } else null,
    )
}
