package info.jukov.player.core.data.cache

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.ForeignKey

@Entity(primaryKeys = ["accountKey", "id"])
data class ArtistEntity(
    val accountKey: String,
    val id: String,
    val name: String,
    val albumCount: Int,
    val coverArtId: String?,
    val isFavorite: Boolean,
)

@Entity(primaryKeys = ["accountKey", "id"])
data class AlbumEntity(
    val accountKey: String,
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val year: Int?,
    val coverArtId: String?,
    val isFavorite: Boolean,
)

@Entity(primaryKeys = ["accountKey", "id"])
data class TrackEntity(
    val accountKey: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumId: String?,
    val artistId: String?,
    val trackNumber: Int?,
    val year: Int?,
    val coverArtId: String?,
    val durationMs: Long,
    val contentType: String?,
    val isFavorite: Boolean,
)

@Entity(
    primaryKeys = ["accountKey", "queryKey", "itemType", "itemId"],
    indices = [Index(value = ["accountKey", "itemType", "itemId"])],
)
data class CacheMembership(
    val accountKey: String,
    val queryKey: String,
    val itemType: String,
    val itemId: String,
    val position: Int,
)

@Entity(primaryKeys = ["accountKey", "queryKey"])
data class CacheMetadata(
    val accountKey: String,
    val queryKey: String,
    val updatedAtMs: Long,
    val lastScan: String? = null,
)

object CacheKeys {
    const val ARTISTS = "artists"
    const val ALBUMS = "albums"
    const val FAVORITES = "favorites"
    const val SCAN = "scan"
    fun artistAlbums(id: String) = "artist:$id:albums"
    fun tracksAll() = "tracks:all"
    fun artistTracks(id: String) = "artist:$id:tracks"
    fun albumTracks(id: String) = "album:$id:tracks"
}

object CacheItemType {
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val TRACK = "track"
}

// Library metadata stays in TrackEntity/AlbumEntity. These entities only describe durable
// offline artifacts and user download intent; CacheDao's orphan cleanup preserves referenced rows.
@Entity(
    primaryKeys = ["accountKey", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["accountKey", "id"],
            childColumns = ["accountKey", "trackId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class OfflineTrackEntity(
    val accountKey: String,
    val trackId: String,
    val relativePath: String?,
    val expectedSize: Long?,
    val downloadedBytes: Long,
    val state: String,
    val error: String?,
    val requestedAtMs: Long,
    val completedAtMs: Long?,
)

@Entity(
    primaryKeys = ["accountKey", "albumId"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["accountKey", "id"],
            childColumns = ["accountKey", "albumId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class OfflineAlbumEntity(
    val accountKey: String,
    val albumId: String,
    val trackCount: Int,
    val requestedAtMs: Long,
)

@Entity(
    primaryKeys = ["accountKey", "ownerType", "ownerId", "trackId"],
    indices = [Index(value = ["accountKey", "trackId"])],
)
data class DownloadOwnershipEntity(
    val accountKey: String,
    val ownerType: String,
    val ownerId: String,
    val trackId: String,
    val position: Int,
)

@Entity(primaryKeys = ["accountKey", "coverArtId"])
data class OfflineArtworkEntity(
    val accountKey: String,
    val coverArtId: String,
    val relativePath: String?,
    val contentType: String?,
    val downloadedBytes: Long,
    val state: String,
    val error: String?,
    val completedAtMs: Long?,
)
