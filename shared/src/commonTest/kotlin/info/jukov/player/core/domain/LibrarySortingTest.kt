package info.jukov.player.core.domain

import com.russhwolf.settings.MapSettings
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.download.domain.OfflineTrack
import info.jukov.player.feature.track.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySortingTest {
    @Test fun artistSortingIsCaseInsensitiveAndStable() {
        val artists = listOf(artist("2", "alpha"), artist("1", "Alpha"), artist("3", "Beta"))
        assertEquals(listOf("1", "2", "3"), artists.sortedArtists(SortOption(ArtistSortCriterion.Name, SortDirection.Ascending)).map { it.id })
    }

    @Test fun albumsWithMissingYearAreLastAscending() {
        val albums = listOf(album("missing", null), album("new", 2024), album("old", 1999))
        assertEquals(listOf("old", "new", "missing"), albums.sortedAlbums(SortOption(AlbumSortCriterion.Year, SortDirection.Ascending)).map { it.id })
    }

    @Test fun albumsWithMissingYearAreLastDescending() {
        val albums = listOf(album("missing", null), album("new", 2024), album("old", 1999))
        assertEquals(listOf("new", "old", "missing"), albums.sortedAlbums(SortOption(AlbumSortCriterion.Year, SortDirection.Descending)).map { it.id })
    }

    @Test fun downloadDateSortsNewestFirst() {
        val items = listOf(offline("old", 1), offline("new", 2))
        assertEquals(listOf("new", "old"), items.sortedDownloadTracks(SortOption(DownloadTrackSortCriterion.Added, SortDirection.Descending)).map { it.track.id })
    }

    @Test fun settingsFallBackAndPersist() {
        val settings = MapSettings("sort.artists" to "broken:value")
        val store = SettingsSortPreferences(settings)
        assertEquals(SortOption(ArtistSortCriterion.Name, SortDirection.Ascending), store.artists)
        store.artistTracks = SortOption(TrackSortCriterion.Artist, SortDirection.Descending)
        assertEquals(SortOption(TrackSortCriterion.Artist, SortDirection.Descending), SettingsSortPreferences(settings).artistTracks)
    }

    @Test fun globalAlbumsNormalizeUnsupportedDescendingAlphabeticalSort() {
        assertEquals(
            SortOption(AlbumSortCriterion.Title, SortDirection.Ascending),
            SortOption(AlbumSortCriterion.Title, SortDirection.Descending).supportedForGlobalAlbums(),
        )
        assertEquals(
            SortOption(AlbumSortCriterion.Year, SortDirection.Descending),
            SortOption(AlbumSortCriterion.Year, SortDirection.Descending).supportedForGlobalAlbums(),
        )
    }

    private fun artist(id: String, name: String) = Artist(id, name, 0, null)
    private fun album(id: String, year: Int?) = Album(id, id, "Artist", null, coverArtUrl = null, year = year)
    private fun offline(id: String, added: Long) = OfflineTrack(track(id), DownloadStatus(DownloadState.Completed), added)
    private fun track(id: String) = Track(id, id, "Artist", null, null, null, null, null, null, isFavorite = false)
}
