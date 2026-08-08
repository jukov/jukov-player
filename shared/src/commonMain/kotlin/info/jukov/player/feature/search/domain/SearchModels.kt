package info.jukov.player.feature.search.domain

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.track.domain.Track

data class SearchPage<T>(
    val items: List<T>,
    val nextOffset: Int,
    val hasMore: Boolean,
)

data class SearchOffsets(
    val artists: Int = 0,
    val albums: Int = 0,
    val tracks: Int = 0,
)

data class LibrarySearchPage(
    val items: List<LibrarySearchItem>,
    val nextOffsets: SearchOffsets,
    val hasMore: Boolean,
)

sealed interface LibrarySearchItem {
    val id: String

    data class ArtistItem(val artist: Artist) : LibrarySearchItem {
        override val id: String = "artist:${artist.id}"
    }

    data class AlbumItem(val album: Album) : LibrarySearchItem {
        override val id: String = "album:${album.id}"
    }

    data class TrackItem(val track: Track) : LibrarySearchItem {
        override val id: String = "track:${track.id}"
    }
}

interface SearchRepository {
    suspend fun artists(query: String, offset: Int, size: Int): SearchPage<Artist>
    suspend fun albums(query: String, offset: Int, size: Int, artistId: String? = null): SearchPage<Album>
    suspend fun tracks(query: String, offset: Int, size: Int, artistId: String? = null): SearchPage<Track>
    suspend fun library(query: String, offsets: SearchOffsets, size: Int): LibrarySearchPage
}

class SearchUseCase(private val repository: SearchRepository) {
    suspend fun artists(query: String, offset: Int, size: Int) = repository.artists(query, offset, size)
    suspend fun albums(query: String, offset: Int, size: Int, artistId: String? = null) =
        repository.albums(query, offset, size, artistId)
    suspend fun tracks(query: String, offset: Int, size: Int, artistId: String? = null) =
        repository.tracks(query, offset, size, artistId)
    suspend fun library(query: String, offsets: SearchOffsets, size: Int) =
        repository.library(query, offsets, size)
}
