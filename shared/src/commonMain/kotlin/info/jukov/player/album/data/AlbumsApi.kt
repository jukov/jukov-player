package info.jukov.player.album.data

import info.jukov.player.album.domain.Album
import info.jukov.player.auth.domain.AuthSession

interface AlbumsApi {
    suspend fun getAlbums(session: AuthSession, artistId: String?): List<Album>
}
