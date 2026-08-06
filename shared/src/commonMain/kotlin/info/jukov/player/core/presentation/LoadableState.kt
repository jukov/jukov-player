package info.jukov.player.core.presentation

import info.jukov.player.core.domain.AppError

sealed interface LoadableState<out T> {
    val content: T?

    data class Content<T>(override val content: T) : LoadableState<T>

    data class Loading<T>(override val content: T? = null) : LoadableState<T>

    data class Failure<T>(
        val error: AppError,
        override val content: T? = null,
    ) : LoadableState<T>
}
