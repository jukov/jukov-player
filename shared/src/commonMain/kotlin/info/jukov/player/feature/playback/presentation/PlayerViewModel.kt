package info.jukov.player.feature.playback.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PlayerUiState(
    val currentTrack: Track? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

class PlayerViewModel(
    private val controller: PlaybackController,
) : ViewModel() {
    val state: StateFlow<LoadableState<PlayerUiState>> = controller.state
        .map { loadable -> loadable.mapContent { it.toUiState() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = controller.state.value.mapContent { it.toUiState() },
        )

    fun play(tracks: List<Track>, startIndex: Int) = controller.play(tracks, startIndex)
    fun playPause() = controller.playPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun stopAndClear() = controller.stopAndClear()

    private fun PlaybackSnapshot.toUiState() = PlayerUiState(
        currentTrack = currentTrack,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
    )

    private fun <T, R> LoadableState<T>.mapContent(transform: (T) -> R): LoadableState<R> =
        when (this) {
            is LoadableState.Content -> LoadableState.Content(transform(content))
            is LoadableState.Loading -> LoadableState.Loading(content?.let(transform))
            is LoadableState.Failure -> LoadableState.Failure(message, content?.let(transform))
        }
}
