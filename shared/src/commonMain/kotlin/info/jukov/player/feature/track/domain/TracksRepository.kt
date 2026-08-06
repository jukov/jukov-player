package info.jukov.player.feature.track.domain

import info.jukov.player.core.domain.LoadableState
import kotlinx.coroutines.flow.Flow

interface TracksRepository {
    fun getTracks(filter: TracksFilter, forceRefresh: Boolean = false): Flow<LoadableState<List<Track>>>
}
