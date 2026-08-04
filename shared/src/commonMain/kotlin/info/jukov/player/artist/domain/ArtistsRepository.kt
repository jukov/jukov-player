package info.jukov.player.artist.domain

interface ArtistsRepository {
    suspend fun getArtists(): Result<List<Artist>>
}
