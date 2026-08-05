package info.jukov.player.favorite.data

import info.jukov.player.album.data.AlbumDto
import info.jukov.player.artist.data.ArtistDto
import info.jukov.player.track.data.TrackDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FavoritesResponseDto(
    @SerialName("subsonic-response") val response: FavoritesPayloadDto,
)

@Serializable
internal data class FavoritesPayloadDto(
    val starred2: StarredDto? = null,
)

@Serializable
internal data class StarredDto(
    val song: List<TrackDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val artist: List<ArtistDto> = emptyList(),
)
