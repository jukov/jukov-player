package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.DownloadOwnershipEntity
import info.jukov.player.core.data.cache.OfflineAlbumEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.core.data.cache.TrackEntity
import info.jukov.player.feature.download.domain.DownloadState
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumDownloadReconciliationTest {
    @Test
    fun createsMissingJobsAndOwnershipsForCompleteAlbumMetadata() {
        val repairs = missingAlbumDownloadRecords(
            albums = listOf(OfflineAlbumEntity(ACCOUNT, ALBUM, 2, 123)),
            metadataTracks = listOf(track("second", 2), track("first", 1)),
            downloads = listOf(download("first")),
            ownerships = listOf(ownership("first", position = 0)),
        )

        assertEquals(
            listOf(
                AlbumDownloadRepair(
                    albumId = ALBUM,
                    trackId = "second",
                    position = 1,
                    requestedAtMs = 123,
                    createDownload = true,
                    createOwnership = true,
                ),
            ),
            repairs,
        )
    }

    @Test
    fun doesNotGuessMissingTracksFromPartialMetadata() {
        val repairs = missingAlbumDownloadRecords(
            albums = listOf(OfflineAlbumEntity(ACCOUNT, ALBUM, 2, 123)),
            metadataTracks = listOf(track("first", 1)),
            downloads = emptyList(),
            ownerships = emptyList(),
        )

        assertEquals(emptyList(), repairs)
    }

    private fun track(id: String, number: Int) = TrackEntity(
        accountKey = ACCOUNT,
        id = id,
        title = id,
        artist = "Artist",
        album = "Album",
        albumId = ALBUM,
        artistId = null,
        trackNumber = number,
        year = null,
        coverArtId = null,
        durationMs = 1,
        contentType = null,
        isFavorite = false,
    )

    private fun download(id: String) = OfflineTrackEntity(
        accountKey = ACCOUNT,
        trackId = id,
        relativePath = null,
        expectedSize = null,
        downloadedBytes = 0,
        state = DownloadState.Queued.name,
        error = null,
        requestedAtMs = 123,
        completedAtMs = null,
    )

    private fun ownership(id: String, position: Int) = DownloadOwnershipEntity(
        accountKey = ACCOUNT,
        ownerType = "album",
        ownerId = ALBUM,
        trackId = id,
        position = position,
    )

    private companion object {
        const val ACCOUNT = "account"
        const val ALBUM = "album"
    }
}
