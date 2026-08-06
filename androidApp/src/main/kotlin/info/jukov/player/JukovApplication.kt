package info.jukov.player

import android.app.Application
import info.jukov.player.di.AppGraph
import info.jukov.player.di.AndroidAppGraphOwner
import info.jukov.player.di.createAppGraph
import info.jukov.player.feature.playback.AndroidPlaybackControllerFactory
import info.jukov.player.core.data.cache.cacheDatabaseBuilder

class JukovApplication : Application(), AndroidAppGraphOwner {
    override lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createAppGraph(AndroidPlaybackControllerFactory(this), cacheDatabaseBuilder(this))
        graph.playbackController
    }
}
