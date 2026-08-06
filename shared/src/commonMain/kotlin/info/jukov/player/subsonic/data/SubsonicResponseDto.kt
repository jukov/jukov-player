package info.jukov.player.subsonic.data

import kotlinx.serialization.Serializable

@Serializable
data class SubsonicResponseDto(
    val status: String,
    val type: String? = null,
    val serverVersion: String? = null,
    val error: SubsonicErrorDto? = null,
)
