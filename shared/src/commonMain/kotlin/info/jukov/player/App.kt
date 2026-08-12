package info.jukov.player

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import info.jukov.player.core.presentation.ui.JukovTheme
import info.jukov.player.di.AppGraph
import info.jukov.player.navigation.AppNavigation

@Composable
fun App(
    graph: AppGraph,
    openDownloads: Boolean = false,
    onOpenDownloadsConsumed: () -> Unit = {},
    openPlayerRequest: Long = 0L,
    onOpenPlayerConsumed: () -> Unit = {},
) {
    val authViewModel = viewModel { graph.authViewModel }
    val playerViewModel = viewModel { graph.playerViewModel }
    JukovTheme {
        AppNavigation(
            authViewModel,
            playerViewModel,
            graph,
            openDownloads,
            onOpenDownloadsConsumed,
            openPlayerRequest,
            onOpenPlayerConsumed,
        )
    }
}
