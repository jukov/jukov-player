package info.jukov.player.feature.library.presentation.ui

import androidx.compose.foundation.clickable
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
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
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
        },
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
                    )
                    is LibrarySearchItem.AlbumItem -> SearchRow(
                        title = item.album.name,
                        subtitle = listOfNotNull(item.album.artist.takeIf(String::isNotBlank), item.album.year?.toString()).joinToString(" · "),
                        imageUrl = item.album.coverArtUrl,
                        fallback = Res.drawable.album,
                        onClick = { onAlbumClick(item.album) },
                    )
                    is LibrarySearchItem.TrackItem -> SearchRow(
                        title = item.track.title,
                        subtitle = listOfNotNull(item.track.artist.takeIf(String::isNotBlank), item.track.album).joinToString(" · "),
                        imageUrl = item.track.coverArtUrl,
                        fallback = Res.drawable.music_box_multiple,
                        onClick = { onTrackClick(item.track) },
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
private fun SearchRow(title: String, subtitle: String, imageUrl: String?, fallback: DrawableResource, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title, maxLines = 1) },
        supportingContent = { if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1) },
        leadingContent = {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                Icon(painterResource(fallback), null, modifier = Modifier.size(40.dp))
            }
        },
    )
}
