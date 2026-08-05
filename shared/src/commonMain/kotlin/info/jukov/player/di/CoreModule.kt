package info.jukov.player.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.subsonic.data.SubsonicApiClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@BindingContainer
object CoreModule {
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
