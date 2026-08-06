package info.jukov.player.feature.artist.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.toAppError
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.artist.domain.GetArtistsUseCase
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.updateItem
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import info.jukov.player.core.presentation.LoadingOrigin

class ArtistsViewModel(
    private val getArtistsUseCase: GetArtistsUseCase,
    authRepository: AuthRepository,
    private val favoriteDelegate: FavoriteDelegate,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Artist>>>(
        LoadableState.Loading(content = null),
    )
    val state: StateFlow<LoadableState<List<Artist>>> = _state.asStateFlow()
    private val _loadingOrigin = MutableStateFlow<LoadingOrigin?>(LoadingOrigin.Initial)
    val loadingOrigin: StateFlow<LoadingOrigin?> = _loadingOrigin.asStateFlow()
    val pending = favoriteDelegate.pending
    val messages = favoriteDelegate.messages
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.LoggedIn -> loadArtists(forceRefresh = false)
                    AuthState.LoggedOut -> _state.update {
                        loadJob?.cancel()
                        _loadingOrigin.value = null
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

    fun retry() = loadArtists(forceRefresh = true)
    fun refresh() = loadArtists(forceRefresh = true, requestedOrigin = LoadingOrigin.PullToRefresh)

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

    private fun loadArtists(forceRefresh: Boolean, requestedOrigin: LoadingOrigin? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            getArtistsUseCase(forceRefresh).collect { state ->
                _state.value = state
                _loadingOrigin.value = when (state) {
                    is LoadableState.Loading -> requestedOrigin
                        ?: if (state.content == null) LoadingOrigin.Initial else LoadingOrigin.Automatic
                    else -> null
                }
            }
        }
    }
}
