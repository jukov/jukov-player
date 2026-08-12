package info.jukov.player.feature.playlist.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.toAppError
import info.jukov.player.core.domain.mapContent
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.favorite.domain.favoriteStateForSelection
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.domain.PlaylistsRepository
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class PlaylistViewModel(
    private val repository: PlaylistsRepository,
    private val downloadDelegate: DownloadDelegate,
    private val favoriteDelegate: FavoriteDelegate,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<Playlist>>(LoadableState.Loading(null))
    val state = _state.asStateFlow()
    private val _messages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private val _pending = MutableStateFlow(false)
    val pending = _pending.asStateFlow()
    val downloadStatuses = downloadDelegate.trackStatuses
    val artworkUris = downloadDelegate.artworkUris
    private var id: String? = null
    private var observeJob: Job? = null
    private var manualTrackOrder: List<String>? = null

    fun load(id: String, forceRefresh: Boolean = false) {
        if (this.id == id && !forceRefresh) return
        if (this.id != id) {
            manualTrackOrder = null
            this.id = id
            observeJob?.cancel()
            observeJob = viewModelScope.launch {
                repository.playlist(id).collect { state ->
                    _state.value = state.mapContent { playlist ->
                        val order = manualTrackOrder ?: return@mapContent playlist
                        val remaining = playlist.tracks.toMutableList()
                        val ordered = order.mapNotNull { id ->
                            remaining.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                                ?.let(remaining::removeAt)
                        } + remaining
                        playlist.copy(tracks = ordered)
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.loadPlaylist(id, forceRefresh)
        }
    }

    fun isEditable(playlist: Playlist) = repository.isEditable(playlist)
    fun moveTrack(from: Int, to: Int) {
        _state.update { current ->
            val playlist = current.content ?: return@update current
            if (from !in playlist.tracks.indices || to !in playlist.tracks.indices) {
                return@update current
            }
            val tracks = playlist.tracks.toMutableList().apply { add(to, removeAt(from)) }
            manualTrackOrder = tracks.map(Track::id)
            current.mapContent { it.copy(tracks = tracks) }
        }
    }

    fun download(tracks: List<Track>) = viewModelScope.launch {
        tracks.forEach { track -> requestDownload(track) }
    }

    fun download(track: Track) = viewModelScope.launch {
        requestDownload(track)
    }

    fun cancelDownload(id: String) = viewModelScope.launch {
        downloadDelegate.cancelTrack(id)
    }

    fun retryDownload(id: String) = viewModelScope.launch {
        downloadDelegate.retry(id)
    }

    private suspend fun requestDownload(track: Track) {
        downloadDelegate.download(track).onFailure { error ->
            _messages.tryEmit(error.toAppError(AppError.DownloadFailed))
        }
    }

    fun toggleFavorites(tracks: List<Track>) = viewModelScope.launch {
        favoriteDelegate.set(tracks, favoriteStateForSelection(tracks)) { track, favorite ->
            _state.update { current ->
                val playlist = current.content ?: return@update current
                LoadableState.Content(
                    playlist.copy(
                        tracks = playlist.tracks.map {
                            if (it.id == track.id) it.copy(isFavorite = favorite) else it
                        },
                    ),
                )
            }
        }
    }

    fun remove(indexes: List<Int>) = mutate(
        action = { repository.removeTracks(requireNotNull(id), indexes) },
    )

    fun update(name: String, isPublic: Boolean, onUpdated: () -> Unit) = mutate(
        action = { repository.updatePlaylist(requireNotNull(id), name.trim(), isPublic) },
        onSuccess = onUpdated,
    )

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        _pending.value = true
        repository.deletePlaylist(requireNotNull(id))
            .onSuccess { onDeleted() }
            .onFailure { _messages.tryEmit(it.toAppError(AppError.PlaylistUpdateFailed)) }
        _pending.value = false
    }

    private fun mutate(
        action: suspend () -> Result<Unit>,
        onSuccess: () -> Unit = {},
    ) = viewModelScope.launch {
        _pending.value = true
        action()
            .onSuccess { onSuccess() }
            .onFailure { _messages.tryEmit(it.toAppError(AppError.PlaylistUpdateFailed)) }
        _pending.value = false
    }
}
