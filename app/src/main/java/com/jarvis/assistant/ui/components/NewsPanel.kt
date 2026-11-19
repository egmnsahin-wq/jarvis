package com.jarvis.assistant.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.commands.NewsItem
import com.jarvis.assistant.data.NewsSession
import com.jarvis.assistant.data.NewsUiState
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisGreen
import com.jarvis.assistant.ui.theme.JarvisOrange
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import kotlin.math.absoluteValue

private val TOPICS = listOf("Gündem", "Spor", "Ekonomi", "Teknoloji", "Dünya", "Sağlık", "Bilim")

/**
 * Iron Man tarzı haber paneli. Dokunma, ses ve el hareketi aynı [NewsSession] üzerinden çalışır.
 */
@Composable
fun NewsPanel(session: NewsSession, state: NewsUiState) {
    val context = LocalContext.current
    BackHandler(enabled = true) { session.onBack() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        session.setGesture(granted)
    }
    fun toggleGesture() {
        if (state.gestureEnabled) session.setGesture(false)
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            session.setGesture(true)
        else permLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground.copy(alpha = 0.97f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // ---- üst çubuk
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("HABER AKIŞI", color = JarvisBlue, fontSize = 20.sp, fontWeight = FontWeight.Light)
                    Text(
                        if (state.query.isBlank()) "Gündem" else state.query,
                        color = JarvisTextSecondary, fontSize = 12.sp
                    )
                }
                IconButton(onClick = { toggleGesture() }) {
                    Icon(Icons.Filled.PanTool, "El hareketi", tint = if (state.gestureEnabled) JarvisGreen else JarvisTextSecondary)
                }
                if (state.gestureEnabled) {
                    IconButton(onClick = { session.flipCamera() }) {
                        Icon(Icons.Filled.FlipCameraAndroid, "Kamerayı çevir", tint = JarvisTextSecondary)
                    }
                }
                IconButton(onClick = { session.toggleSpeak() }) {
                    Icon(
                        if (state.speakOnNavigate) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                        "Sesli okuma", tint = if (state.speakOnNavigate) JarvisBlue else JarvisTextSecondary
                    )
                }
                IconButton(onClick = { session.close() }) {
                    Icon(Icons.Filled.Close, "Kapat", tint = JarvisTextPrimary)
                }
            }

            if (state.detailOpen && state.items.isNotEmpty()) {
                DetailView(session, state)
            } else {
                ListView(session, state)
            }
        }

        // ---- el hareketi kamerası (sağ alt köşe) + durum
        if (state.gestureEnabled) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (state.lastAction.isNotBlank()) {
                    Text(state.lastAction, color = JarvisOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(state.gestureStatus, color = JarvisGreen, fontSize = 11.sp)
                HandGestureCamera(
                    front = state.frontCamera,
                    onGesture = { session.onGesture(it) },
                    onStatus = { session.onGestureStatus(it) },
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(width = 132.dp, height = 99.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, JarvisBlue, RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

// ============================================================================ liste

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.ListView(session: NewsSession, state: NewsUiState) {
    var searchText by remember { mutableStateOf("") }
    fun doSearch() {
        if (searchText.isNotBlank()) { session.openFromUi(searchText.trim()); searchText = "" }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = searchText,
        onValueChange = { searchText = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Haberlerde ara...") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { doSearch() }),
        trailingIcon = {
            IconButton(onClick = { doSearch() }) { Icon(Icons.Filled.Search, "Ara", tint = JarvisBlue) }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = JarvisTextPrimary, unfocusedTextColor = JarvisTextPrimary
        )
    )
    LazyRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TOPICS) { topic ->
            Surface(
                shape = RoundedCornerShape(50),
                color = JarvisSurface,
                border = BorderStroke(1.dp, JarvisBlue.copy(alpha = 0.5f)),
                modifier = Modifier.clickable { session.openFromUi(if (topic == "Gündem") null else topic) }
            ) {
                Text(topic, color = JarvisBlue, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            state.loading -> CircularProgressIndicator(color = JarvisBlue)
            state.error != null -> Text(state.error, color = JarvisTextSecondary)
            state.items.isEmpty() -> Text("Haber bulunamadı.", color = JarvisTextSecondary)
            else -> key(state.items) {
                val pagerState = rememberPagerState(initialPage = state.index) { state.items.size }
                LaunchedEffect(state.index) {
                    if (pagerState.currentPage != state.index && state.index in state.items.indices) {
                        pagerState.animateScrollToPage(state.index)
                    }
                }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { session.select(it) }
                }
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 44.dp),
                    pageSpacing = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    NewsCard(
                        item = state.items[page],
                        number = page + 1,
                        total = state.items.size,
                        modifier = Modifier.graphicsLayer {
                            val sc = 1f - 0.12f * offset
                            scaleX = sc; scaleY = sc
                            alpha = 1f - 0.5f * offset
                        },
                        onClick = { session.select(page); session.openDetail(false) }
                    )
                }
            }
        }
    }

    // ---- dokunmatik alt kontroller
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { session.move(-1, false) }) { Icon(Icons.Filled.ChevronLeft, "Önceki", tint = JarvisBlue, modifier = Modifier.size(32.dp)) }
        Text(
            if (state.items.isEmpty()) "0 / 0" else "${state.index + 1} / ${state.items.size}",
            color = JarvisTextSecondary, modifier = Modifier.width(72.dp)
        )
        IconButton(onClick = { session.move(+1, false) }) { Icon(Icons.Filled.ChevronRight, "Sonraki", tint = JarvisBlue, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(50), color = JarvisBlue,
            modifier = Modifier.clickable(enabled = state.items.isNotEmpty()) { session.openDetail(false) }
        ) {
            Text("DETAY", color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        }
    }
    Text(
        "El: sağdan sola kaydır = sonraki • soldan sağa = önceki • işaret parmağını kameraya yaklaştır = detay • geri çek = liste\n" +
            "Ses: \"sonraki\", \"önceki\", \"detay\", \"3. haber\", \"ekonomi haberlerini ara\", \"kapat\"",
        color = JarvisTextSecondary, fontSize = 10.sp, lineHeight = 13.sp,
        modifier = Modifier.padding(end = if (state.gestureEnabled) 140.dp else 0.dp)
    )
}

@Composable
private fun NewsCard(item: NewsItem, number: Int, total: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().height(260.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = JarvisSurface,
        border = BorderStroke(1.5.dp, JarvisBlue.copy(alpha = 0.8f))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("%02d / %02d".format(number, total), color = JarvisOrange, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    item.title, color = JarvisTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                    lineHeight = 25.sp, maxLines = 6, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                listOf(item.source, item.timeLabel).filter { it.isNotBlank() }.joinToString("  •  "),
                color = JarvisTextSecondary, fontSize = 12.sp
            )
        }
    }
}

// ============================================================================ detay

@Composable
private fun ColumnScope.DetailView(session: NewsSession, state: NewsUiState) {
    val context = LocalContext.current
    val item = state.items[state.index]
    fun openLink(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { /* tarayıcı yoksa sessiz geç */ }
    }

    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { session.closeDetail() }) { Icon(Icons.Filled.ArrowBack, "Listeye dön", tint = JarvisBlue) }
            Text("${state.index + 1} / ${state.items.size}", color = JarvisOrange, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { session.move(-1, false) }) { Icon(Icons.Filled.ChevronLeft, "Önceki", tint = JarvisBlue) }
            IconButton(onClick = { session.move(+1, false) }) { Icon(Icons.Filled.ChevronRight, "Sonraki", tint = JarvisBlue) }
        }
        Surface(
            shape = RoundedCornerShape(16.dp), color = JarvisSurface,
            border = BorderStroke(1.5.dp, JarvisBlue.copy(alpha = 0.8f)), modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(item.title, color = JarvisTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 29.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    listOf(item.source, item.timeLabel).filter { it.isNotBlank() }.joinToString("  •  "),
                    color = JarvisTextSecondary, fontSize = 13.sp
                )
                if (item.description.isNotBlank() && item.description != item.title && !item.description.startsWith(item.title)) {
                    Spacer(Modifier.height(12.dp))
                    Text(item.description, color = JarvisTextPrimary, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }

        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = RoundedCornerShape(50), color = JarvisBlue,
                modifier = Modifier.clickable(enabled = item.link.isNotBlank()) { openLink(item.link) }
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.OpenInBrowser, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tarayıcıda aç", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Surface(
                shape = RoundedCornerShape(50), color = JarvisSurface,
                border = BorderStroke(1.dp, JarvisBlue),
                modifier = Modifier.clickable { /* sesli oku */ session.speakText(session.readCurrent()) }
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VolumeUp, null, tint = JarvisBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Oku", color = JarvisBlue, fontSize = 13.sp)
                }
            }
        }

        Text("AYNI KONUDA DİĞER KAYNAKLAR", color = JarvisOrange, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        when {
            state.relatedLoading -> CircularProgressIndicator(color = JarvisBlue, modifier = Modifier.size(24.dp))
            state.related.isEmpty() -> Text("Benzer haber bulunamadı.", color = JarvisTextSecondary, fontSize = 13.sp)
            else -> state.related.forEach { r ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .clickable(enabled = r.link.isNotBlank()) { openLink(r.link) }
                ) {
                    Text(r.title, color = JarvisTextPrimary, fontSize = 15.sp)
                    Text(
                        listOf(r.source, r.timeLabel).filter { it.isNotBlank() }.joinToString("  •  "),
                        color = JarvisTextSecondary, fontSize = 11.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(if (state.gestureEnabled) 120.dp else 16.dp))
    }
}
