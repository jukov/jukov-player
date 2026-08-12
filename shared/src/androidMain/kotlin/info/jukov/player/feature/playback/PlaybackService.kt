package info.jukov.player.feature.playback

import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import info.jukov.player.di.AndroidAppGraphOwner
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.domain.trackById
import info.jukov.player.feature.playback.domain.updateTrackFavorite
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.add_to_favorites
import jukovplayer.shared.generated.resources.remove_from_favorites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var store: PlaybackStore
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph by lazy { (application as AndroidAppGraphOwner).graph }

    override fun onCreate() {
        super.onCreate()
        store = graph.playbackStore
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        restoreQueue()
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    refreshFavoriteButton()
                }
            },
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(PlaybackNotificationIntent.pendingIntent(this))
            .build()
        observeFavoriteState()
        refreshFavoriteButton()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
        serviceScope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun restoreQueue() {
        val saved = store.read() ?: return
        if (saved.queue.isEmpty() || saved.currentIndex !in saved.queue.indices) {
            store.clear()
            return
        }
        val items = runCatching { saved.queue.map { it.toMediaItem() } }.getOrElse {
            store.clear()
            return
        }
        player.setMediaItems(items, saved.currentIndex, 0)
        player.playWhenReady = false
        player.prepare()
    }

    private fun observeFavoriteState() {
        serviceScope.launch {
            graph.favoriteMutator.changes.collect { change ->
                if (change.target is FavoriteTarget.Track) {
                    updateFavoriteState(change.target.id, change.isFavorite)
                }
            }
        }
        serviceScope.launch {
            graph.favoriteMutator.pending.collectLatest {
                publishFavoriteButton()
            }
        }
    }

    private fun refreshFavoriteButton() {
        serviceScope.launch { publishFavoriteButton() }
    }

    private suspend fun publishFavoriteButton() {
        if (!::session.isInitialized) {
            return
        }
        val track = currentTrack()
        if (track == null || graph.authRepository.authState.value !is AuthState.LoggedIn) {
            session.setMediaButtonPreferences(emptyList())
            return
        }
        val displayName = getString(
            if (track.isFavorite) {
                Res.string.remove_from_favorites
            } else {
                Res.string.add_to_favorites
            },
        )
        session.setMediaButtonPreferences(
            listOf(
                favoriteCommandButton(
                    isFavorite = track.isFavorite,
                    enabled = track.id !in graph.favoriteMutator.pending.value,
                    displayName = displayName,
                ),
            ),
        )
    }

    private fun currentTrack() = store.read()?.trackById(player.currentMediaItem?.mediaId)

    private fun updateFavoriteState(trackId: String, isFavorite: Boolean) {
        store.updateTrackFavorite(trackId, isFavorite)
        refreshFavoriteButton()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (!canAccessFavoriteCommand(controller.isTrusted)) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            }
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(setCurrentTrackFavoriteCommand())
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != ACTION_SET_CURRENT_TRACK_FAVORITE) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            if (!canAccessFavoriteCommand(controller.isTrusted)) {
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED),
                )
            }
            val track = currentTrack()
            if (track == null || graph.authRepository.authState.value !is AuthState.LoggedIn) {
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE),
                )
            }
            serviceScope.launch {
                graph.favoriteMutator.set(track, !track.isFavorite) { isFavorite ->
                    updateFavoriteState(track.id, isFavorite)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle("Jukov Player")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = store.read()
            if (saved == null || saved.currentIndex !in saved.queue.indices) {
                return Futures.immediateFailedFuture(IllegalStateException("No saved playback"))
            }
            val items = saved.queue.map { it.toMediaItem() }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(items, saved.currentIndex, 0),
            )
        }
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}
