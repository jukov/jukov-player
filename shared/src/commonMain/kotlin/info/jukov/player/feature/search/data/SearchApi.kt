package info.jukov.player.feature.search.data

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.search.domain.SearchOffsets
import info.jukov.player.feature.track.domain.Track

data class SearchApiResult(
    val artists: List<Artist>,
    val albums: List<Album>,
    val tracks: List<Track>,
)

interface SearchApi {
    suspend fun search(
        session: AuthSession,
        query: String,
        offsets: SearchOffsets,
        artistCount: Int,
        albumCount: Int,
        trackCount: Int,
    ): SearchApiResult
}
