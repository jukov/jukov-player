package info.jukov.player.feature.track.data

import info.jukov.player.feature.album.data.AlbumDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TracksResponseDto(
    @SerialName("subsonic-response") val response: TracksPayloadDto,
)

@Serializable
internal data class TracksPayloadDto(
    val searchResult3: SearchResult3Dto? = null,
    val album: AlbumWithSongsDto? = null,
    val artist: ArtistWithAlbumsForTracksDto? = null,
)

@Serializable
internal data class SearchResult3Dto(
    val song: List<TrackDto> = emptyList(),
)

@Serializable
internal data class AlbumWithSongsDto(
    val song: List<TrackDto> = emptyList(),
)

@Serializable
internal data class ArtistWithAlbumsForTracksDto(
    val album: List<AlbumDto> = emptyList(),
)
