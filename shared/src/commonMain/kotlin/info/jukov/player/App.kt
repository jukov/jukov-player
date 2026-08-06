package info.jukov.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import info.jukov.player.di.AppGraph
import info.jukov.player.navigation.AppNavigation

@Composable
fun App(graph: AppGraph) {
    val authViewModel = viewModel { graph.authViewModel }
    val playerViewModel = viewModel { graph.playerViewModel }
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
        AppNavigation(authViewModel, playerViewModel, graph)
    }
}
