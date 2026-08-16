package info.jukov.player.feature.download.domain

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.track.domain.Track

enum class DownloadState { Queued, Downloading, Completed, Failed }

enum class DownloadErrorKind { Network, Http, Local, Authentication, InvalidResponse, Unknown }

const val MAX_AUTOMATIC_DOWNLOAD_RETRIES = 5

fun downloadRetryDelayMs(retryCount: Int): Long = when (retryCount) {
    1 -> 1_000L
    2 -> 2_000L
    3 -> 4_000L
    4 -> 8_000L
    else -> 16_000L
}

data class DownloadStatus(
    val state: DownloadState,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val error: String? = null,
    val errorKind: DownloadErrorKind? = null,
    val retryCount: Int = 0,
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
        get() {
            val total = tracks.mapNotNull { it.status.totalBytes }.takeIf { it.size == tracks.size }
                ?.sum()
            val downloaded = tracks.sumOf { it.status.downloadedBytes }
            val state = when {
                tracks.size == expectedTrackCount && tracks.isNotEmpty() &&
                    tracks.all { it.status.state == DownloadState.Completed } -> DownloadState.Completed
                tracks.any { it.status.state == DownloadState.Failed } -> DownloadState.Failed
                tracks.any { it.status.state == DownloadState.Downloading } -> DownloadState.Downloading
                tracks.any { it.status.state == DownloadState.Queued } -> DownloadState.Queued
                else -> DownloadState.Failed
            }
            return DownloadStatus(state, downloaded, total)
        }
}

data class OfflineLibrary(
    val tracks: List<OfflineTrack> = emptyList(),
    val albums: List<OfflineAlbum> = emptyList(),
)

data class DownloadFailureSummary(
    val count: Int = 0,
    val reasons: List<DownloadFailureReason> = emptyList(),
)

data class DownloadFailureReason(
    val kind: DownloadErrorKind,
    val detail: String?,
    val count: Int,
)
