package info.jukov.player.playback.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.di.AppScope
import info.jukov.player.playback.data.PlaybackStore
import info.jukov.player.playback.data.SettingsPlaybackStore
import info.jukov.player.playback.domain.PlaybackController
import info.jukov.player.playback.domain.PlaybackControllerFactory
import kotlinx.serialization.json.Json
import info.jukov.player.playback.presentation.PlayerViewModel

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
    fun providePlayerViewModel(controller: PlaybackController): PlayerViewModel =
        PlayerViewModel(controller)
}
