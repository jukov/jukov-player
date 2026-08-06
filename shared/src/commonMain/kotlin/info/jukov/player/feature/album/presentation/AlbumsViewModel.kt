package info.jukov.player.feature.album.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.album.domain.GetAlbumsUseCase
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
import info.jukov.player.core.domain.toAppError

class AlbumsViewModel(
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val favoriteDelegate: FavoriteDelegate,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Album>>>(
        LoadableState.Loading(content = null),
    )
    val state: StateFlow<LoadableState<List<Album>>> = _state.asStateFlow()
    private val _loadingOrigin = MutableStateFlow<LoadingOrigin?>(LoadingOrigin.Initial)
    val loadingOrigin: StateFlow<LoadingOrigin?> = _loadingOrigin.asStateFlow()
    val pending = favoriteDelegate.pending
    val messages = favoriteDelegate.messages
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var artistId: String? = null
    private var initialized = false
    private var loadJob: Job? = null

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
        _hasMore.value = false
        _state.value = LoadableState.Loading(content = null)
        _loadingOrigin.value = LoadingOrigin.Initial
        if (artistId == null) loadPage(forceRefresh = false) else loadAlbums(forceRefresh = false)
    }

    fun retry() {
        if (artistId == null) {
            if (_state.value.content.isNullOrEmpty()) loadPage(forceRefresh = true) else loadMore()
        } else loadAlbums(forceRefresh = true)
    }

    fun refresh() {
        if (artistId == null) loadPage(forceRefresh = true, requestedOrigin = LoadingOrigin.PullToRefresh)
        else loadAlbums(forceRefresh = true, requestedOrigin = LoadingOrigin.PullToRefresh)
    }

    fun loadMore() {
        if (artistId == null && _hasMore.value && loadJob?.isActive != true) {
            loadPage(forceRefresh = false, append = true, requestedOrigin = LoadingOrigin.Pagination)
        }
    }

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

    private fun loadAlbums(forceRefresh: Boolean, requestedOrigin: LoadingOrigin? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            getAlbumsUseCase(artistId, forceRefresh).collect { state ->
                _state.value = state
                _loadingOrigin.value = when (state) {
                    is LoadableState.Loading -> requestedOrigin
                        ?: if (state.content == null) LoadingOrigin.Initial else LoadingOrigin.Automatic
                    else -> null
                }
            }
        }
    }

    private fun loadPage(
        forceRefresh: Boolean,
        append: Boolean = false,
        requestedOrigin: LoadingOrigin? = null,
    ) {
        loadJob?.cancel()
        val displayed = _state.value.content.orEmpty()
        val previous = if (append) displayed else emptyList()
        loadJob = viewModelScope.launch {
            _state.value = LoadableState.Loading(displayed.ifEmpty { null })
            _loadingOrigin.value = requestedOrigin ?: LoadingOrigin.Initial
            try {
                val page = getAlbumsUseCase.page(previous.size, PAGE_SIZE, forceRefresh)
                _hasMore.value = page.hasMore
                _state.value = LoadableState.Content(previous + page.items)
            } catch (error: Throwable) {
                _state.value = LoadableState.Failure(
                    error.toAppError(AppError.AlbumsLoadFailed),
                    displayed.ifEmpty { null },
                )
            } finally {
                _loadingOrigin.value = null
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
