package info.jukov.player.feature.playlist.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.toAppError
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.domain.PlaylistsRepository
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaylistPickerViewModel(private val repository: PlaylistsRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistPickerState())
    val state = _state.asStateFlow()
    private val _messages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private var onSuccess: () -> Unit = {}

    init {
        viewModelScope.launch {
            repository.playlists.collect { playlists ->
                _state.update { state ->
                    state.copy(playlists = playlists.editableOnly())
                }
            }
        }
    }

    fun open(tracks: List<Track>, onSuccess: () -> Unit = {}) {
        if (tracks.isEmpty()) return
        this.onSuccess = onSuccess
        _state.value = PlaylistPickerState(
            visible = true,
            tracks = tracks,
            playlists = repository.playlists.value.editableOnly(),
        )
        load()
    }

    fun dismiss() {
        if (!_state.value.pending) {
            _state.value = PlaylistPickerState()
        }
    }

    fun showCreate() {
        _state.update { it.copy(creating = true) }
    }

    fun hideCreate() {
        if (!_state.value.pending) {
            _state.update { it.copy(creating = false) }
        }
    }

    private fun load() = viewModelScope.launch {
        repository.loadPlaylists()
    }

    private fun LoadableState<List<Playlist>>.editableOnly(): LoadableState<List<Playlist>> =
        when (this) {
            is LoadableState.Content -> LoadableState.Content(content.filter(repository::isEditable))
            is LoadableState.Loading -> LoadableState.Loading(
                content?.filter(repository::isEditable),
            )
            is LoadableState.Failure -> LoadableState.Failure(
                error,
                content?.filter(repository::isEditable),
            )
        }

    fun addTo(playlist: Playlist) = submit {
        repository.addTracks(playlist.id, _state.value.tracks.map(Track::id))
    }

    fun create(name: String, isPublic: Boolean) = submit {
        repository.createPlaylist(
            name = name.trim(),
            isPublic = isPublic,
            songIds = _state.value.tracks.map(Track::id),
        )
    }

    private fun submit(action: suspend () -> Result<Unit>) = viewModelScope.launch {
        _state.update { it.copy(pending = true) }
        action()
            .onSuccess {
                onSuccess()
                onSuccess = {}
                _state.value = PlaylistPickerState()
            }
            .onFailure {
                _messages.tryEmit(it.toAppError(AppError.PlaylistUpdateFailed))
                _state.update { state -> state.copy(pending = false) }
            }
    }
}
