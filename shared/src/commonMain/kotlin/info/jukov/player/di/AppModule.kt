package info.jukov.player.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.auth.data.AuthApi
import info.jukov.player.auth.data.AuthStorage
import info.jukov.player.auth.data.AuthStorageImpl
import info.jukov.player.auth.data.DefaultAuthRepository
import info.jukov.player.auth.data.SubsonicAuthApi
import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.auth.domain.LoginUseCase
import info.jukov.player.auth.domain.LogoutUseCase
import info.jukov.player.auth.presentation.AuthViewModel
import io.ktor.client.HttpClient
import info.jukov.player.artist.data.ArtistsApi
import info.jukov.player.artist.data.DefaultArtistsRepository
import info.jukov.player.artist.data.SubsonicArtistsApi
import info.jukov.player.artist.domain.ArtistsRepository
import info.jukov.player.artist.domain.GetArtistsUseCase
import info.jukov.player.artist.presentation.ArtistsViewModel
import info.jukov.player.album.data.AlbumsApi
import info.jukov.player.album.data.DefaultAlbumsRepository
import info.jukov.player.album.data.SubsonicAlbumsApi
import info.jukov.player.album.domain.AlbumsRepository
import info.jukov.player.album.domain.GetAlbumsUseCase
import info.jukov.player.album.presentation.AlbumsViewModel
import info.jukov.player.subsonic.data.SubsonicApiClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@BindingContainer
object AppModule {
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

    @Provides
    @SingleIn(AppScope::class)
    fun provideAuthStorage(): AuthStorage = AuthStorageImpl()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAuthApi(client: SubsonicApiClient): AuthApi = SubsonicAuthApi(client)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAuthRepository(api: AuthApi, storage: AuthStorage): AuthRepository =
        DefaultAuthRepository(api, storage)

    @Provides
    fun provideLoginUseCase(repository: AuthRepository): LoginUseCase = LoginUseCase(repository)

    @Provides
    fun provideLogoutUseCase(repository: AuthRepository): LogoutUseCase = LogoutUseCase(repository)

    @Provides
    fun provideAuthViewModel(
        repository: AuthRepository,
        loginUseCase: LoginUseCase,
        logoutUseCase: LogoutUseCase,
    ): AuthViewModel = AuthViewModel(repository, loginUseCase, logoutUseCase)

    @Provides
    @SingleIn(AppScope::class)
    fun provideArtistsApi(client: SubsonicApiClient): ArtistsApi = SubsonicArtistsApi(client)

    @Provides
    @SingleIn(AppScope::class)
    fun provideArtistsRepository(
        api: ArtistsApi,
        authRepository: AuthRepository,
    ): ArtistsRepository = DefaultArtistsRepository(api, authRepository)

    @Provides
    fun provideGetArtistsUseCase(repository: ArtistsRepository): GetArtistsUseCase =
        GetArtistsUseCase(repository)

    @Provides
    fun provideArtistsViewModel(
        getArtistsUseCase: GetArtistsUseCase,
        authRepository: AuthRepository,
    ): ArtistsViewModel = ArtistsViewModel(getArtistsUseCase, authRepository)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAlbumsApi(client: SubsonicApiClient): AlbumsApi = SubsonicAlbumsApi(client)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAlbumsRepository(
        api: AlbumsApi,
        authRepository: AuthRepository,
    ): AlbumsRepository = DefaultAlbumsRepository(api, authRepository)

    @Provides
    fun provideGetAlbumsUseCase(repository: AlbumsRepository): GetAlbumsUseCase =
        GetAlbumsUseCase(repository)

    @Provides
    fun provideAlbumsViewModel(getAlbumsUseCase: GetAlbumsUseCase): AlbumsViewModel =
        AlbumsViewModel(getAlbumsUseCase)

}
