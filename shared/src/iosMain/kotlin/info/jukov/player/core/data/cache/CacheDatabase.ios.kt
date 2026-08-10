package info.jukov.player.core.data.cache

import androidx.room3.Room
import androidx.room3.RoomDatabase
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
fun cacheDatabaseBuilder(): RoomDatabase.Builder<CacheDatabase> {
    val fileManager = NSFileManager.defaultManager
    val applicationSupportDirectory = fileManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: error("Application Support directory is unavailable")
    val applicationSupportPath = "$applicationSupportDirectory/$CACHE_DATABASE_NAME"
    val documentsDirectory = fileManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )?.path
    val databasePath = documentsDirectory?.let { directory ->
        migrateLegacyDatabaseIfNeeded(
            fileManager = fileManager,
            legacyPath = "$directory/$CACHE_DATABASE_NAME",
            destinationPath = applicationSupportPath,
        )
    } ?: applicationSupportPath
    return Room.databaseBuilder<CacheDatabase>(databasePath)
}

@OptIn(ExperimentalForeignApi::class)
internal fun migrateLegacyDatabaseIfNeeded(
    fileManager: NSFileManager,
    legacyPath: String,
    destinationPath: String,
): String {
    if (fileManager.fileExistsAtPath(destinationPath) ||
        !fileManager.fileExistsAtPath(legacyPath)
    ) {
        return destinationPath
    }
    val suffixes = listOf("", "-wal", "-shm")
    val existingSuffixes = suffixes.filter { suffix ->
        fileManager.fileExistsAtPath(legacyPath + suffix)
    }
    val temporarySuffix = ".migration"
    suffixes.forEach { suffix ->
        val temporaryPath = destinationPath + suffix + temporarySuffix
        if (fileManager.fileExistsAtPath(temporaryPath)) {
            fileManager.removeItemAtPath(temporaryPath, error = null)
        }
    }
    val copied = existingSuffixes.all { suffix ->
        fileManager.copyItemAtPath(
            srcPath = legacyPath + suffix,
            toPath = destinationPath + suffix + temporarySuffix,
            error = null,
        )
    }
    if (!copied) {
        suffixes.forEach { suffix ->
            fileManager.removeItemAtPath(destinationPath + suffix + temporarySuffix, error = null)
        }
        return legacyPath
    }
    val finalizedSidecars = existingSuffixes.filter { it.isNotEmpty() }.all { suffix ->
        fileManager.moveItemAtPath(
            srcPath = destinationPath + suffix + temporarySuffix,
            toPath = destinationPath + suffix,
            error = null,
        )
    }
    val finalizedDatabase = finalizedSidecars && fileManager.moveItemAtPath(
        srcPath = destinationPath + temporarySuffix,
        toPath = destinationPath,
        error = null,
    )
    if (!finalizedDatabase) {
        existingSuffixes.filter { it.isNotEmpty() }.forEach { suffix ->
            fileManager.removeItemAtPath(destinationPath + suffix, error = null)
        }
        suffixes.forEach { suffix ->
            fileManager.removeItemAtPath(destinationPath + suffix + temporarySuffix, error = null)
        }
        return legacyPath
    }
    existingSuffixes.forEach { suffix ->
        fileManager.removeItemAtPath(legacyPath + suffix, error = null)
    }
    return destinationPath
}
