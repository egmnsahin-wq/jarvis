package com.jarvis.assistant.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jarvis.assistant.data.JarvisViewModel

private enum class Screen { HOME, SETTINGS }

/** Top-level navigation: just a Home <-> Settings toggle, no nav library needed for two screens. */
@Composable
fun JarvisApp(viewModel: JarvisViewModel, onRequestMicPermission: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    when (screen) {
        Screen.HOME -> HomeScreen(
            viewModel = viewModel,
            onRequestMicPermission = onRequestMicPermission,
            onOpenSettings = { screen = Screen.SETTINGS }
        )
        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
    }
}
