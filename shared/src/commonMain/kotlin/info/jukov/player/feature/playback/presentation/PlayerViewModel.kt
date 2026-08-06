package info.jukov.player.feature.playback.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val favoriteDelegate: FavoriteDelegate,
) : ViewModel() {
    private val favoriteOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val state: StateFlow<LoadableState<PlayerUiState>> = combine(
        controller.state,
        favoriteOverrides,
    ) { loadable, overrides ->
        loadable.mapContent { it.toUiState(overrides) }
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

    fun play(tracks: List<Track>, startIndex: Int) = controller.play(tracks, startIndex)
    fun playPause() = controller.playPause()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun stopAndClear() = controller.stopAndClear()
    fun toggleFavorite() {
        val track = state.value.content?.currentTrack ?: return
        viewModelScope.launch {
            favoriteDelegate.toggle(FavoriteTarget.Track(track.id), track.isFavorite) {
                updateFavorite(track.id, it)
            }
        }
    }

    private fun updateFavorite(trackId: String, isFavorite: Boolean) {
        favoriteOverrides.update { it + (trackId to isFavorite) }
    }

    private fun PlaybackSnapshot.toUiState(favoriteOverrides: Map<String, Boolean>) = PlayerUiState(
        currentTrack = currentTrack?.let { track ->
            favoriteOverrides[track.id]?.let { track.copy(isFavorite = it) } ?: track
        },
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
            is LoadableState.Failure -> LoadableState.Failure(error, content?.let(transform))
        }
}
