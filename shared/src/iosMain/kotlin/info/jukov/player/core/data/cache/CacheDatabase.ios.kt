package info.jukov.player.core.data.cache

import androidx.room3.Room
import androidx.room3.RoomDatabase
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
fun cacheDatabaseBuilder(): RoomDatabase.Builder<CacheDatabase> {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: error("Documents directory is unavailable")
    return Room.databaseBuilder<CacheDatabase>("$directory/$CACHE_DATABASE_NAME")
}
