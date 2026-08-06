package info.jukov.player.core.domain

data class Page<T>(
    val items: List<T>,
    val hasMore: Boolean,
)
