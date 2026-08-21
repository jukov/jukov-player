package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.feature.download.domain.DownloadErrorKind
import info.jukov.player.feature.download.domain.DownloadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadReconciliationTest {
    @Test
    fun legacyNetworkFailureIsQueuedWithItsFirstRetryDelay() {
        val decision = reconcileTrackState(
            track = track(
                state = DownloadState.Failed,
                error = "Connection reset",
                errorKind = null,
                retryCount = 0,
            ),
            localFileExists = false,
            nowMs = 10_000,
        )

        assertTrue(decision.hasPending)
        val update = decision.update!!
        assertEquals(DownloadState.Queued, update.state)
        assertEquals(DownloadErrorKind.Network, update.errorKind)
        assertEquals(1, update.retryCount)
        assertEquals(11_000, update.nextRetryAtMs)
    }

    @Test
    fun completedTrackWithMissingFileBecomesLocalFailure() {
        val decision = reconcileTrackState(
            track = track(
                state = DownloadState.Completed,
                relativePath = "track.mp3",
                downloadedBytes = 100,
                error = null,
                errorKind = null,
                retryCount = 3,
            ),
            localFileExists = false,
            nowMs = 10_000,
        )

        assertFalse(decision.hasPending)
        val update = decision.update!!
        assertEquals(DownloadState.Failed, update.state)
        assertEquals(DownloadErrorKind.Local, update.errorKind)
        assertEquals("Local file is missing", update.error)
        assertEquals(0, update.downloadedBytes)
        assertNull(update.relativePath)
        assertEquals(3, update.retryCount)
    }

    @Test
    fun queuedTrackRemainsUntouchedAndRequestsRecovery() {
        val decision = reconcileTrackState(
            track = track(
                state = DownloadState.Queued,
                error = null,
                errorKind = null,
                retryCount = 0,
            ),
            localFileExists = false,
            nowMs = 10_000,
        )

        assertTrue(decision.hasPending)
        assertNull(decision.update)
    }

    private fun track(
        state: DownloadState,
        relativePath: String? = null,
        downloadedBytes: Long = 0,
        error: String?,
        errorKind: String?,
        retryCount: Int,
    ) = OfflineTrackEntity(
        accountKey = "account",
        trackId = "track",
        relativePath = relativePath,
        expectedSize = 100,
        downloadedBytes = downloadedBytes,
        state = state.name,
        error = error,
        requestedAtMs = 1,
        completedAtMs = null,
        errorKind = errorKind,
        retryCount = retryCount,
    )
}
