package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlayerBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
