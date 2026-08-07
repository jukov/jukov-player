package info.jukov.player.feature.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackControllerFactory
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playback.domain.appendQueueItems
import info.jukov.player.feature.playback.domain.moveFutureQueueItem
import info.jukov.player.feature.playback.domain.moveFutureQueueItemsToTop
import info.jukov.player.feature.playback.domain.removeFutureQueueItems
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidPlaybackControllerFactory(
    private val context: Context,
) : PlaybackControllerFactory {
    override fun create(playbackStore: PlaybackStore): PlaybackController =
        AndroidPlaybackController(context, playbackStore)
}

@OptIn(UnstableApi::class)
private class AndroidPlaybackController(
    context: Context,
    private val playbackStore: PlaybackStore,
) : PlaybackController {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var queuedAction: ((MediaController) -> Unit)? = null

    private val initialSnapshot = playbackStore.read()?.let { saved ->
        PlaybackSnapshot(
            queue = saved.queue,
            currentIndex = saved.currentIndex,
            durationMs = saved.queue.getOrNull(saved.currentIndex)?.durationMs ?: 0,
            origin = saved.origin,
        )
    } ?: PlaybackSnapshot()
    private val _state = MutableStateFlow<LoadableState<PlaybackSnapshot>>(
        LoadableState.Loading(initialSnapshot),
    )
    override val state: StateFlow<LoadableState<PlaybackSnapshot>> = _state.asStateFlow()

    private val positionTicker = object : Runnable {
        override fun run() {
            updatePosition()
            handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { connected ->
                        controller = connected
                        connected.addListener(PlayerListener())
                        queuedAction?.invoke(connected)
                        queuedAction = null
                        replaceFromPlayer(connected)
                        handler.post(positionTicker)
                    }
                    .onFailure { fail(AppError.PlayerConnectionFailed) }
            },
            appContext.mainExecutor,
        )
    }

    override fun play(tracks: List<Track>, startIndex: Int) {
        play(tracks, startIndex, PlaybackOrigin.TrackList)
    }

    override fun play(tracks: List<Track>, startIndex: Int, origin: PlaybackOrigin) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return
        val queue = tracks.drop(startIndex)
        if (queue.any { it.streamUrl == null }) {
            fail(AppError.MissingTrackStreamUrl)
            return
        }
        playbackStore.write(queue, 0, origin)
        withController { player ->
            player.setMediaItems(queue.map(Track::toMediaItem), 0, 0)
            player.prepare()
            player.play()
        }
    }

    override fun playPause() = withController { player ->
        if (player.isPlaying) {
            player.pause()
        } else if (player.mediaItemCount > 0) {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    override fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (tracks.any { it.streamUrl == null }) {
            fail(AppError.MissingTrackStreamUrl)
            return
        }
        val snapshot = _state.value.content ?: PlaybackSnapshot()
        val wasEmpty = snapshot.queue.isEmpty()
        val queue = appendQueueItems(snapshot.queue, tracks)
        val currentIndex = if (wasEmpty) 0 else snapshot.currentIndex
        playbackStore.write(queue, currentIndex, snapshot.origin)
        _state.update {
            LoadableState.Content(
                snapshot.copy(
                    queue = queue,
                    currentIndex = currentIndex,
                    durationMs = if (wasEmpty) tracks.first().durationMs else snapshot.durationMs,
                ),
            )
        }
        withController { player ->
            player.addMediaItems(tracks.map(Track::toMediaItem))
            if (wasEmpty) player.prepare()
        }
    }

    override fun next() = withController { it.seekToNextMediaItem() }
    override fun previous() = withController { it.seekToPrevious() }
    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    override fun playAt(index: Int) {
        val snapshot = _state.value.content ?: return
        if (index !in snapshot.queue.indices || index < snapshot.currentIndex) return
        withController { player ->
            player.seekToDefaultPosition(index)
            player.play()
        }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val snapshot = _state.value.content ?: return
        val queue = moveFutureQueueItem(snapshot.queue, snapshot.currentIndex, fromIndex, toIndex)
        if (queue == snapshot.queue) return
        playbackStore.write(queue, snapshot.currentIndex, snapshot.origin)
        _state.update { it.mapContent { value -> value.copy(queue = queue) } }
        withController { it.moveMediaItem(fromIndex, toIndex) }
    }

    override fun moveQueueItemsToTop(indices: Set<Int>) {
        val snapshot = _state.value.content ?: return
        val queue = moveFutureQueueItemsToTop(snapshot.queue, snapshot.currentIndex, indices)
        if (queue == snapshot.queue) return
        playbackStore.write(queue, snapshot.currentIndex, snapshot.origin)
        _state.update { it.mapContent { value -> value.copy(queue = queue) } }
        withController { player ->
            val workingQueue = snapshot.queue.toMutableList()
            for (targetIndex in snapshot.currentIndex + 1..queue.lastIndex) {
                val fromIndex = workingQueue.indexOfFirstFrom(targetIndex) {
                    it == queue[targetIndex]
                }
                if (fromIndex >= 0 && fromIndex != targetIndex) {
                    player.moveMediaItem(fromIndex, targetIndex)
                    workingQueue.add(targetIndex, workingQueue.removeAt(fromIndex))
                }
            }
        }
    }

    override fun removeQueueItems(indices: Set<Int>) {
        val snapshot = _state.value.content ?: return
        val queue = removeFutureQueueItems(snapshot.queue, snapshot.currentIndex, indices)
        if (queue == snapshot.queue) return
        playbackStore.write(queue, snapshot.currentIndex, snapshot.origin)
        _state.update { it.mapContent { value -> value.copy(queue = queue) } }
        withController { player ->
            indices.filter { it > snapshot.currentIndex && it < player.mediaItemCount }
                .sortedDescending()
                .forEach(player::removeMediaItem)
        }
    }

    override fun stopAndClear() {
        playbackStore.clear()
        queuedAction = null
        withController { player ->
            player.stop()
            player.clearMediaItems()
        }
        _state.update { LoadableState.Content(PlaybackSnapshot()) }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: run { queuedAction = action }
    }

    private fun replaceFromPlayer(player: Player) {
        val saved = playbackStore.read()
        if (saved == null || player.mediaItemCount == 0) {
            _state.update { LoadableState.Content(PlaybackSnapshot()) }
            return
        }
        val index = player.currentMediaItemIndex.coerceIn(saved.queue.indices)
        _state.update {
            LoadableState.Content(
                PlaybackSnapshot(
                    queue = saved.queue,
                    currentIndex = index,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = player.duration.validDuration()
                        ?: saved.queue[index].durationMs,
                    isPlaying = player.isPlaying,
                    origin = saved.origin,
                ),
            )
        }
    }

    private fun updatePosition() {
        val player = controller ?: return
        _state.update { current ->
            current.mapContent { snapshot ->
                snapshot.copy(
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = player.duration.validDuration() ?: snapshot.currentTrack?.durationMs ?: 0,
                )
            }
        }
    }

    private fun updatePlaying(isPlaying: Boolean) {
        _state.update { it.mapContent { snapshot -> snapshot.copy(isPlaying = isPlaying) } }
    }

    private fun updateCurrentItem(player: Player) {
        val saved = playbackStore.read() ?: return
        val index = player.currentMediaItemIndex.coerceIn(saved.queue.indices)
        playbackStore.updateCurrentIndex(index)
        _state.update { current ->
            current.mapContent { snapshot ->
                snapshot.copy(
                    queue = saved.queue,
                    currentIndex = index,
                    positionMs = 0,
                    durationMs = player.duration.validDuration() ?: saved.queue[index].durationMs,
                )
            }
        }
    }

    private fun fail(error: AppError) {
        _state.update { LoadableState.Failure(error, it.content) }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            controller?.let(::replaceFromPlayer)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlaying(isPlaying)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let(::updateCurrentItem)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) = updatePosition()

        override fun onPlayerError(error: PlaybackException) {
            fail(AppError.PlaybackFailed)
        }
    }

    private fun LoadableState<PlaybackSnapshot>.mapContent(
        transform: (PlaybackSnapshot) -> PlaybackSnapshot,
    ): LoadableState<PlaybackSnapshot> = when (this) {
        is LoadableState.Content -> LoadableState.Content(transform(content))
        is LoadableState.Loading -> LoadableState.Loading(content?.let(transform))
        is LoadableState.Failure -> LoadableState.Failure(error, content?.let(transform))
    }

    private fun Long.validDuration(): Long? = takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0)

    private inline fun <T> List<T>.indexOfFirstFrom(
        startIndex: Int,
        predicate: (T) -> Boolean,
    ): Int {
        for (index in startIndex until size) if (predicate(this[index])) return index
        return -1
    }

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}
