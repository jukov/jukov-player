package info.jukov.player.feature.download.domain

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.track.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadModelsTest {
    @Test
    fun automaticRetryBackoffUsesSeconds() {
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L),
            (1..5).map(::downloadRetryDelayMs),
        )
    }
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

    @Test
    fun manualTrackFailureTakesPriorityInAlbumStatus() {
        val tracks = listOf(
            OfflineTrack(track("one"), DownloadStatus(DownloadState.Downloading)),
            OfflineTrack(track("two"), DownloadStatus(DownloadState.Failed)),
        )

        assertEquals(
            DownloadState.Failed,
            OfflineAlbum(album(), tracks, expectedTrackCount = 2).status.state,
        )
    }

    private fun album() = Album("album", "Album", "Artist", null, null, null)

    private fun track(id: String) = Track(
        id = id, title = id, artist = "Artist", albumId = "album", artistId = null,
        trackNumber = null, coverArtUrl = null, streamUrl = null, isFavorite = false,
    )
}
