package info.jukov.player.feature.library.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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

private data class LibraryItem(val title: String, val icon: DrawableResource, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
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
        LibraryItem(stringResource(Res.string.favorites), Res.drawable.heart, onFavoritesClick),
        LibraryItem(stringResource(Res.string.playlists), Res.drawable.playlist_music, onPlaylistsClick),
        LibraryItem(stringResource(Res.string.tracks), Res.drawable.music_box_multiple, onTracksClick),
        LibraryItem(stringResource(Res.string.artists), Res.drawable.account_multiple, onArtistsClick),
        LibraryItem(stringResource(Res.string.albums), Res.drawable.album, onAlbumsClick),
        LibraryItem(stringResource(Res.string.downloads), Res.drawable.download, onDownloadsClick),
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppFlexibleTopAppBar(
                title = stringResource(Res.string.library),
                scrollBehavior = scrollBehavior,
                actions = { SearchAction(viewModel::openSearch) },
                searchQuery = query.takeIf { searchActive },
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSearchClose = viewModel::closeSearch,
            )
        },
    ) { padding ->
        if (!searchActive || query.trim().length < 2) {
            LazyColumn(
                state = browseListState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = Padding.small).withPlayerBottomInset(),
                verticalArrangement = Arrangement.spacedBy(Padding.small),
            ) {
                items(items, key = LibraryItem::title) { item ->
                    ListItem(
                        modifier = Modifier.clickable(onClick = item.onClick),
                        headlineContent = { Text(item.title, style = MaterialTheme.typography.titleLarge) },
                        leadingContent = { Icon(painterResource(item.icon), null) },
                    )
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
        state is LoadableState.Loading && items.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
                item { Box(Modifier.fillMaxWidth().padding(Padding.medium), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
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
