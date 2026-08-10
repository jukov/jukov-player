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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import info.jukov.player.core.domain.LoadableState

class PlaylistsViewModel(private val repository: PlaylistsRepository) : ViewModel() {
    private val _searchActive = MutableStateFlow(false)
    val searchActive = _searchActive.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    val state = combine(repository.playlists, _searchActive, _searchQuery) { source, active, query ->
        if (!active || query.isBlank()) source else when (source) {
            is LoadableState.Content -> LoadableState.Content(source.content.filter { it.name.contains(query.trim(), ignoreCase = true) })
            is LoadableState.Loading -> LoadableState.Loading(source.content?.filter { it.name.contains(query.trim(), ignoreCase = true) })
            is LoadableState.Failure -> LoadableState.Failure(source.error, source.content?.filter { it.name.contains(query.trim(), ignoreCase = true) })
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repository.playlists.value)
    private val _messages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private val _pending = MutableStateFlow(false)
    val pending = _pending.asStateFlow()
    fun isEditable(playlist: Playlist) = repository.isEditable(playlist)
    fun openSearch() { _searchActive.value = true }
    fun updateSearchQuery(value: String) { _searchQuery.value = value }
    fun closeSearch() { _searchActive.value = false; _searchQuery.value = "" }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.loadPlaylists(forceRefresh)
        }
    }

    fun create(name: String, isPublic: Boolean, onCreated: () -> Unit = {}) = viewModelScope.launch {
        _pending.value = true
        repository.createPlaylist(name.trim(), isPublic)
            .onSuccess {
                onCreated()
            }
            .onFailure { _messages.tryEmit(it.toAppError(AppError.PlaylistUpdateFailed)) }
        _pending.value = false
    }
}
