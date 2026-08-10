package info.jukov.player.feature.playlist.domain

import info.jukov.player.core.domain.LoadableState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PlaylistsRepository {
    val playlists: StateFlow<LoadableState<List<Playlist>>>
    fun playlist(id: String): Flow<LoadableState<Playlist>>
    suspend fun loadPlaylists(forceRefresh: Boolean = false): Result<Unit>
    suspend fun loadPlaylist(id: String, forceRefresh: Boolean = false): Result<Unit>
    suspend fun createPlaylist(
        name: String,
        isPublic: Boolean,
        songIds: List<String> = emptyList(),
    ): Result<Unit>
    suspend fun updatePlaylist(id: String, name: String, isPublic: Boolean): Result<Unit>
    suspend fun addTracks(playlistId: String, songIds: List<String>): Result<Unit>
    suspend fun removeTracks(playlistId: String, songIndexes: List<Int>): Result<Unit>
    suspend fun deletePlaylist(id: String): Result<Unit>
    fun isEditable(playlist: Playlist): Boolean
}
