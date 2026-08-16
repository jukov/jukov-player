package info.jukov.player.feature.download.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.download.domain.DownloadFailureSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import info.jukov.player.feature.download.domain.OfflineTrack
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.core.domain.DownloadAlbumSortCriterion
import info.jukov.player.core.domain.DownloadTrackSortCriterion
import info.jukov.player.core.domain.SortOption
import info.jukov.player.core.domain.SortPreferences
import info.jukov.player.core.domain.sortedDownloadAlbums
import info.jukov.player.core.domain.sortedDownloadTracks

enum class DownloadsTab { Tracks, Albums }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val repository: DownloadsRepository,
    private val sortPreferences: SortPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<info.jukov.player.feature.download.domain.OfflineLibrary>>(
        LoadableState.Loading(content = null),
    )
    val state = _state.asStateFlow()
    private val _selectedTab = MutableStateFlow(DownloadsTab.Tracks)
    val selectedTab = _selectedTab.asStateFlow()
    private val _searchActive = MutableStateFlow(false)
    val searchActive = _searchActive.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _trackSort = MutableStateFlow(sortPreferences.downloadTracks)
    val trackSort = _trackSort.asStateFlow()
    private val _albumSort = MutableStateFlow(sortPreferences.downloadAlbums)
    val albumSort = _albumSort.asStateFlow()
    private val _failureSummary = MutableStateFlow(DownloadFailureSummary())
    val failureSummary = _failureSummary.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_searchActive, _searchQuery) { active, query -> active to query }
                .flatMapLatest { (active, query) ->
                    if (active && query.isNotBlank()) repository.searchLibrary(query) else repository.observeLibrary()
                }.collect { library -> _state.value = LoadableState.Content(library.sorted()) }
        }
        viewModelScope.launch {
            repository.observeFailureSummary().collect { summary ->
                _failureSummary.value = summary
            }
        }
    }

    fun selectTab(tab: DownloadsTab) { _selectedTab.value = tab }
    fun openSearch() { _searchActive.value = true }
    fun updateSearchQuery(value: String) { _searchQuery.value = value }
    fun closeSearch() { _searchActive.value = false; _searchQuery.value = "" }
    fun updateTrackSort(value: SortOption<DownloadTrackSortCriterion>) {
        sortPreferences.downloadTracks = value
        _trackSort.value = value
        _state.value = LoadableState.Content((_state.value.content ?: return).sorted())
    }
    fun updateAlbumSort(value: SortOption<DownloadAlbumSortCriterion>) {
        sortPreferences.downloadAlbums = value
        _albumSort.value = value
        _state.value = LoadableState.Content((_state.value.content ?: return).sorted())
    }
    fun removeTrack(id: String) = viewModelScope.launch { repository.cancelTrack(id) }
    fun removeTracks(tracks: List<Track>) = viewModelScope.launch {
        repository.removeTracks(tracks.map(Track::id))
    }
    fun removeAlbum(id: String) = viewModelScope.launch { repository.cancelAlbum(id) }
    fun removeAlbums(albums: List<Album>) = viewModelScope.launch {
        albums.forEach { repository.cancelAlbum(it.id) }
    }
    fun removeAll() = viewModelScope.launch { repository.clearCurrentAccount() }
    fun retryTrack(id: String) = viewModelScope.launch { repository.retryTrack(id) }
    fun retryAllFailed() = viewModelScope.launch { repository.retryAllFailed() }
    fun albumTracks(id: String): Flow<List<OfflineTrack>> = repository.observeAlbumTracks(id)

    private fun info.jukov.player.feature.download.domain.OfflineLibrary.sorted() = copy(
        tracks = tracks.sortedDownloadTracks(_trackSort.value),
        albums = albums.sortedDownloadAlbums(_albumSort.value),
    )
}
