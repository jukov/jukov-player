package info.jukov.player.feature.favorite.domain

import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface FavoriteMutator {
    val pending: StateFlow<Set<String>>
    val changes: SharedFlow<FavoriteChange>

    suspend fun set(
        track: Track,
        isFavorite: Boolean,
        updateFavorite: (Boolean) -> Unit,
    )
}
