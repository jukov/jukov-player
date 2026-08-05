package info.jukov.player.core.presentation.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.heart
import jukovplayer.shared.generated.resources.heart_outline
import org.jetbrains.compose.resources.painterResource

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
                "Удалить из избранного"
            } else {
                "Добавить в избранное"
            },
        )
    }
}
