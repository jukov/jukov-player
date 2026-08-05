package info.jukov.player.album.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.album.domain.Album
import info.jukov.player.album.domain.GetAlbumsUseCase
import info.jukov.player.core.presentation.LoadableState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlbumsViewModel(
    private val getAlbumsUseCase: GetAlbumsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Album>>>(
        LoadableState.Content(emptyList()),
    )
    val state: StateFlow<LoadableState<List<Album>>> = _state.asStateFlow()

    private var artistId: String? = null
    private var initialized = false

    fun load(artistId: String?) {
        if (initialized && this.artistId == artistId) return
        this.artistId = artistId
        initialized = true
        _state.update { LoadableState.Content(emptyList()) }
        loadAlbums()
    }

    fun retry() = loadAlbums()

    private fun loadAlbums() {
        if (_state.value is LoadableState.Loading) return
        viewModelScope.launch {
            _state.update { LoadableState.Loading(it.content) }
            getAlbumsUseCase(artistId)
                .onSuccess { albums ->
                    _state.update { LoadableState.Content(albums) }
                }
                .onFailure { error ->
                    _state.update {
                        LoadableState.Failure(
                            message = error.message ?: "Не удалось загрузить альбомы",
                            content = it.content,
                        )
                    }
                }
        }
    }
}
