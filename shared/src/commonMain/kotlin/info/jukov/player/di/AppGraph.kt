package info.jukov.player.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import info.jukov.player.auth.presentation.AuthViewModel
import info.jukov.player.artist.presentation.ArtistsViewModel
import info.jukov.player.album.presentation.AlbumsViewModel

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [AppModule::class],
)
interface AppGraph {
    val authViewModel: AuthViewModel
    val artistsViewModel: ArtistsViewModel
    val albumsViewModel: AlbumsViewModel
}

fun createAppGraph(): AppGraph = createGraph<AppGraph>()
