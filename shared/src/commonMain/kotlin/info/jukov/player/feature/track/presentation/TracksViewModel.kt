package info.jukov.player.feature.track.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.toAppError
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.updateItem
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TracksViewModel(
    private val getTracksUseCase: GetTracksUseCase,
    private val favoriteDelegate: FavoriteDelegate,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Track>>>(
        LoadableState.Content(emptyList()),
    )
    val state: StateFlow<LoadableState<List<Track>>> = _state.asStateFlow()
    val pending = favoriteDelegate.pending
    val messages = favoriteDelegate.messages

    private var filter: TracksFilter? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            favoriteDelegate.changes.collect { change ->
                val target = change.target as? FavoriteTarget.Track ?: return@collect
                updateFavorite(target.id, change.isFavorite)
            }
        }
    }

    fun load(filter: TracksFilter) {
        if (this.filter == filter) return
        loadJob?.cancel()
        this.filter = filter
        _state.value = LoadableState.Content(emptyList())
        loadTracks()
    }

    fun retry() = loadTracks()

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            favoriteDelegate.toggle(FavoriteTarget.Track(track.id), track.isFavorite) {
                updateFavorite(track.id, it)
            }
        }
    }

    private fun updateFavorite(id: String, isFavorite: Boolean) {
        _state.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
    }

    private fun loadTracks() {
        val currentFilter = filter ?: return
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update { LoadableState.Loading(it.content) }
            getTracksUseCase(currentFilter)
                .onSuccess { tracks -> _state.value = LoadableState.Content(tracks) }
                .onFailure { error ->
                    _state.update {
                        LoadableState.Failure(
                            error = error.toAppError(AppError.TracksLoadFailed),
                            content = it.content,
                        )
                    }
                }
        }
    }
}
