package info.jukov.player.subsonic.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicEnvelopeDto(
    @SerialName("subsonic-response") val response: SubsonicResponseDto,
)
