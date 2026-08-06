package info.jukov.player.feature.track.presentation.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.AppCollapsingTopAppBar
import info.jukov.player.core.presentation.ui.AppCollapsingTopAppBarState
import info.jukov.player.core.presentation.ui.rememberAppCollapsingTopAppBarState
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.PlayPauseButton
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.track.presentation.TracksViewModel
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.core.presentation.LoadingOrigin
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    filter: TracksFilter,
    albumName: String? = null,
    artistName: String? = null,
    coverArtUrl: String? = null,
    albumIsFavorite: Boolean = false,
    viewModel: TracksViewModel,
    onBack: () -> Unit,
    onPlayClick: (List<Track>, Int) -> Unit = { _, _ -> },
    onActiveTrackClick: () -> Unit = {},
    activeTrackId: String? = null,
    isPlaying: Boolean = false,
) {
    LaunchedEffect(filter, albumIsFavorite) { viewModel.load(filter, albumIsFavorite) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadingOrigin by viewModel.loadingOrigin.collectAsStateWithLifecycle()
    val currentAlbumIsFavorite by viewModel.albumIsFavorite.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarError by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarError = it } }
    val snackbarMessage = snackbarError?.localizedMessage()
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
        snackbarError = null
    }
    val tracks = state.content.orEmpty()
    val albumHeader = if (filter is TracksFilter.ByAlbum && albumName != null) {
        AlbumHeader(albumName, artistName.orEmpty(), coverArtUrl, filter.albumId)
    } else null
    val pullToRefreshState = rememberPullToRefreshState()
    val canScrollAppBar = remember(pullToRefreshState) {
        { pullToRefreshState.distanceFraction == 0f }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
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
                if (albumHeader == null) {
                    AppFlexibleTopAppBar(
                        title = stringResource(Res.string.tracks),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { BackButton(onBack) },
                    )
                } else {
                    AlbumTracksTopAppBar(
                        header = albumHeader, tracks = tracks, appBarState = albumAppBarState,
                        onBack = onBack, onPlayClick = { onPlayClick(tracks, 0) },
                        isFavorite = currentAlbumIsFavorite,
                        favoriteEnabled = albumHeader.albumId !in pending,
                        onFavoriteClick = { viewModel.toggleAlbumFavorite(albumHeader.albumId) },
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
            enabled = isPullToRefreshEnabled,
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
                state is LoadableState.Loading && tracks.isEmpty() && !isRefreshing -> CenteredLoading()

                state is LoadableState.Failure && tracks.isEmpty() -> CenteredError(
                    error = (state as LoadableState.Failure).error,
                    onRetry = viewModel::retry,
                )

                tracks.isEmpty() -> Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(Res.string.tracks_not_found))
                }

                else -> TracksList(
                    tracks = tracks,
                    showArtwork = filter !is TracksFilter.ByAlbum,
                    showTrackNumber = filter is TracksFilter.ByAlbum,
                    error = (state as? LoadableState.Failure)?.error,
                    onPlayClick = onPlayClick,
                    onActiveTrackClick = onActiveTrackClick,
                    activeTrackId = activeTrackId,
                    isPlaying = isPlaying,
                    pendingIds = pending,
                    onFavoriteClick = viewModel::toggleFavorite,
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
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteClick: () -> Unit,
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
                    isFavorite = isFavorite,
                    favoriteEnabled = favoriteEnabled,
                    onFavoriteClick = onFavoriteClick,
                )
            },
            collapsedContent = {
                CollapsedAlbumTracksHeader(
                    header = header,
                    onPlayClick = onPlayClick,
                    playEnabled = tracks.isNotEmpty(),
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
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = Padding.small, bottom = Padding.medium),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (header.artist.isNotBlank()) {
                Text(
                    text = header.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(Padding.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    isPlaying = false,
                    onClick = onPlayClick,
                    enabled = playEnabled,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(Padding.small))
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        painter = painterResource(Res.drawable.download_circle),
                        contentDescription = stringResource(Res.string.downloads),
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
                    text = header.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PlayPauseButton(
            isPlaying = false,
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
    pendingIds: Set<String> = emptySet(),
    onFavoriteClick: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Padding.small)
            .withPlayerBottomInset(),
        verticalArrangement = Arrangement.spacedBy(Padding.small),
    ) {
        error?.let { message ->
            item { Text(message.localizedMessage(), color = MaterialTheme.colorScheme.error) }
        }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                showArtwork = showArtwork,
                showTrackNumber = showTrackNumber,
                onPlayClick = {
                    if (track.id == activeTrackId) onActiveTrackClick()
                    else onPlayClick(tracks, index)
                },
                isPlaying = track.id == activeTrackId && isPlaying,
                favoriteEnabled = track.id !in pendingIds,
                onFavoriteClick = { onFavoriteClick(track) },
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    showArtwork: Boolean = true,
    showTrackNumber: Boolean = false,
    onPlayClick: () -> Unit,
    isPlaying: Boolean,
    favoriteEnabled: Boolean = true,
    onFavoriteClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Padding.small, vertical = Padding.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTrackNumber) {
            Text(
                text = track.trackNumber?.toString().orEmpty(),
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (showArtwork) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                track.coverArtUrl?.let { url ->
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
            }
            Spacer(Modifier.width(Padding.medium))
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FavoriteToggleButton(
            isFavorite = track.isFavorite,
            onClick = onFavoriteClick,
            enabled = favoriteEnabled,
        )
        IconButton(onClick = onPlayClick) {
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
    val albumId: String,
)
