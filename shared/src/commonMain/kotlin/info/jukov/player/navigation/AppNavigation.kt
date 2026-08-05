package info.jukov.player.navigation

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
import androidx.navigation3.ui.NavDisplay
import info.jukov.player.artist.presentation.ui.ArtistsScreen
import info.jukov.player.album.presentation.ui.AlbumsScreen
import info.jukov.player.auth.domain.AuthState
import info.jukov.player.auth.presentation.AuthViewModel
import info.jukov.player.auth.presentation.ui.LoginScreen
import info.jukov.player.di.AppGraphHolder

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
) {
    val authUiState by authViewModel.state.collectAsStateWithLifecycle()
    val authState = authUiState.auth.content ?: AuthState.LoggedOut
    val destination: NavKey = when (authState) {
        AuthState.LoggedOut -> Routes.Login
        is AuthState.LoggedIn -> Routes.Artists
    }
    val backStack = rememberNavBackStack(destination)

    LaunchedEffect(destination) {
        if (backStack.lastOrNull() != destination) {
            backStack.clear()
            backStack.add(destination)
        }
    }

    Surface(Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            entryProvider = entryProvider {
                entry<Routes.Login> {
                    LoginScreen(authUiState, authViewModel)
                }
                entry<Routes.Artists> {
                    val session = (authState as? AuthState.LoggedIn)?.session
                    if (session != null) {
                        val artistsViewModel = viewModel {
                            AppGraphHolder.graph.artistsViewModel
                        }
                        ArtistsScreen(
                            viewModel = artistsViewModel,
                            onLogout = authViewModel::logout,
                            onArtistClick = { backStack.add(Routes.Albums(it)) },
                            onAllAlbumsClick = { backStack.add(Routes.Albums()) },
                        )
                    }
                }
                entry<Routes.Albums> { route ->
                    val albumsViewModel = viewModel {
                        AppGraphHolder.graph.albumsViewModel
                    }
                    AlbumsScreen(
                        artistId = route.artistId,
                        viewModel = albumsViewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}
