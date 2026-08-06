package info.jukov.player.feature.artist.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.artist.domain.GetArtistsUseCase
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.updateItem
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArtistsViewModel(
    private val getArtistsUseCase: GetArtistsUseCase,
    authRepository: AuthRepository,
    private val favoriteDelegate: FavoriteDelegate,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Artist>>>(
        LoadableState.Content(emptyList()),
    )
    val state: StateFlow<LoadableState<List<Artist>>> = _state.asStateFlow()
    val pending = favoriteDelegate.pending
    val messages = favoriteDelegate.messages

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.LoggedIn -> loadArtists()
                    AuthState.LoggedOut -> _state.update {
                        LoadableState.Content(emptyList())
                    }
                }
            }
        }
        viewModelScope.launch {
            favoriteDelegate.changes.collect { change ->
                val target = change.target as? FavoriteTarget.Artist ?: return@collect
                updateFavorite(target.id, change.isFavorite)
            }
        }
    }

    fun retry() = loadArtists()

    fun toggleFavorite(artist: Artist) {
        viewModelScope.launch {
            favoriteDelegate.toggle(FavoriteTarget.Artist(artist.id), artist.isFavorite) {
                updateFavorite(artist.id, it)
            }
        }
    }

    private fun updateFavorite(id: String, isFavorite: Boolean) {
        _state.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
    }

    private fun loadArtists() {
        if (_state.value is LoadableState.Loading) return
        viewModelScope.launch {
            _state.update { LoadableState.Loading(it.content) }
            getArtistsUseCase()
                .onSuccess { artists ->
                    _state.update { LoadableState.Content(artists) }
                }
                .onFailure { error ->
                    _state.update {
                        LoadableState.Failure(
                            message = error.message ?: "Не удалось загрузить исполнителей",
                            content = it.content,
                        )
                    }
                }
        }
    }
}
