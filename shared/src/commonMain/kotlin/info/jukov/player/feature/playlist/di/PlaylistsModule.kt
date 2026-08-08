package info.jukov.player.feature.playlist.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.di.AppScope
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.playlist.data.*
import info.jukov.player.feature.playlist.domain.PlaylistsRepository
import info.jukov.player.feature.playlist.presentation.*
import info.jukov.player.subsonic.data.SubsonicApiClient

@BindingContainer
object PlaylistsModule {
    @Provides @SingleIn(AppScope::class) fun api(client: SubsonicApiClient): PlaylistsApi = SubsonicPlaylistsApi(client)
    @Provides @SingleIn(AppScope::class) fun repository(api: PlaylistsApi, auth: AuthRepository): PlaylistsRepository = DefaultPlaylistsRepository(api, auth)
    @Provides @SingleIn(AppScope::class) fun listViewModel(repository: PlaylistsRepository): PlaylistsViewModel = PlaylistsViewModel(repository)
    @Provides fun detailViewModel(
        repository: PlaylistsRepository,
        downloads: DownloadDelegate,
        favorites: info.jukov.player.feature.favorite.presentation.FavoriteDelegate,
    ): PlaylistViewModel = PlaylistViewModel(repository, downloads, favorites)
    @Provides @SingleIn(AppScope::class) fun pickerViewModel(repository: PlaylistsRepository): PlaylistPickerViewModel = PlaylistPickerViewModel(repository)
}
