package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.AlbumEntity
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.DownloadOwnershipEntity
import info.jukov.player.core.data.cache.OfflineAlbumEntity
import info.jukov.player.core.data.cache.OfflineArtworkEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.core.data.cache.TrackEntity
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.download.domain.DownloadFailureSummary
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.download.domain.OfflineAlbum
import info.jukov.player.feature.download.domain.OfflineLibrary
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.subsonic.data.SubsonicApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal data class OfflineLibrarySnapshot(
    val visibleDownloads: List<OfflineTrackEntity>,
    val allDownloads: List<OfflineTrackEntity>,
    val offlineAlbums: List<OfflineAlbumEntity>,
    val ownerships: List<DownloadOwnershipEntity>,
    val artworks: List<OfflineArtworkEntity>,
    val tracks: List<TrackEntity>,
    val albums: List<AlbumEntity>,
)

internal class OfflineLibraryBuilder(
    private val platform: OfflinePlatform,
    private val client: SubsonicApiClient,
) {
    fun build(session: AuthSession, snapshot: OfflineLibrarySnapshot): OfflineLibrary {
        val accountKey = session.accountKey
        val tracksById = snapshot.tracks.associateBy(TrackEntity::id)
        val albumsById = snapshot.albums.associateBy(AlbumEntity::id)
        val artworkPaths = snapshot.artworks
            .filter { it.state == DownloadState.Completed.name }
            .associate { it.coverArtId to it.relativePath }
        val allOfflineTracks = snapshot.allDownloads.mapNotNull { download ->
            val track = tracksById[download.trackId] ?: return@mapNotNull null
            download.trackId to download.toOfflineTrack(
                metadata = track,
                key = accountKey,
                platform = platform,
                client = client,
                session = session,
                artworkPath = artworkPaths[track.coverArtId],
            )
        }.toMap()
        val visibleTrackIds = snapshot.visibleDownloads.mapTo(hashSetOf()) { it.trackId }
        return OfflineLibrary(
            tracks = allOfflineTracks.filterKeys { it in visibleTrackIds }.values.toList(),
            albums = snapshot.offlineAlbums.mapNotNull { offlineAlbum ->
                val album = albumsById[offlineAlbum.albumId] ?: return@mapNotNull null
                val albumTracks = snapshot.ownerships.asSequence()
                    .filter {
                        it.ownerType == DOWNLOAD_OWNER_ALBUM && it.ownerId == offlineAlbum.albumId
                    }
                    .sortedBy { it.position }
                    .mapNotNull { allOfflineTracks[it.trackId] }
                    .toList()
                OfflineAlbum(
                    album = album.toAlbum(
                        key = accountKey,
                        platform = platform,
                        client = client,
                        session = session,
                        artworkPath = artworkPaths[album.coverArtId],
                    ),
                    tracks = albumTracks,
                    expectedTrackCount = offlineAlbum.trackCount,
                    requestedAtMs = offlineAlbum.requestedAtMs,
                )
            },
        )
    }
}

internal class OfflineLibraryDataSource(
    private val dao: CacheDao,
    private val builder: OfflineLibraryBuilder,
) {
    fun observe(session: AuthSession, query: String?): Flow<OfflineLibrary> {
        val accountKey = session.accountKey
        val metadata = combine(
            dao.observeAccountTracks(accountKey),
            dao.observeAccountAlbums(accountKey),
        ) { tracks, albums -> tracks to albums }
        val downloads = if (query == null) {
            dao.observeOfflineTracks(accountKey).map { it to it }
        } else {
            combine(
                dao.observeOfflineTracksSearch(accountKey, query),
                dao.observeOfflineTracks(accountKey),
            ) { visible, all -> visible to all }
        }
        return combine(
            downloads,
            if (query == null) {
                dao.observeOfflineAlbums(accountKey)
            } else {
                dao.observeOfflineAlbumsSearch(accountKey, query)
            },
            dao.observeDownloadOwnerships(accountKey),
            dao.observeOfflineArtworks(accountKey),
            metadata,
        ) { downloadLists, offlineAlbums, ownerships, artworks, metadataLists ->
            builder.build(
                session = session,
                snapshot = OfflineLibrarySnapshot(
                    visibleDownloads = downloadLists.first,
                    allDownloads = downloadLists.second,
                    offlineAlbums = offlineAlbums,
                    ownerships = ownerships,
                    artworks = artworks,
                    tracks = metadataLists.first,
                    albums = metadataLists.second,
                ),
            )
        }
    }

    fun observeTrackStatuses(accountKey: String): Flow<Map<String, DownloadStatus>> =
        dao.observeOfflineTracks(accountKey).map { tracks ->
            tracks.associate { it.trackId to it.toDownloadStatus() }
        }

    fun observeFailureSummary(accountKey: String): Flow<DownloadFailureSummary> =
        dao.observeOfflineTracks(accountKey).map { it.toFailureSummary() }
}
