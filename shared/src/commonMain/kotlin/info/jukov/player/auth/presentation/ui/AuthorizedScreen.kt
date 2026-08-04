package info.jukov.player.auth.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.jukov.player.auth.domain.AuthSession

@Composable
fun AuthorizedScreen(session: AuthSession, onLogout: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().safeContentPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Авторизованная зона", style = MaterialTheme.typography.headlineMedium)
            Text("Всё заебись, ${session.username}!")
            Text(session.serverUrl, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onLogout) { Text("Выйти") }
        }
    }
}
