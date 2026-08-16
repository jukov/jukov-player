package info.jukov.player.feature.album.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.feature.album.data.AlbumsApi
import info.jukov.player.feature.album.data.DefaultAlbumsRepository
import info.jukov.player.feature.album.data.SubsonicAlbumsApi
import info.jukov.player.feature.album.domain.AlbumsRepository
import info.jukov.player.feature.album.domain.GetAlbumsUseCase
import info.jukov.player.feature.album.presentation.AlbumsViewModel
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.di.AppScope
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.LibraryCachePolicy
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.search.domain.SearchUseCase
import info.jukov.player.core.domain.SortPreferences

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
        dao: CacheDao,
        policy: LibraryCachePolicy,
        client: SubsonicApiClient,
    ): AlbumsRepository = DefaultAlbumsRepository(api, authRepository, dao, policy, client)

    @Provides
    fun provideGetAlbumsUseCase(repository: AlbumsRepository): GetAlbumsUseCase =
        GetAlbumsUseCase(repository)

    @Provides
    fun provideAlbumsViewModel(
        getAlbumsUseCase: GetAlbumsUseCase,
        favoriteDelegate: FavoriteDelegate,
        downloadDelegate: DownloadDelegate,
        getTracksUseCase: GetTracksUseCase,
        search: SearchUseCase,
        sortPreferences: SortPreferences,
    ): AlbumsViewModel = AlbumsViewModel(
        getAlbumsUseCase,
        favoriteDelegate,
        downloadDelegate,
        getTracksUseCase,
        search,
        sortPreferences,
    )
}
