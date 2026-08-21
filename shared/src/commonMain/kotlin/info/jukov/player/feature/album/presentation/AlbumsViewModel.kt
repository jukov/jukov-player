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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import info.jukov.player.core.presentation.LoadingOrigin
import info.jukov.player.core.domain.toAppError
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import info.jukov.player.feature.search.domain.SearchUseCase
import info.jukov.player.feature.search.presentation.PagedSearchDelegate
import info.jukov.player.core.domain.AlbumSortCriterion
import info.jukov.player.core.domain.SortDirection
import info.jukov.player.core.domain.SortOption
import info.jukov.player.core.domain.SortPreferences
import info.jukov.player.core.domain.sortedAlbums
import info.jukov.player.core.domain.mapContent
import info.jukov.player.core.domain.supportedForGlobalAlbums
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext

class AlbumsViewModel(
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val favoriteDelegate: FavoriteDelegate,
    private val downloadDelegate: DownloadDelegate,
    private val getTracksUseCase: GetTracksUseCase,
    search: SearchUseCase,
    private val sortPreferences: SortPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Album>>>(
        LoadableState.Loading(content = null),
    )
    val state: StateFlow<LoadableState<List<Album>>> = _state.asStateFlow()
    private val _loadingOrigin = MutableStateFlow<LoadingOrigin?>(LoadingOrigin.Initial)
    val loadingOrigin: StateFlow<LoadingOrigin?> = _loadingOrigin.asStateFlow()
    val pending = favoriteDelegate.pending
    private val _downloadMessages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = merge(favoriteDelegate.messages, _downloadMessages)
    val artworkUris = downloadDelegate.artworkUris
    val downloadStatuses = downloadDelegate.albumStatuses
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    private val searchDelegate = PagedSearchDelegate<Album>(viewModelScope) { query, offset, size ->
        search.albums(query, offset, size, artistId)
    }
    val searchActive = searchDelegate.active
    val searchQuery = searchDelegate.query
    val searchState = searchDelegate.state
    val searchHasMore = searchDelegate.hasMore
    private val _sort = MutableStateFlow(sortPreferences.albums)
    val sort = _sort.asStateFlow()

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
        if (initialized && this.artistId == artistId) {
            return
        }
        closeSearch()
        this.artistId = artistId
        if (artistId == null) {
            val supportedSort = _sort.value.supportedForGlobalAlbums()
            if (supportedSort != _sort.value) {
                sortPreferences.albums = supportedSort
                _sort.value = supportedSort
            }
        }
        initialized = true
        _hasMore.value = false
        _state.value = LoadableState.Loading(content = null)
        _loadingOrigin.value = LoadingOrigin.Initial
        loadCurrent(forceRefresh = false)
    }

    fun retry() {
        if (artistId == null) {
            if (_state.value.content.isNullOrEmpty()) {
                loadPage(forceRefresh = true)
            } else {
                loadMore()
            }
        } else {
            loadAlbums(forceRefresh = true)
        }
    }

    fun refresh() = loadCurrent(
        forceRefresh = true,
        requestedOrigin = LoadingOrigin.PullToRefresh,
    )

    fun loadMore() {
        if (artistId == null && _hasMore.value && loadJob?.isActive != true) {
            loadPage(forceRefresh = false, append = true, requestedOrigin = LoadingOrigin.Pagination)
        }
    }

    fun openSearch() = searchDelegate.open()
    fun updateSearchQuery(value: String) = searchDelegate.updateQuery(value)
    fun loadMoreSearch() = searchDelegate.loadMore()
    fun retrySearch() = searchDelegate.retry()
    fun closeSearch() = searchDelegate.close()
    fun updateSort(value: SortOption<AlbumSortCriterion>) {
        if (artistId == null && value.criterion != AlbumSortCriterion.Year && value.direction == SortDirection.Descending) {
            return
        }
        sortPreferences.albums = value
        _sort.value = value
        if (artistId == null) {
            _hasMore.value = false
            loadPage(forceRefresh = false, requestedOrigin = LoadingOrigin.Sorting)
        } else {
            _state.update { it.mapContent { albums -> albums.sortedAlbums(value) } }
        }
    }

    fun toggleFavorite(album: Album) {
        viewModelScope.launch {
            favoriteDelegate.toggle(FavoriteTarget.Album(album.id), album.isFavorite) {
                updateFavorite(album.id, it)
            }
        }
    }

    fun toggleFavorites(albums: List<Album>) = viewModelScope.launch {
        val desired = albums.any { !it.isFavorite }
        favoriteDelegate.setAlbums(albums, desired) { album, isFavorite ->
            updateFavorite(album.id, isFavorite)
        }
    }

    fun downloadAlbums(albums: List<Album>) = viewModelScope.launch {
        albums.forEach { album ->
            downloadDelegate.download(album).onFailure { error ->
                _downloadMessages.tryEmit(error.toAppError(AppError.DownloadFailed))
            }
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

    private fun updateFavorite(id: String, isFavorite: Boolean) {
        _state.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
        searchDelegate.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
    }

    private fun loadAlbums(forceRefresh: Boolean, requestedOrigin: LoadingOrigin? = null) {
        replaceLoadJob {
            getAlbumsUseCase(artistId, forceRefresh).collect { state ->
                publish(state, requestedOrigin)
            }
        }
    }

    private fun loadPage(
        forceRefresh: Boolean,
        append: Boolean = false,
        requestedOrigin: LoadingOrigin? = null,
    ) {
        val displayed = _state.value.content.orEmpty()
        val offset = if (append) {
            displayed.size
        } else {
            0
        }
        replaceLoadJob {
            _state.update { current ->
                LoadableState.Loading(current.content?.ifEmpty { null })
            }
            _loadingOrigin.value = requestedOrigin ?: LoadingOrigin.Initial
            try {
                val page = getAlbumsUseCase.page(offset, PAGE_SIZE, _sort.value, forceRefresh)
                _hasMore.value = page.hasMore
                _state.update { current ->
                    val content = if (append) {
                        current.content.orEmpty() + page.items
                    } else {
                        page.items
                    }
                    LoadableState.Content(content)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update { current ->
                    LoadableState.Failure(
                        error.toAppError(AppError.AlbumsLoadFailed),
                        current.content?.ifEmpty { null },
                    )
                }
            } finally {
                if (loadJob === currentCoroutineContext()[Job]) {
                    _loadingOrigin.value = null
                }
            }
        }
    }

    private fun loadCurrent(
        forceRefresh: Boolean,
        requestedOrigin: LoadingOrigin? = null,
    ) {
        if (artistId == null) {
            loadPage(forceRefresh, requestedOrigin = requestedOrigin)
        } else {
            loadAlbums(forceRefresh, requestedOrigin)
        }
    }

    private fun replaceLoadJob(block: suspend () -> Unit) {
        loadJob?.cancel()
        val replacement = viewModelScope.launch(start = CoroutineStart.LAZY) { block() }
        loadJob = replacement
        replacement.start()
    }

    private fun publish(
        state: LoadableState<List<Album>>,
        requestedOrigin: LoadingOrigin?,
    ) {
        _state.update { current ->
            state.withFallbackContent(current.content).mapContent { albums ->
                albums.sortedAlbums(_sort.value)
            }
        }
        _loadingOrigin.value = _state.value.loadingOrigin(requestedOrigin)
    }

    private fun LoadableState<List<Album>>.withFallbackContent(
        fallback: List<Album>?,
    ): LoadableState<List<Album>> = when (this) {
        is LoadableState.Content -> this
        is LoadableState.Loading -> LoadableState.Loading(content ?: fallback)
        is LoadableState.Failure -> LoadableState.Failure(error, content ?: fallback)
    }

    private fun LoadableState<List<Album>>.loadingOrigin(
        requestedOrigin: LoadingOrigin?,
    ): LoadingOrigin? = when (this) {
        is LoadableState.Loading -> requestedOrigin ?: if (content == null) {
            LoadingOrigin.Initial
        } else {
            LoadingOrigin.Automatic
        }

        else -> null
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
