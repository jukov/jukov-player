package info.jukov.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.auth.presentation.ui.AuthScreen

@Composable
fun App(repository: AuthRepository) {
    MaterialTheme {
        AuthScreen(repository)
    }
}
