package info.jukov.player

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.JukovTheme
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.presentation.AuthUiState
import info.jukov.player.feature.auth.presentation.ui.LoginScreen
import info.jukov.player.feature.library.presentation.LibraryViewModel
import info.jukov.player.feature.library.presentation.ui.LibraryScreen
import info.jukov.player.feature.search.domain.LibrarySearchPage
import info.jukov.player.feature.search.domain.SearchOffsets
import info.jukov.player.feature.search.domain.SearchPage
import info.jukov.player.feature.search.domain.SearchRepository
import info.jukov.player.feature.search.domain.SearchUseCase
import info.jukov.player.feature.track.domain.Track

private const val ScreenshotWidth = 412
private const val ScreenshotHeight = 892

@PreviewTest
@Preview(
    name = "Login",
    locale = "en",
    widthDp = ScreenshotWidth,
    heightDp = ScreenshotHeight,
    fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
fun LoginScreenScreenshot() {
    ScreenshotSurface {
        LoginScreen(
            state = AuthUiState(
                auth = LoadableState.Content(AuthState.LoggedOut),
                server = "https://music.example.com",
                username = "listener",
                password = "fixed-password",
            ),
            onServerChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onLogin = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Library",
    locale = "en",
    widthDp = ScreenshotWidth,
    heightDp = ScreenshotHeight,
    fontScale = 1f,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
fun LibraryScreenScreenshot() {
    val viewModel = remember { LibraryViewModel(SearchUseCase(FixedSearchRepository)) }
    ScreenshotSurface {
        LibraryScreen(
            viewModel = viewModel,
            onLogout = {},
            onFavoritesClick = {},
            onTracksClick = {},
            onArtistsClick = {},
            onAlbumsClick = {},
            onDownloadsClick = {},
            onPlaylistsClick = {},
            onArtistClick = {},
            onAlbumClick = {},
            onTrackClick = {},
        )
    }
}

@Composable
private fun ScreenshotSurface(content: @Composable () -> Unit) {
    JukovTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

private object FixedSearchRepository : SearchRepository {
    override suspend fun artists(query: String, offset: Int, size: Int): SearchPage<Artist> =
        SearchPage(items = emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun albums(
        query: String,
        offset: Int,
        size: Int,
        artistId: String?,
    ): SearchPage<Album> = SearchPage(items = emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun tracks(
        query: String,
        offset: Int,
        size: Int,
        artistId: String?,
    ): SearchPage<Track> = SearchPage(items = emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun library(
        query: String,
        offsets: SearchOffsets,
        size: Int,
    ): LibrarySearchPage = LibrarySearchPage(
        items = emptyList(),
        nextOffsets = SearchOffsets(),
        hasMore = false,
    )
}
