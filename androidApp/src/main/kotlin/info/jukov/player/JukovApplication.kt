package info.jukov.player

import android.app.Application
import info.jukov.player.di.AppGraph
import info.jukov.player.di.AndroidAppGraphOwner
import info.jukov.player.di.createAppGraph
import info.jukov.player.playback.AndroidPlaybackControllerFactory

class JukovApplication : Application(), AndroidAppGraphOwner {
    override lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createAppGraph(AndroidPlaybackControllerFactory(this))
        graph.playbackController
    }
}
