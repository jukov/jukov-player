package info.jukov.player.feature.track.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.di.AppScope
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.feature.track.data.DefaultTracksRepository
import info.jukov.player.feature.track.data.SubsonicTracksApi
import info.jukov.player.feature.track.data.TracksApi
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.track.domain.TracksRepository
import info.jukov.player.feature.track.presentation.TracksViewModel
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.LibraryCachePolicy
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.search.domain.SearchUseCase

@BindingContainer
object TracksModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideTracksApi(client: SubsonicApiClient): TracksApi = SubsonicTracksApi(client)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTracksRepository(
        api: TracksApi,
        authRepository: AuthRepository,
        dao: CacheDao,
        policy: LibraryCachePolicy,
        client: SubsonicApiClient,
    ): TracksRepository = DefaultTracksRepository(api, authRepository, dao, policy, client)

    @Provides
    fun provideGetTracksUseCase(repository: TracksRepository): GetTracksUseCase =
        GetTracksUseCase(repository)

    @Provides
    fun provideTracksViewModel(
        getTracksUseCase: GetTracksUseCase,
        favoriteDelegate: FavoriteDelegate,
        downloadDelegate: DownloadDelegate,
        search: SearchUseCase,
    ): TracksViewModel = TracksViewModel(getTracksUseCase, favoriteDelegate, downloadDelegate, search)
}
