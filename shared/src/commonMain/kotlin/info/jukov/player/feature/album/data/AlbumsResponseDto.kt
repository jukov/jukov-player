package info.jukov.player.feature.album.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AlbumsResponseDto(
    @SerialName("subsonic-response") val response: AlbumsPayloadDto,
)

@Serializable
internal data class AlbumsPayloadDto(
    val albumList2: AlbumListDto? = null,
    val artist: ArtistWithAlbumsDto? = null,
)

@Serializable
internal data class AlbumListDto(val album: List<AlbumDto> = emptyList())

@Serializable
internal data class ArtistWithAlbumsDto(
    val name: String = "",
    val album: List<AlbumDto> = emptyList(),
)
