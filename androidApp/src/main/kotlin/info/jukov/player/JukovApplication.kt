package info.jukov.player

import android.app.Application
import info.jukov.player.core.data.cache.cacheDatabaseBuilder
import info.jukov.player.di.AppGraph
import info.jukov.player.di.AndroidAppGraphOwner
import info.jukov.player.di.createAppGraph
import info.jukov.player.di.createHttpClient
import info.jukov.player.feature.auth.data.AuthStorageImpl
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.download.AndroidOfflinePlatformFactory
import info.jukov.player.feature.playback.AndroidPlaybackControllerFactory
import info.jukov.player.feature.playback.data.SettingsPlaybackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

open class JukovApplication : Application(), AndroidAppGraphOwner {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    final override lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraph()
        graph.playbackController
        applicationScope.launch {
            graph.authRepository.authState
                .filterIsInstance<AuthState.LoggedIn>()
                .map { it.session.accountKey }
                .distinctUntilChanged()
                .collect {
                    try {
                        graph.downloadsRepository.reconcile()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        // A later login can retry without terminating the auth-state observer.
                    }
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
