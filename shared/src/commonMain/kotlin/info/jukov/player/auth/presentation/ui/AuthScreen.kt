package info.jukov.player.auth.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.artist.presentation.ui.ArtistsScreen
import info.jukov.player.artist.presentation.ui.ArtistsViewModel
import info.jukov.player.auth.domain.AuthState

@Composable
fun AuthScreen(viewModel: AuthViewModel, artistsViewModel: ArtistsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize()) {
        when (val authState = state.auth.content ?: AuthState.LoggedOut) {
            AuthState.LoggedOut -> LoginScreen(state, viewModel)
            is AuthState.LoggedIn -> ArtistsScreen(
                viewModel = artistsViewModel,
                username = authState.session.username,
                onLogout = viewModel::logout,
            )
        }
    }
}
