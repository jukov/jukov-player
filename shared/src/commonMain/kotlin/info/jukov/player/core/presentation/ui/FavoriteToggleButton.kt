package info.jukov.player.core.presentation.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun FavoriteToggleButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(
                if (isFavorite) Res.drawable.heart else Res.drawable.heart_outline,
            ),
            contentDescription = if (isFavorite) {
                stringResource(Res.string.remove_from_favorites)
            } else {
                stringResource(Res.string.add_to_favorites)
            },
        )
    }
}
