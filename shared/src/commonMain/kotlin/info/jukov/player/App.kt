package info.jukov.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import info.jukov.player.di.AppGraphHolder
import info.jukov.player.navigation.AppNavigation

@Composable
fun App() {
    val authViewModel = viewModel { AppGraphHolder.graph.authViewModel }

    MaterialTheme {
        AppNavigation(authViewModel)
    }
}
