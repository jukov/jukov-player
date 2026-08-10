package info.jukov.player.feature.download.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import info.jukov.player.feature.download.domain.OfflineTrack
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.favoriteStateForSelection
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.album.domain.Album

enum class DownloadsTab { Tracks, Albums }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val repository: DownloadsRepository,
    private val favoriteDelegate: FavoriteDelegate,
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

    init {
        viewModelScope.launch {
            combine(_searchActive, _searchQuery) { active, query -> active to query }
                .flatMapLatest { (active, query) ->
                    if (active && query.isNotBlank()) repository.searchLibrary(query) else repository.observeLibrary()
                }.collect { _state.value = LoadableState.Content(it) }
        }
    }

    fun selectTab(tab: DownloadsTab) { _selectedTab.value = tab }
    fun openSearch() { _searchActive.value = true }
    fun updateSearchQuery(value: String) { _searchQuery.value = value }
    fun closeSearch() { _searchActive.value = false; _searchQuery.value = "" }
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
    fun albumTracks(id: String): Flow<List<OfflineTrack>> = repository.observeAlbumTracks(id)
    fun toggleFavorite(track: Track) = viewModelScope.launch {
        favoriteDelegate.toggle(FavoriteTarget.Track(track.id), track.isFavorite) { }
    }
    fun toggleFavorites(tracks: List<Track>) = viewModelScope.launch {
        val desired = favoriteStateForSelection(tracks)
        favoriteDelegate.set(tracks, desired) { _, _ -> }
    }
    fun toggleFavoriteAlbums(albums: List<Album>) = viewModelScope.launch {
        val desired = albums.any { !it.isFavorite }
        favoriteDelegate.setAlbums(albums, desired) { _, _ -> }
    }
}
