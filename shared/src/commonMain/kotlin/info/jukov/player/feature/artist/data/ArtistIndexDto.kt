package info.jukov.player.feature.artist.data

import kotlinx.serialization.Serializable

@Serializable
internal data class ArtistIndexDto(
    val name: String,
    val artist: List<ArtistDto> = emptyList(),
)
