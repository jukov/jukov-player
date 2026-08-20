package info.jukov.player

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.download.safeComponent
import info.jukov.player.feature.download.isSafeRelativePath
import info.jukov.player.feature.download.iosTaskDescription
import info.jukov.player.feature.download.isActiveDownloadAttempt
import info.jukov.player.feature.download.isApiErrorContentType
import info.jukov.player.feature.download.isCurrentDownloadGeneration
import info.jukov.player.feature.download.IosDownloadProgress
import info.jukov.player.feature.download.IosDownloadFinalization
import info.jukov.player.feature.download.IosDownloadTaskMetadata
import info.jukov.player.feature.download.IosBackgroundCallbackCoordinator
import info.jukov.player.feature.download.IosProgressCoalescer
import info.jukov.player.feature.download.IosLiveActivityProgress
import info.jukov.player.feature.download.IosLiveActivityProgressCoalescer
import info.jukov.player.feature.download.finalizeIosDownload
import info.jukov.player.feature.download.remainingIosDownloadCount
import info.jukov.player.feature.download.COMPLETION_NOTIFICATION_IDENTIFIER
import info.jukov.player.feature.download.iosNotificationsClearedOnDownloadStart
import info.jukov.player.feature.download.parseIosTaskDescription
import info.jukov.player.feature.playback.indexAfterQueueAppend
import info.jukov.player.feature.playback.playbackToggleAction
import info.jukov.player.feature.playback.playbackLoadableState
import info.jukov.player.feature.playback.playbackPositionMs
import info.jukov.player.feature.playback.PlaybackToggleAction
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.playback.isCurrentArtworkRequest
import info.jukov.player.feature.playback.shouldResumeAfterInterruption
import info.jukov.player.feature.playback.shouldPublishPlaybackFailure
import info.jukov.player.feature.playback.terminalPlaybackPositionMs
import info.jukov.player.core.data.cache.migrateLegacyDatabaseIfNeeded
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class SharedLogicIOSTest {

    @Test
    fun downloadsDeepLinkMatchesOnlyDownloadsDestination() {
        assertTrue(isIosDownloadsDeepLink("jukovplayer://downloads"))
        assertTrue(isIosDownloadsDeepLink("JUKOVPLAYER://DOWNLOADS/"))
        assertFalse(isIosDownloadsDeepLink("jukovplayer://library"))
        assertFalse(isIosDownloadsDeepLink("https://downloads"))
        assertFalse(isIosDownloadsDeepLink("not a url"))
    }

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
    fun onlyCurrentFailedPlayerItemPublishesPlaybackFailure() {
        assertTrue(shouldPublishPlaybackFailure(isCurrentItem = true, hasError = true))
        assertFalse(shouldPublishPlaybackFailure(isCurrentItem = false, hasError = true))
        assertFalse(shouldPublishPlaybackFailure(isCurrentItem = true, hasError = false))
    }

    @Test
    fun terminalPlaybackPositionFallsBackToTrackDuration() {
        assertEquals(12_345, terminalPlaybackPositionMs(0, 12_345))
        assertEquals(12_000, terminalPlaybackPositionMs(12_000, 12_345))
        assertEquals(0, terminalPlaybackPositionMs(0, null))
    }

    @Test
    fun pendingSeekPositionIsStableUntilNativeSeekCompletes() {
        assertEquals(
            8_000,
            playbackPositionMs(
                positionOverrideMs = null,
                pendingSeekPositionMs = 8_000,
                currentPositionMs = 2_000,
            ),
        )
        assertEquals(
            9_000,
            playbackPositionMs(
                positionOverrideMs = 9_000,
                pendingSeekPositionMs = 8_000,
                currentPositionMs = 2_000,
            ),
        )
    }

    @Test
    fun playbackFailureIsRetainedWhilePublishingProgress() {
        val snapshot = PlaybackSnapshot(positionMs = 500)

        assertEquals(
            LoadableState.Failure(AppError.PlayerConnectionFailed, snapshot),
            playbackLoadableState(snapshot, AppError.PlayerConnectionFailed),
        )
        assertEquals(
            LoadableState.Content(snapshot),
            playbackLoadableState(snapshot, null),
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

    @Test
    fun progressCallbacksAreCoalescedToTheLatestValue() {
        val coalescer = IosProgressCoalescer()
        val metadata = IosDownloadTaskMetadata("track", "account", "track-id")

        assertTrue(coalescer.offer(IosDownloadProgress(metadata, 7u, 10, 100)))
        repeat(1_000) { index ->
            assertFalse(
                coalescer.offer(
                    IosDownloadProgress(metadata, 7u, index.toLong() + 11, 100),
                ),
            )
        }

        assertEquals(1_010, coalescer.take(7u)?.downloadedBytes)
        assertFalse(coalescer.completeFlush(7u))
    }

    @Test
    fun liveActivityPublishesOnlyChangedProgress() {
        val coalescer = IosLiveActivityProgressCoalescer()
        val progress = IosLiveActivityProgress(percent = 25, pendingCount = 2)

        assertTrue(coalescer.shouldPublish(progress))
        assertFalse(coalescer.shouldPublish(progress))
        assertTrue(coalescer.shouldPublish(progress.copy(percent = 26)))
        coalescer.reset()
        assertTrue(coalescer.shouldPublish(progress.copy(percent = 26)))
    }

    @Test
    fun cancelledDownloadsAreExcludedFromLiveActivityQueueSize() {
        val pending = setOf("one", "two", "three")

        assertEquals(2, remainingIosDownloadCount(pending, setOf("two")))
        assertEquals(0, remainingIosDownloadCount(pending, pending))
    }

    @Test
    fun startingDownloadClearsPreviousCompletionNotification() {
        assertEquals(
            setOf(COMPLETION_NOTIFICATION_IDENTIFIER),
            iosNotificationsClearedOnDownloadStart(),
        )
    }

    @Test
    fun progressArrivingDuringAFlushSchedulesOneFollowUp() {
        val coalescer = IosProgressCoalescer()
        val metadata = IosDownloadTaskMetadata("track", "account", "track-id")

        assertTrue(coalescer.offer(IosDownloadProgress(metadata, 9u, 10, 100)))
        assertEquals(10, coalescer.take(9u)?.downloadedBytes)
        assertFalse(coalescer.offer(IosDownloadProgress(metadata, 9u, 90, 100)))
        assertTrue(coalescer.completeFlush(9u))
        assertEquals(90, coalescer.take(9u)?.downloadedBytes)
        assertFalse(coalescer.completeFlush(9u))
    }

    @Test
    fun backgroundCompletionWaitsForEveryPersistedCallback() {
        val coordinator = IosBackgroundCallbackCoordinator()
        var completions = 0

        coordinator.beginProcessing()
        assertNull(coordinator.register { completions++ })
        assertNull(coordinator.finishEvents())
        assertEquals(0, completions)

        val completion = assertNotNull(coordinator.endProcessing())
        completion()
        assertEquals(1, completions)
        assertNull(coordinator.finishEvents())
    }

    @Test
    fun backgroundCompletionWaitsForNotificationReplacementAfterProgressFlush() {
        val coordinator = IosBackgroundCallbackCoordinator()

        coordinator.beginProcessing() // Progress flush.
        coordinator.beginProcessing() // Notification-center replacement callback.
        assertNull(coordinator.register { })
        assertNull(coordinator.finishEvents())
        assertNull(coordinator.endProcessing())
        assertNotNull(coordinator.endProcessing())
    }

    @Test
    fun backgroundCompletionHandlesSystemCallbackBeforeRuntimeRegistration() {
        val coordinator = IosBackgroundCallbackCoordinator()
        var completions = 0

        assertNull(coordinator.finishEvents())
        val completion = assertNotNull(coordinator.register { completions++ })
        completion()

        assertEquals(1, completions)
    }

    @Test
    fun cancellationRacingWithCompletionCannotLeaveACommittedFile() = runTest {
        var activeCheck = 0
        var fileExists = false
        var databaseCommitted = false

        val result = finalizeIosDownload(
            isActive = {
                activeCheck++
                activeCheck == 1
            },
            moveToDestination = {
                fileExists = true
                true
            },
            commit = {
                databaseCommitted = true
                true
            },
            removeDestination = { fileExists = false },
        )

        assertEquals(IosDownloadFinalization.Cancelled, result)
        assertFalse(fileExists)
        assertFalse(databaseCommitted)
    }

    @Test
    fun rejectedDatabaseCommitRemovesFinalizedFile() = runTest {
        var fileExists = false

        val result = finalizeIosDownload(
            isActive = { true },
            moveToDestination = {
                fileExists = true
                true
            },
            commit = { false },
            removeDestination = { fileExists = false },
        )

        assertEquals(IosDownloadFinalization.CommitRejected, result)
        assertFalse(fileExists)
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
