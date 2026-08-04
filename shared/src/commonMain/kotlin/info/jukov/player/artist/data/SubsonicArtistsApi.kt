package info.jukov.player.artist.data

import info.jukov.player.artist.domain.Artist
import info.jukov.player.auth.domain.AuthSession
import info.jukov.player.subsonic.data.SubsonicApiClient

class SubsonicArtistsApi(private val client: SubsonicApiClient) : ArtistsApi {
    override suspend fun getArtists(session: AuthSession): List<Artist> {
        val response = client.get(
            endpoint = "getArtists",
            session = session,
            deserializer = ArtistsResponseDto.serializer(),
        ).response
        return response.artists.orEmpty().index
            .flatMap { it.artist }
            .map(ArtistDto::toDomain)
            .sortedBy { it.name.lowercase() }
    }

    private fun ArtistsDto?.orEmpty() = this ?: ArtistsDto()
}
