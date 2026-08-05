package info.jukov.player.auth.di

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
import info.jukov.player.di.AppScope
import info.jukov.player.subsonic.data.SubsonicApiClient

@BindingContainer
object AuthModule {
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
}
