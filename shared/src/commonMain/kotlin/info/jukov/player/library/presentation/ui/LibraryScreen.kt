package info.jukov.player.library.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import info.jukov.player.core.presentation.ui.Padding

private data class LibraryItem(
    val title: String,
    val onClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
) {
    val items = listOf(
        LibraryItem("Плейлисты"),
        LibraryItem("Треки"),
        LibraryItem("Артисты", onArtistsClick),
        LibraryItem("Альбомы", onAlbumsClick),
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Библиотека") })
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
                )
            }
        }
    }
}
