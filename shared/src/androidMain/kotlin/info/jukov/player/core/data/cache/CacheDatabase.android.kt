package info.jukov.player.core.data.cache

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

fun cacheDatabaseBuilder(context: Context): RoomDatabase.Builder<CacheDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<CacheDatabase>(appContext, appContext.getDatabasePath(CACHE_DATABASE_NAME).absolutePath)
}
