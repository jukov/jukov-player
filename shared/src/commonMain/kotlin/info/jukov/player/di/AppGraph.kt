package info.jukov.player.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metro.Provides
import info.jukov.player.auth.presentation.AuthViewModel
import info.jukov.player.auth.di.AuthModule
import info.jukov.player.artist.presentation.ArtistsViewModel
import info.jukov.player.artist.di.ArtistsModule
import info.jukov.player.album.presentation.AlbumsViewModel
import info.jukov.player.album.di.AlbumsModule
import info.jukov.player.track.presentation.TracksViewModel
import info.jukov.player.track.di.TracksModule
import info.jukov.player.playback.di.PlaybackModule
import info.jukov.player.playback.domain.PlaybackController
import info.jukov.player.playback.domain.PlaybackControllerFactory
import info.jukov.player.playback.data.PlaybackStore
import info.jukov.player.playback.presentation.PlayerViewModel
import info.jukov.player.favorite.di.FavoritesModule
import info.jukov.player.favorite.presentation.FavoritesViewModel

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        CoreModule::class,
        AuthModule::class,
        ArtistsModule::class,
        AlbumsModule::class,
        TracksModule::class,
        PlaybackModule::class,
        FavoritesModule::class,
    ],
)
interface AppGraph {
    val authViewModel: AuthViewModel
    val artistsViewModel: ArtistsViewModel
    val albumsViewModel: AlbumsViewModel
    val tracksViewModel: TracksViewModel
    val playbackController: PlaybackController
    val playbackStore: PlaybackStore
    val playerViewModel: PlayerViewModel
    val favoritesViewModel: FavoritesViewModel

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides playbackControllerFactory: PlaybackControllerFactory): AppGraph
    }
}

fun createAppGraph(playbackControllerFactory: PlaybackControllerFactory): AppGraph =
    createGraphFactory<AppGraph.Factory>().create(playbackControllerFactory)
