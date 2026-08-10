package info.jukov.player

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import info.jukov.player.core.data.cache.cacheDatabaseBuilder
import info.jukov.player.di.AppGraph
import info.jukov.player.di.AndroidAppGraphOwner
import info.jukov.player.di.createAppGraph
import info.jukov.player.di.createHttpClient
import info.jukov.player.feature.auth.data.AuthStorageImpl
import info.jukov.player.feature.download.AndroidOfflinePlatformFactory
import info.jukov.player.feature.playback.AndroidPlaybackControllerFactory
import info.jukov.player.feature.playback.data.SettingsPlaybackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

open class JukovApplication : Application(), AndroidAppGraphOwner {
    final override lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraph()
        graph.playbackController
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                graph.downloadsRepository.reconcile()
            }
        }
    }

    protected open fun createGraph(): AppGraph {
        val json = Json { ignoreUnknownKeys = true }
        return createAppGraph(
            playbackControllerFactory = AndroidPlaybackControllerFactory(this),
            cacheDatabaseBuilder = cacheDatabaseBuilder(this),
            offlinePlatformFactory = AndroidOfflinePlatformFactory(this),
            httpClient = createHttpClient(json),
            authStorage = AuthStorageImpl(),
            playbackStore = SettingsPlaybackStore(json),
        )
    }
}
