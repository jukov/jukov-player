package info.jukov.player.favorite.data

import info.jukov.player.auth.domain.AuthSession
import info.jukov.player.favorite.domain.FavoriteTarget
import info.jukov.player.favorite.domain.Favorites

interface FavoritesApi {
    suspend fun getFavorites(session: AuthSession): Favorites
    suspend fun setFavorite(session: AuthSession, target: FavoriteTarget, isFavorite: Boolean)
}
