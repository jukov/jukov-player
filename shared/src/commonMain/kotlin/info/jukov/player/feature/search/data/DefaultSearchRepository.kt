package info.jukov.player.feature.search.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.AppException
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.search.domain.*
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import info.jukov.player.feature.search.util.rankSearchItems

class DefaultSearchRepository(
    private val api: SearchApi,
    private val authRepository: AuthRepository,
) : SearchRepository {
    override suspend fun artists(query: String, offset: Int, size: Int): SearchPage<Artist> {
        val result = request(query, SearchOffsets(artists = offset), size, 0, 0)
        return SearchPage(result.artists, offset + result.artists.size, result.artists.size == size)
    }

    override suspend fun albums(query: String, offset: Int, size: Int, artistId: String?): SearchPage<Album> =
        filteredPage(query, offset, size, artistId, type = SearchType.Album)

    override suspend fun tracks(query: String, offset: Int, size: Int, artistId: String?): SearchPage<Track> =
        filteredPage(query, offset, size, artistId, type = SearchType.Track)

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> filteredPage(
        query: String,
        offset: Int,
        size: Int,
        artistId: String?,
        type: SearchType,
    ): SearchPage<T> {
        var serverOffset = offset
        val collected = mutableListOf<T>()
        var hasMore: Boolean
        do {
            val result = when (type) {
                SearchType.Album -> request(query, SearchOffsets(albums = serverOffset), 0, size, 0)
                SearchType.Track -> request(query, SearchOffsets(tracks = serverOffset), 0, 0, size)
            }
            val raw = when (type) {
                SearchType.Album -> result.albums
                SearchType.Track -> result.tracks
            }
            serverOffset += raw.size
            hasMore = raw.size == size
            val matching = if (artistId == null) raw else raw.filter {
                when (it) {
                    is Album -> it.artistId == artistId
                    is Track -> it.artistId == artistId
                    else -> false
                }
            }
            collected += matching.take(size - collected.size) as List<T>
        } while (artistId != null && collected.size < size && hasMore)
        return SearchPage(collected, serverOffset, hasMore)
    }

    override suspend fun library(query: String, offsets: SearchOffsets, size: Int): LibrarySearchPage {
        val result = request(query, offsets, size, size, size)
        val items = buildList {
            result.artists.forEach { add(LibrarySearchItem.ArtistItem(it)) }
            result.albums.forEach { add(LibrarySearchItem.AlbumItem(it)) }
            result.tracks.forEach { add(LibrarySearchItem.TrackItem(it)) }
        }
        val ranked = withContext(Dispatchers.Default) { rankSearchItems(query, items) }
        return LibrarySearchPage(
            items = ranked,
            nextOffsets = SearchOffsets(
                offsets.artists + result.artists.size,
                offsets.albums + result.albums.size,
                offsets.tracks + result.tracks.size,
            ),
            hasMore = result.artists.size == size || result.albums.size == size || result.tracks.size == size,
        )
    }

    private suspend fun request(query: String, offsets: SearchOffsets, artists: Int, albums: Int, tracks: Int): SearchApiResult {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: throw AppException(AppError.AuthenticationRequired)
        return api.search(session, query, offsets, artists, albums, tracks)
    }

    private enum class SearchType { Album, Track }
}
