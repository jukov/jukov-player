package info.jukov.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import info.jukov.player.artist.presentation.ui.ArtistsViewModel
import info.jukov.player.auth.presentation.ui.AuthScreen
import info.jukov.player.di.AppGraphHolder

@Composable
fun App() {
    val authViewModel = viewModel { AppGraphHolder.graph.authViewModel }
    val artistsViewModel = viewModel { AppGraphHolder.graph.artistsViewModel }

    MaterialTheme {
        AuthScreen(authViewModel, artistsViewModel)
    }
}
