package info.jukov.player.feature.library.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.withPlayerBottomInset
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private data class LibraryItem(
    val title: String,
    val icon: DrawableResource,
    val onClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onFavoritesClick: () -> Unit,
    onTracksClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onDownloadsClick: () -> Unit,
) {
    val items = listOf(
        LibraryItem(stringResource(Res.string.favorites), Res.drawable.heart, onFavoritesClick),
        LibraryItem(stringResource(Res.string.playlists), Res.drawable.playlist_music),
        LibraryItem(stringResource(Res.string.tracks), Res.drawable.music_box_multiple, onTracksClick),
        LibraryItem(stringResource(Res.string.artists), Res.drawable.account_multiple, onArtistsClick),
        LibraryItem(stringResource(Res.string.albums), Res.drawable.album, onAlbumsClick),
        LibraryItem(stringResource(Res.string.downloads), Res.drawable.download_circle, onDownloadsClick),
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppFlexibleTopAppBar(
                title = stringResource(Res.string.library),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(vertical = Padding.small).withPlayerBottomInset(),
            verticalArrangement = Arrangement.spacedBy(Padding.small),
        ) {
            items(items, key = LibraryItem::title) { item ->
                ListItem(
                    modifier = item.onClick?.let { onClick ->
                        Modifier.clickable(onClick = onClick)
                    } ?: Modifier,
                    headlineContent = {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}
