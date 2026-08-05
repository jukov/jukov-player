package info.jukov.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import info.jukov.player.di.AppGraph
import info.jukov.player.navigation.AppNavigation

@Composable
fun App(graph: AppGraph) {
    val authViewModel = viewModel { graph.authViewModel }
    val playerViewModel = viewModel { graph.playerViewModel }

    MaterialTheme {
        AppNavigation(authViewModel, playerViewModel, graph)
    }
}
