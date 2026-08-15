package info.jukov.player.feature.album.domain

import info.jukov.player.core.domain.LoadableState
import kotlinx.coroutines.flow.Flow
import info.jukov.player.core.domain.Page
import info.jukov.player.core.domain.SortOption
import info.jukov.player.core.domain.AlbumSortCriterion

interface AlbumsRepository {
    fun getAlbums(artistId: String?, forceRefresh: Boolean = false): Flow<LoadableState<List<Album>>>
    suspend fun getAlbumsPage(offset: Int, size: Int, sort: SortOption<AlbumSortCriterion>, forceRefresh: Boolean = false): Page<Album>
}
