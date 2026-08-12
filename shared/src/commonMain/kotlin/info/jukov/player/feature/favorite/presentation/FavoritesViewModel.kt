package info.jukov.player.feature.favorite.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.toAppError
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.mapContent
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.favoriteStateForSelection
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import info.jukov.player.core.presentation.LoadingOrigin

enum class FavoritesTab { Tracks, Albums, Artists }

class FavoritesViewModel(
    private val repository: FavoritesRepository,
    private val downloadDelegate: DownloadDelegate,
    private val getTracksUseCase: GetTracksUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<Favorites>>(
        LoadableState.Loading(content = null),
    )
    val state = _state.asStateFlow()
    private val _loadingOrigin = MutableStateFlow<LoadingOrigin?>(LoadingOrigin.Initial)
    val loadingOrigin = _loadingOrigin.asStateFlow()
    private val _selectedTab = MutableStateFlow(FavoritesTab.Tracks)
    val selectedTab = _selectedTab.asStateFlow()
    private val _pending = MutableStateFlow<Set<FavoriteTarget>>(emptySet())
    val pending = _pending.asStateFlow()
    private val _messages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    val downloadStatuses = downloadDelegate.trackStatuses
    val artworkUris = downloadDelegate.artworkUris
    private var initialized = false
    private var loadJob: Job? = null
    private var manualTrackOrder: List<String>? = null

    fun load() {
        if (initialized) return
        initialized = true
        loadFavorites(forceRefresh = false)
    }

    fun selectTab(tab: FavoritesTab) { _selectedTab.value = tab }

    fun refresh() {
        loadFavorites(forceRefresh = true, requestedOrigin = LoadingOrigin.PullToRefresh)
    }

    fun retry() = loadFavorites(forceRefresh = true)

    private fun loadFavorites(forceRefresh: Boolean, requestedOrigin: LoadingOrigin? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.getFavorites(forceRefresh).collect { state ->
                _state.value = state.mapContent { favorites ->
                    val order = manualTrackOrder
                    if (order == null) {
                        favorites
                    } else {
                        val rank = order.withIndex().associate { it.value to it.index }
                        favorites.copy(tracks = favorites.tracks.sortedBy { rank[it.id] ?: Int.MAX_VALUE })
                    }
                }
                _loadingOrigin.value = when (state) {
                    is LoadableState.Loading -> requestedOrigin
                        ?: if (state.content == null) LoadingOrigin.Initial else LoadingOrigin.Automatic
                    else -> null
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
                    _messages.tryEmit(error.toAppError(AppError.FavoriteUpdateFailed))
                }
            _pending.update { it - target }
        }
    }

    fun downloadTrack(track: Track) = viewModelScope.launch { download(track) }
    fun moveTrack(from: Int, to: Int) {
        _state.update { current ->
            val favorites = current.content ?: return@update current
            if (from !in favorites.tracks.indices || to !in favorites.tracks.indices) {
                return@update current
            }
            val tracks = favorites.tracks.toMutableList().apply { add(to, removeAt(from)) }
            manualTrackOrder = tracks.map(Track::id)
            current.mapContent { it.copy(tracks = tracks) }
        }
    }
    fun cancelTrackDownload(id: String) = viewModelScope.launch { downloadDelegate.cancelTrack(id) }
    fun retryTrackDownload(id: String) = viewModelScope.launch { downloadDelegate.retry(id) }
    fun toggleFavorites(tracks: List<Track>) = viewModelScope.launch {
        val desired = favoriteStateForSelection(tracks)
        val targets = tracks.filter { it.isFavorite != desired }
            .map { FavoriteTarget.Track(it.id) }
        if (targets.isEmpty()) {
            return@launch
        }
        _pending.update { it + targets }
        repository.setFavorites(targets, desired)
            .onSuccess {
                _state.update { current ->
                    LoadableState.Content(
                        targets.fold(current.content ?: Favorites()) { favorites, target ->
                            favorites.updateFavorite(target, desired)
                        },
                    )
                }
            }
            .onFailure { error ->
                _messages.tryEmit(error.toAppError(AppError.FavoriteUpdateFailed))
            }
        _pending.update { it - targets.toSet() }
    }
    fun downloadTracks(tracks: List<Track>) = viewModelScope.launch {
        val statuses = downloadStatuses.value
        tracks.forEach { track ->
            when (statuses[track.id]?.state) {
                null -> download(track)
                info.jukov.player.feature.download.domain.DownloadState.Failed -> downloadDelegate.retry(track.id)
                else -> Unit
            }
        }
    }

    fun toggleFavoriteAlbums(albums: List<Album>) = viewModelScope.launch {
        val desired = albums.any { !it.isFavorite }
        val targets = albums.filter { it.isFavorite != desired }
            .map { FavoriteTarget.Album(it.id) }
        if (targets.isEmpty()) {
            return@launch
        }
        _pending.update { it + targets }
        repository.setFavorites(targets, desired)
            .onSuccess {
                _state.update { current ->
                    LoadableState.Content(
                        targets.fold(current.content ?: Favorites()) { favorites, target ->
                            favorites.updateFavorite(target, desired)
                        },
                    )
                }
            }
            .onFailure { error ->
                _messages.tryEmit(error.toAppError(AppError.FavoriteUpdateFailed))
            }
        _pending.update { it - targets.toSet() }
    }

    fun downloadAlbums(albums: List<Album>) = viewModelScope.launch {
        albums.forEach { album -> download(album) }
    }

    private suspend fun download(track: Track) {
        downloadDelegate.download(track).onFailure { error ->
            _messages.tryEmit(error.toAppError(AppError.DownloadFailed))
        }
    }

    private suspend fun download(album: Album) {
        downloadDelegate.download(album).onFailure { error ->
            _messages.tryEmit(error.toAppError(AppError.DownloadFailed))
        }
    }

    fun addAlbumsToQueue(albums: List<Album>, onTracksReady: (List<Track>) -> Unit) =
        viewModelScope.launch {
            val tracks = albums.flatMap { album ->
                getTracksUseCase(TracksFilter.ByAlbum(album.id))
                    .first { it !is LoadableState.Loading }
                    .content.orEmpty()
            }
            onTracksReady(tracks)
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
