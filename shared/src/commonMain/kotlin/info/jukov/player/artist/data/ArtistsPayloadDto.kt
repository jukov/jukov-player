package info.jukov.player.artist.data

import kotlinx.serialization.Serializable

@Serializable
internal data class ArtistsPayloadDto(
    val artists: ArtistsDto? = null,
)
