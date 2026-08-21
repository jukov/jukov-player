package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.download.domain.DownloadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DownloadDataDecisionsTest {
    @Test
    fun albumCancellationRemovesOnlyTracksWithoutAnotherOwner() {
        val tracks = listOf(offlineTrack("album-only"), offlineTrack("also-track-owned"))

        val removable = trackIdsWithoutOtherOwners(
            tracks = tracks,
            ownershipCounts = mapOf("album-only" to 1, "also-track-owned" to 2),
        )

        assertEquals(listOf("album-only"), removable)
    }

    @Test
    fun localMediaResolutionReturnsOnlyCompletedExistingFiles() {
        val items = listOf(
            Media("ready", "ready.mp3", DownloadState.Completed.name),
            Media("missing", "missing.mp3", DownloadState.Completed.name),
            Media("queued", "queued.mp3", DownloadState.Queued.name),
            Media("no-path", null, DownloadState.Completed.name),
        )

        val uris = resolveLocalMediaUris(
            items = items,
            id = Media::id,
            relativePath = Media::path,
            state = Media::state,
            exists = { it != "missing.mp3" },
            fileUri = { "file://$it" },
        )

        assertEquals(mapOf("ready" to "file://ready.mp3"), uris)
    }

    @Test
    fun albumFetchCommitAcceptsCredentialRefreshButRejectsAccountSwitch() {
        val requested = session(server = "https://one.example", username = "user", token = "old")
        val refreshed = session(server = "https://one.example/", username = "USER", token = "new")
        val switched = session(server = "https://two.example", username = "user", token = "new")

        assertSame(refreshed, matchingAccountSession(requested, refreshed))
        assertNull(matchingAccountSession(requested, switched))
        assertNull(matchingAccountSession(requested, currentSession = null))
    }

    private fun offlineTrack(id: String) = OfflineTrackEntity(
        accountKey = "account",
        trackId = id,
        relativePath = null,
        expectedSize = null,
        downloadedBytes = 0,
        state = DownloadState.Queued.name,
        error = null,
        requestedAtMs = 1,
        completedAtMs = null,
    )

    private fun session(server: String, username: String, token: String) = AuthSession(
        serverUrl = server,
        username = username,
        token = token,
        salt = "salt",
    )

    private data class Media(val id: String, val path: String?, val state: String)
}
