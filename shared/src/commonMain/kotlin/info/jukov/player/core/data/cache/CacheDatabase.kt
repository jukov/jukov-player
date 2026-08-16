package info.jukov.player.core.data.cache

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.AutoMigration
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [
        ArtistEntity::class, AlbumEntity::class, TrackEntity::class, CacheMembership::class,
        CacheMetadata::class, OfflineTrackEntity::class, OfflineAlbumEntity::class,
        DownloadOwnershipEntity::class, OfflineArtworkEntity::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
@ConstructedBy(CacheDatabaseConstructor::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}

@Suppress("KotlinNoActualForExpect")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}

fun buildCacheDatabase(builder: RoomDatabase.Builder<CacheDatabase>): CacheDatabase =
    builder.setDriver(BundledSQLiteDriver()).build()

const val CACHE_DATABASE_NAME = "jukov-cache.db"
