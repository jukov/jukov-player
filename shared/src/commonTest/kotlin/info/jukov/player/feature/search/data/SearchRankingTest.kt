package info.jukov.player.feature.search.data

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.search.domain.LibrarySearchItem
import info.jukov.player.feature.track.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import info.jukov.player.feature.search.util.rankSearchItems

class SearchRankingTest {
    @Test
    fun exactPrefixSubstringAndFuzzyResultsHaveStableOrder() {
        val items = listOf(
            LibrarySearchItem.TrackItem(track("4", "Muze")),
            LibrarySearchItem.AlbumItem(album("3", "Best Muse Record")),
            LibrarySearchItem.ArtistItem(Artist("2", "Muse Live", 1, null)),
            LibrarySearchItem.ArtistItem(Artist("1", "Muse", 1, null)),
        )

        val sorted = rankSearchItems("muse", items)

        assertEquals(listOf("artist:1", "artist:2", "album:3", "track:4"), sorted.map { it.id })
    }

    @Test
    fun itemTypePriorityIsArtistThenAlbumThenTrack() {
        val artist = LibrarySearchItem.ArtistItem(Artist("1", "Muse", 1, null))
        val album = LibrarySearchItem.AlbumItem(
            album("2", "Origin of Symmetry").copy(artist = "Muse"),
        )
        val track = LibrarySearchItem.TrackItem(track("3", "New Born").copy(artist = "Muse"))

        val sorted = rankSearchItems("muse", listOf(track, album, artist))

        assertEquals(listOf("artist:1", "album:2", "track:3"), sorted.map { it.id })
    }

    @Test
    fun exactTrackTitleRanksBeforeTrackMatchingOnlyByAlbum() {
        val albumMatch = LibrarySearchItem.TrackItem(
            track("1", "Первый трек").copy(album = "Город дорог"),
        )
        val titleMatch = LibrarySearchItem.TrackItem(
            track("2", "Город дорог").copy(album = "Город дорог"),
        )

        val sorted = rankSearchItems("город дорог", listOf(albumMatch, titleMatch))

        assertEquals(listOf("track:2", "track:1"), sorted.map { it.id })
    }

    private fun album(id: String, name: String) = Album(
        id, name, "Artist", null, coverArtUrl = null,
    )

    private fun track(id: String, title: String) = Track(
        id = id, title = title, artist = "Artist", albumId = null, artistId = null,
        trackNumber = null, coverArtUrl = null, isFavorite = false,
    )
}
