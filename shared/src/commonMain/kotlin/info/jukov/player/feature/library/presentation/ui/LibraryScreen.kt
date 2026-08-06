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
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.account_multiple
import jukovplayer.shared.generated.resources.album
import jukovplayer.shared.generated.resources.download_circle
import jukovplayer.shared.generated.resources.heart
import jukovplayer.shared.generated.resources.music_box_multiple
import jukovplayer.shared.generated.resources.playlist_music
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

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
) {
    val items = listOf(
        LibraryItem("Избранное", Res.drawable.heart, onFavoritesClick),
        LibraryItem("Плейлисты", Res.drawable.playlist_music),
        LibraryItem("Треки", Res.drawable.music_box_multiple, onTracksClick),
        LibraryItem("Артисты", Res.drawable.account_multiple, onArtistsClick),
        LibraryItem("Альбомы", Res.drawable.album, onAlbumsClick),
        LibraryItem("Загрузки", Res.drawable.download_circle),
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppFlexibleTopAppBar(
                title = "Библиотека",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(vertical = Padding.small),
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
