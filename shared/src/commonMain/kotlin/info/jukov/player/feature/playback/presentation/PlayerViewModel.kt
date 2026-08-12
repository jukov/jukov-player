package info.jukov.player.feature.playback.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import info.jukov.player.feature.playback.domain.PlaybackQueueResolver
import info.jukov.player.feature.download.presentation.DownloadDelegate

data class PlayerUiState(
    val queue: List<PlayerQueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrack: Track? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val loadingTrackId: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val origin: PlaybackOrigin = PlaybackOrigin.TrackList,
)

data class PlayerQueueItem(
    val uiId: String,
    val track: Track,
)

class PlayerViewModel(
    private val controller: PlaybackController,
    private val favoriteDelegate: FavoriteDelegate,
    private val queueResolver: PlaybackQueueResolver,
    private val downloadDelegate: DownloadDelegate,
) : ViewModel() {
    private var playJob: Job? = null
    private var nextQueueItemId = 0L
    private var queueItems = emptyList<PlayerQueueItem>()
    private val favoriteOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val pendingPlayback = MutableStateFlow<PendingPlayback?>(null)

    val state: StateFlow<LoadableState<PlayerUiState>> = combine(
        controller.state,
        favoriteOverrides,
        pendingPlayback,
    ) { loadable, overrides, pending ->
        loadable.mapContent { it.toUiState(overrides).withPendingPlayback(pending) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = controller.state.value.mapContent { it.toUiState(emptyMap()) },
        )
    val favoritePending = favoriteDelegate.pending

    init {
        viewModelScope.launch {
            favoriteDelegate.changes.collect { change ->
                val target = change.target
                if (target is FavoriteTarget.Track) {
                    updateFavorite(target.id, change.isFavorite)
                }
            }
        }
    }

    fun play(
        tracks: List<Track>,
        startIndex: Int,
        origin: PlaybackOrigin = PlaybackOrigin.TrackList,
    ) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) return
        val requested = PendingPlayback(tracks[startIndex], origin)
        if (pendingPlayback.value?.matches(requested) == true) {
            cancelPendingPlayback()
            return
        }
        playJob?.cancel()
        pendingPlayback.value = requested
        playJob = viewModelScope.launch {
            try {
                val queue = queueResolver.resolve(tracks.drop(startIndex))
                controller.play(queue, startIndex = 0, origin = origin)
            } finally {
                pendingPlayback.update { pending ->
                    pending?.takeUnless { it.matches(requested) }
                }
            }
        }
    }
    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            controller.addToQueue(queueResolver.resolve(tracks))
        }
    }
    fun playPause() {
        if (pendingPlayback.value != null) {
            cancelPendingPlayback()
            if (controller.state.value.content?.isPlaying == true) {
                controller.playPause()
            }
        } else {
            controller.playPause()
        }
    }
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun playAt(index: Int) = controller.playAt(index)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentIndex = state.value.content?.currentIndex ?: return
        if (
            fromIndex !in queueItems.indices ||
            toIndex !in queueItems.indices ||
            fromIndex <= currentIndex ||
            toIndex <= currentIndex ||
            fromIndex == toIndex
        ) {
            return
        }
        queueItems = queueItems.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        controller.moveQueueItem(fromIndex, toIndex)
    }
    fun removeQueueItem(index: Int) = removeQueueItems(setOf(index))
    fun removeQueueItems(indices: Set<Int>) {
        val currentIndex = state.value.content?.currentIndex ?: return
        queueItems = queueItems.filterIndexed { index, _ ->
            index <= currentIndex || index !in indices
        }
        controller.removeQueueItems(indices)
    }
    fun moveQueueItemsToTop(indices: Set<Int>) {
        val currentIndex = state.value.content?.currentIndex ?: return
        val selected = indices.filter { it in queueItems.indices && it > currentIndex }.sorted()
        if (selected.isEmpty()) return
        val prefix = queueItems.take(currentIndex + 1)
        val moved = selected.map(queueItems::get)
        val remaining = queueItems.drop(currentIndex + 1).filterIndexed { offset, _ ->
            currentIndex + 1 + offset !in selected
        }
        queueItems = prefix + moved + remaining
        controller.moveQueueItemsToTop(indices)
    }
    fun stopAndClear() {
        cancelPendingPlayback()
        queueItems = emptyList()
        controller.stopAndClear()
    }
    fun toggleFavorite() {
        val track = state.value.content?.currentTrack ?: return
        viewModelScope.launch {
            favoriteDelegate.toggle(FavoriteTarget.Track(track.id), track.isFavorite) {
                updateFavorite(track.id, it)
            }
        }
    }

    fun downloadCurrentTrack() {
        val track = state.value.content?.currentTrack ?: return
        viewModelScope.launch { downloadDelegate.download(track) }
    }

    private fun updateFavorite(trackId: String, isFavorite: Boolean) {
        favoriteOverrides.update { it + (trackId to isFavorite) }
    }

    private fun PlaybackSnapshot.toUiState(favoriteOverrides: Map<String, Boolean>) = PlayerUiState(
        queue = reconcileQueue(queue),
        currentIndex = currentIndex,
        currentTrack = currentTrack?.let { track ->
            favoriteOverrides[track.id]?.let { track.copy(isFavorite = it) } ?: track
        },
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        isLoading = isLoading,
        loadingTrackId = currentTrack?.id.takeIf { isLoading },
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        origin = origin,
    )

    private fun PlayerUiState.withPendingPlayback(pending: PendingPlayback?): PlayerUiState {
        if (pending == null) return this
        val track = pending.track
        return copy(
            queue = listOf(PlayerQueueItem(uiId = "pending-${track.id}", track = track)),
            currentIndex = 0,
            currentTrack = track,
            positionMs = 0,
            durationMs = track.durationMs,
            isPlaying = false,
            isLoading = true,
            loadingTrackId = track.id,
            hasPrevious = false,
            hasNext = false,
            origin = pending.origin,
        )
    }

    private fun cancelPendingPlayback() {
        playJob?.cancel()
        playJob = null
        pendingPlayback.value = null
    }

    private fun reconcileQueue(tracks: List<Track>): List<PlayerQueueItem> {
        if (
            tracks.size == queueItems.size &&
            tracks.indices.all { index -> tracks[index] == queueItems[index].track }
        ) {
            return queueItems
        }
        val unmatched = queueItems.toMutableList()
        queueItems = tracks.map { track ->
            val existingIndex = unmatched.indexOfFirst { it.track == track }
            if (existingIndex >= 0) {
                unmatched.removeAt(existingIndex)
            } else {
                PlayerQueueItem(uiId = "queue-item-${nextQueueItemId++}", track = track)
            }
        }
        return queueItems
    }

    private fun <T, R> LoadableState<T>.mapContent(transform: (T) -> R): LoadableState<R> =
        when (this) {
            is LoadableState.Content -> LoadableState.Content(transform(content))
            is LoadableState.Loading -> LoadableState.Loading(content?.let(transform))
            is LoadableState.Failure -> LoadableState.Failure(error, content?.let(transform))
        }
}

private data class PendingPlayback(
    val track: Track,
    val origin: PlaybackOrigin,
) {
    fun matches(other: PendingPlayback): Boolean =
        track.id == other.track.id && origin == other.origin
}
