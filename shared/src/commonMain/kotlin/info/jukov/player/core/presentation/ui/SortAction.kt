package info.jukov.player.core.presentation.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import info.jukov.player.core.domain.SortDirection
import info.jukov.player.core.domain.SortOption
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.sort
import jukovplayer.shared.generated.resources.sort_variant
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

data class SortMenuItem<C>(val criterion: C, val label: String, val directions: Set<SortDirection> = SortDirection.entries.toSet())

@Composable
fun <C> SortAction(
    selected: SortOption<C>,
    items: List<SortMenuItem<C>>,
    ascendingLabel: String,
    descendingLabel: String,
    onSelect: (SortOption<C>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(painterResource(Res.drawable.sort_variant), stringResource(Res.string.sort))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        items.forEach { item ->
            item.directions.forEach { direction ->
                val option = SortOption(item.criterion, direction)
                DropdownMenuItem(
                    text = {
                        Text("${if (selected == option) "✓ " else ""}${item.label} · ${if (direction == SortDirection.Ascending) ascendingLabel else descendingLabel}")
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
