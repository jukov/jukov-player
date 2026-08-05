package info.jukov.player.favorite.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.di.AppScope
import info.jukov.player.favorite.data.DefaultFavoritesRepository
import info.jukov.player.favorite.data.FavoritesApi
import info.jukov.player.favorite.data.SubsonicFavoritesApi
import info.jukov.player.favorite.domain.FavoritesRepository
import info.jukov.player.favorite.presentation.FavoritesViewModel
import info.jukov.player.favorite.presentation.FavoriteDelegate
import info.jukov.player.subsonic.data.SubsonicApiClient

@BindingContainer
object FavoritesModule {
    @Provides @SingleIn(AppScope::class)
    fun provideApi(client: SubsonicApiClient): FavoritesApi = SubsonicFavoritesApi(client)

    @Provides @SingleIn(AppScope::class)
    fun provideRepository(api: FavoritesApi, authRepository: AuthRepository): FavoritesRepository =
        DefaultFavoritesRepository(api, authRepository)

    @Provides
    fun provideViewModel(repository: FavoritesRepository): FavoritesViewModel =
        FavoritesViewModel(repository)

    @Provides
    fun provideFavoriteDelegate(repository: FavoritesRepository): FavoriteDelegate =
        FavoriteDelegate(repository)
}
