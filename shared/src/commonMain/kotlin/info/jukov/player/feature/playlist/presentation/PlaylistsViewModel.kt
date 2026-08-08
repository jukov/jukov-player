package info.jukov.player.feature.playlist.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.toAppError
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.domain.PlaylistsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class PlaylistsViewModel(private val repository: PlaylistsRepository) : ViewModel() {
    val state = repository.playlists
    private val _messages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    fun isEditable(playlist: Playlist) = repository.isEditable(playlist)

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.loadPlaylists(forceRefresh)
        }
    }

    fun create(name: String, onCreated: () -> Unit = {}) = viewModelScope.launch {
        repository.createPlaylist(name.trim())
            .onSuccess {
                onCreated()
            }
            .onFailure { _messages.tryEmit(it.toAppError(AppError.PlaylistUpdateFailed)) }
    }
}
