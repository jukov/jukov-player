package info.jukov.player.core.data.cache

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.accountKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

const val CACHE_TTL_MS = 24L * 60L * 60L * 1_000L

class LibraryCachePolicy(
    private val dao: CacheDao,
    private val scanApi: ScanApi,
) {
    private val refreshMutex = Mutex()
    private val refreshes = mutableMapOf<String, CompletableDeferred<Unit>>()

    suspend fun isFresh(session: AuthSession, queryKey: String): Boolean {
        val updatedAt = dao.metadata(session.accountKey, queryKey)?.updatedAtMs ?: return false
        return Clock.System.now().toEpochMilliseconds() - updatedAt < CACHE_TTL_MS
    }

    suspend fun shouldRefreshFromNetwork(session: AuthSession): Boolean {
        if (session.serverType != null && !session.serverType.equals("navidrome", ignoreCase = true)) {
            return true
        }
        val current = runCatching { scanApi.lastScan(session) }.getOrNull() ?: return true
        val previous = dao.metadata(session.accountKey, CacheKeys.SCAN)?.lastScan
        dao.upsertMetadata(
            CacheMetadata(
                accountKey = session.accountKey,
                queryKey = CacheKeys.SCAN,
                updatedAtMs = Clock.System.now().toEpochMilliseconds(),
                lastScan = current,
            ),
        )
        if (previous == null) return true
        if (previous == current) return false
        dao.invalidate(session.accountKey)
        return true
    }

    suspend fun runDeduplicated(accountKey: String, queryKey: String, block: suspend () -> Unit) {
        val key = "$accountKey|$queryKey"
        val (refresh, owner) = refreshMutex.withLock {
            refreshes[key]?.let { it to false } ?: (CompletableDeferred<Unit>().also {
                refreshes[key] = it
            } to true)
        }
        if (!owner) return refresh.await()
        try {
            block()
            refresh.complete(Unit)
        } catch (error: Throwable) {
            refresh.completeExceptionally(error)
            throw error
        } finally {
            refreshMutex.withLock { if (refreshes[key] === refresh) refreshes.remove(key) }
        }
    }
}
