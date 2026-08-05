package info.jukov.player.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import info.jukov.player.di.AndroidAppGraphOwner
import info.jukov.player.playback.data.PlaybackStore

class PlaybackService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var store: PlaybackStore

    override fun onCreate() {
        super.onCreate()
        store = (application as AndroidAppGraphOwner).graph.playbackStore
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
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
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

    private inner class LibraryCallback : MediaLibrarySession.Callback {
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
