package info.jukov.player.subsonic.data

import kotlinx.serialization.Serializable

@Serializable
data class SubsonicResponseDto(
    val status: String,
    val error: SubsonicErrorDto? = null,
)
