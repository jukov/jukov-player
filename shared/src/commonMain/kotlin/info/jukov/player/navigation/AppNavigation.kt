package info.jukov.player.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import info.jukov.player.artist.presentation.ui.ArtistsScreen
import info.jukov.player.artist.presentation.ArtistsViewModel
import info.jukov.player.auth.domain.AuthState
import info.jukov.player.auth.presentation.AuthViewModel
import info.jukov.player.auth.presentation.ui.LoginScreen

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    artistsViewModel: ArtistsViewModel,
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
                        ArtistsScreen(
                            viewModel = artistsViewModel,
                            username = session.username,
                            onLogout = authViewModel::logout,
                        )
                    }
                }
            },
        )
    }
}
