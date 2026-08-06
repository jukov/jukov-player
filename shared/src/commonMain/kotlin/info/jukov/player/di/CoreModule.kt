package info.jukov.player.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.subsonic.data.SubsonicApiClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import androidx.room3.RoomDatabase
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.CacheDatabase
import info.jukov.player.core.data.cache.buildCacheDatabase
import info.jukov.player.core.data.cache.LibraryCachePolicy
import info.jukov.player.core.data.cache.ScanApi
import info.jukov.player.core.data.cache.SubsonicScanApi

@BindingContainer
object CoreModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideCacheDatabase(builder: RoomDatabase.Builder<CacheDatabase>): CacheDatabase =
        buildCacheDatabase(builder)

    @Provides
    fun provideCacheDao(database: CacheDatabase): CacheDao = database.cacheDao()

    @Provides
    fun provideScanApi(client: SubsonicApiClient): ScanApi = SubsonicScanApi(client)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLibraryCachePolicy(dao: CacheDao, scanApi: ScanApi): LibraryCachePolicy =
        LibraryCachePolicy(dao, scanApi)

    @Provides
    @SingleIn(AppScope::class)
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(json: Json): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSubsonicApiClient(client: HttpClient, json: Json): SubsonicApiClient =
        SubsonicApiClient(client, json)
}
