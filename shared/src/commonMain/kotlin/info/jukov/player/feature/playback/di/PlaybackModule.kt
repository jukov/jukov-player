package info.jukov.player.feature.playback.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.di.AppScope
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackControllerFactory
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.favorite.domain.FavoriteMutator
import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.playback.domain.DefaultPlaybackQueueResolver
import info.jukov.player.feature.playback.domain.PlaybackQueueResolver

@BindingContainer
object PlaybackModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackController(
        factory: PlaybackControllerFactory,
        playbackStore: PlaybackStore,
        favoriteMutator: FavoriteMutator,
    ): PlaybackController = factory.create(playbackStore, favoriteMutator)

    @Provides
    fun providePlaybackQueueResolver(
        downloadsRepository: DownloadsRepository,
    ): PlaybackQueueResolver = DefaultPlaybackQueueResolver(downloadsRepository)

    @Provides
    fun providePlayerViewModel(
        controller: PlaybackController,
        favoriteDelegate: FavoriteDelegate,
        queueResolver: PlaybackQueueResolver,
    ): PlayerViewModel = PlayerViewModel(controller, favoriteDelegate, queueResolver)
}
