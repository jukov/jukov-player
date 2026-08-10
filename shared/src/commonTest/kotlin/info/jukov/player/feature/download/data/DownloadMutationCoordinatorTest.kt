package info.jukov.player.feature.download.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
