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
import info.jukov.player.feature.download.domain.OfflineTrack
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.favoriteStateForSelection
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.track.domain.Track

enum class DownloadsTab { Tracks, Albums }

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

    init {
        viewModelScope.launch {
            repository.observeLibrary().collect { _state.value = LoadableState.Content(it) }
        }
    }

    fun selectTab(tab: DownloadsTab) { _selectedTab.value = tab }
    fun removeTrack(id: String) = viewModelScope.launch { repository.cancelTrack(id) }
    fun removeAlbum(id: String) = viewModelScope.launch { repository.cancelAlbum(id) }
    fun retryTrack(id: String) = viewModelScope.launch { repository.retryTrack(id) }
    fun albumTracks(id: String): Flow<List<OfflineTrack>> = repository.observeAlbumTracks(id)
    fun toggleFavorite(track: Track) = viewModelScope.launch {
        favoriteDelegate.toggle(FavoriteTarget.Track(track.id), track.isFavorite) { }
    }
    fun toggleFavorites(tracks: List<Track>) = viewModelScope.launch {
        val desired = favoriteStateForSelection(tracks)
        favoriteDelegate.set(tracks, desired) { _, _ -> }
    }
}
