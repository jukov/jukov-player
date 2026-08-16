package info.jukov.player.feature.artist.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.feature.artist.data.ArtistsApi
import info.jukov.player.feature.artist.data.DefaultArtistsRepository
import info.jukov.player.feature.artist.data.SubsonicArtistsApi
import info.jukov.player.feature.artist.domain.ArtistsRepository
import info.jukov.player.feature.artist.domain.GetArtistsUseCase
import info.jukov.player.feature.artist.presentation.ArtistsViewModel
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.di.AppScope
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.LibraryCachePolicy
import info.jukov.player.feature.search.domain.SearchUseCase
import info.jukov.player.core.domain.SortPreferences
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.track.domain.GetTracksUseCase

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
        dao: CacheDao,
        policy: LibraryCachePolicy,
    ): ArtistsRepository = DefaultArtistsRepository(api, authRepository, dao, policy)

    @Provides
    fun provideGetArtistsUseCase(repository: ArtistsRepository): GetArtistsUseCase =
        GetArtistsUseCase(repository)

    @Provides
    fun provideArtistsViewModel(
        getArtistsUseCase: GetArtistsUseCase,
        authRepository: AuthRepository,
        favoriteDelegate: FavoriteDelegate,
        search: SearchUseCase,
        sortPreferences: SortPreferences,
        getTracksUseCase: GetTracksUseCase,
        downloadDelegate: DownloadDelegate,
    ): ArtistsViewModel = ArtistsViewModel(
        getArtistsUseCase,
        authRepository,
        favoriteDelegate,
        search,
        sortPreferences,
        getTracksUseCase,
        downloadDelegate,
    )
}
