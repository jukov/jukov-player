package info.jukov.player.feature.artist.data

import kotlinx.serialization.Serializable

@Serializable
internal data class ArtistsPayloadDto(
    val artists: ArtistsDto? = null,
)
