package info.jukov.player.feature.download.data

import info.jukov.player.feature.auth.domain.AuthSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadMutationCoordinatorTest {
    @Test
    fun removalFinishesBeforeAConcurrentEnqueueMutationStarts() = runTest {
        val coordinator = DownloadMutationCoordinator()
        val releaseRemoval = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val removal = launch {
            coordinator.run {
                events += "remove-start"
                releaseRemoval.await()
                events += "remove-finish"
            }
        }
        val enqueue = launch {
            coordinator.run {
                events += "enqueue"
            }
        }

        runCurrent()
        assertEquals(listOf("remove-start"), events)

        releaseRemoval.complete(Unit)
        removal.join()
        enqueue.join()

        assertEquals(listOf("remove-start", "remove-finish", "enqueue"), events)
    }

    @Test
    fun destructiveMutationDoesNotWaitForFetchAndRejectsItsLateCommit() = runTest {
        val coordinator = DownloadMutationCoordinator()
        val (generation, _) = coordinator.snapshot { "account" }!!
        val releaseFetch = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val fetch = launch {
            releaseFetch.await()
            val committed = coordinator.runIfCurrent(generation) {
                events += "late-commit"
            }
            events += "committed=$committed"
        }
        val removal = launch {
            coordinator.invalidateAndRun {
                events += "removed"
            }
        }

        runCurrent()
        removal.join()
        assertEquals(listOf("removed"), events)

        releaseFetch.complete(Unit)
        fetch.join()

        assertEquals(listOf("removed", "committed=false"), events)
    }

    @Test
    fun suspendedAlbumFetchDoesNotCommitAfterDestructiveMutation() = runTest {
        val coordinator = DownloadMutationCoordinator()
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val download = async {
            fetchAndCommitIfCurrentAccount(
                coordinator = coordinator,
                session = { SESSION },
                fetch = {
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    "album-tracks"
                },
                commit = { _, fetched -> events += "committed:$fetched" },
            )
        }

        fetchStarted.await()
        coordinator.invalidateAndRun { events += "removed" }
        releaseFetch.complete(Unit)

        assertFalse(download.await())
        assertEquals(listOf("removed"), events)
    }

    @Test
    fun suspendedAlbumFetchDoesNotCommitAfterAccountSwitch() = runTest {
        val coordinator = DownloadMutationCoordinator()
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        var currentSession = SESSION
        val commits = mutableListOf<String>()

        val download = async {
            fetchAndCommitIfCurrentAccount(
                coordinator = coordinator,
                session = { currentSession },
                fetch = {
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    "album-tracks"
                },
                commit = { _, fetched -> commits += fetched },
            )
        }

        fetchStarted.await()
        currentSession = AuthSession("https://other.example", "user", "token", "salt")
        releaseFetch.complete(Unit)

        assertFalse(download.await())
        assertEquals(emptyList(), commits)
    }

    private companion object {
        val SESSION = AuthSession("https://music.example", "user", "token", "salt")
    }
}
