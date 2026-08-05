package info.jukov.player.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import info.jukov.player.auth.presentation.AuthViewModel
import info.jukov.player.auth.di.AuthModule
import info.jukov.player.artist.presentation.ArtistsViewModel
import info.jukov.player.artist.di.ArtistsModule
import info.jukov.player.album.presentation.AlbumsViewModel
import info.jukov.player.album.di.AlbumsModule
import info.jukov.player.track.presentation.TracksViewModel
import info.jukov.player.track.di.TracksModule

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        CoreModule::class,
        AuthModule::class,
        ArtistsModule::class,
        AlbumsModule::class,
        TracksModule::class,
    ],
)
interface AppGraph {
    val authViewModel: AuthViewModel
    val artistsViewModel: ArtistsViewModel
    val albumsViewModel: AlbumsViewModel
    val tracksViewModel: TracksViewModel
}

fun createAppGraph(): AppGraph = createGraph<AppGraph>()
