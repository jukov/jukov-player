package info.jukov.player.feature.album.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.album.domain.GetAlbumsUseCase
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.updateItem
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlbumsViewModel(
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val favoriteDelegate: FavoriteDelegate,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Album>>>(
        LoadableState.Content(emptyList()),
    )
    val state: StateFlow<LoadableState<List<Album>>> = _state.asStateFlow()
    val pending = favoriteDelegate.pending
    val messages = favoriteDelegate.messages

    private var artistId: String? = null
    private var initialized = false

    init {
        viewModelScope.launch {
            favoriteDelegate.changes.collect { change ->
                val target = change.target as? FavoriteTarget.Album ?: return@collect
                updateFavorite(target.id, change.isFavorite)
            }
        }
    }

    fun load(artistId: String?) {
        if (initialized && this.artistId == artistId) return
        this.artistId = artistId
        initialized = true
        _state.update { LoadableState.Content(emptyList()) }
        loadAlbums()
    }

    fun retry() = loadAlbums()

    fun toggleFavorite(album: Album) {
        viewModelScope.launch {
            favoriteDelegate.toggle(FavoriteTarget.Album(album.id), album.isFavorite) {
                updateFavorite(album.id, it)
            }
        }
    }

    private fun updateFavorite(id: String, isFavorite: Boolean) {
        _state.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
    }

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
