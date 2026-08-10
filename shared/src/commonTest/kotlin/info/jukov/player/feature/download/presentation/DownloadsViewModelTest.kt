package info.jukov.player.feature.download.presentation

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.favorite.domain.FavoriteChange
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun removeAlbumsCancelsEverySelectedAlbumInOrder() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val downloadsRepository = RecordingDownloadsRepository()
        val viewModel = DownloadsViewModel(
            downloadsRepository,
            FavoriteDelegate(RecordingFavoritesRepository()),
        )
        val albums = listOf(album("first"), album("second"))

        viewModel.removeAlbums(albums)
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), downloadsRepository.cancelledAlbumIds)
    }

    @Test
    fun toggleFavoriteAlbumsFavoritesOnlyAlbumsThatNeedChanging() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val favoritesRepository = RecordingFavoritesRepository()
        val viewModel = DownloadsViewModel(
            RecordingDownloadsRepository(),
            FavoriteDelegate(favoritesRepository),
        )

        viewModel.toggleFavoriteAlbums(
            listOf(album("new", isFavorite = false), album("existing", isFavorite = true)),
        )
        advanceUntilIdle()

        assertEquals(
            listOf(FavoriteTarget.Album("new")) to true,
            favoritesRepository.bulkCalls.single(),
        )
        assertTrue(viewModel.state.value is LoadableState.Content)
    }

    private fun album(id: String, isFavorite: Boolean = false) = Album(
        id = id,
        name = "Album $id",
        artist = "Artist",
        artistId = null,
        coverArtUrl = null,
        isFavorite = isFavorite,
    )
}

private class RecordingFavoritesRepository : FavoritesRepository {
    override val changes = MutableSharedFlow<FavoriteChange>()
    val bulkCalls = mutableListOf<Pair<List<FavoriteTarget>, Boolean>>()

    override fun getFavorites(forceRefresh: Boolean): Flow<LoadableState<Favorites>> = emptyFlow()

    override suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean): Result<Unit> =
        setFavorites(listOf(target), isFavorite)

    override suspend fun setFavorites(
        targets: List<FavoriteTarget>,
        isFavorite: Boolean,
    ): Result<Unit> {
        bulkCalls += targets to isFavorite
        return Result.success(Unit)
    }
}
