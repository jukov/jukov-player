package info.jukov.player.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import info.jukov.player.auth.presentation.ui.AuthViewModel

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [AppModule::class],
)
interface AppGraph {
    val authViewModel: AuthViewModel
}

fun createAppGraph(): AppGraph = createGraph<AppGraph>()
