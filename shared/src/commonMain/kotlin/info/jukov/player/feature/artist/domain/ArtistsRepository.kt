package info.jukov.player.feature.artist.domain

interface ArtistsRepository {
    suspend fun getArtists(): Result<List<Artist>>
}
