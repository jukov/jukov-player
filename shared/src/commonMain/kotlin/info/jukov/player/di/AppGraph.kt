package info.jukov.player.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import info.jukov.player.auth.presentation.AuthViewModel
import info.jukov.player.artist.presentation.ArtistsViewModel

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [AppModule::class],
)
interface AppGraph {
    val authViewModel: AuthViewModel
    val artistsViewModel: ArtistsViewModel
}

fun createAppGraph(): AppGraph = createGraph<AppGraph>()
