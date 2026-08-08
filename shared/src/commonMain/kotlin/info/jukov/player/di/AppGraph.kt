package info.jukov.player.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metro.Provides
import info.jukov.player.feature.auth.presentation.AuthViewModel
import info.jukov.player.feature.auth.di.AuthModule
import info.jukov.player.feature.artist.presentation.ArtistsViewModel
import info.jukov.player.feature.artist.di.ArtistsModule
import info.jukov.player.feature.album.presentation.AlbumsViewModel
import info.jukov.player.feature.album.di.AlbumsModule
import info.jukov.player.feature.track.presentation.TracksViewModel
import info.jukov.player.feature.track.di.TracksModule
import info.jukov.player.feature.playback.di.PlaybackModule
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackControllerFactory
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.favorite.di.FavoritesModule
import info.jukov.player.feature.favorite.presentation.FavoritesViewModel
import androidx.room3.RoomDatabase
import info.jukov.player.core.data.cache.CacheDatabase
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.download.di.DownloadsModule
import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.feature.download.presentation.DownloadsViewModel
import info.jukov.player.feature.playlist.di.PlaylistsModule
import info.jukov.player.feature.playlist.presentation.PlaylistPickerViewModel
import info.jukov.player.feature.playlist.presentation.PlaylistViewModel
import info.jukov.player.feature.playlist.presentation.PlaylistsViewModel
import info.jukov.player.feature.search.di.SearchModule
import info.jukov.player.feature.library.presentation.LibraryViewModel

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
        DownloadsModule::class,
        PlaylistsModule::class,
        SearchModule::class,
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
    val downloadsRepository: DownloadsRepository
    val cacheDao: CacheDao
    val authRepository: AuthRepository
    val subsonicApiClient: SubsonicApiClient
    val downloadsViewModel: DownloadsViewModel
    val playlistsViewModel: PlaylistsViewModel
    val playlistViewModel: PlaylistViewModel
    val playlistPickerViewModel: PlaylistPickerViewModel
    val libraryViewModel: LibraryViewModel

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides playbackControllerFactory: PlaybackControllerFactory,
            @Provides cacheDatabaseBuilder: RoomDatabase.Builder<CacheDatabase>,
            @Provides offlinePlatform: OfflinePlatform,
        ): AppGraph
    }
}

fun createAppGraph(
    playbackControllerFactory: PlaybackControllerFactory,
    cacheDatabaseBuilder: RoomDatabase.Builder<CacheDatabase>,
    offlinePlatform: OfflinePlatform,
): AppGraph = createGraphFactory<AppGraph.Factory>().create(
    playbackControllerFactory, cacheDatabaseBuilder, offlinePlatform,
)
