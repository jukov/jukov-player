package info.jukov.player.album.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.album.data.AlbumsApi
import info.jukov.player.album.data.DefaultAlbumsRepository
import info.jukov.player.album.data.SubsonicAlbumsApi
import info.jukov.player.album.domain.AlbumsRepository
import info.jukov.player.album.domain.GetAlbumsUseCase
import info.jukov.player.album.presentation.AlbumsViewModel
import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.di.AppScope
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.favorite.presentation.FavoriteDelegate

@BindingContainer
object AlbumsModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideAlbumsApi(client: SubsonicApiClient): AlbumsApi = SubsonicAlbumsApi(client)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAlbumsRepository(
        api: AlbumsApi,
        authRepository: AuthRepository,
    ): AlbumsRepository = DefaultAlbumsRepository(api, authRepository)

    @Provides
    fun provideGetAlbumsUseCase(repository: AlbumsRepository): GetAlbumsUseCase =
        GetAlbumsUseCase(repository)

    @Provides
    fun provideAlbumsViewModel(
        getAlbumsUseCase: GetAlbumsUseCase,
        favoriteDelegate: FavoriteDelegate,
    ): AlbumsViewModel = AlbumsViewModel(getAlbumsUseCase, favoriteDelegate)
}
