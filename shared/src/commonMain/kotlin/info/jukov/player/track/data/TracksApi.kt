package info.jukov.player.track.data

import info.jukov.player.auth.domain.AuthSession
import info.jukov.player.track.domain.Track
import info.jukov.player.track.domain.TracksFilter

interface TracksApi {
    suspend fun getTracks(session: AuthSession, filter: TracksFilter): List<Track>
}
