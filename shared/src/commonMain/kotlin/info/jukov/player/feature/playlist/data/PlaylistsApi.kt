package info.jukov.player.feature.playlist.data

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.playlist.domain.Playlist

interface PlaylistsApi {
    suspend fun getPlaylists(session: AuthSession): List<Playlist>
    suspend fun getPlaylist(session: AuthSession, id: String): Playlist
    suspend fun createPlaylist(
        session: AuthSession,
        name: String,
        songIds: List<String>,
    ): Playlist?
    suspend fun updatePlaylist(session: AuthSession, id: String, name: String, isPublic: Boolean)
    suspend fun addTracks(session: AuthSession, playlistId: String, songIds: List<String>)
    suspend fun removeTracks(session: AuthSession, playlistId: String, songIndexes: List<Int>)
    suspend fun deletePlaylist(session: AuthSession, id: String)
}
