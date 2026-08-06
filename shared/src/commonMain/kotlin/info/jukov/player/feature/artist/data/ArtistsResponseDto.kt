package info.jukov.player.feature.artist.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ArtistsResponseDto(
    @SerialName("subsonic-response") val response: ArtistsPayloadDto,
)
