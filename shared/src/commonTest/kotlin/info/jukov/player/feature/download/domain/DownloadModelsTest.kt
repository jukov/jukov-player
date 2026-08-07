package info.jukov.player.feature.download.domain

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.track.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadModelsTest {
    @Test
    fun progressIsCalculatedFromBytes() {
        assertEquals(0.25f, DownloadStatus(DownloadState.Downloading, 25, 100).progress)
        assertEquals(null, DownloadStatus(DownloadState.Downloading, 25, null).progress)
    }

    @Test
    fun albumIsCompletedOnlyWhenEveryExpectedTrackIsPresentAndCompleted() {
        val track = OfflineTrack(track("one"), DownloadStatus(DownloadState.Completed, 100, 100))
        assertEquals(
            DownloadState.Failed,
            OfflineAlbum(album(), listOf(track), expectedTrackCount = 2).status.state,
        )
        assertEquals(
            DownloadState.Completed,
            OfflineAlbum(album(), listOf(track), expectedTrackCount = 1).status.state,
        )
    }

    @Test
    fun albumProgressAggregatesTrackBytes() {
        val tracks = listOf(
            OfflineTrack(track("one"), DownloadStatus(DownloadState.Completed, 100, 100)),
            OfflineTrack(track("two"), DownloadStatus(DownloadState.Downloading, 50, 100)),
        )
        assertEquals(0.75f, OfflineAlbum(album(), tracks, expectedTrackCount = 2).status.progress)
    }

    private fun album() = Album("album", "Album", "Artist", null, null, null)

    private fun track(id: String) = Track(
        id = id, title = id, artist = "Artist", albumId = "album", artistId = null,
        trackNumber = null, coverArtUrl = null, streamUrl = null, isFavorite = false,
    )
}
