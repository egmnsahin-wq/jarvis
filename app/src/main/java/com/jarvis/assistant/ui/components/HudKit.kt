package com.jarvis.assistant.ui.components

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.HudCyan
import com.jarvis.assistant.ui.theme.HudGold
import com.jarvis.assistant.ui.theme.HudPanelBottom
import com.jarvis.assistant.ui.theme.HudPanelTop
import com.jarvis.assistant.ui.theme.HudStroke
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisGreen
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Panel iskeleti
// ---------------------------------------------------------------------------------------------

@Composable
fun HudPanel(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onMinimize: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(HudPanelTop, HudPanelBottom)))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(HudCyan.copy(alpha = 0.55f), HudStroke.copy(alpha = 0.25f))),
                shape
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(
                text = title,
                color = JarvisTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onMinimize != null) {
                IconButton(onClick = onMinimize, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = "Küçült", tint = JarvisTextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = JarvisTextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        content()
    }
}

@Composable
fun HudIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(HudPanelBottom)
            .border(1.dp, HudStroke, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Üst bar
// ---------------------------------------------------------------------------------------------

private val TitleShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width * 0.9f, size.height)
    lineTo(size.width * 0.1f, size.height)
    close()
}

@Composable
fun HudTitle(text: String, compact: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(TitleShape)
            .background(Brush.verticalGradient(listOf(HudPanelTop, HudPanelBottom)))
            .border(1.dp, HudStroke, TitleShape)
            .padding(horizontal = if (compact) 30.dp else 60.dp, vertical = if (compact) 6.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (compact) 22.sp else 38.sp,
            letterSpacing = if (compact) 4.sp else 8.sp,
            fontWeight = FontWeight.Medium,
            color = JarvisTextPrimary,
            maxLines = 1
        )
    }
}

data class HudStats(
    val batteryPercent: Int = 0,
    val charging: Boolean = false,
    val temperatureC: Float? = null,
    val ramPercent: Int = 0,
    val connection: String = "…"
)

private fun readHudStats(context: Context): HudStats {
    return try {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempRaw = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temp = if (tempRaw == Int.MIN_VALUE) null else tempRaw / 10f

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val ram = if (mi.totalMem > 0) (((mi.totalMem - mi.availMem) * 100) / mi.totalMem).toInt() else 0

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val conn = when {
            caps == null -> "Çevrimdışı"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobil"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Kablolu"
            else -> "Bağlı"
        }
        HudStats(pct, charging, temp, ram, conn)
    } catch (e: Exception) {
        HudStats()
    }
}

@Composable
fun rememberHudStats(): State<HudStats> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(HudStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = readHudStats(context)
            delay(3000)
        }
    }
    return state
}

@Composable
fun rememberClock(): State<String> {
    val state = remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale("tr"))
        while (true) {
            state.value = fmt.format(Date())
            delay(1000)
        }
    }
    return state
}

@Composable
private fun StatItem(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, color = JarvisTextPrimary, fontSize = 13.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
fun HudStatusChip(stats: HudStats, time: String, compact: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(HudPanelBottom)
            .border(1.dp, HudStroke, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!compact) StatItem(Icons.Filled.Memory, "RAM %${stats.ramPercent}", HudGold)
        val batteryText = buildString {
            append("%${stats.batteryPercent}")
            if (stats.charging) append(" ⚡")
            if (!compact && stats.temperatureC != null) append("  ${"%.0f".format(stats.temperatureC)}°C")
        }
        StatItem(Icons.Filled.BatteryFull, batteryText, JarvisGreen)
        StatItem(
            if (stats.connection == "Çevrimdışı") Icons.Filled.WifiOff else Icons.Filled.Wifi,
            stats.connection,
            HudCyan
        )
        Text(time, color = JarvisTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------------------------
// Alt bar
// ---------------------------------------------------------------------------------------------

@Composable
private fun HudActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(accent.copy(alpha = if (active) 0.24f else 0.10f))
                .border(1.dp, accent.copy(alpha = if (active) 0.95f else 0.45f), shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(28.dp))
        }
        Text(
            text = label,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = if (active) accent else JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun HudBottomBar(
    listening: Boolean,
    chatOpen: Boolean,
    cameraOpen: Boolean,
    filesOpen: Boolean,
    onMic: () -> Unit,
    onText: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(HudPanelBottom)
            .border(1.dp, HudStroke, shape)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudActionButton(Icons.Filled.Mic, "KONUŞ", listening, HudGold, onMic)
        HudActionButton(Icons.Filled.Keyboard, "YAZ", chatOpen, HudCyan, onText)
        HudActionButton(Icons.Filled.CameraAlt, "KAMERA", cameraOpen, HudCyan, onCamera)
        HudActionButton(Icons.Filled.AttachFile, "DOSYA", filesOpen, HudCyan, onFiles)
    }
}

// ---------------------------------------------------------------------------------------------
// Dinleme / dalga paneli
// ---------------------------------------------------------------------------------------------

@Composable
fun WavePanel(
    state: VoiceState,
    amplitude: Float,
    onMic: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val label = when (state) {
        VoiceState.LISTENING -> "Dinlemede..."
        VoiceState.THINKING -> "Düşünüyor..."
        VoiceState.SPEAKING -> "Konuşuyor..."
        VoiceState.IDLE -> "Hazır"
    }
    val dot = when (state) {
        VoiceState.LISTENING -> HudGold
        VoiceState.SPEAKING -> HudCyan
        VoiceState.THINKING -> JarvisAmber
        VoiceState.IDLE -> JarvisGreen
    }
    HudPanel(
        title = label,
        onClose = onClose,
        modifier = modifier,
        leading = { Box(Modifier.padding(end = 8.dp).size(10.dp).clip(CircleShape).background(dot)) }
    ) {
        WaveCanvas(state, amplitude, Modifier.fillMaxWidth().weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
            HudIconButton(Icons.Filled.Mic, "Konuş", HudGold, onMic)
        }
    }
}

@Composable
private fun WaveCanvas(state: VoiceState, amplitude: Float, modifier: Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) time.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    Canvas(modifier) {
        val t = time.floatValue
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val energy = when (state) {
            VoiceState.LISTENING -> 0.25f + amplitude * 0.9f
            VoiceState.SPEAKING -> 0.55f + 0.2f * sin(t * 6f)
            VoiceState.THINKING -> 0.18f
            VoiceState.IDLE -> 0.08f
        }.coerceIn(0.05f, 1f)
        val brush = Brush.horizontalGradient(listOf(HudGold, HudCyan))
        val steps = 80
        for (layer in 0 until 3) {
            val path = Path()
            val phase = t * (2f + layer)
            for (i in 0..steps) {
                val nx = i / steps.toFloat()
                val envelope = sin(PI.toFloat() * nx).pow(2)
                val wave = sin(nx * 14f + phase) * 0.6f + sin(nx * 31f - phase * 1.3f + layer) * 0.4f
                val y = mid + wave * envelope * energy * h * 0.42f * (1f - layer * 0.25f)
                if (i == 0) path.moveTo(nx * w, y) else path.lineTo(nx * w, y)
            }
            drawPath(
                path = path,
                brush = brush,
                alpha = 1f - layer * 0.3f,
                style = Stroke(width = (2.5f - layer * 0.6f).dp.toPx())
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Giriş satırı + sohbet paneli
// ---------------------------------------------------------------------------------------------

@Composable
fun HudInputRow(
    placeholder: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowBlank: Boolean = false
) {
    var text by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(12.dp)
    val submit = {
        if (allowBlank || text.isNotBlank()) {
            onSend(text.trim())
            text = ""
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x33000000))
            .border(1.dp, HudStroke, shape)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = JarvisTextPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(HudCyan),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(placeholder, color = JarvisTextSecondary, fontSize = 15.sp, maxLines = 1)
                    }
                    inner()
                }
            }
        )
        IconButton(onClick = { submit() }) {
            Icon(Icons.Filled.Send, contentDescription = "Gönder", tint = HudCyan)
        }
    }
}

@Composable
private fun ChatBubble(name: String, text: String, nameColor: Color) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) { append("$name: ") }
            withStyle(SpanStyle(color = JarvisTextPrimary)) { append(text) }
        },
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x22FFFFFF))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
fun ChatPanel(
    assistantName: String,
    history: List<Pair<String, String>>,
    fallbackResponse: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onMinimize: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    LaunchedEffect(history.size, collapsed) {
        if (history.isNotEmpty() && !collapsed) listState.animateScrollToItem(history.size * 2 - 1)
    }
    val jarvisName = assistantName.uppercase(Locale("tr"))
    HudPanel(title = "Sohbet", modifier = modifier, onMinimize = onMinimize, onClose = onClose) {
        if (!collapsed) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (history.isEmpty()) {
                    item { ChatBubble(jarvisName, fallbackResponse, HudGold) }
                }
                history.forEach { (user, reply) ->
                    item { ChatBubble("SEN", user, HudCyan) }
                    item { ChatBubble(jarvisName, reply, HudGold) }
                }
            }
            HudInputRow(placeholder = "Bir şey yaz...", onSend = onSend, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dosya paneli
// ---------------------------------------------------------------------------------------------

@Composable
private fun FileTile(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .background(tint.copy(alpha = 0.12f))
                .border(1.dp, tint.copy(alpha = 0.7f), shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
        }
        Text(label, color = JarvisTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun FilePanel(
    onPickTextFile: () -> Unit,
    onPickImage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    HudPanel(title = "Dosya ekle", onClose = onClose, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FileTile(Icons.Filled.Description, "Metin dosyası", HudGold, onPickTextFile)
            FileTile(Icons.Filled.Image, "Fotoğraf", HudCyan, onPickImage)
        }
    }
}
