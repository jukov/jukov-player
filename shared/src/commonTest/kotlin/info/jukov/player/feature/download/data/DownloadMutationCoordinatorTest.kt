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
}
