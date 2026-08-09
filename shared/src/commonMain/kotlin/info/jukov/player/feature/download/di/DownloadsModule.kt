package info.jukov.player.feature.download.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.di.AppScope
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.download.data.DefaultDownloadsRepository
import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.feature.download.domain.OfflinePlatformFactory
import info.jukov.player.feature.track.data.TracksApi
import info.jukov.player.feature.download.presentation.DownloadsViewModel
import info.jukov.player.feature.download.presentation.DownloadDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.subsonic.data.SubsonicApiClient

@BindingContainer
object DownloadsModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @SingleIn(AppScope::class)
    fun provideOfflinePlatform(
        factory: OfflinePlatformFactory,
        authRepository: AuthRepository,
        dao: CacheDao,
        client: SubsonicApiClient,
        scope: CoroutineScope,
    ): OfflinePlatform = factory.create(authRepository, dao, client, scope)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadsRepository(
        authRepository: AuthRepository,
        dao: CacheDao,
        tracksApi: TracksApi,
        platform: OfflinePlatform,
        client: SubsonicApiClient,
    ): DownloadsRepository = DefaultDownloadsRepository(
        authRepository, dao, tracksApi, platform, client,
    )

    @Provides
    fun provideDownloadsViewModel(
        repository: DownloadsRepository,
        favoriteDelegate: FavoriteDelegate,
    ): DownloadsViewModel = DownloadsViewModel(repository, favoriteDelegate)
}
