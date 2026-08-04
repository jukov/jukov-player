package info.jukov.player.artist.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.artist.domain.GetArtistsUseCase
import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.auth.domain.AuthState
import info.jukov.player.artist.domain.Artist
import info.jukov.player.core.presentation.LoadableState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArtistsViewModel(
    private val getArtistsUseCase: GetArtistsUseCase,
    authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Artist>>>(
        LoadableState.Content(emptyList()),
    )
    val state: StateFlow<LoadableState<List<Artist>>> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.LoggedIn -> loadArtists()
                    AuthState.LoggedOut -> _state.value = LoadableState.Content(emptyList())
                }
            }
        }
    }

    fun retry() = loadArtists()

    private fun loadArtists() {
        if (_state.value is LoadableState.Loading) return
        viewModelScope.launch {
            _state.value = LoadableState.Loading(_state.value.content)
            getArtistsUseCase()
                .onSuccess { artists ->
                    _state.value = LoadableState.Content(artists)
                }
                .onFailure { error ->
                    _state.value = LoadableState.Failure(
                        message = error.message ?: "Не удалось загрузить исполнителей",
                        content = _state.value.content,
                    )
                }
        }
    }
}
