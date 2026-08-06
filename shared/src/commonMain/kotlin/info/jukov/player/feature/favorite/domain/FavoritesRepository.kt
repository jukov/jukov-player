package info.jukov.player.feature.favorite.domain

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import info.jukov.player.core.domain.LoadableState

interface FavoritesRepository {
    val changes: SharedFlow<FavoriteChange>
    fun getFavorites(forceRefresh: Boolean = false): Flow<LoadableState<Favorites>>
    suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean): Result<Unit>
}
