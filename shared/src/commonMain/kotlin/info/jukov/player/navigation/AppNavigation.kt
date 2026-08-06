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
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.favorite.presentation.ui.FavoritesScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    playerViewModel: PlayerViewModel,
    graph: AppGraph,
) {
    val authUiState by authViewModel.state.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.state.collectAsStateWithLifecycle()
    val authState = authUiState.auth.content ?: AuthState.LoggedOut
    val destination: NavKey = when (authState) {
        AuthState.LoggedOut -> Routes.Login
        is AuthState.LoggedIn -> Routes.Library
    }
    val backStack = rememberNavBackStack(NAV_SAVED_STATE_CONFIGURATION, destination)

    LaunchedEffect(destination) {
        if (backStack.lastOrNull() != destination) {
            backStack.clear()
            backStack.add(destination)
        }
    }

    LaunchedEffect(authState) {
        if (authState == AuthState.LoggedOut) playerViewModel.stopAndClear()
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
                    LibraryScreen(
                        onFavoritesClick = { backStack.add(Routes.Favorites) },
                        onTracksClick = { backStack.add(Routes.Tracks()) },
                        onArtistsClick = { backStack.add(Routes.Artists) },
                        onAlbumsClick = { backStack.add(Routes.Albums()) },
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
                        onPlayClick = playerViewModel::play,
                        onActiveTrackClick = playerViewModel::playPause,
                        activeTrackId = playbackState.content?.currentTrack?.id,
                        isPlaying = playbackState.content?.isPlaying == true,
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
                            onLogout = authViewModel::logout,
                            onBack = { backStack.removeLastOrNull() },
                            onArtistClick = { artist ->
                                backStack.add(Routes.Albums(artist.id, artist.name))
                            },
                            onAllAlbumsClick = { backStack.add(Routes.Albums()) },
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
                        coverArtUrl = route.coverArtUrl,
                        albumIsFavorite = route.albumIsFavorite,
                        viewModel = tracksViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onPlayClick = playerViewModel::play,
                        onActiveTrackClick = playerViewModel::playPause,
                        activeTrackId = playbackState.content?.currentTrack?.id,
                        isPlaying = playbackState.content?.isPlaying == true,
                    )
                }
                },
            )
        }
        if (authState is AuthState.LoggedIn) {
            PlayerHost(viewModel = playerViewModel, content = navigationContent)
        } else {
            navigationContent()
        }
    }
}

private val NAV_SAVED_STATE_CONFIGURATION = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Routes.Login.serializer())
            subclass(Routes.Library.serializer())
            subclass(Routes.Favorites.serializer())
            subclass(Routes.Artists.serializer())
            subclass(Routes.Albums.serializer())
            subclass(Routes.Tracks.serializer())
        }
    }
}

private fun info.jukov.player.feature.album.domain.Album.tracksRoute() = Routes.Tracks(
    albumId = id,
    albumName = name,
    artistName = artist,
    coverArtUrl = coverArtUrl,
    albumIsFavorite = isFavorite,
)

private const val NAVIGATION_ANIMATION_DURATION_MILLIS = 250

private fun navigationContentTransform() = ContentTransform(
    targetContentEnter = fadeIn(tween(NAVIGATION_ANIMATION_DURATION_MILLIS)),
    initialContentExit = fadeOut(tween(NAVIGATION_ANIMATION_DURATION_MILLIS)),
)
