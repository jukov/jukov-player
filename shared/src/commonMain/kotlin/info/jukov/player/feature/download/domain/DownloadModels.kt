package info.jukov.player.feature.download.domain

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.track.domain.Track

enum class DownloadState { Queued, Downloading, Completed, Failed }

data class DownloadStatus(
    val state: DownloadState,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val error: String? = null,
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { downloadedBytes.toFloat() / it }
}

sealed interface DownloadTarget {
    val id: String
    data class Track(override val id: String) : DownloadTarget
    data class Album(override val id: String) : DownloadTarget
}

data class OfflineTrack(val track: Track, val status: DownloadStatus, val requestedAtMs: Long = 0)

data class OfflineAlbum(
    val album: Album,
    val tracks: List<OfflineTrack>,
    val expectedTrackCount: Int,
    val requestedAtMs: Long = 0,
) {
    val status: DownloadStatus
        get() = aggregateDownloadStatus(expectedTrackCount, tracks.map(OfflineTrack::status))
}

fun aggregateDownloadStatus(
    expectedTrackCount: Int,
    tracks: List<DownloadStatus>,
): DownloadStatus {
    val total = tracks.mapNotNull(DownloadStatus::totalBytes).takeIf { it.size == tracks.size }
        ?.sum()
    val downloaded = tracks.sumOf(DownloadStatus::downloadedBytes)
    val state = when {
        tracks.size == expectedTrackCount && tracks.isNotEmpty() &&
            tracks.all { it.state == DownloadState.Completed } -> DownloadState.Completed
        tracks.any { it.state == DownloadState.Downloading } -> DownloadState.Downloading
        tracks.any { it.state == DownloadState.Queued } -> DownloadState.Queued
        else -> DownloadState.Failed
    }
    return DownloadStatus(state, downloaded, total)
}

data class OfflineLibrary(
    val tracks: List<OfflineTrack> = emptyList(),
    val albums: List<OfflineAlbum> = emptyList(),
)
