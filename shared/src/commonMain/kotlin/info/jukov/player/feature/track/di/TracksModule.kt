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
    ): TracksRepository = DefaultTracksRepository(api, authRepository)

    @Provides
    fun provideGetTracksUseCase(repository: TracksRepository): GetTracksUseCase =
        GetTracksUseCase(repository)

    @Provides
    fun provideTracksViewModel(
        getTracksUseCase: GetTracksUseCase,
        favoriteDelegate: FavoriteDelegate,
    ): TracksViewModel = TracksViewModel(getTracksUseCase, favoriteDelegate)
}
