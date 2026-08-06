package info.jukov.player.feature.album.domain

import info.jukov.player.core.domain.LoadableState
import kotlinx.coroutines.flow.Flow

interface AlbumsRepository {
    fun getAlbums(artistId: String?, forceRefresh: Boolean = false): Flow<LoadableState<List<Album>>>
}
