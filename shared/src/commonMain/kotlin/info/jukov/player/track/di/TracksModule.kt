package info.jukov.player.track.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.di.AppScope
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.track.data.DefaultTracksRepository
import info.jukov.player.track.data.SubsonicTracksApi
import info.jukov.player.track.data.TracksApi
import info.jukov.player.track.domain.GetTracksUseCase
import info.jukov.player.track.domain.TracksRepository
import info.jukov.player.track.presentation.TracksViewModel
import info.jukov.player.favorite.presentation.FavoriteDelegate

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
