package info.jukov.player.core.data.cache

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.toAppError
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.accountKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

fun <T> cachedLoadableFlow(
    session: AuthSession,
    queryKey: String,
    forceRefresh: Boolean,
    fallbackError: AppError,
    policy: LibraryCachePolicy,
    dao: CacheDao,
    observe: () -> Flow<T>,
    refresh: suspend () -> Unit,
): Flow<LoadableState<T>> = flow {
    val metadata = dao.metadata(session.accountKey, queryKey)
    val cached = observe().first()
    val shouldRefresh = forceRefresh || metadata == null || !policy.isFresh(session, queryKey)
    if (!shouldRefresh) {
        emit(LoadableState.Content(cached))
    } else {
        emit(LoadableState.Loading(if (metadata == null) null else cached))
        try {
            policy.runDeduplicated(session.accountKey, queryKey) {
                val serverRequiresRefresh = policy.shouldRefreshFromNetwork(session)
                val cacheRequiresRefresh = forceRefresh || metadata == null
                if (cacheRequiresRefresh || serverRequiresRefresh) {
                    refresh()
                } else {
                    dao.upsertMetadata(
                        CacheMetadata(
                            accountKey = session.accountKey,
                            queryKey = queryKey,
                            updatedAtMs = Clock.System.now().toEpochMilliseconds(),
                        ),
                    )
                }
            }
            emit(LoadableState.Content(observe().first()))
        } catch (error: Throwable) {
            emit(LoadableState.Failure(error.toAppError(fallbackError), if (metadata == null) null else cached))
        }
    }
}
