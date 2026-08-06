package info.jukov.player.feature.artist.domain

import info.jukov.player.core.domain.LoadableState
import kotlinx.coroutines.flow.Flow

interface ArtistsRepository {
    fun getArtists(forceRefresh: Boolean = false): Flow<LoadableState<List<Artist>>>
}
