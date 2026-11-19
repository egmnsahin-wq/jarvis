package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.data.PeopleStore
import com.jarvis.assistant.speaker.SpeakerIdController
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisOrange
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings > Kişiler ve Ses Tanıma.
 * Everything here saves immediately (no need to press the main Kaydet button).
 */
@Composable
fun PeopleSettingsSection(fieldColors: TextFieldColors) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var people by remember { mutableStateOf(PeopleStore.getPeople(context)) }
    var forcedId by remember { mutableStateOf(PeopleStore.getForcedSpeakerId(context)) }
    var sensitivity by remember { mutableStateOf(PeopleStore.getSensitivity(context)) }
    var newName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun refresh() { people = PeopleStore.getPeople(context) }

    Text(
        text = "Kişiler ve Ses Tanıma",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)
    )
    Text(
        text = "Her kişinin adını yaz ve sesini kaydet. Jarvis konuşanı sesinden tanır; o kişinin kendi konuşma geçmişi, hatırlattıkları ve kişilik profiliyle cevap verir. " +
            "Kişilik profili o kişinin Jarvis'le konuşmalarından yapay zekayla çıkarılır (ses tonundan kişilik anlaşılamaz) — istediğin zaman kendin düzeltebilirsin.",
        style = MaterialTheme.typography.labelSmall,
        color = JarvisTextSecondary,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    people.forEach { p ->
        key(p.id) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .border(1.dp, JarvisTextSecondary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = p.name,
                    onValueChange = { PeopleStore.setName(context, p.id, it); refresh() },
                    label = { Text("Kişinin adı") },
                    colors = fieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (p.fingerprints.isEmpty()) "Henüz ses kaydı yok — tanınabilmesi için sesini kaydet."
                    else "${p.fingerprints.size} ses örneği kayıtlı.",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue),
                        onClick = {
                            busy = true
                            scope.launch {
                                val label = p.name.ifBlank { "Kişi" }
                                if (!SpeakerIdController.startCapture(context)) {
                                    status = "Mikrofon açılamadı (izin verilmemiş ya da başka bir uygulama kullanıyor olabilir)."
                                } else {
                                    for (s in 8 downTo 1) {
                                        status = "$label konuşsun… $s sn (doğal şekilde, sürekli konuş)"
                                        delay(1000)
                                    }
                                    val pcm = withContext(Dispatchers.IO) { SpeakerIdController.stopCapture() }
                                    status = "İşleniyor…"
                                    val result = SpeakerIdController.buildEnrollment(pcm)
                                    if (result.fingerprints.isNotEmpty()) {
                                        PeopleStore.addFingerprints(context, p.id, result.fingerprints)
                                        status = "$label için kaydedildi (${result.fingerprints.size} örnek eklendi)."
                                    } else {
                                        status = result.error ?: "Ses kaydedilemedi."
                                    }
                                    refresh()
                                }
                                busy = false
                            }
                        }
                    ) { Text("Sesi Kaydet (8 sn)", color = Color.Black) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { PeopleStore.clearFingerprints(context, p.id); refresh() }) {
                        Text("Sesi Sıfırla", color = JarvisTextSecondary)
                    }
                }

                OutlinedTextField(
                    value = p.personality,
                    onValueChange = { PeopleStore.setPersonality(context, p.id, it); refresh() },
                    label = { Text("Kişilik profili") },
                    placeholder = { Text("Konuşmalardan otomatik çıkarılır; istersen kendin yaz ya da düzelt.") },
                    colors = fieldColors,
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Switch(
                        checked = p.autoPersonality,
                        onCheckedChange = { PeopleStore.setAutoPersonality(context, p.id, it); refresh() },
                        colors = SwitchDefaults.colors(checkedThumbColor = JarvisOrange, checkedTrackColor = JarvisBlue)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Profili konuşmalardan otomatik güncelle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Row {
                    TextButton(onClick = { PeopleStore.clearHistory(context, p.id) }) {
                        Text("Konuşma geçmişini temizle", color = JarvisTextSecondary)
                    }
                    TextButton(onClick = {
                        PeopleStore.deletePerson(context, p.id)
                        if (forcedId == p.id) forcedId = null
                        refresh()
                    }) {
                        Text("Kişiyi sil", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // --- Add a person ---
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Yeni kişinin adı") },
            colors = fieldColors,
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue),
            onClick = {
                if (PeopleStore.addPerson(context, newName) != null) {
                    newName = ""
                    refresh()
                }
            }
        ) { Text("Ekle", color = Color.Black) }
    }

    if (status.isNotBlank()) {
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = JarvisBlue,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    // --- Who is speaking: automatic or fixed ---
    Text(
        text = "Konuşan kişi",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectableButton("Otomatik (ses)", forcedId == null) {
            PeopleStore.setForcedSpeakerId(context, null); forcedId = null
        }
        people.forEach { p ->
            SelectableButton(p.name.ifBlank { "(adsız)" }, forcedId == p.id) {
                PeopleStore.setForcedSpeakerId(context, p.id); forcedId = p.id
            }
        }
    }
    Text(
        text = "\"Otomatik\" sesinden tanır. Bir kişiyi seçersen Jarvis herkesi o kişi sayar (ses tanıma güvenilmez kaldığında işe yarar).",
        style = MaterialTheme.typography.labelSmall,
        color = JarvisTextSecondary,
        modifier = Modifier.padding(top = 4.dp)
    )

    // --- Recognition strictness + test ---
    Text(
        text = "Tanıma sıkılığı",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Esnek", 1 to "Dengeli", 2 to "Sıkı").forEach { (level, label) ->
            SelectableButton(label, sensitivity == level) {
                PeopleStore.setSensitivity(context, level); sensitivity = level
            }
        }
    }
    Button(
        enabled = !busy && people.any { it.fingerprints.isNotEmpty() },
        colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue),
        modifier = Modifier.padding(top = 12.dp),
        onClick = {
            busy = true
            scope.launch {
                if (!SpeakerIdController.startCapture(context)) {
                    status = "Mikrofon açılamadı."
                } else {
                    for (s in 4 downTo 1) { status = "Bir şeyler söyle… $s sn"; delay(1000) }
                    val pcm = withContext(Dispatchers.IO) { SpeakerIdController.stopCapture() }
                    val r = withContext(Dispatchers.Default) { SpeakerIdController.evaluate(context, pcm) }
                    status = if (r == null) {
                        "Yeterli net konuşma algılanamadı (daha yüksek sesle, sürekli konuşmayı dene)."
                    } else {
                        val name = PeopleStore.getPerson(context, r.closestId)?.name ?: "?"
                        val d = "%.1f".format(r.distance)
                        val l = "%.1f".format(r.limit)
                        if (r.acceptedId != null) "Tanınan: $name (uzaklık $d, sınır $l)"
                        else "Eşleşme yok — en yakın: $name (uzaklık $d, sınır $l). Gerekirse \"Esnek\" seç ya da sesi yeniden kaydet."
                    }
                }
                busy = false
            }
        }
    ) { Text("Tanıma Testi (4 sn)", color = Color.Black) }

    Text(
        text = "Not: Ses tanıma basit bir yöntemle çalışır; benzer sesleri karıştırabilir, gürültüde zorlanabilir ve çok kısa komutlarda önceki kişiyi korur. " +
            "Arka plan dinleme açıkken kayıt/test mikrofonu alamayabilir — o durumda dinlemeyi kapatıp tekrar dene.",
        style = MaterialTheme.typography.labelSmall,
        color = JarvisTextSecondary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SelectableButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) JarvisOrange else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            label,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
