package info.jukov.player.feature.artist.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.artist.domain.GetArtistsUseCase
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.updateItem
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import info.jukov.player.core.domain.ArtistSortCriterion
import info.jukov.player.core.domain.SortOption
import info.jukov.player.core.domain.SortPreferences
import info.jukov.player.core.domain.sortedArtists
import info.jukov.player.core.domain.mapContent
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import info.jukov.player.core.presentation.LoadingOrigin
import info.jukov.player.feature.search.domain.SearchUseCase
import info.jukov.player.feature.search.presentation.PagedSearchDelegate
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

class ArtistsViewModel(
    private val getArtistsUseCase: GetArtistsUseCase,
    authRepository: AuthRepository,
    private val favoriteDelegate: FavoriteDelegate,
    search: SearchUseCase,
    private val sortPreferences: SortPreferences,
    private val getTracksUseCase: GetTracksUseCase,
    private val downloadDelegate: DownloadDelegate,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadableState<List<Artist>>>(
        LoadableState.Loading(content = null),
    )
    val state: StateFlow<LoadableState<List<Artist>>> = _state.asStateFlow()
    private val _sort = MutableStateFlow(sortPreferences.artists)
    val sort = _sort.asStateFlow()
    private val _loadingOrigin = MutableStateFlow<LoadingOrigin?>(LoadingOrigin.Initial)
    val loadingOrigin: StateFlow<LoadingOrigin?> = _loadingOrigin.asStateFlow()
    val pending = favoriteDelegate.pending
    private val actionMessages = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    val messages = merge(favoriteDelegate.messages, actionMessages)
    private val searchDelegate = PagedSearchDelegate(viewModelScope, search::artists)
    val searchActive = searchDelegate.active
    val searchQuery = searchDelegate.query
    val searchState = searchDelegate.state
    val searchHasMore = searchDelegate.hasMore
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.LoggedIn -> loadArtists(forceRefresh = false)
                    AuthState.LoggedOut -> {
                        loadJob?.cancel()
                        closeSearch()
                        _loadingOrigin.value = null
                        _state.value = LoadableState.Content(emptyList())
                    }
                }
            }
        }
        viewModelScope.launch {
            favoriteDelegate.changes.collect { change ->
                val target = change.target as? FavoriteTarget.Artist ?: return@collect
                updateFavorite(target.id, change.isFavorite)
            }
        }
    }

    fun retry() = loadArtists(forceRefresh = true)
    fun refresh() = loadArtists(forceRefresh = true, requestedOrigin = LoadingOrigin.PullToRefresh)

    fun openSearch() = searchDelegate.open()
    fun updateSearchQuery(value: String) = searchDelegate.updateQuery(value)
    fun updateSort(value: SortOption<ArtistSortCriterion>) {
        sortPreferences.artists = value
        _sort.value = value
        _state.update { it.mapContent { artists -> artists.sortedArtists(value) } }
    }
    fun loadMoreSearch() = searchDelegate.loadMore()
    fun retrySearch() = searchDelegate.retry()
    fun closeSearch() = searchDelegate.close()

    fun toggleFavorite(artist: Artist) {
        viewModelScope.launch {
            favoriteDelegate.toggle(FavoriteTarget.Artist(artist.id), artist.isFavorite) {
                updateFavorite(artist.id, it)
            }
        }
    }

    fun toggleFavorites(artists: List<Artist>) = viewModelScope.launch {
        val desired = artists.any { !it.isFavorite }
        favoriteDelegate.setArtists(artists, desired) { artist, isFavorite ->
            updateFavorite(artist.id, isFavorite)
        }
    }

    fun downloadArtists(artists: List<Artist>, onComplete: () -> Unit = {}) =
        resolveArtistTracks(artists) { tracks ->
            tracks.forEach { downloadDelegate.download(it) }
            onComplete()
        }

    fun addArtistsToQueue(
        artists: List<Artist>,
        onTracksReady: (List<Track>) -> Unit,
    ) = resolveArtistTracks(artists) { tracks ->
        onTracksReady(tracks)
    }

    private fun resolveArtistTracks(artists: List<Artist>, action: suspend (List<Track>) -> Unit) =
        viewModelScope.launch {
            val tracks = buildList {
                for (artist in artists) {
                    when (val state = getTracksUseCase(TracksFilter.ByArtist(artist.id))
                        .first { it !is LoadableState.Loading }) {
                        is LoadableState.Content -> addAll(state.content)
                        is LoadableState.Failure -> {
                            actionMessages.emit(state.error)
                            return@launch
                        }
                        is LoadableState.Loading -> Unit
                    }
                }
            }
            action(tracks)
        }

    private fun updateFavorite(id: String, isFavorite: Boolean) {
        _state.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
        searchDelegate.updateItem({ it.id == id }) { it.copy(isFavorite = isFavorite) }
    }

    private fun loadArtists(forceRefresh: Boolean, requestedOrigin: LoadingOrigin? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            getArtistsUseCase(forceRefresh).collect { state ->
                publish(state, requestedOrigin)
            }
        }
    }

    private fun publish(
        state: LoadableState<List<Artist>>,
        requestedOrigin: LoadingOrigin?,
    ) {
        _state.update { current ->
            state.withFallbackContent(current.content).mapContent { artists ->
                artists.sortedArtists(_sort.value)
            }
        }
        _loadingOrigin.value = _state.value.loadingOrigin(requestedOrigin)
    }

    private fun LoadableState<List<Artist>>.withFallbackContent(
        fallback: List<Artist>?,
    ): LoadableState<List<Artist>> = when (this) {
        is LoadableState.Content -> this
        is LoadableState.Loading -> LoadableState.Loading(content ?: fallback)
        is LoadableState.Failure -> LoadableState.Failure(error, content ?: fallback)
    }

    private fun LoadableState<List<Artist>>.loadingOrigin(
        requestedOrigin: LoadingOrigin?,
    ): LoadingOrigin? = when (this) {
        is LoadableState.Loading -> requestedOrigin ?: if (content == null) {
            LoadingOrigin.Initial
        } else {
            LoadingOrigin.Automatic
        }

        else -> null
    }
}
