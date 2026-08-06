package info.jukov.player.core.data.cache

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
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
    @Query("DELETE FROM AlbumEntity WHERE accountKey=:accountKey AND NOT EXISTS (SELECT 1 FROM CacheMembership m WHERE m.accountKey=AlbumEntity.accountKey AND m.itemType='album' AND m.itemId=AlbumEntity.id)")
    suspend fun deleteOrphanAlbums(accountKey: String)
    @Query("DELETE FROM TrackEntity WHERE accountKey=:accountKey AND NOT EXISTS (SELECT 1 FROM CacheMembership m WHERE m.accountKey=TrackEntity.accountKey AND m.itemType='track' AND m.itemId=TrackEntity.id)")
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
        deleteMemberships(accountKey)
        deleteMetadata(accountKey)
        deleteArtists(accountKey)
        deleteAlbums(accountKey)
        deleteTracks(accountKey)
    }
}
