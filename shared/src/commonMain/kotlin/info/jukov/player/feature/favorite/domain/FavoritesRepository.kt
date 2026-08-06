package info.jukov.player.feature.favorite.domain

import kotlinx.coroutines.flow.SharedFlow

interface FavoritesRepository {
    val changes: SharedFlow<FavoriteChange>
    suspend fun getFavorites(): Result<Favorites>
    suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean): Result<Unit>
}
