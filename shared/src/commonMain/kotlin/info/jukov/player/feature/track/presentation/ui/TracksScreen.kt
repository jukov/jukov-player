package info.jukov.player.feature.track.presentation.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.SearchAction
import info.jukov.player.core.presentation.ui.AppCollapsingTopAppBar
import info.jukov.player.core.presentation.ui.AppCollapsingTopAppBarState
import info.jukov.player.core.domain.TrackSortCriterion
import info.jukov.player.core.presentation.ui.SortAction
import info.jukov.player.core.presentation.ui.SortMenuItem
import info.jukov.player.core.presentation.ui.rememberAppCollapsingTopAppBarState
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.PlayPauseButton
import info.jukov.player.core.presentation.ui.MetadataPill
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.track.presentation.TracksViewModel
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.playerGradientColors
import info.jukov.player.core.presentation.ui.rememberArtworkPalette
import info.jukov.player.core.presentation.LoadingOrigin
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playback.presentation.ui.PlayerBackHandler
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.album.domain.Album
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    filter: TracksFilter,
    albumName: String? = null,
    artistName: String? = null,
    albumArtistId: String? = null,
    albumYear: Int? = null,
    coverArtUrl: String? = null,
    coverArtId: String? = null,
    albumIsFavorite: Boolean = false,
    viewModel: TracksViewModel,
    onBack: () -> Unit,
    onPlayClick: (List<Track>, Int, PlaybackOrigin) -> Unit = { _, _, _ -> },
    onActiveTrackClick: () -> Unit = {},
    activeTrackId: String? = null,
    isPlaying: Boolean = false,
    isPlaybackLoading: Boolean = false,
    loadingTrackId: String? = null,
    activeOrigin: PlaybackOrigin = PlaybackOrigin.TrackList,
    onAddToQueue: (List<Track>) -> Unit = {},
    onAddToPlaylist: (List<Track>, () -> Unit) -> Unit = { _, _ -> },
    onArtistClick: (String, String) -> Unit = { _, _ -> },
) {
    LaunchedEffect(filter, albumIsFavorite) { viewModel.load(filter, albumIsFavorite) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchHasMore by viewModel.searchHasMore.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val loadingOrigin by viewModel.loadingOrigin.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val currentAlbumIsFavorite by viewModel.albumIsFavorite.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val downloadStatuses by viewModel.downloadStatuses.collectAsStateWithLifecycle()
    val albumDownloadStatuses by viewModel.albumDownloadStatuses.collectAsStateWithLifecycle()
    val artworkUris by viewModel.artworkUris.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarError by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarError = it } }
    val snackbarMessage = snackbarError?.localizedMessage()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
        snackbarError = null
    }
    val displayedState = if (searchActive && searchQuery.trim().length >= 2) searchState else state
    val tracks = displayedState.content.orEmpty()
    val browseListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    LaunchedEffect(searchQuery) {
        if (searchActive) {
            searchListState.scrollToItem(0)
        }
    }
    val selectionState = rememberTrackSelectionState(tracks, key = filter)
    PlayerBackHandler(
        enabled = selectionState.isActive,
        onBack = selectionState::clear,
    )
    val collectionOrigin = when (filter) {
        is TracksFilter.ByAlbum -> PlaybackOrigin.Album(filter.albumId)
        is TracksFilter.ByArtist -> PlaybackOrigin.Artist(filter.artistId)
        TracksFilter.All -> null
    }
    val isCollectionActive = collectionOrigin != null && activeOrigin == collectionOrigin
    val onCollectionPlayClick: () -> Unit = {
        if (isCollectionActive) {
            onActiveTrackClick()
        } else if (collectionOrigin != null) {
            onPlayClick(tracks, 0, collectionOrigin)
        }
    }
    val albumHeader = if (filter is TracksFilter.ByAlbum && albumName != null) {
        AlbumHeader(
            albumName, artistName.orEmpty(),
            coverArtId?.let(artworkUris::get) ?: coverArtUrl,
            coverArtId, filter.albumId, albumArtistId, albumYear,
        )
    } else null
    val pullToRefreshState = rememberPullToRefreshState()
    val canScrollAppBar = remember(pullToRefreshState) {
        { pullToRefreshState.distanceFraction == 0f }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        canScroll = canScrollAppBar,
    )
    val albumAppBarState = rememberAppCollapsingTopAppBarState(
        canScroll = canScrollAppBar,
    )
    val isRefreshing = loadingOrigin == LoadingOrigin.PullToRefresh
    val isPullToRefreshEnabled by remember(
        albumHeader,
        albumAppBarState,
        scrollBehavior,
        pullToRefreshState,
    ) {
        derivedStateOf {
            val isAppBarExpanded = if (albumHeader == null) {
                scrollBehavior.state.collapsedFraction == 0f
            } else {
                albumAppBarState.collapsedFraction == 0f
            }
            isAppBarExpanded || pullToRefreshState.distanceFraction > 0f
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(
            albumHeader?.let { albumAppBarState.nestedScrollConnection }
                ?: scrollBehavior.nestedScrollConnection,
        ),
        topBar = {
            Column {
                if (selectionState.isActive) {
                    TrackSelectionTopAppBar(
                        selectedCount = selectionState.selectedCount,
                        allSelectedFavorite = selectionState.areAllSelectedFavorite(tracks),
                        onClose = selectionState::clear,
                        onFavorite = { selectionState.finish(tracks, viewModel::toggleFavorites) },
                        onDownload = { selectionState.finish(tracks, viewModel::downloadTracks) },
                        onAddToQueue = { selectionState.finish(tracks, onAddToQueue) },
                        onAddToPlaylist = {
                            onAddToPlaylist(selectionState.selectedTracks(tracks), selectionState::clear)
                        },
                    )
                } else if (albumHeader == null) {
                    AppFlexibleTopAppBar(
                        title = artistName ?: stringResource(Res.string.tracks),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack) },
                        actions = {
                            if (filter is TracksFilter.ByArtist && !searchActive) {
                                SortAction(sort, listOf(
                                    SortMenuItem(TrackSortCriterion.Title, stringResource(Res.string.sort_title)),
                                    SortMenuItem(TrackSortCriterion.Artist, stringResource(Res.string.sort_artist)),
                                ), stringResource(Res.string.sort_ascending), stringResource(Res.string.sort_descending)) { option ->
                                    browseListState.requestScrollToItem(0)
                                    viewModel.updateSort(option)
                                }
                            }
                            if (filter !is TracksFilter.ByAlbum) {
                                SearchAction(viewModel::openSearch)
                            }
                            if (filter is TracksFilter.ByArtist) {
                                PlayPauseButton(
                                    isPlaying = isCollectionActive && isPlaying,
                                    isLoading = isCollectionActive && isPlaybackLoading,
                                    onClick = onCollectionPlayClick,
                                    enabled = tracks.isNotEmpty(),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                        },
                        searchQuery = searchQuery.takeIf { searchActive },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSearchClose = viewModel::closeSearch,
                    )
                } else {
                    AlbumTracksTopAppBar(
                        header = albumHeader, tracks = tracks, appBarState = albumAppBarState,
                        onBack = onBack,
                        onPlayClick = onCollectionPlayClick,
                        isPlaying = isCollectionActive && isPlaying,
                        isLoading = isCollectionActive && isPlaybackLoading,
                        isFavorite = currentAlbumIsFavorite,
                        favoriteEnabled = albumHeader.albumId !in pending,
                        onFavoriteClick = { viewModel.toggleAlbumFavorite(albumHeader.albumId) },
                        onAddToPlaylist = { onAddToPlaylist(tracks, {}) },
                        onAddToQueue = { onAddToQueue(tracks) },
                        downloadStatus = albumDownloadStatuses[albumHeader.albumId],
                        onDownloadClick = {
                            val status = albumDownloadStatuses[albumHeader.albumId]
                            if (status?.state == DownloadState.Queued || status?.state == DownloadState.Downloading) {
                                viewModel.cancelAlbumDownload(albumHeader.albumId)
                            } else if (status?.state != DownloadState.Completed) {
                                viewModel.downloadAlbum(
                                    Album(
                                        id = albumHeader.albumId,
                                        name = albumHeader.name,
                                        artist = albumHeader.artist,
                                        artistId = null,
                                        year = albumHeader.year,
                                        coverArtId = albumHeader.coverArtId,
                                        coverArtUrl = albumHeader.coverArtUrl,
                                        isFavorite = currentAlbumIsFavorite,
                                    ),
                                )
                            }
                        },
                        onArtistClick = onArtistClick,
                    )
                }
                if (loadingOrigin == LoadingOrigin.Automatic && tracks.isNotEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
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
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            when {
                displayedState is LoadableState.Loading && tracks.isEmpty() && !isRefreshing -> CenteredLoading()

                displayedState is LoadableState.Failure && tracks.isEmpty() -> CenteredError(
                    error = (displayedState as LoadableState.Failure).error,
                    onRetry = if (searchActive) viewModel::retrySearch else viewModel::retry,
                )

                tracks.isEmpty() -> Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(if (searchActive && searchQuery.length >= 2) Res.string.nothing_found else Res.string.tracks_not_found))
                }

                else -> TracksList(
                    tracks = tracks,
                    showArtwork = filter !is TracksFilter.ByAlbum,
                    showTrackNumber = filter is TracksFilter.ByAlbum,
                    error = (displayedState as? LoadableState.Failure)?.error,
                    onPlayClick = { queue, index ->
                        if (searchActive) {
                            onPlayClick(listOf(queue[index]), 0, PlaybackOrigin.TrackList)
                        } else {
                            onPlayClick(
                                queue,
                                index,
                                collectionOrigin ?: PlaybackOrigin.TrackList,
                            )
                        }
                    },
                    onActiveTrackClick = onActiveTrackClick,
                    activeTrackId = activeTrackId,
                    isPlaying = isPlaying,
                    loadingTrackId = loadingTrackId,
                    pendingIds = pending,
                    onFavoriteClick = viewModel::toggleFavorite,
                    downloadStatuses = downloadStatuses,
                    onDownloadClick = viewModel::downloadTrack,
                    onCancelDownload = viewModel::cancelTrackDownload,
                    onRetryDownload = viewModel::retryTrackDownload,
                    selectionMode = selectionState.isActive,
                    selectedIds = selectionState.selectedIds,
                    onSelectionChange = selectionState::setSelected,
                    artworkUris = artworkUris,
                    hasMore = if (searchActive) searchHasMore else hasMore,
                    isLoadingMore = if (searchActive) searchState is LoadableState.Loading && tracks.isNotEmpty() else loadingOrigin == LoadingOrigin.Pagination,
                    onLoadMore = if (searchActive) viewModel::loadMoreSearch else viewModel::loadMore,
                    listState = if (searchActive) searchListState else browseListState,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumTracksTopAppBar(
    header: AlbumHeader,
    tracks: List<Track>,
    appBarState: AppCollapsingTopAppBarState,
    onBack: () -> Unit,
    onPlayClick: () -> Unit,
    isPlaying: Boolean,
    isLoading: Boolean,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    downloadStatus: DownloadStatus?,
    onDownloadClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
) {
    BoxWithConstraints {
        val artworkSize = (maxWidth * 0.75f).coerceAtMost(400.dp)
        AppCollapsingTopAppBar(
            state = appBarState,
            navigationIcon = { BackButton(onBack) },
            expandedContent = {
                ExpandedAlbumTracksHeader(
                    header = header,
                    artworkSize = artworkSize,
                    onPlayClick = onPlayClick,
                    playEnabled = tracks.isNotEmpty(),
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    isFavorite = isFavorite,
                    favoriteEnabled = favoriteEnabled,
                    onFavoriteClick = onFavoriteClick,
                    onAddToPlaylist = onAddToPlaylist,
                    onAddToQueue = onAddToQueue,
                    downloadStatus = downloadStatus,
                    onDownloadClick = onDownloadClick,
                    onArtistClick = onArtistClick,
                )
            },
            collapsedContent = {
                CollapsedAlbumTracksHeader(
                    header = header,
                    onPlayClick = onPlayClick,
                    playEnabled = tracks.isNotEmpty(),
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    onArtistClick = onArtistClick,
                )
            },
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            painter = painterResource(Res.drawable.arrow_back),
            contentDescription = stringResource(Res.string.back),
        )
    }
}

@Composable
private fun ExpandedAlbumTracksHeader(
    header: AlbumHeader,
    artworkSize: androidx.compose.ui.unit.Dp,
    onPlayClick: () -> Unit,
    playEnabled: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    downloadStatus: DownloadStatus?,
    onDownloadClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
) {
    val palette = rememberArtworkPalette(
        key = header.coverArtId ?: header.coverArtUrl ?: header.albumId,
        url = header.coverArtUrl,
    )
    val gradientColors = palette.playerGradientColors(
        surface = MaterialTheme.colorScheme.surface,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to gradientColors[0],
                        .48f to gradientColors[1],
                        1f to gradientColors[2],
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = Padding.small, bottom = Padding.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(artworkSize)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            header.coverArtUrl?.let { url ->
                AsyncImage(
                    model = rememberArtworkRequest(url, header.albumId, SMALL_ARTWORK_SIZE),
                    contentDescription = stringResource(Res.string.album_cover, header.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.size(Padding.small))
        Column(
            modifier = Modifier.width(artworkSize),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = header.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (header.artist.isNotBlank()) {
                Text(
                    text = header.artistAndYear,
                    modifier = Modifier.clickable(
                        enabled = header.artistId != null,
                        onClick = {
                            header.artistId?.let { onArtistClick(it, header.artist) }
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(Padding.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onAddToPlaylist,
                    enabled = playEnabled,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.playlist_plus),
                        contentDescription = stringResource(Res.string.add_to_playlist),
                    )
                }
                Spacer(Modifier.width(Padding.small))
                IconButton(
                    onClick = onFavoriteClick,
                    enabled = favoriteEnabled,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isFavorite) Res.drawable.heart else Res.drawable.heart_outline,
                        ),
                        contentDescription = stringResource(
                            if (isFavorite) {
                                Res.string.remove_from_favorites
                            } else {
                                Res.string.add_to_favorites
                            },
                        ),
                    )
                }
                Spacer(Modifier.width(Padding.small))
                PlayPauseButton(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    onClick = onPlayClick,
                    enabled = playEnabled,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(Padding.small))
                DownloadIconButton(downloadStatus, onDownloadClick)
                Spacer(Modifier.width(Padding.small))
                IconButton(
                    onClick = onAddToQueue,
                    enabled = playEnabled,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.playlist_play),
                        contentDescription = stringResource(Res.string.add_album_to_queue),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedAlbumTracksHeader(
    header: AlbumHeader,
    onPlayClick: () -> Unit,
    playEnabled: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onArtistClick: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            header.coverArtUrl?.let { url ->
                AsyncImage(
                    model = rememberArtworkRequest(url, header.albumId, SMALL_ARTWORK_SIZE),
                    contentDescription = stringResource(Res.string.album_cover, header.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(Padding.medium))
        Column(
            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = header.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (header.artist.isNotBlank()) {
                Text(
                    text = header.artistAndYear,
                    modifier = Modifier.clickable(
                        enabled = header.artistId != null,
                        onClick = {
                            header.artistId?.let { onArtistClick(it, header.artist) }
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PlayPauseButton(
            isPlaying = isPlaying,
            isLoading = isLoading,
            onClick = onPlayClick,
            enabled = playEnabled,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(Padding.small))
    }
}

@Composable
fun TracksList(
    tracks: List<Track>,
    showArtwork: Boolean = true,
    showTrackNumber: Boolean = false,
    error: AppError?,
    onPlayClick: (List<Track>, Int) -> Unit,
    onActiveTrackClick: () -> Unit,
    activeTrackId: String?,
    isPlaying: Boolean,
    loadingTrackId: String? = null,
    pendingIds: Set<String> = emptySet(),
    onFavoriteClick: (Track) -> Unit = {},
    downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    onDownloadClick: (Track) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onRetryDownload: (String) -> Unit = {},
    artworkUris: Map<String, String> = emptyMap(),
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    selectionKey: (Int, Track) -> String = { _, track -> track.id },
    itemKey: (Int, Track) -> Any = selectionKey,
    trailingAction: TrackTrailingAction = TrackTrailingAction.Favorite,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    reorderEnabled: Boolean = false,
    onMove: (Int, Int) -> Unit = { _, _ -> },
) {
    var draggedItemKey by remember { mutableStateOf<Any?>(null) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Padding.medium)
            .withPlayerBottomInset(),
        verticalArrangement = Arrangement.spacedBy(Padding.small),
    ) {
        error?.let { message ->
            item { Text(message.localizedMessage(), color = MaterialTheme.colorScheme.error) }
        }
        itemsIndexed(tracks, key = itemKey) { index, track ->
            val key = itemKey(index, track)
            var dragOffset by remember(key) { mutableFloatStateOf(0f) }
            var dragIndex by remember(key) { mutableIntStateOf(index) }
            var measuredRowHeightPx by remember(key) { mutableIntStateOf(0) }
            val isDragging = draggedItemKey == key
            LaunchedEffect(index, isDragging) {
                if (!isDragging) {
                    dragIndex = index
                }
            }
            if (hasMore && index >= tracks.lastIndex - LOAD_MORE_THRESHOLD) {
                LaunchedEffect(tracks.size) { onLoadMore() }
            }
            TrackRow(
                track = track,
                showArtwork = showArtwork,
                showTrackNumber = showTrackNumber,
                onPlayClick = {
                    if (track.id == activeTrackId) onActiveTrackClick()
                    else onPlayClick(tracks, index)
                },
                isPlaying = track.id == activeTrackId && isPlaying,
                isLoading = track.id == loadingTrackId,
                favoriteEnabled = track.id !in pendingIds,
                onFavoriteClick = { onFavoriteClick(track) },
                downloadStatus = downloadStatuses[track.id],
                onDownloadClick = { onDownloadClick(track) },
                onCancelDownload = { onCancelDownload(track.id) },
                onRetryDownload = { onRetryDownload(track.id) },
                artworkUrl = track.coverArtId?.let(artworkUris::get) ?: track.coverArtUrl,
                selectionMode = selectionMode,
                selected = selectionKey(index, track) in selectedIds,
                onSelectedChange = { onSelectionChange(selectionKey(index, track), it) },
                trailingAction = trailingAction,
                modifier = if (!reorderEnabled) {
                    Modifier
                } else if (isDragging) {
                    Modifier.animateItem(placementSpec = null)
                        .graphicsLayer { translationY = dragOffset }
                        .onSizeChanged { measuredRowHeightPx = it.height }
                        .zIndex(1f)
                } else {
                    Modifier.animateItem()
                        .onSizeChanged { measuredRowHeightPx = it.height }
                },
                dragHandle = if (reorderEnabled && !selectionMode) {
                    {
                        Icon(
                            painterResource(Res.drawable.drag_handle),
                            stringResource(Res.string.move_track, track.title),
                            Modifier.size(48.dp).padding(12.dp).pointerInput(key) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        draggedItemKey = key
                                        dragIndex = index
                                    },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val itemStridePx = measuredRowHeightPx + Padding.small.toPx()
                                        if (itemStridePx > 0f) {
                                            val moveThreshold = itemStridePx / 2f
                                            while (dragOffset > moveThreshold && dragIndex < tracks.lastIndex) {
                                                onMove(dragIndex, dragIndex + 1)
                                                dragIndex += 1
                                                dragOffset -= itemStridePx
                                            }
                                            while (dragOffset < -moveThreshold && dragIndex > 0) {
                                                onMove(dragIndex, dragIndex - 1)
                                                dragIndex -= 1
                                                dragOffset += itemStridePx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragOffset = 0f
                                        draggedItemKey = null
                                    },
                                    onDragCancel = {
                                        dragOffset = 0f
                                        draggedItemKey = null
                                    },
                                )
                            },
                        )
                    }
                } else null,
            )
        }
        if (isLoadingMore) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(Padding.medium),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 12

enum class TrackTrailingAction { Favorite, RemoveDownload }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    showArtwork: Boolean = true,
    showTrackNumber: Boolean = false,
    onPlayClick: () -> Unit,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    favoriteEnabled: Boolean = true,
    onFavoriteClick: () -> Unit = {},
    downloadStatus: DownloadStatus? = null,
    onDownloadClick: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onRetryDownload: () -> Unit = {},
    artworkUrl: String? = track.coverArtUrl,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    trailingAction: TrackTrailingAction = TrackTrailingAction.Favorite,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("track.${track.id}")
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isPlaying) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onSelectedChange(!selected)
                    } else {
                        onPlayClick()
                    }
                },
                onLongClick = { onSelectedChange(true) },
            )
            .padding(
                start = Padding.medium,
                end = Padding.small,
                top = Padding.small,
                bottom = Padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTrackNumber) {
            if (selectionMode) {
                SelectionBadge(selected, track.title, Modifier.width(28.dp))
            } else {
                Text(
                    text = track.trackNumber?.toString().orEmpty(),
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (showArtwork) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                artworkUrl?.let { url ->
                    AsyncImage(
                        model = rememberArtworkRequest(
                            url = url,
                            albumId = track.albumId,
                            requestedSize = SMALL_ARTWORK_SIZE,
                        ),
                        contentDescription = stringResource(Res.string.track_cover, track.title),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (selectionMode) {
                    SelectionBadge(selected, track.title, Modifier.align(Alignment.BottomEnd))
                }
            }
            Spacer(Modifier.width(Padding.medium))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    isLoading -> TrackLoadingIndicator()
                    isPlaying -> PlayingEqualizer()
                }
                if (isLoading || isPlaying) {
                    Spacer(Modifier.width(Padding.xSmall))
                }
                Text(
                    text = track.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.durationMs > 0) {
                    MetadataPill(formatTrackDuration(track.durationMs))
                }
                val trackDetails = listOfNotNull(
                    track.artist.takeIf(String::isNotBlank),
                    track.album?.takeIf(String::isNotBlank),
                ).distinct().joinToString(" · ")
                if (track.durationMs > 0 && trackDetails.isNotEmpty()) {
                    Spacer(Modifier.width(Padding.small))
                }
                Text(
                    text = trackDetails,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingAction == TrackTrailingAction.Favorite) {
            FavoriteToggleButton(
                isFavorite = track.isFavorite,
                onClick = onFavoriteClick,
                enabled = favoriteEnabled,
            )
        }
        if (trailingAction == TrackTrailingAction.RemoveDownload) {
            IconButton(onClick = onCancelDownload) {
                Icon(painterResource(Res.drawable.delete), stringResource(Res.string.remove_download))
            }
        }
        DownloadBadge(
            status = downloadStatus,
            modifier = Modifier.padding(end = Padding.small),
        )
        dragHandle?.invoke()
    }
}

@Composable
private fun TrackLoadingIndicator() {
    val description = stringResource(Res.string.track_loading)
    LoadingIndicator(
        modifier = Modifier.size(16.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun PlayingEqualizer() {
    val transition = rememberInfiniteTransition(label = "playing equalizer")
    val firstBar by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 480), RepeatMode.Reverse),
        label = "first bar",
    )
    val secondBar by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 620), RepeatMode.Reverse),
        label = "second bar",
    )
    val thirdBar by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 390), RepeatMode.Reverse),
        label = "third bar",
    )
    val color = MaterialTheme.colorScheme.primary
    val description = stringResource(Res.string.track_playing)
    Canvas(
        Modifier.size(16.dp).semantics { contentDescription = description },
    ) {
        val barWidth = size.width * 0.18f
        val gap = size.width * 0.12f
        val totalWidth = barWidth * 3 + gap * 2
        val startX = (size.width - totalWidth) / 2
        listOf(firstBar, secondBar, thirdBar).forEachIndexed { index, heightFraction ->
            val barHeight = size.height * heightFraction
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

internal fun formatTrackDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

@Composable
fun DownloadPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    status: DownloadStatus?,
) {
    val loadingDescription = stringResource(Res.string.pause)
    Box {
        IconButton(onClick = onClick) {
            if (isLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp).semantics {
                        contentDescription = loadingDescription
                    },
                    color = LocalContentColor.current,
                )
            } else {
                Icon(
                    painter = painterResource(
                        if (isPlaying) Res.drawable.pause else Res.drawable.play_arrow,
                    ),
                    contentDescription = stringResource(
                        if (isPlaying) Res.string.pause else Res.string.play,
                    ),
                )
            }
        }
        DownloadBadge(
            status = status,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-6).dp),
        )
    }
}

@Composable
private fun SelectionBadge(selected: Boolean, title: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(22.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(
                painterResource(Res.drawable.check),
                contentDescription = stringResource(Res.string.selected_track, title),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectionTopAppBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    allSelectedFavorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    removesDownloads: Boolean = false,
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.selected_tracks, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.clear_selection))
            }
        },
        actions = {
            onFavorite?.let { action ->
                IconButton(onClick = action) {
                    Icon(
                        painterResource(
                            if (allSelectedFavorite) {
                                Res.drawable.heart_outline
                            } else {
                                Res.drawable.heart
                            },
                        ),
                        stringResource(
                            if (allSelectedFavorite) {
                                Res.string.remove_from_favorites
                            } else {
                                Res.string.favorite_selected_tracks
                            },
                        ),
                    )
                }
            }
            IconButton(onClick = onDownload) {
                Icon(
                    painterResource(
                        if (removesDownloads) {
                            Res.drawable.download_off
                        } else {
                            Res.drawable.download
                        },
                    ),
                    stringResource(
                        if (removesDownloads) {
                            Res.string.remove_selected_downloads
                        } else {
                            Res.string.download_selected_tracks
                        },
                    ),
                )
            }
            IconButton(onClick = onAddToQueue) {
                Icon(painterResource(Res.drawable.playlist_play), stringResource(Res.string.add_selected_to_queue))
            }
            onAddToPlaylist?.let { action ->
                IconButton(onClick = action) {
                    Icon(painterResource(Res.drawable.playlist_plus), stringResource(Res.string.add_to_playlist))
                }
            }
        },
    )
}

@Composable
private fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(Modifier.size(96.dp))
    }
}

@Composable
private fun CenteredError(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error.localizedMessage(), color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}

data class AlbumHeader(
    val name: String,
    val artist: String,
    val coverArtUrl: String?,
    val coverArtId: String?,
    val albumId: String,
    val artistId: String?,
    val year: Int?,
)

private val AlbumHeader.artistAndYear: String
    get() = year?.let { "$artist · $it" } ?: artist

@Composable
private fun DownloadIconButton(status: DownloadStatus?, onClick: () -> Unit) {
    Box {
        IconButton(onClick = onClick) {
            Icon(painterResource(Res.drawable.download), stringResource(Res.string.downloads))
        }
        DownloadBadge(
            status = status,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-6).dp),
        )
    }
}

@Composable
private fun DownloadBadge(status: DownloadStatus?, modifier: Modifier = Modifier) {
    when (status?.state) {
        DownloadState.Queued, DownloadState.Downloading -> DownloadStatusBadgeContainer(modifier) {
            status.progress?.let { progress ->
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                )
            } ?: CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.5.dp,
            )
        }
        DownloadState.Completed -> DownloadStatusBadgeContainer(modifier) {
            Icon(
                painter = painterResource(Res.drawable.arrow_down_bold),
                contentDescription = stringResource(Res.string.downloads),
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        DownloadState.Failed -> DownloadStatusBadgeContainer(modifier) {
            Text(
                "!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        null -> Unit
    }
}

@Composable
private fun DownloadStatusBadgeContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
