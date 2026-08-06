package info.jukov.player.feature.track.domain

import info.jukov.player.core.domain.LoadableState
import kotlinx.coroutines.flow.Flow
import info.jukov.player.core.domain.Page

interface TracksRepository {
    fun getTracks(filter: TracksFilter, forceRefresh: Boolean = false): Flow<LoadableState<List<Track>>>
    suspend fun getTracksPage(offset: Int, size: Int, forceRefresh: Boolean = false): Page<Track>
}
