package info.jukov.player.feature.favorite.data

import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultFavoritesRepositoryTest {
    @Test
    fun getFavoritesRequiresLoggedInSession() = runTest {
        val repository = DefaultFavoritesRepository(FakeApi(), FakeAuthRepository(AuthState.LoggedOut))

        val result = repository.getFavorites().first()

        assertTrue(result is info.jukov.player.core.domain.LoadableState.Failure)
    }

    @Test
    fun successfulMutationPublishesConfirmedChange() = runTest {
        val api = FakeApi()
        val repository = DefaultFavoritesRepository(api, FakeAuthRepository(loggedInState()))
        val change = async(start = CoroutineStart.UNDISPATCHED) { repository.changes.first() }
        val target = FavoriteTarget.Album("album-1")

        repository.setFavorite(target, true).getOrThrow()

        assertEquals(target, api.target)
        assertEquals(true, api.isFavorite)
        val emitted = change.await()
        assertEquals(target, emitted.target)
        assertTrue(emitted.isFavorite)
    }

    @Test
    fun failedMutationDoesNotPublishChange() = runTest {
        val api = FakeApi(failMutation = true)
        val repository = DefaultFavoritesRepository(api, FakeAuthRepository(loggedInState()))

        val result = repository.setFavorite(FavoriteTarget.Track("track-1"), false)

        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(0, repository.changes.replayCache.size)
    }

    private class FakeApi(private val failMutation: Boolean = false) : FavoritesApi {
        var target: FavoriteTarget? = null
        var isFavorite: Boolean? = null
        override suspend fun getFavorites(session: AuthSession) = Favorites()
        override suspend fun setFavorite(
            session: AuthSession,
            target: FavoriteTarget,
            isFavorite: Boolean,
        ) {
            if (failMutation) error("failure")
            this.target = target
            this.isFavorite = isFavorite
        }
    }

    private class FakeAuthRepository(initial: AuthState) : AuthRepository {
        override val authState = MutableStateFlow(initial)
        override suspend fun login(serverUrl: String, username: String, password: String) =
            Result.failure<AuthSession>(UnsupportedOperationException())
        override suspend fun logout() { authState.value = AuthState.LoggedOut }
    }

    private companion object {
        fun loggedInState() = AuthState.LoggedIn(
            AuthSession("https://example.com", "user", "token", "salt"),
        )
    }
}
