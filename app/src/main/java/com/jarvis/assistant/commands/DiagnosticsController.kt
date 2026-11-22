package com.jarvis.assistant.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.ai.AssistantAi
import com.jarvis.assistant.ai.GeminiClient
import com.jarvis.assistant.data.PreferencesManager
import com.jarvis.assistant.service.JarvisAccessibilityService

/** "Neden çalışmıyorsun / durumunu kontrol et": kendi kendini tarar ve sorunları sade bir dille anlatır. */
object DiagnosticsController {

    suspend fun run(context: Context): String {
        val problems = mutableListOf<String>()

        val online = isOnline(context)
        if (!online) problems += "internete bağlı görünmüyorsun"

        if (!AssistantAi.hasAnyKey(context)) {
            problems += "henüz hiç yapay zeka anahtarı girilmemiş, Ayarlar'dan ücretsiz bir Gemini anahtarı eklemen yeterli"
        } else if (online) {
            val key = PreferencesManager.getApiKey(context)
            if (key.isNotBlank()) {
                val r = GeminiClient.test(context, key)
                if (r.startsWith("✗")) problems += "Gemini anahtarında bir sorun var: " + r.removePrefix("✗").trim().trimEnd('.')
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            problems += "mikrofon izni verilmemiş"
        }
        if (!JarvisAccessibilityService.isEnabled()) {
            problems += "Erişilebilirlik izni kapalı, bu yüzden WhatsApp mesajlarını kendiliğinden gönderemem ve ekranı okuyamam"
        }
        if (PreferencesManager.isWhatsAppAssistantEnabled(context) &&
            !NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        ) {
            problems += "WhatsApp asistanı açık ama bildirim erişimi verilmemiş"
        }

        return if (problems.isEmpty()) {
            listOf(
                "Kendimi kontrol ettim, her şey yolunda görünüyor.",
                "Bir bakayım dedim, internet, anahtar ve izinler tamam, sorun göremedim.",
                "Sistem temiz görünüyor, bir aksaklık bulamadım."
            ).random()
        } else {
            val intro = listOf("Kontrol ettim, şunları fark ettim:", "Baktım da şu sorunlar var:", "Şunlar eksik görünüyor:").random()
            "$intro " + problems.joinToString("; ") + "."
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
