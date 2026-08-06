package info.jukov.player.feature.artist.data

import kotlinx.serialization.Serializable

@Serializable
internal data class ArtistsDto(
    val index: List<ArtistIndexDto> = emptyList(),
)
