package info.jukov.player.feature.favorite.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FavoritesTab { Tracks, Albums, Artists }

class FavoritesViewModel(private val repository: FavoritesRepository) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<Favorites>>(LoadableState.Loading())
    val state = _state.asStateFlow()
    private val _selectedTab = MutableStateFlow(FavoritesTab.Tracks)
    val selectedTab = _selectedTab.asStateFlow()
    private val _pending = MutableStateFlow<Set<FavoriteTarget>>(emptySet())
    val pending = _pending.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private var initialized = false

    fun load() {
        if (initialized) return
        initialized = true
        loadFavorites()
    }

    fun selectTab(tab: FavoritesTab) { _selectedTab.value = tab }

    fun refresh() {
        if (_state.value is LoadableState.Loading) return
        loadFavorites()
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            _state.update { LoadableState.Loading(it.content) }
            repository.getFavorites()
                .onSuccess { _state.value = LoadableState.Content(it) }
                .onFailure { error ->
                    _state.update {
                        LoadableState.Failure(
                            error.message ?: "Не удалось загрузить избранное",
                            it.content,
                        )
                    }
                }
        }
    }

    fun toggleFavorite(target: FavoriteTarget, isFavorite: Boolean) {
        if (target in _pending.value) return
        if (_state.value.content == null) return
        val desired = !isFavorite
        _pending.update { it + target }
        viewModelScope.launch {
            repository.setFavorite(target, desired)
                .onSuccess {
                    _state.update { current ->
                        LoadableState.Content(
                            (current.content ?: Favorites()).updateFavorite(target, desired),
                        )
                    }
                }
                .onFailure { error ->
                    _messages.tryEmit(error.message ?: "Не удалось изменить избранное")
                }
            _pending.update { it - target }
        }
    }

    private fun Favorites.updateFavorite(target: FavoriteTarget, isFavorite: Boolean) = when (target) {
        is FavoriteTarget.Track -> copy(
            tracks = tracks.map {
                if (it.id == target.id) it.copy(isFavorite = isFavorite) else it
            },
        )
        is FavoriteTarget.Album -> copy(
            albums = albums.map {
                if (it.id == target.id) it.copy(isFavorite = isFavorite) else it
            },
        )
        is FavoriteTarget.Artist -> copy(
            artists = artists.map {
                if (it.id == target.id) it.copy(isFavorite = isFavorite) else it
            },
        )
    }
}
