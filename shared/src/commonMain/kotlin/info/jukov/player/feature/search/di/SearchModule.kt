package info.jukov.player.feature.search.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import info.jukov.player.di.AppScope
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.search.data.*
import info.jukov.player.feature.search.domain.SearchRepository
import info.jukov.player.feature.search.domain.SearchUseCase
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.feature.library.presentation.LibraryViewModel

@BindingContainer
object SearchModule {
    @Provides @SingleIn(AppScope::class)
    fun api(client: SubsonicApiClient): SearchApi = SubsonicSearchApi(client)

    @Provides @SingleIn(AppScope::class)
    fun repository(api: SearchApi, authRepository: AuthRepository): SearchRepository =
        DefaultSearchRepository(api, authRepository)

    @Provides fun useCase(repository: SearchRepository): SearchUseCase = SearchUseCase(repository)
    @Provides fun libraryViewModel(search: SearchUseCase): LibraryViewModel = LibraryViewModel(search)
}
