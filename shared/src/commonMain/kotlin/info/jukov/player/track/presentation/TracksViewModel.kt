package info.jukov.player.track.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.track.domain.GetTracksUseCase
import info.jukov.player.track.domain.Track
import info.jukov.player.track.domain.TracksFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TracksViewModel(
    private val getTracksUseCase: GetTracksUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Track>>>(
        LoadableState.Content(emptyList()),
    )
    val state: StateFlow<LoadableState<List<Track>>> = _state.asStateFlow()

    private var filter: TracksFilter? = null
    private var loadJob: Job? = null

    fun load(filter: TracksFilter) {
        if (this.filter == filter) return
        loadJob?.cancel()
        this.filter = filter
        _state.value = LoadableState.Content(emptyList())
        loadTracks()
    }

    fun retry() = loadTracks()

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
                            message = error.message ?: "Не удалось загрузить треки",
                            content = it.content,
                        )
                    }
                }
        }
    }
}
