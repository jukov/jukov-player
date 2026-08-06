package info.jukov.player.feature.album.data

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthSession

interface AlbumsApi {
    suspend fun getAlbums(session: AuthSession, artistId: String?): List<Album>
}
