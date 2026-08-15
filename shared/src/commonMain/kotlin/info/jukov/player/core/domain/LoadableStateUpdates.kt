package info.jukov.player.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

fun <T> MutableStateFlow<LoadableState<List<T>>>.updateItem(
    predicate: (T) -> Boolean,
    transform: (T) -> T,
) = update { state -> state.mapContent { items -> items.map { if (predicate(it)) transform(it) else it } } }

fun <T> LoadableState<T>.mapContent(transform: (T) -> T): LoadableState<T> = when (this) {
    is LoadableState.Content -> LoadableState.Content(transform(content))
    is LoadableState.Loading -> LoadableState.Loading(content?.let(transform))
    is LoadableState.Failure -> LoadableState.Failure(error, content?.let(transform))
}
