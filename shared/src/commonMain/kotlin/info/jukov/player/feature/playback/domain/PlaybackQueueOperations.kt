package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.track.domain.Track

internal fun appendQueueItems(queue: List<Track>, tracks: List<Track>): List<Track> = queue + tracks

internal fun moveFutureQueueItem(
    queue: List<Track>,
    currentIndex: Int,
    fromIndex: Int,
    toIndex: Int,
): List<Track> {
    if (fromIndex !in queue.indices || toIndex !in queue.indices) return queue
    if (fromIndex <= currentIndex || toIndex <= currentIndex || fromIndex == toIndex) return queue
    return queue.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

internal fun removeFutureQueueItems(
    queue: List<Track>,
    currentIndex: Int,
    indices: Set<Int>,
): List<Track> {
    return queue.filterIndexed { index, _ -> index <= currentIndex || index !in indices }
}

internal fun moveFutureQueueItemsToTop(
    queue: List<Track>,
    currentIndex: Int,
    indices: Set<Int>,
): List<Track> {
    val selected = indices.filter { it in queue.indices && it > currentIndex }.sorted()
    if (selected.isEmpty()) return queue
    val selectedSet = selected.toSet()
    val prefix = queue.take(currentIndex + 1)
    val moved = selected.map(queue::get)
    val remaining = queue.drop(currentIndex + 1).filterIndexed { offset, _ ->
        currentIndex + 1 + offset !in selectedSet
    }
    return prefix + moved + remaining
}
