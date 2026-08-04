package info.jukov.player.auth.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(state: AuthUiState, viewModel: AuthViewModel) {
    Box(
        modifier = Modifier.fillMaxSize().safeContentPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Jukov Player", style = MaterialTheme.typography.headlineLarge)
            Text("Подключитесь к своему Subsonic-серверу", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = state.server,
                onValueChange = viewModel::setServer,
                label = { Text("Сервер") },
                placeholder = { Text("https://example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !state.isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::setUsername,
                label = { Text("Логин") },
                enabled = !state.isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !state.isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = viewModel::login,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Войти")
                }
            }
        }
    }
}
