package info.jukov.player.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.toAppError
import info.jukov.player.feature.search.domain.LibrarySearchItem
import info.jukov.player.feature.search.domain.SearchOffsets
import info.jukov.player.feature.search.domain.SearchUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

class LibraryViewModel(
    private val search: SearchUseCase,
    private val favoriteDelegate: FavoriteDelegate? = null,
    private val downloadDelegate: DownloadDelegate? = null,
    private val getTracksUseCase: GetTracksUseCase? = null,
) : ViewModel() {
    private val _searchActive = MutableStateFlow(false)
    val searchActive = _searchActive.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _results = MutableStateFlow<LoadableState<List<LibrarySearchItem>>>(LoadableState.Content(emptyList()))
    val results = _results.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()
    private var offsets = SearchOffsets()
    private var searchJob: Job? = null
    private val actionMessages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = favoriteDelegate?.let { merge(it.messages, actionMessages) } ?: actionMessages

    fun openSearch() { _searchActive.value = true }

    fun updateSearchQuery(value: String) {
        _query.value = value
        searchJob?.cancel()
        offsets = SearchOffsets()
        _hasMore.value = false
        if (value.trim().length < MIN_QUERY) {
            _results.value = LoadableState.Content(emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            loadPage(append = false)
        }
    }

    fun loadMoreSearch() {
        if (_hasMore.value && searchJob?.isActive != true) {
            searchJob = viewModelScope.launch { loadPage(append = true) }
        }
    }

    fun retrySearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { loadPage(append = _results.value.content?.isNotEmpty() == true) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _searchActive.value = false
        _query.value = ""
        offsets = SearchOffsets()
        _hasMore.value = false
        _results.value = LoadableState.Content(emptyList())
    }

    fun toggleFavorites(items: List<LibrarySearchItem>) = viewModelScope.launch {
        val desired = items.any { !it.isFavorite }
        items.forEach { item ->
            val target = when (item) {
                is LibrarySearchItem.ArtistItem -> FavoriteTarget.Artist(item.artist.id)
                is LibrarySearchItem.AlbumItem -> FavoriteTarget.Album(item.album.id)
                is LibrarySearchItem.TrackItem -> FavoriteTarget.Track(item.track.id)
            }
            favoriteDelegate?.toggle(target, isFavorite = !desired) { updateFavorite(item.id, it) }
        }
    }

    fun download(items: List<LibrarySearchItem>, onComplete: () -> Unit) = resolveTracks(items) { tracks ->
        viewModelScope.launch { tracks.forEach { downloadDelegate?.download(it) } }
        onComplete()
    }

    fun resolveTracks(items: List<LibrarySearchItem>, action: (List<Track>) -> Unit) = viewModelScope.launch {
        val tracks = buildList {
            for (item in items) {
                when (item) {
                    is LibrarySearchItem.TrackItem -> add(item.track)
                    is LibrarySearchItem.AlbumItem -> if (!addResolved(TracksFilter.ByAlbum(item.album.id))) return@launch
                    is LibrarySearchItem.ArtistItem -> if (!addResolved(TracksFilter.ByArtist(item.artist.id))) return@launch
                }
            }
        }.distinctBy(Track::id)
        action(tracks)
    }

    private suspend fun MutableList<Track>.addResolved(filter: TracksFilter): Boolean =
        when (val state = getTracksUseCase?.invoke(filter)?.first { it !is LoadableState.Loading }
            ?: return false) {
            is LoadableState.Content -> { addAll(state.content); true }
            is LoadableState.Failure -> { actionMessages.emit(state.error); false }
            is LoadableState.Loading -> false
        }

    private fun updateFavorite(id: String, isFavorite: Boolean) {
        val state = _results.value
        val updated = state.content?.map { item ->
            if (item.id != id) item else when (item) {
                    is LibrarySearchItem.ArtistItem -> item.copy(artist = item.artist.copy(isFavorite = isFavorite))
                    is LibrarySearchItem.AlbumItem -> item.copy(album = item.album.copy(isFavorite = isFavorite))
                    is LibrarySearchItem.TrackItem -> item.copy(track = item.track.copy(isFavorite = isFavorite))
            }
        }
        _results.value = when (state) {
            is LoadableState.Content -> LoadableState.Content(updated.orEmpty())
            is LoadableState.Loading -> LoadableState.Loading(updated)
            is LoadableState.Failure -> LoadableState.Failure(state.error, updated)
        }
    }

    private val LibrarySearchItem.isFavorite: Boolean get() = when (this) {
        is LibrarySearchItem.ArtistItem -> artist.isFavorite
        is LibrarySearchItem.AlbumItem -> album.isFavorite
        is LibrarySearchItem.TrackItem -> track.isFavorite
    }

    private suspend fun loadPage(append: Boolean) {
        val activeQuery = _query.value.trim()
        if (!_searchActive.value || activeQuery.length < MIN_QUERY) return
        val old = if (append) _results.value.content.orEmpty() else emptyList()
        _results.value = LoadableState.Loading(old.ifEmpty { null })
        try {
            val page = search.library(activeQuery, if (append) offsets else SearchOffsets(), PAGE_SIZE)
            if (!_searchActive.value || _query.value.trim() != activeQuery) return
            offsets = page.nextOffsets
            _hasMore.value = page.hasMore
            _results.value = LoadableState.Content(old + page.items)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _results.value = LoadableState.Failure(error.toAppError(AppError.SearchFailed), old.ifEmpty { null })
        }
    }

    private companion object {
        const val MIN_QUERY = 2
        const val PAGE_SIZE = 100
        const val DEBOUNCE_MS = 350L
    }
}
