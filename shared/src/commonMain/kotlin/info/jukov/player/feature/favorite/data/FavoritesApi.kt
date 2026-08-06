package info.jukov.player.feature.favorite.data

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites

interface FavoritesApi {
    suspend fun getFavorites(session: AuthSession): Favorites
    suspend fun setFavorite(session: AuthSession, target: FavoriteTarget, isFavorite: Boolean)
}
