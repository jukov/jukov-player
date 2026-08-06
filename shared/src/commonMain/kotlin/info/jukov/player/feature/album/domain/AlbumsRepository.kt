package info.jukov.player.feature.album.domain

import info.jukov.player.core.domain.LoadableState
import kotlinx.coroutines.flow.Flow
import info.jukov.player.core.domain.Page

interface AlbumsRepository {
    fun getAlbums(artistId: String?, forceRefresh: Boolean = false): Flow<LoadableState<List<Album>>>
    suspend fun getAlbumsPage(offset: Int, size: Int, forceRefresh: Boolean = false): Page<Album>
}
