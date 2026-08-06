package info.jukov.player.feature.favorite.presentation

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.toAppError

import info.jukov.player.feature.favorite.domain.FavoriteChange
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FavoriteDelegate(private val repository: FavoritesRepository) {
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending = _pending.asStateFlow()

    private val _messages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    val changes: SharedFlow<FavoriteChange> = repository.changes

    suspend fun toggle(
        target: FavoriteTarget,
        isFavorite: Boolean,
        updateFavorite: (Boolean) -> Unit,
    ) {
        if (target.id in _pending.value) return
        val desired = !isFavorite
        _pending.update { it + target.id }
        updateFavorite(desired)
        repository.setFavorite(target, desired).onFailure { error ->
            updateFavorite(isFavorite)
            _messages.tryEmit(error.toAppError(AppError.FavoriteUpdateFailed))
        }
        _pending.update { it - target.id }
    }
}
