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
import androidx.compose.ui.platform.testTag
import info.jukov.player.feature.auth.presentation.AuthUiState
import info.jukov.player.feature.auth.presentation.AuthViewModel
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.localizedMessage
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(state: AuthUiState, viewModel: AuthViewModel) {
    LoginScreen(
        state = state,
        onServerChange = viewModel::setServer,
        onUsernameChange = viewModel::setUsername,
        onPasswordChange = viewModel::setPassword,
        onLogin = viewModel::login,
    )
}

@Composable
fun LoginScreen(
    state: AuthUiState,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val isLoading = state.auth is LoadableState.Loading
    Box(
        modifier = Modifier.fillMaxSize().safeContentPadding().padding(Padding.large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(Padding.medium),
        ) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(Res.string.login_subtitle), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = state.server,
                onValueChange = onServerChange,
                label = { Text(stringResource(Res.string.server)) },
                placeholder = { Text("https://example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("login.server"),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(Res.string.username)) },
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("login.username"),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(Res.string.password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("login.password"),
            )
            (state.auth as? LoadableState.Failure)?.error?.let {
                Text(it.localizedMessage(), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onLogin,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("login.submit"),
            ) {
                if (isLoading) {
                    LoadingIndicator(Modifier.size(22.dp))
                } else {
                    Text(stringResource(Res.string.sign_in))
                }
            }
        }
    }
}
