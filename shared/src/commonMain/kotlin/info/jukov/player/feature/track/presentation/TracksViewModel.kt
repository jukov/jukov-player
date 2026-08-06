package info.jukov.player.feature.track.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.toAppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.updateItem
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import info.jukov.player.core.presentation.LoadingOrigin
import kotlinx.coroutines.launch

class TracksViewModel(
    private val getTracksUseCase: GetTracksUseCase,
    private val favoriteDelegate: FavoriteDelegate,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Track>>>(
        LoadableState.Loading(content = null),
    )
    val state: StateFlow<LoadableState<List<Track>>> = _state.asStateFlow()
    private val _loadingOrigin = MutableStateFlow<LoadingOrigin?>(LoadingOrigin.Initial)
    val loadingOrigin: StateFlow<LoadingOrigin?> = _loadingOrigin.asStateFlow()
    private val _albumIsFavorite = MutableStateFlow(false)
    val albumIsFavorite: StateFlow<Boolean> = _albumIsFavorite.asStateFlow()
    val pending = favoriteDelegate.pending
    val messages = favoriteDelegate.messages

    private var filter: TracksFilter? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            favoriteDelegate.changes.collect { change ->
                when (val target = change.target) {
                    is FavoriteTarget.Track -> updateFavorite(target.id, change.isFavorite)
                    is FavoriteTarget.Album -> if (
                        (filter as? TracksFilter.ByAlbum)?.albumId == target.id
                    ) {
                        _albumIsFavorite.value = change.isFavorite
                    }
                    is FavoriteTarget.Artist -> Unit
                }
            }
        }
    }

    fun load(filter: TracksFilter, albumIsFavorite: Boolean = false) {
        if (this.filter == filter) return
        loadJob?.cancel()
        this.filter = filter
        _albumIsFavorite.value = albumIsFavorite
        _state.value = LoadableState.Loading(content = null)
        _loadingOrigin.value = LoadingOrigin.Initial
        loadTracks(forceRefresh = false)
    }

    fun retry() = loadTracks(forceRefresh = true)
    fun refresh() = loadTracks(forceRefresh = true, requestedOrigin = LoadingOrigin.PullToRefresh)

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            favoriteDelegate.toggle(FavoriteTarget.Track(track.id), track.isFavorite) {
                updateFavorite(track.id, it)
            }
        }
    }

    fun toggleAlbumFavorite(albumId: String) {
        viewModelScope.launch {
            favoriteDelegate.toggle(FavoriteTarget.Album(albumId), _albumIsFavorite.value) {
                _albumIsFavorite.value = it
            }
        }
    }

    private fun updateFavorite(id: String, isFavorite: Boolean) {
        _state.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
    }

    private fun loadTracks(forceRefresh: Boolean, requestedOrigin: LoadingOrigin? = null) {
        val currentFilter = filter ?: return
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            getTracksUseCase(currentFilter, forceRefresh).collect { state ->
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
