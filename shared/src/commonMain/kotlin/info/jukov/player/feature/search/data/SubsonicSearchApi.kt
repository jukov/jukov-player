package info.jukov.player.feature.search.data

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.search.domain.SearchOffsets
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.subsonic.data.SubsonicApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

class SubsonicSearchApi(private val client: SubsonicApiClient) : SearchApi {
    override suspend fun search(
        session: AuthSession,
        query: String,
        offsets: SearchOffsets,
        artistCount: Int,
        albumCount: Int,
        trackCount: Int,
    ): SearchApiResult {
        val result = client.get(
            endpoint = "search3",
            session = session,
            deserializer = SearchEnvelopeDto.serializer(),
            parameters = mapOf(
                "query" to query,
                "artistCount" to artistCount.toString(),
                "artistOffset" to offsets.artists.toString(),
                "albumCount" to albumCount.toString(),
                "albumOffset" to offsets.albums.toString(),
                "songCount" to trackCount.toString(),
                "songOffset" to offsets.tracks.toString(),
            ),
        ).response.searchResult3 ?: SearchResultDto()
        return SearchApiResult(
            artists = result.artist.map { dto ->
                Artist(dto.id, dto.name, dto.albumCount, dto.coverArt, dto.starred != null)
            },
            albums = result.album.map { dto ->
                Album(
                    id = dto.id, name = dto.name, artist = dto.artist,
                    artistId = dto.artistId, year = dto.year, coverArtId = dto.coverArt,
                    coverArtUrl = dto.coverArt?.let { client.buildUrl("getCoverArt", session, mapOf("id" to it)) },
                    isFavorite = dto.starred != null,
                )
            },
            tracks = result.song.map { dto ->
                Track(
                    id = dto.id, title = dto.title, artist = dto.artist, album = dto.album,
                    albumId = dto.albumId, artistId = dto.artistId, trackNumber = dto.track,
                    year = dto.year, coverArtId = dto.coverArt,
                    coverArtUrl = dto.coverArt?.let { client.buildUrl("getCoverArt", session, mapOf("id" to it)) },
                    streamUrl = client.buildUrl("stream", session, mapOf("id" to dto.id)),
                    durationMs = dto.duration?.times(1_000L) ?: 0L,
                    contentType = dto.contentType, isFavorite = dto.starred != null,
                )
            },
        )
    }
}

@Serializable private data class SearchEnvelopeDto(
    @SerialName("subsonic-response") val response: SearchResponseDto,
)
@Serializable private data class SearchResponseDto(val searchResult3: SearchResultDto? = null)
@Serializable private data class SearchResultDto(
    val artist: List<SearchArtistDto> = emptyList(),
    val album: List<SearchAlbumDto> = emptyList(),
    val song: List<SearchTrackDto> = emptyList(),
)
@Serializable private data class SearchArtistDto(
    val id: String, val name: String, val albumCount: Int = 0,
    val coverArt: String? = null, val starred: String? = null,
)
@Serializable private data class SearchAlbumDto(
    val id: String, val name: String, val artist: String = "", val artistId: String? = null,
    val year: Int? = null, val coverArt: String? = null, val starred: String? = null,
)
@Serializable private data class SearchTrackDto(
    val id: String, val title: String, val artist: String = "", val album: String? = null,
    val albumId: String? = null, val artistId: String? = null, val track: Int? = null,
    val year: Int? = null, val coverArt: String? = null, val duration: Int? = null,
    val contentType: String? = null, val starred: String? = null,
)
