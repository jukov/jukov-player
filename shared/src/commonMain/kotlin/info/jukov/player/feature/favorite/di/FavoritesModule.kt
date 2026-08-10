package info.jukov.player.feature.favorite.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.di.AppScope
import info.jukov.player.feature.favorite.data.DefaultFavoritesRepository
import info.jukov.player.feature.favorite.data.FavoritesApi
import info.jukov.player.feature.favorite.data.SubsonicFavoritesApi
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import info.jukov.player.feature.favorite.domain.FavoriteMutator
import info.jukov.player.feature.favorite.presentation.FavoritesViewModel
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.LibraryCachePolicy
import info.jukov.player.feature.download.presentation.DownloadDelegate

@BindingContainer
object FavoritesModule {
    @Provides @SingleIn(AppScope::class)
    fun provideApi(client: SubsonicApiClient): FavoritesApi = SubsonicFavoritesApi(client)

    @Provides @SingleIn(AppScope::class)
    fun provideRepository(
        api: FavoritesApi,
        authRepository: AuthRepository,
        dao: CacheDao,
        policy: LibraryCachePolicy,
        client: SubsonicApiClient,
    ): FavoritesRepository = DefaultFavoritesRepository(api, authRepository, dao, policy, client)

    @Provides
    fun provideViewModel(
        repository: FavoritesRepository,
        downloadDelegate: DownloadDelegate,
    ): FavoritesViewModel = FavoritesViewModel(repository, downloadDelegate)

    @Provides @SingleIn(AppScope::class)
    fun provideFavoriteDelegate(repository: FavoritesRepository): FavoriteDelegate =
        FavoriteDelegate(repository)

    @Provides
    fun provideFavoriteMutator(delegate: FavoriteDelegate): FavoriteMutator = delegate
}
