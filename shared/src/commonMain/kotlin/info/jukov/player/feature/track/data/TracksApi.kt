package info.jukov.player.feature.track.data

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.core.domain.Page

interface TracksApi {
    suspend fun getTracks(session: AuthSession, filter: TracksFilter): List<Track>
    suspend fun getTracksPage(session: AuthSession, offset: Int, size: Int): Page<Track>
}
