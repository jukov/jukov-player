package info.jukov.player.feature.auth.data

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DefaultAuthRepositoryTest {
    @Test
    fun successfulLoginPersistsSessionAndUpdatesState() = runTest {
        val storage = FakeAuthStorage()
        val repository = DefaultAuthRepository(SuccessfulAuthApi, storage)

        val result = repository.login(SERVER, USERNAME, PASSWORD)

        val session = result.getOrThrow()
        assertEquals(session, storage.session)
        assertEquals(AuthState.LoggedIn(session), repository.authState.value)
    }

    @Test
    fun failedLoginDoesNotPersistSessionOrUpdateState() = runTest {
        val storage = FakeAuthStorage()
        val repository = DefaultAuthRepository(FailingAuthApi, storage)

        val result = repository.login(SERVER, USERNAME, PASSWORD)

        assertIs<AuthState.LoggedOut>(repository.authState.value)
        assertNull(storage.session)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun logoutClearsSessionAndUpdatesState() = runTest {
        val storedSession = AuthSession(SERVER, USERNAME, "token", "salt")
        val storage = FakeAuthStorage(storedSession)
        val repository = DefaultAuthRepository(SuccessfulAuthApi, storage)

        repository.logout()

        assertNull(storage.session)
        assertIs<AuthState.LoggedOut>(repository.authState.value)
    }

    private class FakeAuthStorage(initialSession: AuthSession? = null) : AuthStorage {
        var session = initialSession
        override fun read() = session
        override fun write(session: AuthSession) { this.session = session }
        override fun clear() { session = null }
    }

    private object SuccessfulAuthApi : AuthApi {
        override suspend fun ping(session: AuthSession) = ServerInfo("navidrome", "test")
    }

    private object FailingAuthApi : AuthApi {
        override suspend fun ping(session: AuthSession): ServerInfo {
            error("Unauthorized")
        }
    }

    private companion object {
        const val SERVER = "https://music.example.com"
        const val USERNAME = "user"
        const val PASSWORD = "password"
    }
}
