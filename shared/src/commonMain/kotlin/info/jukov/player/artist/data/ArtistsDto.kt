package info.jukov.player.artist.data

import kotlinx.serialization.Serializable

@Serializable
internal data class ArtistsDto(
    val index: List<ArtistIndexDto> = emptyList(),
)
