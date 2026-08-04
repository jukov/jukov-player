package info.jukov.player.artist.data

import info.jukov.player.artist.domain.Artist
import info.jukov.player.auth.domain.AuthSession

interface ArtistsApi {
    suspend fun getArtists(session: AuthSession): List<Artist>
}
