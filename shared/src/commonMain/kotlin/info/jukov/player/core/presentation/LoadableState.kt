package info.jukov.player.core.presentation

sealed interface LoadableState<out T> {
    val content: T?

    data class Content<T>(override val content: T) : LoadableState<T>

    data class Loading<T>(override val content: T? = null) : LoadableState<T>

    data class Failure<T>(
        val message: String,
        override val content: T? = null,
    ) : LoadableState<T>
}
