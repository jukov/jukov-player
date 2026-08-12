package info.jukov.player.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.subsonic.data.SubsonicApiClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import androidx.room3.RoomDatabase
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.CacheDatabase
import info.jukov.player.core.data.cache.buildCacheDatabase
import info.jukov.player.core.data.cache.LibraryCachePolicy
import info.jukov.player.core.data.cache.ScanApi
import info.jukov.player.core.data.cache.SubsonicScanApi
import info.jukov.player.core.domain.SettingsSortPreferences
import info.jukov.player.core.domain.SortPreferences

@BindingContainer
object CoreModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSortPreferences(): SortPreferences = SettingsSortPreferences()
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
    fun provideSubsonicApiClient(client: HttpClient, json: Json): SubsonicApiClient =
        SubsonicApiClient(client, json)
}

fun createHttpClient(json: Json = Json { ignoreUnknownKeys = true }): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(json)
    }
    install(Logging) {
        level = LogLevel.HEADERS
        logger = object : Logger {
            override fun log(message: String) {
                println("KtorHttp ${message.maskSubsonicCredentials()}")
            }
        }
        sanitizeHeader { header ->
            header == HttpHeaders.Authorization ||
                header == HttpHeaders.Cookie ||
                header == HttpHeaders.SetCookie
        }
    }
}

private fun String.maskSubsonicCredentials(): String = replace(
    Regex("([?&](?:u|t|s)=)[^&\\s]+"),
    "${'$'}1***",
)
