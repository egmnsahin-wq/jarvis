package com.jarvis.assistant.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.commands.CameraBridge
import com.jarvis.assistant.data.JarvisViewModel
import com.jarvis.assistant.data.PreferencesManager
import com.jarvis.assistant.ui.components.ChatPanel
import com.jarvis.assistant.ui.components.FilePanel
import com.jarvis.assistant.ui.components.HudBottomBar
import com.jarvis.assistant.ui.components.HudIconButton
import com.jarvis.assistant.ui.components.HudStats
import com.jarvis.assistant.ui.components.HudStatusChip
import com.jarvis.assistant.ui.components.HudTitle
import com.jarvis.assistant.ui.components.LiveCameraPanel
import com.jarvis.assistant.ui.components.NewsPanel
import com.jarvis.assistant.ui.components.ParticleOrb
import com.jarvis.assistant.ui.components.VoiceState
import com.jarvis.assistant.ui.components.WavePanel
import com.jarvis.assistant.ui.components.rememberClock
import com.jarvis.assistant.ui.components.rememberHudStats
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisOrange
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: JarvisViewModel,
    onRequestMicPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val assistantName = remember(state) { PreferencesManager.getAssistantName(context) }
    val stats by rememberHudStats()
    val clock by rememberClock()

    var showWave by remember { mutableStateOf(true) }
    var showCamera by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }          // yalnızca telefon düzeninde kaplama
    var chatCollapsed by remember { mutableStateOf(false) }     // yalnızca tablet düzeninde
    var cameraOffset by remember { mutableStateOf(Offset.Zero) }

    // "Jarvis, kameradan bak" komutu paneli kendiliğinden açabilsin.
    DisposableEffect(Unit) {
        CameraBridge.panelOpener = { showCamera = true }
        onDispose { CameraBridge.panelOpener = null }
    }

    val textFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
            } ?: "dosya"
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { /* her sağlayıcı kalıcı izin vermez, sorun değil */ }
            viewModel.onFileSelected(uri, name)
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImageSelected(uri)
    }

    val onMic: () -> Unit = {
        showWave = true
        onRequestMicPermission()
        viewModel.onMicButtonPressed()
    }
    val onNews: () -> Unit = { viewModel.news.openFromUi(null) }
    val onSend: (String) -> Unit = { viewModel.onTextSubmitted(it) }
    val onBackgroundToggle: (Boolean) -> Unit = { enabled ->
        onRequestMicPermission()
        viewModel.toggleBackgroundListening(enabled)
    }
    val listening = state.voiceState == VoiceState.LISTENING

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1520), JarvisBackground)))
    ) {
        val wide = maxWidth >= 840.dp && maxWidth > maxHeight

        if (wide) {
            // ------------------------------ TABLET (yatay) ------------------------------
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                TopBar(assistantName, false, stats, clock, onNews, onOpenSettings)

                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(0.28f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (showWave) {
                            WavePanel(
                                state = state.voiceState,
                                amplitude = state.amplitude,
                                onMic = onMic,
                                onClose = { showWave = false },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        if (showCamera) {
                            LiveCameraPanel(
                                onClose = { showCamera = false },
                                onAsk = { viewModel.onCameraQuestion(it) },
                                modifier = Modifier.fillMaxWidth().weight(1.35f)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(0.44f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ParticleOrb(
                                state = state.voiceState,
                                amplitude = state.amplitude,
                                diameter = minOf(maxHeight - 260.dp, maxWidth * 0.40f, 460.dp).coerceAtLeast(200.dp),
                                particleCount = 800
                            )
                        }
                        ReplyText(state.transcript, state.response, maxLines = 3)
                        BackgroundListenRow(assistantName, state.backgroundListeningEnabled, onBackgroundToggle)
                    }

                    Column(
                        modifier = Modifier.weight(0.28f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ChatPanel(
                            assistantName = assistantName,
                            history = state.history,
                            fallbackResponse = state.response,
                            onSend = onSend,
                            collapsed = chatCollapsed,
                            onMinimize = { chatCollapsed = !chatCollapsed },
                            modifier = Modifier.fillMaxWidth().then(if (chatCollapsed) Modifier else Modifier.weight(1f))
                        )
                        if (showFiles) {
                            FilePanel(
                                onPickTextFile = { textFilePicker.launch(arrayOf("*/*")) },
                                onPickImage = { imagePicker.launch(arrayOf("image/*")) },
                                onClose = { showFiles = false },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    HudBottomBar(
                        listening = listening,
                        chatOpen = !chatCollapsed,
                        cameraOpen = showCamera,
                        filesOpen = showFiles,
                        onMic = onMic,
                        onText = { chatCollapsed = false },
                        onCamera = { showCamera = !showCamera },
                        onFiles = { showFiles = !showFiles }
                    )
                }
            }
        } else {
            // ------------------------------ TELEFON / DİKEY ------------------------------
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TopBar(assistantName, true, stats, clock, onNews, onOpenSettings)
                BackgroundListenRow(assistantName, state.backgroundListeningEnabled, onBackgroundToggle)
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ParticleOrb(
                        state = state.voiceState,
                        amplitude = state.amplitude,
                        diameter = minOf(maxWidth - 24.dp, maxHeight * 0.42f).coerceAtLeast(180.dp),
                        particleCount = 550
                    )
                }
                ReplyText(state.transcript, state.response, maxLines = 4)
                HudBottomBar(
                    listening = listening,
                    chatOpen = showChat,
                    cameraOpen = showCamera,
                    filesOpen = showFiles,
                    onMic = onMic,
                    onText = {
                        showChat = !showChat
                        if (showChat) showFiles = false
                    },
                    onCamera = { showCamera = !showCamera },
                    onFiles = {
                        showFiles = !showFiles
                        if (showFiles) showChat = false
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Sürüklenebilir küçük kamera penceresi
            if (showCamera) {
                LiveCameraPanel(
                    onClose = { showCamera = false },
                    onAsk = { viewModel.onCameraQuestion(it) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 130.dp, end = 12.dp)
                        .offset { IntOffset(cameraOffset.x.roundToInt(), cameraOffset.y.roundToInt()) }
                        .size(width = 220.dp, height = 300.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                cameraOffset += drag
                            }
                        }
                )
            }

            AnimatedVisibility(
                visible = showChat,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 120.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                ChatPanel(
                    assistantName = assistantName,
                    history = state.history,
                    fallbackResponse = state.response,
                    onSend = onSend,
                    onClose = { showChat = false },
                    modifier = Modifier.fillMaxWidth().height(maxHeight * 0.5f)
                )
            }

            AnimatedVisibility(
                visible = showFiles,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 120.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                FilePanel(
                    onPickTextFile = { textFilePicker.launch(arrayOf("*/*")) },
                    onPickImage = { imagePicker.launch(arrayOf("image/*")) },
                    onClose = { showFiles = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val newsState by viewModel.news.state.collectAsState()
        if (newsState.visible) {
            NewsPanel(session = viewModel.news, state = newsState)
        }
    }
}

@Composable
private fun TopBar(
    assistantName: String,
    compact: Boolean,
    stats: HudStats,
    time: String,
    onNews: () -> Unit,
    onSettings: () -> Unit
) {
    if (compact) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HudIconButton(Icons.Filled.Article, "Haberler", JarvisBlue, onNews)
                HudTitle(assistantName.uppercase(), compact = true)
                HudIconButton(Icons.Filled.Settings, "Ayarlar", JarvisTextSecondary, onSettings)
            }
            HudStatusChip(stats, time, compact = true, modifier = Modifier.padding(top = 8.dp))
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            HudStatusChip(stats, time, compact = false, modifier = Modifier.align(Alignment.CenterStart))
            HudTitle(assistantName.uppercase(), compact = false, modifier = Modifier.align(Alignment.TopCenter))
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HudIconButton(Icons.Filled.Article, "Haberler", JarvisBlue, onNews)
                HudIconButton(Icons.Filled.Settings, "Ayarlar", JarvisTextSecondary, onSettings)
            }
        }
    }
}

@Composable
private fun ReplyText(transcript: String, response: String, maxLines: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (transcript.isNotBlank()) {
            Text(
                text = "\"$transcript\"",
                fontSize = 12.sp,
                color = JarvisTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = response,
            fontSize = 15.sp,
            color = JarvisTextPrimary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun BackgroundListenRow(assistantName: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Arka planda \"$assistantName\" ile uyan",
            style = MaterialTheme.typography.bodySmall,
            color = JarvisTextSecondary
        )
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JarvisOrange,
                checkedTrackColor = JarvisOrange.copy(alpha = 0.5f)
            ),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
