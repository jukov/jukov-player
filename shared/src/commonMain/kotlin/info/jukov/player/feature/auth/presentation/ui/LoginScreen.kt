package info.jukov.player.feature.auth.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import info.jukov.player.feature.auth.presentation.AuthUiState
import info.jukov.player.feature.auth.presentation.AuthViewModel
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.ui.Padding

@Composable
fun LoginScreen(state: AuthUiState, viewModel: AuthViewModel) {
    val isLoading = state.auth is LoadableState.Loading
    Box(
        modifier = Modifier.fillMaxSize().safeContentPadding().padding(Padding.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(Padding.medium),
        ) {
            Text("Jukov Player", style = MaterialTheme.typography.headlineLarge)
            Text("Подключитесь к своему Subsonic-серверу", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = state.server,
                onValueChange = viewModel::setServer,
                label = { Text("Сервер") },
                placeholder = { Text("https://example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::setUsername,
                label = { Text("Логин") },
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            (state.auth as? LoadableState.Failure)?.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = viewModel::login,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Войти")
                }
            }
        }
    }
}
