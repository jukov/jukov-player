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
import info.jukov.player.feature.favorite.domain.favoriteStateForSelection
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.Job
import info.jukov.player.core.presentation.LoadingOrigin
import kotlinx.coroutines.launch
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.search.domain.SearchUseCase
import info.jukov.player.feature.search.presentation.PagedSearchDelegate
import info.jukov.player.core.domain.SortOption
import info.jukov.player.core.domain.SortPreferences
import info.jukov.player.core.domain.TrackSortCriterion
import info.jukov.player.core.domain.sortedTracks
import info.jukov.player.core.domain.mapContent

class TracksViewModel(
    private val getTracksUseCase: GetTracksUseCase,
    private val favoriteDelegate: FavoriteDelegate,
    private val downloadDelegate: DownloadDelegate,
    search: SearchUseCase,
    private val sortPreferences: SortPreferences,
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
    private val _downloadMessages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = merge(favoriteDelegate.messages, _downloadMessages)
    val downloadStatuses = downloadDelegate.trackStatuses
    val albumDownloadStatuses = downloadDelegate.albumStatuses
    val artworkUris = downloadDelegate.artworkUris
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    private val searchDelegate = PagedSearchDelegate<Track>(viewModelScope) { query, offset, size ->
        search.tracks(query, offset, size, (filter as? TracksFilter.ByArtist)?.artistId)
    }
    val searchActive = searchDelegate.active
    val searchQuery = searchDelegate.query
    val searchState = searchDelegate.state
    val searchHasMore = searchDelegate.hasMore
    private val _sort = MutableStateFlow(sortPreferences.artistTracks)
    val sort = _sort.asStateFlow()

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
        closeSearch()
        loadJob?.cancel()
        this.filter = filter
        _hasMore.value = false
        _albumIsFavorite.value = albumIsFavorite
        _state.value = LoadableState.Loading(content = null)
        _loadingOrigin.value = LoadingOrigin.Initial
        if (filter == TracksFilter.All) loadPage(forceRefresh = false) else loadTracks(forceRefresh = false)
    }

    fun retry() {
        if (filter == TracksFilter.All) {
            if (_state.value.content.isNullOrEmpty()) loadPage(forceRefresh = true) else loadMore()
        } else loadTracks(forceRefresh = true)
    }

    fun refresh() {
        if (filter == TracksFilter.All) loadPage(forceRefresh = true, requestedOrigin = LoadingOrigin.PullToRefresh)
        else loadTracks(forceRefresh = true, requestedOrigin = LoadingOrigin.PullToRefresh)
    }

    fun loadMore() {
        if (filter == TracksFilter.All && _hasMore.value && loadJob?.isActive != true) {
            loadPage(forceRefresh = false, append = true, requestedOrigin = LoadingOrigin.Pagination)
        }
    }

    fun openSearch() = searchDelegate.open()
    fun updateSearchQuery(value: String) = searchDelegate.updateQuery(value)
    fun loadMoreSearch() = searchDelegate.loadMore()
    fun retrySearch() = searchDelegate.retry()
    fun closeSearch() = searchDelegate.close()
    fun updateSort(value: SortOption<TrackSortCriterion>) {
        sortPreferences.artistTracks = value
        _sort.value = value
        if (filter is TracksFilter.ByArtist) {
            _state.update { it.mapContent { tracks -> tracks.sortedTracks(value) } }
        }
    }

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

    fun downloadTrack(track: Track) = viewModelScope.launch { download(track) }
    fun cancelTrackDownload(id: String) = viewModelScope.launch { downloadDelegate.cancelTrack(id) }
    fun retryTrackDownload(id: String) = viewModelScope.launch { downloadDelegate.retry(id) }
    fun toggleFavorites(tracks: List<Track>) = viewModelScope.launch {
        val desired = favoriteStateForSelection(tracks)
        favoriteDelegate.set(tracks, desired) { track, isFavorite ->
            updateFavorite(track.id, isFavorite)
        }
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
    fun downloadAlbum(album: Album) = viewModelScope.launch { download(album) }
    fun cancelAlbumDownload(id: String) = viewModelScope.launch { downloadDelegate.cancelAlbum(id) }

    private suspend fun download(track: Track) {
        downloadDelegate.download(track).onFailure { error ->
            _downloadMessages.tryEmit(error.toAppError(AppError.DownloadFailed))
        }
    }

    private suspend fun download(album: Album) {
        downloadDelegate.download(album).onFailure { error ->
            _downloadMessages.tryEmit(error.toAppError(AppError.DownloadFailed))
        }
    }

    private fun updateFavorite(id: String, isFavorite: Boolean) {
        _state.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
        searchDelegate.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
    }

    private fun loadTracks(forceRefresh: Boolean, requestedOrigin: LoadingOrigin? = null) {
        val currentFilter = filter ?: return
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            getTracksUseCase(currentFilter, forceRefresh).collect { state ->
                _state.value = if (currentFilter is TracksFilter.ByArtist) {
                    state.mapContent { it.sortedTracks(_sort.value) }
                } else {
                    state
                }
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
                val page = getTracksUseCase.page(previous.size, PAGE_SIZE, forceRefresh)
                _hasMore.value = page.hasMore
                _state.value = LoadableState.Content(previous + page.items)
            } catch (error: Throwable) {
                _state.value = LoadableState.Failure(
                    error.toAppError(AppError.TracksLoadFailed),
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
