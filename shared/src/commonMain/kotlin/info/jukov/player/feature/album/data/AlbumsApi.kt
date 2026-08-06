package info.jukov.player.feature.album.data

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.core.domain.Page

interface AlbumsApi {
    suspend fun getAlbums(session: AuthSession, artistId: String?): List<Album>
    suspend fun getAlbumsPage(session: AuthSession, offset: Int, size: Int): Page<Album>
}
