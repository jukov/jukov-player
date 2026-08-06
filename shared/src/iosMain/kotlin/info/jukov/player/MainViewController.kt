package info.jukov.player

import androidx.compose.ui.window.ComposeUIViewController
import info.jukov.player.di.createAppGraph
import info.jukov.player.feature.playback.IosPlaybackControllerFactory

fun MainViewController() = ComposeUIViewController {
    App(createAppGraph(IosPlaybackControllerFactory))
}
