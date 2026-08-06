package info.jukov.player.feature.artist.data

import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.auth.domain.AuthSession

interface ArtistsApi {
    suspend fun getArtists(session: AuthSession): List<Artist>
}
