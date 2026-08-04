package info.jukov.player.subsonic.data

import kotlinx.serialization.Serializable

@Serializable
data class SubsonicErrorDto(
    val code: Int,
    val message: String? = null,
    val helpUrl: String? = null,
)
