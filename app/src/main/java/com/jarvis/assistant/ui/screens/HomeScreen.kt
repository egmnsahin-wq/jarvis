package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import com.jarvis.assistant.data.JarvisViewModel
import com.jarvis.assistant.ui.components.JarvisOrb
import com.jarvis.assistant.ui.components.VoiceState
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisOrange
import com.jarvis.assistant.ui.theme.JarvisTextSecondary

@Composable
fun HomeScreen(
    viewModel: JarvisViewModel,
    onRequestMicPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val assistantName = remember(state) { com.jarvis.assistant.data.PreferencesManager.getAssistantName(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Ayarlar", tint = JarvisTextSecondary)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = assistantName.uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                color = JarvisBlue,
                fontWeight = FontWeight.Light
            )

            Text(
                text = statusLabel(state.voiceState),
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    text = "Arka planda \"$assistantName\" ile uyan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Switch(
                    checked = state.backgroundListeningEnabled,
                    onCheckedChange = { enabled ->
                        onRequestMicPermission()
                        viewModel.toggleBackgroundListening(enabled)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = JarvisOrange, checkedTrackColor = JarvisOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Central Jarvis orb — sits on its own, roughly mid-screen, not tied to the mic button.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                JarvisOrb(state = state.voiceState, amplitude = state.amplitude)
            }

            // Conversation history
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Bottom,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.history) { (userText, jarvisText) ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Sen: $userText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary
                        )
                        Text(
                            text = "Jarvis: $jarvisText",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (state.history.isEmpty()) {
                    item {
                        Text(
                            text = state.response,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Mic button — standalone now, the orb above already carries the visual feedback.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val context = LocalContext.current
                val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                        } ?: "dosya"
                        try {
                            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: Exception) { /* not all providers support persistable permissions, fine either way */ }
                        viewModel.onFileSelected(uri, name)
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { filePicker.launch(arrayOf("*/*")) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "Dosya seç ve analiz et",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable {
                            onRequestMicPermission()
                            viewModel.onMicButtonPressed()
                        },
                    shape = CircleShape,
                    color = if (state.voiceState == VoiceState.LISTENING) JarvisOrange else JarvisBlue
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Konuş",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Typed input — same pipeline as voice. The speaker icon decides whether typed
            // messages also get a spoken reply (off by default: you're probably typing because
            // you can't talk right now).
            var typed by rememberSaveable { mutableStateOf("") }
            val sendTyped = {
                if (typed.isNotBlank()) {
                    viewModel.onTextSubmitted(typed)
                    typed = ""
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Yazarak sor ya da komut ver...") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendTyped() })
                )
                IconButton(onClick = { viewModel.toggleSpeakTypedReplies() }) {
                    Icon(
                        imageVector = if (state.speakTypedReplies) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        contentDescription = "Yazılı mesajlara sesli cevap",
                        tint = if (state.speakTypedReplies) JarvisOrange else JarvisTextSecondary
                    )
                }
                IconButton(
                    onClick = { sendTyped() },
                    enabled = typed.isNotBlank() && state.voiceState != VoiceState.THINKING
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Gönder",
                        tint = if (typed.isNotBlank()) JarvisBlue else JarvisTextSecondary
                    )
                }
            }

            Text(
                text = if (state.transcript.isNotBlank()) "\"${state.transcript}\"" else "Basılı tutmadan tek dokun, konuş",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextSecondary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

private fun statusLabel(state: VoiceState): String = when (state) {
    VoiceState.IDLE -> "HAZIR"
    VoiceState.LISTENING -> "DİNLİYORUM..."
    VoiceState.THINKING -> "DÜŞÜNÜYORUM..."
    VoiceState.SPEAKING -> "KONUŞUYORUM..."
}
