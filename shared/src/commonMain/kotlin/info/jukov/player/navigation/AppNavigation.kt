package info.jukov.player.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.navigation3.ui.NavDisplay
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.artist.presentation.ui.ArtistsScreen
import info.jukov.player.feature.album.presentation.ui.AlbumsScreen
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.presentation.AuthViewModel
import info.jukov.player.feature.auth.presentation.ui.LoginScreen
import info.jukov.player.di.AppGraph
import info.jukov.player.feature.library.presentation.ui.LibraryScreen
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.track.presentation.ui.TracksScreen
import info.jukov.player.feature.playback.presentation.ui.PlayerHost
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.favorite.presentation.ui.FavoritesScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import info.jukov.player.feature.download.presentation.ui.DownloadsScreen
import info.jukov.player.feature.download.presentation.ui.OfflineAlbumTracksScreen
import info.jukov.player.feature.playlist.presentation.ui.PlaylistPickerHost
import info.jukov.player.feature.playlist.presentation.ui.PlaylistScreen
import info.jukov.player.feature.playlist.presentation.ui.PlaylistsScreen

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    playerViewModel: PlayerViewModel,
    graph: AppGraph,
    openDownloads: Boolean = false,
    onOpenDownloadsConsumed: () -> Unit = {},
    openPlayerRequest: Long = 0L,
    onOpenPlayerConsumed: () -> Unit = {},
) {
    val authUiState by authViewModel.state.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.state.collectAsStateWithLifecycle()
    val authState = authUiState.auth.content ?: AuthState.LoggedOut
    val destination: NavKey = when (authState) {
        AuthState.LoggedOut -> Routes.Login
        is AuthState.LoggedIn -> Routes.Library
    }
    val backStack = rememberNavBackStack(NAV_SAVED_STATE_CONFIGURATION, destination)
    val playlistPickerViewModel = viewModel { graph.playlistPickerViewModel }

    LaunchedEffect(destination) {
        if (backStack.lastOrNull() != destination) {
            backStack.clear()
            backStack.add(destination)
        }
    }

    LaunchedEffect(authState) {
        if (authState == AuthState.LoggedOut) playerViewModel.stopAndClear()
    }

    LaunchedEffect(openDownloads, authState) {
        if (openDownloads && authState is AuthState.LoggedIn) {
            if (backStack.lastOrNull() != Routes.Downloads) backStack.add(Routes.Downloads)
            onOpenDownloadsConsumed()
        }
    }

    LaunchedEffect(openPlayerRequest, authState) {
        if (openPlayerRequest != 0L && authState is AuthState.LoggedIn) {
            backStack.clear()
            backStack.add(Routes.Library)
        }
    }

    val playerNotificationResolution = resolvePlayerNotification(
        requested = openPlayerRequest != 0L && authState is AuthState.LoggedIn,
        playbackState = playbackState,
    )
    LaunchedEffect(playerNotificationResolution) {
        if (playerNotificationResolution == PlayerNotificationResolution.ShowLibrary) {
            onOpenPlayerConsumed()
        }
    }

    Surface(Modifier.fillMaxSize()) {
        val navigationContent: @Composable () -> Unit = {
            NavDisplay(
                backStack = backStack,
                transitionSpec = { navigationContentTransform() },
                popTransitionSpec = { navigationContentTransform() },
                predictivePopTransitionSpec = { navigationContentTransform() },
                entryProvider = entryProvider {
                entry<Routes.Login> {
                    LoginScreen(authUiState, authViewModel)
                }
                entry<Routes.Library> {
                    val libraryViewModel = viewModel { graph.libraryViewModel }
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onLogout = authViewModel::logout,
                        onFavoritesClick = { backStack.add(Routes.Favorites) },
                        onTracksClick = { backStack.add(Routes.Tracks()) },
                        onArtistsClick = { backStack.add(Routes.Artists) },
                        onAlbumsClick = { backStack.add(Routes.Albums()) },
                        onDownloadsClick = { backStack.add(Routes.Downloads) },
                        onPlaylistsClick = { backStack.add(Routes.Playlists) },
                        onArtistClick = { artist ->
                            backStack.add(Routes.Albums(artist.id, artist.name))
                        },
                        onAlbumClick = { album -> backStack.add(album.tracksRoute()) },
                        onTrackClick = { track -> playerViewModel.play(listOf(track), 0) },
                        onAddToQueue = playerViewModel::addToQueue,
                        onAddToPlaylist = playlistPickerViewModel::open,
                    )
                }
                entry<Routes.Downloads> {
                    val downloadsViewModel = viewModel { graph.downloadsViewModel }
                    DownloadsScreen(
                        viewModel = downloadsViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onAlbumClick = { album ->
                            backStack.add(Routes.OfflineAlbum(album.album.id, album.album.name))
                        },
                        onPlayClick = { tracks, index -> playerViewModel.play(tracks, index) },
                        onActiveTrackClick = playerViewModel::playPause,
                        activeTrackId = playbackState.content?.currentTrack?.id,
                        isPlaying = playbackState.content?.isPlaying == true,
                        loadingTrackId = playbackState.content?.loadingTrackId,
                        onAddToQueue = playerViewModel::addToQueue,
                    )
                }
                entry<Routes.OfflineAlbum> { route ->
                    val downloadsViewModel = viewModel { graph.downloadsViewModel }
                    OfflineAlbumTracksScreen(
                        albumId = route.albumId,
                        albumName = route.albumName,
                        viewModel = downloadsViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onPlayClick = { tracks, index -> playerViewModel.play(tracks, index) },
                        onActiveTrackClick = playerViewModel::playPause,
                        activeTrackId = playbackState.content?.currentTrack?.id,
                        isPlaying = playbackState.content?.isPlaying == true,
                        loadingTrackId = playbackState.content?.loadingTrackId,
                        onAddToQueue = playerViewModel::addToQueue,
                    )
                }
                entry<Routes.Playlists> {
                    val playlistsViewModel = viewModel { graph.playlistsViewModel }
                    PlaylistsScreen(
                        viewModel = playlistsViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onPlaylistClick = { backStack.add(Routes.Playlist(it.id, it.name)) },
                    )
                }
                entry<Routes.Playlist> { route ->
                    val playlistViewModel = viewModel { graph.playlistViewModel }
                    PlaylistScreen(
                        id = route.id, title = route.name, viewModel = playlistViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onDeleted = {
                            graph.playlistsViewModel.load(forceRefresh = true)
                            backStack.removeLastOrNull()
                        },
                        onPlay = playerViewModel::play,
                        onActiveTrackClick = playerViewModel::playPause,
                        activeTrackId = playbackState.content?.currentTrack?.id,
                        isPlaying = playbackState.content?.isPlaying == true,
                        loadingTrackId = playbackState.content?.loadingTrackId,
                        onAddToQueue = playerViewModel::addToQueue,
                    )
                }
                entry<Routes.Favorites> {
                    val favoritesViewModel = viewModel { graph.favoritesViewModel }
                    FavoritesScreen(
                        viewModel = favoritesViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onAlbumClick = { album -> backStack.add(album.tracksRoute()) },
                        onArtistClick = { artist ->
                            backStack.add(Routes.Albums(artist.id, artist.name))
                        },
                        onPlayClick = { tracks, index -> playerViewModel.play(tracks, index) },
                        onActiveTrackClick = playerViewModel::playPause,
                        activeTrackId = playbackState.content?.currentTrack?.id,
                        isPlaying = playbackState.content?.isPlaying == true,
                        loadingTrackId = playbackState.content?.loadingTrackId,
                        onAddToQueue = playerViewModel::addToQueue,
                        onAddToPlaylist = playlistPickerViewModel::open,
                    )
                }
                entry<Routes.Artists> {
                    val session = (authState as? AuthState.LoggedIn)?.session
                    if (session != null) {
                        val artistsViewModel = viewModel {
                            graph.artistsViewModel
                        }
                        ArtistsScreen(
                            viewModel = artistsViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            onArtistClick = { artist ->
                                backStack.add(Routes.Albums(artist.id, artist.name))
                            },
                            onAddToQueue = playerViewModel::addToQueue,
                            onAddToPlaylist = playlistPickerViewModel::open,
                        )
                    }
                }
                entry<Routes.Albums> { route ->
                    val albumsViewModel = viewModel {
                        graph.albumsViewModel
                    }
                    AlbumsScreen(
                        artistId = route.artistId,
                        artistName = route.artistName,
                        viewModel = albumsViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onAlbumClick = { album -> backStack.add(album.tracksRoute()) },
                        onAllTracksClick = {
                            route.artistId?.let { artistId ->
                                backStack.add(
                                    Routes.Tracks(
                                        artistId = artistId,
                                        artistName = route.artistName,
                                    ),
                                )
                            }
                        },
                        onAddToQueue = playerViewModel::addToQueue,
                        onAddToPlaylist = playlistPickerViewModel::open,
                    )
                }
                entry<Routes.Tracks> { route ->
                    val filter = when {
                        route.albumId != null -> TracksFilter.ByAlbum(route.albumId)
                        route.artistId != null -> TracksFilter.ByArtist(route.artistId)
                        else -> TracksFilter.All
                    }
                    val tracksViewModel = viewModel {
                        graph.tracksViewModel
                    }
                    TracksScreen(
                        filter = filter,
                        albumName = route.albumName,
                        artistName = route.artistName,
                        albumArtistId = route.albumArtistId,
                        albumYear = route.albumYear,
                        coverArtUrl = route.coverArtUrl,
                        coverArtId = route.coverArtId,
                        albumIsFavorite = route.albumIsFavorite,
                        viewModel = tracksViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onPlayClick = playerViewModel::play,
                        onActiveTrackClick = playerViewModel::playPause,
                        activeTrackId = playbackState.content?.currentTrack?.id,
                        isPlaying = playbackState.content?.isPlaying == true,
                        isPlaybackLoading = playbackState.content?.isLoading == true,
                        loadingTrackId = playbackState.content?.loadingTrackId,
                        activeOrigin = playbackState.content?.origin ?: PlaybackOrigin.TrackList,
                        onAddToQueue = playerViewModel::addToQueue,
                        onAddToPlaylist = playlistPickerViewModel::open,
                        onArtistClick = { artistId, artistName ->
                            backStack.add(Routes.Albums(artistId, artistName))
                        },
                    )
                }
                },
            )
        }
        if (authState is AuthState.LoggedIn) {
            PlayerHost(
                viewModel = playerViewModel,
                expandRequest = if (
                    playerNotificationResolution == PlayerNotificationResolution.OpenPlayer
                ) {
                    openPlayerRequest
                } else {
                    0L
                },
                onExpandRequestConsumed = onOpenPlayerConsumed,
                onAddToPlaylist = { tracks -> playlistPickerViewModel.open(tracks) },
                onArtistClick = { track ->
                    track.artistId?.let { artistId ->
                        backStack.add(Routes.Albums(artistId, track.artist))
                    }
                },
                onAlbumClick = { track ->
                    track.albumId?.let { albumId ->
                        backStack.add(
                            Routes.Tracks(
                                albumId = albumId,
                                albumName = track.album,
                                artistName = track.artist,
                                albumArtistId = track.artistId,
                                albumYear = track.year,
                                coverArtUrl = track.coverArtUrl,
                                coverArtId = track.coverArtId,
                            ),
                        )
                    }
                },
                content = navigationContent,
            )
            PlaylistPickerHost(playlistPickerViewModel)
        } else {
            navigationContent()
        }
    }
}

internal enum class PlayerNotificationResolution {
    None,
    WaitForPlayback,
    OpenPlayer,
    ShowLibrary,
}

internal fun resolvePlayerNotification(
    requested: Boolean,
    playbackState: LoadableState<PlayerUiState>,
): PlayerNotificationResolution {
    if (!requested) {
        return PlayerNotificationResolution.None
    }
    if (playbackState.content?.currentTrack != null) {
        return PlayerNotificationResolution.OpenPlayer
    }
    return if (playbackState is LoadableState.Loading) {
        PlayerNotificationResolution.WaitForPlayback
    } else {
        PlayerNotificationResolution.ShowLibrary
    }
}

private val NAV_SAVED_STATE_CONFIGURATION = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Routes.Login.serializer())
            subclass(Routes.Library.serializer())
            subclass(Routes.Favorites.serializer())
            subclass(Routes.Downloads.serializer())
            subclass(Routes.OfflineAlbum.serializer())
            subclass(Routes.Artists.serializer())
            subclass(Routes.Albums.serializer())
            subclass(Routes.Tracks.serializer())
            subclass(Routes.Playlists.serializer())
            subclass(Routes.Playlist.serializer())
        }
    }
}

private fun info.jukov.player.feature.album.domain.Album.tracksRoute() = Routes.Tracks(
    albumId = id,
    albumName = name,
    artistName = artist,
    albumArtistId = artistId,
    albumYear = year,
    coverArtUrl = coverArtUrl,
    coverArtId = coverArtId,
    albumIsFavorite = isFavorite,
)

private const val NAVIGATION_ANIMATION_DURATION_MILLIS = 250

private fun navigationContentTransform() = ContentTransform(
    targetContentEnter = fadeIn(tween(NAVIGATION_ANIMATION_DURATION_MILLIS)),
    initialContentExit = fadeOut(tween(NAVIGATION_ANIMATION_DURATION_MILLIS)),
)
