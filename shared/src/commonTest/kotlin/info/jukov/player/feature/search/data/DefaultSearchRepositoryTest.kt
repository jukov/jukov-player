package info.jukov.player.feature.search.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.AppException
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.search.domain.SearchOffsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultSearchRepositoryTest {
    @Test
    fun searchRequiresAuthenticatedSession() = runTest {
        val repository = DefaultSearchRepository(FakeSearchApi(), FakeAuthRepository(AuthState.LoggedOut))

        val exception = assertFailsWith<AppException> {
            repository.library("test", SearchOffsets(), size = 10)
        }

        assertEquals(AppError.AuthenticationRequired, exception.error)
    }

    @Test
    fun artistFilterContinuesAcrossServerPages() = runTest {
        val api = FakeSearchApi(
            pages = listOf(
                listOf(album("other-1", "other"), album("match-1", "wanted")),
                listOf(album("match-2", "wanted")),
            ),
        )
        val repository = DefaultSearchRepository(api, FakeAuthRepository(AuthState.LoggedIn(SESSION)))

        val page = repository.albums("album", offset = 0, size = 2, artistId = "wanted")

        assertEquals(listOf("match-1", "match-2"), page.items.map(Album::id))
        assertEquals(3, page.nextOffset)
        assertEquals(false, page.hasMore)
    }

    private class FakeSearchApi(
        private val pages: List<List<Album>> = emptyList(),
    ) : SearchApi {
        private var request = 0
        override suspend fun search(
            session: AuthSession,
            query: String,
            offsets: SearchOffsets,
            artistCount: Int,
            albumCount: Int,
            trackCount: Int,
        ): SearchApiResult = SearchApiResult(
            artists = emptyList(),
            albums = pages.getOrElse(request++) { emptyList() },
            tracks = emptyList(),
        )
    }

    private class FakeAuthRepository(initial: AuthState) : AuthRepository {
        override val authState = MutableStateFlow(initial)
        override suspend fun login(
            serverUrl: String,
            username: String,
            password: String,
        ) = Result.failure<AuthSession>(UnsupportedOperationException())
        override suspend fun logout() = Unit
    }

    private companion object {
        val SESSION = AuthSession("https://music.test", "listener", "token", "salt")

        fun album(id: String, artistId: String) = Album(
            id = id,
            name = id,
            artist = artistId,
            artistId = artistId,
            year = null,
            coverArtId = null,
            coverArtUrl = null,
            isFavorite = false,
        )
    }
}
