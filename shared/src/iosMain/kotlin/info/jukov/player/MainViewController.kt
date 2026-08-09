package info.jukov.player

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.di.createAppGraph
import info.jukov.player.core.data.cache.cacheDatabaseBuilder
import info.jukov.player.core.presentation.ui.IosArtworkPaletteExtractor
import info.jukov.player.core.presentation.ui.LocalArtworkPaletteExtractor
import info.jukov.player.feature.download.IosOfflinePlatform
import info.jukov.player.feature.download.IosOfflinePlatformFactory
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.playback.IosPlaybackControllerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.Foundation.NSLock

object IosAppRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recoveryLock = NSLock()
    private var recoveryStarted = false
    val graph by lazy {
        createAppGraph(
            IosPlaybackControllerFactory,
            cacheDatabaseBuilder(),
            IosOfflinePlatformFactory(),
        ).also { it.playbackController }
    }

    private val _openDownloads = MutableStateFlow(value = false)
    internal val openDownloads = _openDownloads.asStateFlow()

    fun requestOpenDownloads() {
        _openDownloads.value = true
    }

    fun start() {
        graph
    }

    fun startRecovery() {
        recoveryLock.lock()
        val shouldStart = try {
            if (recoveryStarted) {
                false
            } else {
                recoveryStarted = true
                true
            }
        } finally {
            recoveryLock.unlock()
        }
        if (!shouldStart) {
            return
        }
        scope.launch {
            graph.downloadsRepository.reconcile()
            val authState = graph.authRepository.authState.value as? AuthState.LoggedIn
            authState?.session?.accountKey?.let(graph.offlinePlatform::recover)
        }
    }

    fun handleEventsForBackgroundSession(
        identifier: String,
        completionHandler: () -> Unit,
    ) {
        (graph.offlinePlatform as IosOfflinePlatform).handleEventsForBackgroundSession(
            identifier,
            completionHandler,
            ::startRecovery,
        )
    }

    internal fun consumeOpenDownloads() {
        _openDownloads.value = false
    }
}

fun MainViewController() = ComposeUIViewController {
    val openDownloads by IosAppRuntime.openDownloads.collectAsStateWithLifecycle()
    val paletteExtractor = remember { IosArtworkPaletteExtractor() }
    CompositionLocalProvider(LocalArtworkPaletteExtractor provides paletteExtractor) {
        App(
            graph = IosAppRuntime.graph,
            openDownloads = openDownloads,
            onOpenDownloadsConsumed = IosAppRuntime::consumeOpenDownloads,
        )
    }
}
