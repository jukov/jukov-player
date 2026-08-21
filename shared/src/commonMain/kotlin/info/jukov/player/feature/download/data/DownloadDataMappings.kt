package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.AlbumEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.core.data.cache.TrackEntity
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.download.domain.DownloadErrorKind
import info.jukov.player.feature.download.domain.DownloadFailureReason
import info.jukov.player.feature.download.domain.DownloadFailureSummary
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.feature.download.domain.OfflineTrack
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.subsonic.data.SubsonicApiClient

internal const val DOWNLOAD_OWNER_TRACK = "track"
internal const val DOWNLOAD_OWNER_ALBUM = "album"

internal fun OfflineTrackEntity.toDownloadStatus() = DownloadStatus(
    state = DownloadState.valueOf(state),
    downloadedBytes = downloadedBytes,
    totalBytes = expectedSize,
    error = error,
    errorKind = DownloadErrorKind.entries.firstOrNull { it.name == errorKind },
    retryCount = retryCount,
)

internal fun List<OfflineTrackEntity>.toFailureSummary(): DownloadFailureSummary {
    val failed = filter { it.state == DownloadState.Failed.name }
    return DownloadFailureSummary(
        count = failed.size,
        reasons = failed.groupBy { track ->
            DownloadErrorKind.entries.firstOrNull { kind -> kind.name == track.errorKind }
                ?: DownloadErrorKind.Unknown
        }.flatMap { (kind, items) ->
            items.groupBy { it.error.takeIf { kind == DownloadErrorKind.Http } }
                .map { (detail, grouped) -> DownloadFailureReason(kind, detail, grouped.size) }
        },
    )
}

internal fun OfflineTrackEntity.toOfflineTrack(
    metadata: TrackEntity,
    key: String,
    platform: OfflinePlatform,
    client: SubsonicApiClient,
    session: AuthSession,
    artworkPath: String? = null,
) = OfflineTrack(
    track = Track(
        id = trackId,
        title = metadata.title,
        artist = metadata.artist,
        album = metadata.album,
        albumId = metadata.albumId,
        artistId = metadata.artistId,
        trackNumber = metadata.trackNumber,
        year = metadata.year,
        coverArtId = metadata.coverArtId,
        coverArtUrl = artworkPath?.let { platform.fileUri(key, it) }
            ?: metadata.coverArtId?.let {
                client.buildUrl("getCoverArt", session, mapOf("id" to it))
            },
        streamUrl = relativePath?.let { platform.fileUri(key, it) },
        durationMs = metadata.durationMs,
        contentType = metadata.contentType,
        isFavorite = metadata.isFavorite,
    ),
    status = toDownloadStatus(),
    requestedAtMs = requestedAtMs,
)

internal fun AlbumEntity.toAlbum(
    key: String,
    platform: OfflinePlatform,
    client: SubsonicApiClient,
    session: AuthSession,
    artworkPath: String?,
) = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    coverArtId = coverArtId,
    coverArtUrl = artworkPath?.let { platform.fileUri(key, it) }
        ?: coverArtId?.let { client.buildUrl("getCoverArt", session, mapOf("id" to it)) },
    isFavorite = isFavorite,
    year = year,
)
