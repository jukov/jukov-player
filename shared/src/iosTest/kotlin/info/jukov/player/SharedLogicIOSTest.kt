package info.jukov.player

import info.jukov.player.feature.download.safeComponent
import info.jukov.player.feature.download.isSafeRelativePath
import info.jukov.player.feature.download.iosTaskDescription
import info.jukov.player.feature.download.isActiveDownloadAttempt
import info.jukov.player.feature.download.isApiErrorContentType
import info.jukov.player.feature.download.isCurrentDownloadGeneration
import info.jukov.player.feature.download.parseIosTaskDescription
import info.jukov.player.feature.playback.indexAfterQueueAppend
import info.jukov.player.feature.playback.playbackToggleAction
import info.jukov.player.feature.playback.PlaybackToggleAction
import info.jukov.player.feature.playback.isCurrentArtworkRequest
import info.jukov.player.feature.playback.shouldResumeAfterInterruption
import info.jukov.player.core.data.cache.migrateLegacyDatabaseIfNeeded
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class SharedLogicIOSTest {

    @Test
    fun offlinePathComponentsAreStableAndAccountScoped() {
        val first = safeComponent("https://music.example|listener")
        val repeated = safeComponent("https://music.example|listener")
        val other = safeComponent("https://music.example|other")

        assertEquals(first, repeated)
        assertNotEquals(first, other)
        assertEquals(32, first.length)
    }

    @Test
    fun offlinePathsCannotEscapeAccountDirectory() {
        assertTrue(isSafeRelativePath("tracks/song.audio"))
        assertFalse(isSafeRelativePath("../song.audio"))
        assertFalse(isSafeRelativePath("tracks/../../song.audio"))
        assertFalse(isSafeRelativePath("/tmp/song.audio"))
        assertFalse(isSafeRelativePath("tracks\\..\\song.audio"))
    }

    @Test
    fun backgroundTaskMetadataIsAccountScopedWithoutCredentials() {
        val accountKey = "https://music.example|listener|secret"
        val description = iosTaskDescription("track", accountKey, "folder:track-id")
        val metadata = parseIosTaskDescription(description)

        assertEquals("track", metadata?.kind)
        assertEquals(safeComponent(accountKey), metadata?.accountToken)
        assertEquals("folder:track-id", metadata?.id)
        assertFalse(description.contains("listener"))
        assertFalse(description.contains("secret"))
        assertNull(parseIosTaskDescription("track:missing-id"))
        assertNull(parseIosTaskDescription("unknown:token:id"))
    }

    @Test
    fun appendAfterExhaustionSelectsFirstNewTrack() {
        assertEquals(
            3,
            indexAfterQueueAppend(
                previousQueueSize = 3,
                currentIndex = 2,
                playerWasExhausted = true,
            ),
        )
        assertEquals(
            1,
            indexAfterQueueAppend(
                previousQueueSize = 3,
                currentIndex = 1,
                playerWasExhausted = false,
            ),
        )
    }

    @Test
    fun pauseIntentDoesNotDependOnPlayerRateWhileBuffering() {
        assertEquals(PlaybackToggleAction.Pause, playbackToggleAction(playWhenReady = true))
        assertEquals(PlaybackToggleAction.Play, playbackToggleAction(playWhenReady = false))
    }

    @Test
    fun pauseDuringInterruptionPreventsAutomaticResume() {
        assertFalse(
            shouldResumeAfterInterruption(
                playWhenReady = false,
                systemAllowsResume = true,
            ),
        )
        assertTrue(
            shouldResumeAfterInterruption(
                playWhenReady = true,
                systemAllowsResume = true,
            ),
        )
    }

    @Test
    fun staleArtworkRequestCannotOverwriteReplacementPlayback() {
        assertFalse(
            isCurrentArtworkRequest(3, 4, "track|old", "track|new"),
        )
        assertTrue(
            isCurrentArtworkRequest(4, 4, "track|new", "track|new"),
        )
    }

    @Test
    fun cancellationInvalidatesPreviouslySubmittedSchedulingWork() {
        assertTrue(isCurrentDownloadGeneration(submitted = 7, current = 7))
        assertFalse(isCurrentDownloadGeneration(submitted = 7, current = 8))
    }

    @Test
    fun delayedCancellationCallbackCannotFailReplacementAttempt() {
        val cancelledAttempts = setOf<ULong>(41u)

        assertFalse(isActiveDownloadAttempt(41u, cancelledAttempts))
        assertTrue(isActiveDownloadAttempt(42u, cancelledAttempts))
    }

    @Test
    fun apiErrorPayloadsAreNotAcceptedAsDownloadedMedia() {
        assertTrue(isApiErrorContentType("application/json; charset=utf-8"))
        assertTrue(isApiErrorContentType("application/xml"))
        assertTrue(isApiErrorContentType("text/xml; charset=UTF-8"))
        assertFalse(isApiErrorContentType("audio/mpeg"))
        assertFalse(isApiErrorContentType("image/jpeg"))
        assertFalse(isApiErrorContentType(null))
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun legacyDatabaseAndSidecarsMoveToApplicationSupportLocation() {
        val fileManager = NSFileManager.defaultManager
        val directory = "${NSTemporaryDirectory()}/jukov-db-${NSUUID.UUID().UUIDString}"
        assertTrue(
            fileManager.createDirectoryAtPath(
                path = directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            ),
        )
        val legacyPath = "$directory/legacy.db"
        val destinationPath = "$directory/application-support.db"
        listOf("", "-wal", "-shm").forEach { suffix ->
            assertTrue(
                fileManager.createFileAtPath(
                    path = legacyPath + suffix,
                    contents = null,
                    attributes = null,
                ),
            )
        }
        try {
            assertEquals(
                destinationPath,
                migrateLegacyDatabaseIfNeeded(fileManager, legacyPath, destinationPath),
            )
            listOf("", "-wal", "-shm").forEach { suffix ->
                assertTrue(fileManager.fileExistsAtPath(destinationPath + suffix))
                assertFalse(fileManager.fileExistsAtPath(legacyPath + suffix))
            }
        } finally {
            fileManager.removeItemAtPath(directory, error = null)
        }
    }
}
