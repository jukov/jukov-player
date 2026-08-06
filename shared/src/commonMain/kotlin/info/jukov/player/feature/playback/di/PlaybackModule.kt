package info.jukov.player.feature.playback.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.di.AppScope
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.data.SettingsPlaybackStore
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackControllerFactory
import kotlinx.serialization.json.Json
import info.jukov.player.feature.playback.presentation.PlayerViewModel
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate

@BindingContainer
object PlaybackModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackStore(json: Json): PlaybackStore = SettingsPlaybackStore(json)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaybackController(
        factory: PlaybackControllerFactory,
        playbackStore: PlaybackStore,
    ): PlaybackController = factory.create(playbackStore)

    @Provides
    fun providePlayerViewModel(
        controller: PlaybackController,
        favoriteDelegate: FavoriteDelegate,
    ): PlayerViewModel = PlayerViewModel(controller, favoriteDelegate)
}
