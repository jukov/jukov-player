package info.jukov.player.core.data.cache

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    @Query("SELECT * FROM TrackEntity WHERE accountKey=:accountKey")
    fun observeAccountTracks(accountKey: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM AlbumEntity WHERE accountKey=:accountKey")
    fun observeAccountAlbums(accountKey: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM TrackEntity WHERE accountKey=:accountKey AND id=:trackId")
    suspend fun track(accountKey: String, trackId: String): TrackEntity?

    @Query("SELECT * FROM OfflineTrackEntity WHERE accountKey=:accountKey AND EXISTS (SELECT 1 FROM DownloadOwnershipEntity o WHERE o.accountKey=OfflineTrackEntity.accountKey AND o.trackId=OfflineTrackEntity.trackId) ORDER BY requestedAtMs DESC")
    fun observeOfflineTracks(accountKey: String): Flow<List<OfflineTrackEntity>>

    @Query("""
        SELECT d.* FROM OfflineTrackEntity d
        JOIN TrackEntity t ON t.accountKey=d.accountKey AND t.id=d.trackId
        WHERE d.accountKey=:accountKey
          AND EXISTS (
              SELECT 1 FROM DownloadOwnershipEntity o
              WHERE o.accountKey=d.accountKey AND o.trackId=d.trackId
          )
          AND (
              t.title LIKE '%' || :query || '%' COLLATE NOCASE
              OR t.artist LIKE '%' || :query || '%' COLLATE NOCASE
              OR COALESCE(t.album, '') LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY d.requestedAtMs DESC
    """)
    fun observeOfflineTracksSearch(accountKey: String, query: String): Flow<List<OfflineTrackEntity>>

    @Query("SELECT * FROM OfflineAlbumEntity WHERE accountKey=:accountKey ORDER BY requestedAtMs DESC")
    fun observeOfflineAlbums(accountKey: String): Flow<List<OfflineAlbumEntity>>

    @Query("SELECT * FROM OfflineAlbumEntity WHERE accountKey=:accountKey ORDER BY requestedAtMs DESC")
    suspend fun allOfflineAlbums(accountKey: String): List<OfflineAlbumEntity>

    @Query("""
        SELECT d.* FROM OfflineAlbumEntity d
        JOIN AlbumEntity a ON a.accountKey=d.accountKey AND a.id=d.albumId
        WHERE d.accountKey=:accountKey
          AND (
              a.name LIKE '%' || :query || '%' COLLATE NOCASE
              OR a.artist LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY d.requestedAtMs DESC
    """)
    fun observeOfflineAlbumsSearch(accountKey: String, query: String): Flow<List<OfflineAlbumEntity>>

    @Query("SELECT * FROM DownloadOwnershipEntity WHERE accountKey=:accountKey")
    fun observeDownloadOwnerships(accountKey: String): Flow<List<DownloadOwnershipEntity>>

    @Query("SELECT * FROM DownloadOwnershipEntity WHERE accountKey=:accountKey")
    suspend fun downloadOwnerships(accountKey: String): List<DownloadOwnershipEntity>

    @Query("SELECT * FROM OfflineArtworkEntity WHERE accountKey=:accountKey")
    fun observeOfflineArtworks(accountKey: String): Flow<List<OfflineArtworkEntity>>

    @Query("SELECT * FROM OfflineTrackEntity WHERE accountKey=:accountKey AND trackId=:trackId")
    fun observeOfflineTrack(accountKey: String, trackId: String): Flow<OfflineTrackEntity?>

    @Query("SELECT * FROM OfflineTrackEntity WHERE accountKey=:accountKey AND trackId=:trackId")
    suspend fun offlineTrack(accountKey: String, trackId: String): OfflineTrackEntity?

    @Query("SELECT * FROM OfflineTrackEntity WHERE accountKey=:accountKey AND trackId IN (:trackIds)")
    suspend fun offlineTracks(accountKey: String, trackIds: List<String>): List<OfflineTrackEntity>

    @Query("SELECT * FROM TrackEntity WHERE accountKey=:accountKey AND id IN (:trackIds)")
    suspend fun tracks(accountKey: String, trackIds: List<String>): List<TrackEntity>

    @Query("SELECT * FROM OfflineArtworkEntity WHERE accountKey=:accountKey AND coverArtId=:coverArtId")
    suspend fun offlineArtwork(accountKey: String, coverArtId: String): OfflineArtworkEntity?

    @Query("SELECT * FROM OfflineArtworkEntity WHERE accountKey=:accountKey AND coverArtId IN (:coverArtIds)")
    suspend fun offlineArtworks(accountKey: String, coverArtIds: List<String>): List<OfflineArtworkEntity>

    @Query("SELECT * FROM OfflineTrackEntity WHERE accountKey=:accountKey AND state IN ('Queued','Downloading')")
    suspend fun pendingOfflineTracks(accountKey: String): List<OfflineTrackEntity>

    @Query("SELECT COUNT(*) FROM OfflineTrackEntity WHERE accountKey=:accountKey AND state IN ('Queued','Downloading')")
    suspend fun pendingOfflineTrackCount(accountKey: String): Int

    @Query("SELECT COUNT(*) FROM OfflineTrackEntity WHERE accountKey=:accountKey AND state='Completed'")
    suspend fun completedOfflineTrackCount(accountKey: String): Int

    @Query("UPDATE OfflineTrackEntity SET state='Failed', error=:message WHERE accountKey=:accountKey AND state IN ('Queued','Downloading')")
    suspend fun failPendingOfflineTracks(accountKey: String, message: String)

    @Query("SELECT * FROM OfflineTrackEntity WHERE accountKey=:accountKey")
    suspend fun allOfflineTracks(accountKey: String): List<OfflineTrackEntity>

    @Query("SELECT t.* FROM OfflineTrackEntity t JOIN DownloadOwnershipEntity o ON t.accountKey=o.accountKey AND t.trackId=o.trackId WHERE o.accountKey=:accountKey AND o.ownerType='album' AND o.ownerId=:albumId ORDER BY o.position")
    suspend fun offlineAlbumTracks(accountKey: String, albumId: String): List<OfflineTrackEntity>

    @Query("SELECT * FROM OfflineTrackEntity WHERE accountKey=:accountKey AND state IN ('Queued','Downloading') ORDER BY requestedAtMs, trackId LIMIT 1")
    suspend fun nextPendingOfflineTrack(accountKey: String): OfflineTrackEntity?

    @Query("SELECT COUNT(*) FROM DownloadOwnershipEntity WHERE accountKey=:accountKey AND trackId=:trackId")
    suspend fun ownershipCount(accountKey: String, trackId: String): Int

    @Query("SELECT COUNT(*) FROM OfflineTrackEntity d JOIN TrackEntity t ON t.accountKey=d.accountKey AND t.id=d.trackId WHERE d.accountKey=:accountKey AND t.coverArtId=:coverArtId AND EXISTS (SELECT 1 FROM DownloadOwnershipEntity o WHERE o.accountKey=d.accountKey AND o.trackId=d.trackId)")
    suspend fun artworkReferenceCount(accountKey: String, coverArtId: String): Int

    @Upsert suspend fun upsertOfflineTrack(item: OfflineTrackEntity)
    @Upsert suspend fun upsertOfflineAlbum(item: OfflineAlbumEntity)
    @Upsert suspend fun upsertDownloadOwnership(items: List<DownloadOwnershipEntity>)
    @Upsert suspend fun upsertOfflineArtwork(item: OfflineArtworkEntity)

    @Transaction
    suspend fun upsertOfflineArtworkIfReferenced(item: OfflineArtworkEntity): Boolean {
        if (artworkReferenceCount(item.accountKey, item.coverArtId) == 0) {
            return false
        }
        upsertOfflineArtwork(item)
        return true
    }

    @Query("UPDATE OfflineTrackEntity SET state=:state, downloadedBytes=:downloadedBytes, expectedSize=:expectedSize, relativePath=:relativePath, error=:error, completedAtMs=:completedAtMs WHERE accountKey=:accountKey AND trackId=:trackId")
    suspend fun updateOfflineTrackState(accountKey: String, trackId: String, state: String, downloadedBytes: Long, expectedSize: Long?, relativePath: String?, error: String?, completedAtMs: Long?)

    @Query("UPDATE OfflineTrackEntity SET state='Downloading', downloadedBytes=MAX(downloadedBytes, :downloadedBytes), expectedSize=COALESCE(:expectedSize, expectedSize), error=NULL, completedAtMs=NULL WHERE accountKey=:accountKey AND trackId=:trackId AND state IN ('Queued','Downloading')")
    suspend fun updateOfflineTrackProgress(
        accountKey: String,
        trackId: String,
        downloadedBytes: Long,
        expectedSize: Long?,
    )

    @Query("DELETE FROM DownloadOwnershipEntity WHERE accountKey=:accountKey AND trackId=:trackId")
    suspend fun deleteTrackOwnerships(accountKey: String, trackId: String)

    @Query("DELETE FROM DownloadOwnershipEntity WHERE accountKey=:accountKey AND trackId IN (:trackIds)")
    suspend fun deleteTrackOwnerships(accountKey: String, trackIds: List<String>)

    @Query("DELETE FROM DownloadOwnershipEntity WHERE accountKey=:accountKey AND ownerType='album' AND ownerId=:albumId")
    suspend fun deleteAlbumOwnerships(accountKey: String, albumId: String)

    @Query("DELETE FROM OfflineTrackEntity WHERE accountKey=:accountKey AND trackId=:trackId")
    suspend fun deleteOfflineTrack(accountKey: String, trackId: String)

    @Query("DELETE FROM OfflineTrackEntity WHERE accountKey=:accountKey AND trackId IN (:trackIds)")
    suspend fun deleteOfflineTracks(accountKey: String, trackIds: List<String>)

    @Query("DELETE FROM OfflineAlbumEntity WHERE accountKey=:accountKey AND albumId=:albumId")
    suspend fun deleteOfflineAlbum(accountKey: String, albumId: String)

    @Query("DELETE FROM OfflineArtworkEntity WHERE accountKey=:accountKey AND coverArtId=:coverArtId")
    suspend fun deleteOfflineArtwork(accountKey: String, coverArtId: String)

    @Query("DELETE FROM OfflineArtworkEntity WHERE accountKey=:accountKey AND coverArtId IN (:coverArtIds)")
    suspend fun deleteOfflineArtworks(accountKey: String, coverArtIds: List<String>)

    @Query("SELECT * FROM OfflineArtworkEntity a WHERE a.accountKey=:accountKey AND a.coverArtId IN (:coverArtIds) AND NOT EXISTS (SELECT 1 FROM OfflineTrackEntity d JOIN TrackEntity t ON t.accountKey=d.accountKey AND t.id=d.trackId WHERE d.accountKey=a.accountKey AND t.coverArtId=a.coverArtId AND EXISTS (SELECT 1 FROM DownloadOwnershipEntity o WHERE o.accountKey=d.accountKey AND o.trackId=d.trackId))")
    suspend fun unreferencedOfflineArtworks(accountKey: String, coverArtIds: List<String>): List<OfflineArtworkEntity>

    @Query("DELETE FROM DownloadOwnershipEntity WHERE accountKey=:accountKey")
    suspend fun deleteOfflineOwnerships(accountKey: String)
    @Query("DELETE FROM OfflineTrackEntity WHERE accountKey=:accountKey")
    suspend fun deleteOfflineTracks(accountKey: String)
    @Query("DELETE FROM OfflineAlbumEntity WHERE accountKey=:accountKey")
    suspend fun deleteOfflineAlbums(accountKey: String)
    @Query("DELETE FROM OfflineArtworkEntity WHERE accountKey=:accountKey")
    suspend fun deleteOfflineArtworks(accountKey: String)
    @Query("SELECT a.* FROM ArtistEntity a JOIN CacheMembership m ON a.accountKey=m.accountKey AND a.id=m.itemId WHERE m.accountKey=:accountKey AND m.queryKey=:queryKey AND m.itemType='artist' ORDER BY m.position")
    fun observeArtists(accountKey: String, queryKey: String): Flow<List<ArtistEntity>>

    @Query("SELECT a.* FROM AlbumEntity a JOIN CacheMembership m ON a.accountKey=m.accountKey AND a.id=m.itemId WHERE m.accountKey=:accountKey AND m.queryKey=:queryKey AND m.itemType='album' ORDER BY m.position")
    fun observeAlbums(accountKey: String, queryKey: String): Flow<List<AlbumEntity>>

    @Query("SELECT t.* FROM TrackEntity t JOIN CacheMembership m ON t.accountKey=m.accountKey AND t.id=m.itemId WHERE m.accountKey=:accountKey AND m.queryKey=:queryKey AND m.itemType='track' ORDER BY m.position")
    fun observeTracks(accountKey: String, queryKey: String): Flow<List<TrackEntity>>

    @Query("SELECT a.* FROM AlbumEntity a JOIN CacheMembership m ON a.accountKey=m.accountKey AND a.id=m.itemId WHERE m.accountKey=:accountKey AND m.queryKey=:queryKey AND m.itemType='album' ORDER BY m.position LIMIT :limit OFFSET :offset")
    suspend fun albumPage(accountKey: String, queryKey: String, offset: Int, limit: Int): List<AlbumEntity>

    @Query("SELECT t.* FROM TrackEntity t JOIN CacheMembership m ON t.accountKey=m.accountKey AND t.id=m.itemId WHERE m.accountKey=:accountKey AND m.queryKey=:queryKey AND m.itemType='track' ORDER BY m.position LIMIT :limit OFFSET :offset")
    suspend fun trackPage(accountKey: String, queryKey: String, offset: Int, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM CacheMetadata WHERE accountKey=:accountKey AND queryKey=:queryKey")
    suspend fun metadata(accountKey: String, queryKey: String): CacheMetadata?

    @Upsert suspend fun upsertArtists(items: List<ArtistEntity>)
    @Upsert suspend fun upsertAlbums(items: List<AlbumEntity>)
    @Upsert suspend fun upsertTracks(items: List<TrackEntity>)
    @Upsert suspend fun upsertMembership(items: List<CacheMembership>)
    @Upsert suspend fun upsertMetadata(item: CacheMetadata)

    @Query("DELETE FROM CacheMembership WHERE accountKey=:accountKey AND queryKey=:queryKey AND itemType=:itemType")
    suspend fun deleteMembership(accountKey: String, queryKey: String, itemType: String)

    @Query("DELETE FROM CacheMetadata WHERE accountKey=:accountKey AND queryKey=:queryKey")
    suspend fun deleteQueryMetadata(accountKey: String, queryKey: String)

    @Query("UPDATE CacheMetadata SET updatedAtMs=0 WHERE accountKey=:accountKey AND queryKey != 'scan'")
    suspend fun invalidate(accountKey: String)

    @Query("DELETE FROM ArtistEntity WHERE accountKey=:accountKey") suspend fun deleteArtists(accountKey: String)
    @Query("DELETE FROM AlbumEntity WHERE accountKey=:accountKey") suspend fun deleteAlbums(accountKey: String)
    @Query("DELETE FROM TrackEntity WHERE accountKey=:accountKey") suspend fun deleteTracks(accountKey: String)
    @Query("DELETE FROM CacheMembership WHERE accountKey=:accountKey") suspend fun deleteMemberships(accountKey: String)
    @Query("DELETE FROM CacheMetadata WHERE accountKey=:accountKey") suspend fun deleteMetadata(accountKey: String)
    @Query("DELETE FROM ArtistEntity WHERE accountKey=:accountKey AND NOT EXISTS (SELECT 1 FROM CacheMembership m WHERE m.accountKey=ArtistEntity.accountKey AND m.itemType='artist' AND m.itemId=ArtistEntity.id)")
    suspend fun deleteOrphanArtists(accountKey: String)
    @Query("DELETE FROM AlbumEntity WHERE accountKey=:accountKey AND NOT EXISTS (SELECT 1 FROM CacheMembership m WHERE m.accountKey=AlbumEntity.accountKey AND m.itemType='album' AND m.itemId=AlbumEntity.id) AND NOT EXISTS (SELECT 1 FROM OfflineAlbumEntity d WHERE d.accountKey=AlbumEntity.accountKey AND d.albumId=AlbumEntity.id)")
    suspend fun deleteOrphanAlbums(accountKey: String)
    @Query("DELETE FROM TrackEntity WHERE accountKey=:accountKey AND NOT EXISTS (SELECT 1 FROM CacheMembership m WHERE m.accountKey=TrackEntity.accountKey AND m.itemType='track' AND m.itemId=TrackEntity.id) AND NOT EXISTS (SELECT 1 FROM OfflineTrackEntity d WHERE d.accountKey=TrackEntity.accountKey AND d.trackId=TrackEntity.id)")
    suspend fun deleteOrphanTracks(accountKey: String)
    @Query("UPDATE ArtistEntity SET isFavorite=:favorite WHERE accountKey=:accountKey AND id=:id") suspend fun updateArtistFavorite(accountKey: String, id: String, favorite: Boolean)
    @Query("UPDATE AlbumEntity SET isFavorite=:favorite WHERE accountKey=:accountKey AND id=:id") suspend fun updateAlbumFavorite(accountKey: String, id: String, favorite: Boolean)
    @Query("UPDATE TrackEntity SET isFavorite=:favorite WHERE accountKey=:accountKey AND id=:id") suspend fun updateTrackFavorite(accountKey: String, id: String, favorite: Boolean)
    @Query("DELETE FROM CacheMembership WHERE accountKey=:accountKey AND queryKey='favorites' AND itemType=:itemType AND itemId=:id")
    suspend fun deleteFavoriteMembership(accountKey: String, itemType: String, id: String)

    @Transaction
    suspend fun replaceArtists(accountKey: String, queryKey: String, items: List<ArtistEntity>, now: Long) {
        upsertArtists(items)
        deleteMembership(accountKey, queryKey, CacheItemType.ARTIST)
        upsertMembership(items.mapIndexed { i, it -> CacheMembership(accountKey, queryKey, CacheItemType.ARTIST, it.id, i) })
        upsertMetadata(CacheMetadata(accountKey, queryKey, now))
        deleteOrphanArtists(accountKey)
    }

    @Transaction
    suspend fun replaceAlbums(accountKey: String, queryKey: String, items: List<AlbumEntity>, now: Long) {
        upsertAlbums(items)
        deleteMembership(accountKey, queryKey, CacheItemType.ALBUM)
        upsertMembership(items.mapIndexed { i, it -> CacheMembership(accountKey, queryKey, CacheItemType.ALBUM, it.id, i) })
        upsertMetadata(CacheMetadata(accountKey, queryKey, now))
        deleteOrphanAlbums(accountKey)
    }

    @Transaction
    suspend fun replaceTracks(accountKey: String, queryKey: String, items: List<TrackEntity>, now: Long) {
        upsertTracks(items)
        deleteMembership(accountKey, queryKey, CacheItemType.TRACK)
        upsertMembership(items.mapIndexed { i, it -> CacheMembership(accountKey, queryKey, CacheItemType.TRACK, it.id, i) })
        upsertMetadata(CacheMetadata(accountKey, queryKey, now))
        deleteOrphanTracks(accountKey)
    }

    @Transaction
    suspend fun storeAlbumPage(
        accountKey: String,
        queryKey: String,
        items: List<AlbumEntity>,
        offset: Int,
        isLastPage: Boolean,
        now: Long,
    ) {
        if (offset == 0) {
            deleteMembership(accountKey, queryKey, CacheItemType.ALBUM)
            deleteQueryMetadata(accountKey, queryKey)
        }
        upsertAlbums(items)
        upsertMembership(items.mapIndexed { index, item ->
            CacheMembership(accountKey, queryKey, CacheItemType.ALBUM, item.id, offset + index)
        })
        if (isLastPage) upsertMetadata(CacheMetadata(accountKey, queryKey, now))
    }

    @Transaction
    suspend fun storeTrackPage(
        accountKey: String,
        queryKey: String,
        items: List<TrackEntity>,
        offset: Int,
        isLastPage: Boolean,
        now: Long,
    ) {
        if (offset == 0) {
            deleteMembership(accountKey, queryKey, CacheItemType.TRACK)
            deleteQueryMetadata(accountKey, queryKey)
        }
        upsertTracks(items)
        upsertMembership(items.mapIndexed { index, item ->
            CacheMembership(accountKey, queryKey, CacheItemType.TRACK, item.id, offset + index)
        })
        if (isLastPage) upsertMetadata(CacheMetadata(accountKey, queryKey, now))
    }

    @Transaction
    suspend fun replaceFavorites(
        accountKey: String,
        artists: List<ArtistEntity>,
        albums: List<AlbumEntity>,
        tracks: List<TrackEntity>,
        now: Long,
    ) {
        upsertArtists(artists)
        upsertAlbums(albums)
        upsertTracks(tracks)
        deleteMembership(accountKey, CacheKeys.FAVORITES, CacheItemType.ARTIST)
        deleteMembership(accountKey, CacheKeys.FAVORITES, CacheItemType.ALBUM)
        deleteMembership(accountKey, CacheKeys.FAVORITES, CacheItemType.TRACK)
        upsertMembership(artists.mapIndexed { i, it -> CacheMembership(accountKey, CacheKeys.FAVORITES, CacheItemType.ARTIST, it.id, i) })
        upsertMembership(albums.mapIndexed { i, it -> CacheMembership(accountKey, CacheKeys.FAVORITES, CacheItemType.ALBUM, it.id, i) })
        upsertMembership(tracks.mapIndexed { i, it -> CacheMembership(accountKey, CacheKeys.FAVORITES, CacheItemType.TRACK, it.id, i) })
        upsertMetadata(CacheMetadata(accountKey, CacheKeys.FAVORITES, now))
        deleteOrphanArtists(accountKey)
        deleteOrphanAlbums(accountKey)
        deleteOrphanTracks(accountKey)
    }

    @Transaction
    suspend fun setFavorite(accountKey: String, itemType: String, id: String, favorite: Boolean) {
        when (itemType) {
            CacheItemType.ARTIST -> updateArtistFavorite(accountKey, id, favorite)
            CacheItemType.ALBUM -> updateAlbumFavorite(accountKey, id, favorite)
            CacheItemType.TRACK -> updateTrackFavorite(accountKey, id, favorite)
        }
        if (favorite) {
            upsertMembership(listOf(CacheMembership(accountKey, CacheKeys.FAVORITES, itemType, id, 0)))
        } else {
            deleteFavoriteMembership(accountKey, itemType, id)
        }
        deleteOrphanArtists(accountKey)
        deleteOrphanAlbums(accountKey)
        deleteOrphanTracks(accountKey)
    }

    @Transaction
    suspend fun clearAccount(accountKey: String) {
        clearOfflineAccount(accountKey)
        deleteMemberships(accountKey)
        deleteMetadata(accountKey)
        deleteArtists(accountKey)
        deleteAlbums(accountKey)
        deleteTracks(accountKey)
    }

    @Transaction
    suspend fun clearOfflineAccount(accountKey: String) {
        deleteOfflineOwnerships(accountKey)
        deleteOfflineTracks(accountKey)
        deleteOfflineAlbums(accountKey)
        deleteOfflineArtworks(accountKey)
    }

    @Transaction
    suspend fun removeOfflineTracks(accountKey: String, trackIds: List<String>): RemovedOfflineFiles {
        val downloads = offlineTracks(accountKey, trackIds)
        val coverArtIds = tracks(accountKey, trackIds).mapNotNull(TrackEntity::coverArtId).distinct()
        deleteTrackOwnerships(accountKey, trackIds)
        deleteOfflineTracks(accountKey, trackIds)
        val artworks = if (coverArtIds.isEmpty()) {
            emptyList()
        } else {
            unreferencedOfflineArtworks(accountKey, coverArtIds)
        }
        if (artworks.isNotEmpty()) {
            deleteOfflineArtworks(accountKey, artworks.map(OfflineArtworkEntity::coverArtId))
        }
        return RemovedOfflineFiles(
            trackPaths = downloads.mapNotNull(OfflineTrackEntity::relativePath),
            artworkPaths = artworks.mapNotNull(OfflineArtworkEntity::relativePath),
        )
    }

    @Transaction
    suspend fun removeOfflineAlbumDownload(
        accountKey: String,
        albumId: String,
        unownedTrackIds: List<String>,
    ): RemovedOfflineFiles {
        deleteAlbumOwnerships(accountKey, albumId)
        deleteOfflineAlbum(accountKey, albumId)
        return if (unownedTrackIds.isEmpty()) {
            RemovedOfflineFiles(emptyList(), emptyList())
        } else {
            removeOfflineTracks(accountKey, unownedTrackIds)
        }
    }
}

data class RemovedOfflineFiles(
    val trackPaths: List<String>,
    val artworkPaths: List<String>,
)
