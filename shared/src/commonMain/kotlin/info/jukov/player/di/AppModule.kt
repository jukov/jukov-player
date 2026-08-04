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
import info.jukov.player.auth.presentation.ui.AuthViewModel
import io.ktor.client.HttpClient

@BindingContainer
object AppModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient = HttpClient()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAuthStorage(): AuthStorage = AuthStorageImpl()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAuthApi(client: HttpClient): AuthApi = SubsonicAuthApi(client)

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

}
