package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.track.domain.Track
import kotlin.random.Random

data class PlaybackQueueOrder(
    val queue: List<Track>,
    val canonicalQueue: List<Track>,
)

fun enableShuffle(
    queue: List<Track>,
    currentIndex: Int,
    random: Random = Random.Default,
): PlaybackQueueOrder {
    if (currentIndex !in queue.indices) {
        return PlaybackQueueOrder(queue, queue)
    }
    return PlaybackQueueOrder(
        queue = queue.take(currentIndex + 1) + queue.drop(currentIndex + 1).shuffled(random),
        canonicalQueue = queue,
    )
}

fun disableShuffle(
    queue: List<Track>,
    canonicalQueue: List<Track>,
    currentIndex: Int,
): PlaybackQueueOrder {
    if (currentIndex !in queue.indices) {
        return PlaybackQueueOrder(queue, queue)
    }
    val played = queue.take(currentIndex + 1)
    val remaining = canonicalQueue.removeOccurrences(played)
    return PlaybackQueueOrder(played + remaining, played + remaining)
}

fun updateCanonicalQueue(
    previousQueue: List<Track>,
    newQueue: List<Track>,
    canonicalQueue: List<Track>,
    currentIndex: Int,
): List<Track> {
    if (currentIndex !in newQueue.indices) {
        return newQueue
    }
    val previousFuture = previousQueue.drop(currentIndex + 1)
    val newFuture = newQueue.drop(currentIndex + 1)
    val removed = previousFuture.removeOccurrences(newFuture)
    val added = newFuture.removeOccurrences(previousFuture)
    val canonicalFuture = canonicalQueue
        .removeOccurrences(newQueue.take(currentIndex + 1))
        .removeOccurrences(removed)
    return newQueue.take(currentIndex + 1) + canonicalFuture + added
}

private fun List<Track>.removeOccurrences(items: List<Track>): List<Track> {
    val remaining = toMutableList()
    items.forEach { item ->
        val index = remaining.indexOf(item)
        if (index >= 0) {
            remaining.removeAt(index)
        }
    }
    return remaining
}
