package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import android.content.Intent
import android.os.Build
import com.jarvis.assistant.service.RemoteCommandServerService
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.material.icons.filled.Close
import com.jarvis.assistant.data.Routine
import com.jarvis.assistant.data.RoutineAction
import com.jarvis.assistant.data.PreferencesManager
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisOrange
import com.jarvis.assistant.ui.theme.JarvisTextSecondary

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var apiKey by remember { mutableStateOf(PreferencesManager.getApiKey(context)) }
    var groqApiKey by remember { mutableStateOf(PreferencesManager.getGroqApiKey(context)) }
    var youtubeApiKey by remember { mutableStateOf(PreferencesManager.getYoutubeApiKey(context)) }
    var spotifyClientId by remember { mutableStateOf(PreferencesManager.getSpotifyClientId(context)) }
    var spotifyClientSecret by remember { mutableStateOf(PreferencesManager.getSpotifyClientSecret(context)) }
    var aiProvider by remember { mutableStateOf(PreferencesManager.getAiProvider(context)) }
    var assistantName by remember { mutableStateOf(PreferencesManager.getAssistantName(context)) }
    var personality by remember { mutableStateOf(PreferencesManager.getPersonality(context)) }
    var tvIp by remember { mutableStateOf(PreferencesManager.getTvIp(context)) }
    var pcIp by remember { mutableStateOf(PreferencesManager.getPcIp(context)) }
    var phone1Name by remember { mutableStateOf(PreferencesManager.getRemotePhoneSlot(context, 1).name) }
    var phone1Ip by remember { mutableStateOf(PreferencesManager.getRemotePhoneSlot(context, 1).ip) }
    var phone2Name by remember { mutableStateOf(PreferencesManager.getRemotePhoneSlot(context, 2).name) }
    var phone2Ip by remember { mutableStateOf(PreferencesManager.getRemotePhoneSlot(context, 2).ip) }
    var phone3Name by remember { mutableStateOf(PreferencesManager.getRemotePhoneSlot(context, 3).name) }
    var phone3Ip by remember { mutableStateOf(PreferencesManager.getRemotePhoneSlot(context, 3).ip) }
    var remoteServerEnabled by remember { mutableStateOf(PreferencesManager.isRemoteServerEnabled(context)) }
    val routines = remember { PreferencesManager.getRoutines(context).toMutableStateList() }
    var newRoutineName by remember { mutableStateOf("") }
    var newRoutineTrigger by remember { mutableStateOf("") }
    var newActionType by remember { mutableStateOf("PLAY_SONG") }
    var newActionService by remember { mutableStateOf("spotify") }
    var newActionValue by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = JarvisBlue,
        unfocusedBorderColor = JarvisTextSecondary,
        focusedLabelColor = JarvisBlue,
        cursorColor = JarvisBlue
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Geri", tint = JarvisBlue)
            }
            Text(
                text = "Ayarlar",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                color = JarvisBlue
            )
        }

        Text(
            text = "Asistanın adı",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = assistantName,
            onValueChange = { assistantName = it },
            placeholder = { Text("Jarvis") },
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text(
            text = "Örneğin \"Tom\" yazarsan, arka planda \"Tom\" diyerek çağırman yeterli olur.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Kişiliği",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = personality,
            onValueChange = { personality = it },
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = { Text("Nasıl konuşsun, nasıl bir tavrı olsun...") }
        )

        Text(
            text = "Gemini API Anahtarı",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            placeholder = { Text("AIza...") }
        )
        Text(
            text = "aistudio.google.com/app/apikey adresinden ücretsiz alabilirsin.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Groq API Anahtarı",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = groqApiKey,
            onValueChange = { groqApiKey = it },
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            placeholder = { Text("gsk_...") }
        )
        Text(
            text = "console.groq.com/keys adresinden ücretsiz alabilirsin. Normal sohbet için önerilen ana yapay zeka.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Text(
            text = "Sohbet için öncelikli yapay zeka",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row {
            Button(
                onClick = { aiProvider = "gemini" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (aiProvider == "gemini") JarvisOrange else MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text("Gemini", color = if (aiProvider == "gemini") androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { aiProvider = "groq" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (aiProvider == "groq") JarvisOrange else MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text("Groq", color = if (aiProvider == "groq") androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            text = "Normal sorular ve komutlar için önce bu kullanılır. Ekran/dosya analizinde her zaman önce Gemini çalışır. Biri hata verir ya da limite takılırsa (diğerinin anahtarı varsa) otomatik diğerine geçilir.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Şarkı Çalma (opsiyonel)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
        )
        Text(
            text = "Bunları girmezsen \"bir şarkı aç\" dediğinde sadece ilgili uygulamada arama ekranı açılır, otomatik çalmaz.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = spotifyClientId,
            onValueChange = { spotifyClientId = it },
            colors = fieldColors,
            singleLine = true,
            placeholder = { Text("Spotify Client ID") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = spotifyClientSecret,
            onValueChange = { spotifyClientSecret = it },
            colors = fieldColors,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            placeholder = { Text("Spotify Client Secret") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
        Text(
            text = "developer.spotify.com/dashboard adresinden ücretsiz bir uygulama oluşturup alabilirsin.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        OutlinedTextField(
            value = youtubeApiKey,
            onValueChange = { youtubeApiKey = it },
            colors = fieldColors,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            placeholder = { Text("YouTube Data API Anahtarı") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "console.cloud.google.com adresinden \"YouTube Data API v3\" için ücretsiz anahtar alabilirsin.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Televizyon IP Adresi",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = tvIp,
            onValueChange = { tvIp = it },
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("192.168.1.50") }
        )
        Text(
            text = "TV'nin Ayarlar > Ağ menüsünden görebilirsin. İlk bağlantıda TV'de bir onay ekranı çıkacak.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Bilgisayar IP Adresi",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = pcIp,
            onValueChange = { pcIp = it },
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("192.168.1.60") }
        )
        Text(
            text = "Bilgisayarında jarvis_pc_agent.py çalışıyor olmalı. IP'yi cmd'de \"ipconfig\" yazarak görebilirsin.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Diğer Telefonlar (en fazla 3)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)
        )
        Text(
            text = "Her telefonda da bu uygulama kurulu olmalı ve Ayarlar'dan \"Bu cihazı uzaktan kontrole aç\" açılmalı. \"Samsung telefonda X aç\" gibi komutlarda bu isimleri kullanacaksın.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        RemotePhoneRow(name = phone1Name, ip = phone1Ip, onNameChange = { phone1Name = it }, onIpChange = { phone1Ip = it }, fieldColors = fieldColors, label = "Telefon 1")
        RemotePhoneRow(name = phone2Name, ip = phone2Ip, onNameChange = { phone2Name = it }, onIpChange = { phone2Ip = it }, fieldColors = fieldColors, label = "Telefon 2")
        RemotePhoneRow(name = phone3Name, ip = phone3Ip, onNameChange = { phone3Name = it }, onIpChange = { phone3Ip = it }, fieldColors = fieldColors, label = "Telefon 3")

        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Switch(
                checked = remoteServerEnabled,
                onCheckedChange = { enabled ->
                    remoteServerEnabled = enabled
                    PreferencesManager.setRemoteServerEnabled(context, enabled)
                    val intent = Intent(context, RemoteCommandServerService::class.java)
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                        else context.startService(intent)
                    } else {
                        intent.action = RemoteCommandServerService.ACTION_STOP
                        context.startService(intent)
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = JarvisOrange, checkedTrackColor = JarvisBlue)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Bu cihazı uzaktan kontrole aç",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = "Açarsan, yukarıdaki gibi bu cihazı BAŞKA bir Jarvis telefonundan kaydedip komut gönderebilirler (aynı Wi-Fi ağında).",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Ekran Anlama",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)
        )
        Text(
            text = "\"Ekranımda ne var\" gibi sorulara cevap verebilmesi için bir kerelik Erişilebilirlik izni gerekiyor (Android 11+). Sadece ekran görüntüsü alır, başka hiçbir şeye dokunmaz.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue)
        ) {
            Text("Erişilebilirlik Ayarlarını Aç", color = androidx.compose.ui.graphics.Color.Black)
        }

        Text(
            text = "Özel Senaryolar",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)
        )
        Text(
            text = "Bir tetikleyici cümle söylediğinde birden fazla şeyi birden yapsın — örn. \"çalışma ortamını ayarla\" dediğinde bir şarkı çalsın ve bir uygulama açsın.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        routines.forEachIndexed { index, routine ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "\"${routine.trigger}\" -> ${routine.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = routine.actions.joinToString(" + ") {
                            when (it.type) {
                                "PLAY_SONG" -> "${if (it.service == "youtube") "YouTube" else "Spotify"}: ${it.value}"
                                "PC_OPEN_APP" -> "PC'de Aç: ${it.value}"
                                else -> "Aç: ${it.value}"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextSecondary
                    )
                }
                IconButton(onClick = {
                    routines.removeAt(index)
                    PreferencesManager.setRoutines(context, routines.toList())
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Sil", tint = JarvisTextSecondary)
                }
            }
        }

        OutlinedTextField(
            value = newRoutineTrigger,
            onValueChange = { newRoutineTrigger = it },
            colors = fieldColors,
            singleLine = true,
            placeholder = { Text("Tetikleyici cümle, örn. çalışma ortamını ayarla") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = newRoutineName,
            onValueChange = { newRoutineName = it },
            colors = fieldColors,
            singleLine = true,
            placeholder = { Text("Senaryo adı, örn. Çalışma Modu") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Text(
            text = "Eylem",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        Row {
            Button(
                onClick = { newActionType = "PLAY_SONG" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (newActionType == "PLAY_SONG") JarvisOrange else MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text("Şarkı Çal", color = if (newActionType == "PLAY_SONG") androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { newActionType = "OPEN_APP" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (newActionType == "OPEN_APP") JarvisOrange else MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text("Uygulama Aç", color = if (newActionType == "OPEN_APP") androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { newActionType = "PC_OPEN_APP" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (newActionType == "PC_OPEN_APP") JarvisOrange else MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text("PC'de Aç", color = if (newActionType == "PC_OPEN_APP") androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        if (newActionType == "PLAY_SONG") {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { newActionService = "spotify" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (newActionService == "spotify") JarvisBlue else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Spotify", color = if (newActionService == "spotify") androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { newActionService = "youtube" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (newActionService == "youtube") JarvisBlue else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("YouTube", color = if (newActionService == "youtube") androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        OutlinedTextField(
            value = newActionValue,
            onValueChange = { newActionValue = it },
            colors = fieldColors,
            singleLine = true,
            placeholder = { Text(if (newActionType == "PLAY_SONG") "Şarkı/sanatçı adı" else "Uygulama adı") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Button(
            onClick = {
                if (newRoutineTrigger.isNotBlank() && newRoutineName.isNotBlank() && newActionValue.isNotBlank()) {
                    val action = RoutineAction(
                        type = newActionType,
                        value = newActionValue.trim(),
                        service = if (newActionType == "PLAY_SONG") newActionService else ""
                    )
                    routines.add(Routine(name = newRoutineName.trim(), trigger = newRoutineTrigger.trim(), actions = listOf(action)))
                    PreferencesManager.setRoutines(context, routines.toList())
                    newRoutineName = ""; newRoutineTrigger = ""; newActionValue = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Senaryo Ekle", color = androidx.compose.ui.graphics.Color.Black)
        }
        Text(
            text = "Birden fazla eylem eklemek istersen, aynı tetikleyici cümleyle ikinci bir senaryo daha eklemen yeterli — ikisi de aynı anda çalışır.",
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Button(
            onClick = {
                PreferencesManager.setAssistantName(context, assistantName.ifBlank { PreferencesManager.DEFAULT_NAME })
                PreferencesManager.setPersonality(context, personality.ifBlank { PreferencesManager.DEFAULT_PERSONALITY })
                PreferencesManager.setApiKey(context, apiKey)
                PreferencesManager.setGroqApiKey(context, groqApiKey)
                PreferencesManager.setAiProvider(context, aiProvider)
                PreferencesManager.setYoutubeApiKey(context, youtubeApiKey)
                PreferencesManager.setSpotifyClientId(context, spotifyClientId)
                PreferencesManager.setSpotifyClientSecret(context, spotifyClientSecret)
                PreferencesManager.setTvIp(context, tvIp)
                PreferencesManager.setPcIp(context, pcIp)
                PreferencesManager.setRemotePhoneSlot(context, 1, phone1Name, phone1Ip)
                PreferencesManager.setRemotePhoneSlot(context, 2, phone2Name, phone2Ip)
                PreferencesManager.setRemotePhoneSlot(context, 3, phone3Name, phone3Ip)
                saved = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = JarvisOrange),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Text("Kaydet", color = androidx.compose.ui.graphics.Color.Black)
        }

        if (saved) {
            Text(
                text = "Kaydedildi.",
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisBlue,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun RemotePhoneRow(
    name: String,
    ip: String,
    onNameChange: (String) -> Unit,
    onIpChange: (String) -> Unit,
    fieldColors: androidx.compose.material3.TextFieldColors,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            colors = fieldColors,
            singleLine = true,
            placeholder = { Text(label) },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = ip,
            onValueChange = onIpChange,
            colors = fieldColors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("192.168.1.xx") },
            modifier = Modifier.weight(1f)
        )
    }
}
