package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.DownloadOwnershipEntity
import info.jukov.player.core.data.cache.OfflineAlbumEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.feature.download.domain.DownloadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlbumDownloadReconciliationTest {
    @Test
    fun recreatesAlbumRecordFromSurvivingOwnership() {
        val albums = missingOfflineAlbums(
            accountKey = ACCOUNT,
            albums = emptyList(),
            ownerships = listOf(ownership("track", position = 0)),
            requestedAtMs = 456,
        )

        assertEquals(
            listOf(OfflineAlbumEntity(ACCOUNT, ALBUM, 0, 456)),
            albums,
        )
    }

    @Test
    fun recreatesStandaloneJobFromSurvivingOwnership() {
        val repairs = missingStandaloneDownloadRecords(
            downloads = emptyList(),
            ownerships = listOf(
                DownloadOwnershipEntity(ACCOUNT, "track", "track", "track", 0),
            ),
            requestedAtMs = 456,
        )

        assertEquals(
            listOf(StandaloneDownloadRepair("track", 456, true, false)),
            repairs,
        )
    }

    @Test
    fun recreatesStandaloneOwnershipForOrphanedDownloadJob() {
        val repairs = missingStandaloneDownloadRecords(
            downloads = listOf(download("track")),
            ownerships = emptyList(),
            requestedAtMs = 456,
        )

        assertEquals(
            listOf(StandaloneDownloadRepair("track", 123, false, true)),
            repairs,
        )
    }

    @Test
    fun albumOwnershipPreventsStandaloneOwnershipFromBeingInvented() {
        val repairs = missingStandaloneDownloadRecords(
            downloads = listOf(download("track")),
            ownerships = listOf(ownership("track", position = 0)),
            requestedAtMs = 456,
        )

        assertEquals(emptyList(), repairs)
    }

    @Test
    fun zeroTrackLegacyAlbumNeedsServerRepair() {
        assertTrue(
            albumNeedsDownloadRepair(
                album = OfflineAlbumEntity(ACCOUNT, ALBUM, 0, 123),
                downloads = emptyList(),
                ownerships = emptyList(),
            ),
        )
    }

    @Test
    fun createsMissingJobsAndOwnershipsForCompleteAlbumMetadata() {
        val repairs = missingAlbumDownloadRecords(
            album = OfflineAlbumEntity(ACCOUNT, ALBUM, 2, 123),
            orderedTrackIds = listOf("first", "second"),
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
            album = OfflineAlbumEntity(ACCOUNT, ALBUM, 2, 123),
            orderedTrackIds = listOf("first"),
            downloads = emptyList(),
            ownerships = emptyList(),
        )

        assertEquals(emptyList(), repairs)
    }

    @Test
    fun repairsSurvivingOwnershipPositionFromServerOrder() {
        val repairs = missingAlbumDownloadRecords(
            album = OfflineAlbumEntity(ACCOUNT, ALBUM, 2, 123),
            orderedTrackIds = listOf("disc-two", "disc-one"),
            downloads = listOf(download("disc-two"), download("disc-one")),
            ownerships = listOf(
                ownership("disc-two", position = 1),
                ownership("disc-one", position = 0),
            ),
        )

        assertEquals(listOf(0, 1), repairs.map(AlbumDownloadRepair::position))
        assertEquals(listOf("disc-two", "disc-one"), repairs.map(AlbumDownloadRepair::trackId))
    }

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
