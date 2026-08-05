package info.jukov.player.artist.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.artist.data.ArtistsApi
import info.jukov.player.artist.data.DefaultArtistsRepository
import info.jukov.player.artist.data.SubsonicArtistsApi
import info.jukov.player.artist.domain.ArtistsRepository
import info.jukov.player.artist.domain.GetArtistsUseCase
import info.jukov.player.artist.presentation.ArtistsViewModel
import info.jukov.player.favorite.presentation.FavoriteDelegate
import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.di.AppScope
import info.jukov.player.subsonic.data.SubsonicApiClient

@BindingContainer
object ArtistsModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideArtistsApi(client: SubsonicApiClient): ArtistsApi = SubsonicArtistsApi(client)

    @Provides
    @SingleIn(AppScope::class)
    fun provideArtistsRepository(
        api: ArtistsApi,
        authRepository: AuthRepository,
    ): ArtistsRepository = DefaultArtistsRepository(api, authRepository)

    @Provides
    fun provideGetArtistsUseCase(repository: ArtistsRepository): GetArtistsUseCase =
        GetArtistsUseCase(repository)

    @Provides
    fun provideArtistsViewModel(
        getArtistsUseCase: GetArtistsUseCase,
        authRepository: AuthRepository,
        favoriteDelegate: FavoriteDelegate,
    ): ArtistsViewModel = ArtistsViewModel(getArtistsUseCase, authRepository, favoriteDelegate)
}
