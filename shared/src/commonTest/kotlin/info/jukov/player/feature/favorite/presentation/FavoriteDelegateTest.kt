package info.jukov.player.feature.favorite.presentation

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.favorite.domain.FavoriteChange
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.album.domain.Album
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteDelegateTest {
    @Test
    fun setOptimisticallyUpdatesAndKeepsSuccessfulValue() = runTest {
        val repository = RecordingFavoritesRepository()
        val delegate = FavoriteDelegate(repository)
        val updates = mutableListOf<Boolean>()

        delegate.set(track(), isFavorite = true, updates::add)

        assertEquals(listOf(true), updates)
        assertEquals(FavoriteTarget.Track("track"), repository.calls.single().first)
        assertTrue(repository.calls.single().second)
        assertTrue(delegate.pending.value.isEmpty())
    }

    @Test
    fun setRollsBackWhenRepositoryFails() = runTest {
        val repository = RecordingFavoritesRepository(result = Result.failure(Exception("failed")))
        val delegate = FavoriteDelegate(repository)
        val updates = mutableListOf<Boolean>()

        delegate.set(track(), isFavorite = true, updates::add)

        assertEquals(listOf(true, false), updates)
        assertTrue(delegate.pending.value.isEmpty())
    }

    @Test
    fun setIgnoresDuplicateMutationWhileTrackIsPending() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = RecordingFavoritesRepository(gate = gate)
        val delegate = FavoriteDelegate(repository)
        val updates = mutableListOf<Boolean>()
        val first = launch {
            delegate.set(track(), isFavorite = true, updates::add)
        }
        runCurrent()

        delegate.set(track(), isFavorite = true, updates::add)

        assertEquals(1, repository.calls.size)
        assertEquals(listOf(true), updates)
        gate.complete(Unit)
        first.join()
    }

    @Test
    fun setAlbumsUpdatesEveryChangedAlbum() = runTest {
        val repository = RecordingFavoritesRepository()
        val delegate = FavoriteDelegate(repository)
        val updates = mutableListOf<Pair<String, Boolean>>()
        val albums = listOf(
            album(id = "first", isFavorite = false),
            album(id = "second", isFavorite = true),
        )

        delegate.setAlbums(albums, isFavorite = true) { album, isFavorite ->
            updates += album.id to isFavorite
        }

        assertEquals(listOf("first" to true), updates)
        assertEquals(FavoriteTarget.Album("first"), repository.calls.single().first)
        assertTrue(repository.calls.single().second)
        assertTrue(delegate.pending.value.isEmpty())
    }

    private fun track() = Track(
        id = "track",
        title = "Track",
        artist = "Artist",
        albumId = null,
        artistId = null,
        trackNumber = 1,
        coverArtUrl = null,
        isFavorite = false,
    )

    private fun album(id: String, isFavorite: Boolean) = Album(
        id = id,
        name = "Album",
        artist = "Artist",
        artistId = null,
        coverArtUrl = null,
        isFavorite = isFavorite,
    )
}

private class RecordingFavoritesRepository(
    private val result: Result<Unit> = Result.success(Unit),
    private val gate: CompletableDeferred<Unit>? = null,
) : FavoritesRepository {
    private val mutableChanges = MutableSharedFlow<FavoriteChange>()
    override val changes: SharedFlow<FavoriteChange> = mutableChanges
    val calls = mutableListOf<Pair<FavoriteTarget, Boolean>>()

    override fun getFavorites(forceRefresh: Boolean): Flow<LoadableState<Favorites>> = emptyFlow()

    override suspend fun setFavorite(
        target: FavoriteTarget,
        isFavorite: Boolean,
    ): Result<Unit> = setFavorites(listOf(target), isFavorite)

    override suspend fun setFavorites(
        targets: List<FavoriteTarget>,
        isFavorite: Boolean,
    ): Result<Unit> {
        calls += targets.map { it to isFavorite }
        gate?.await()
        if (result.isSuccess) {
            targets.forEach { mutableChanges.emit(FavoriteChange(it, isFavorite)) }
        }
        return result
    }
}
