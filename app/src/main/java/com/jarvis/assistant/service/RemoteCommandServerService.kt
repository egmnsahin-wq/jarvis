package com.jarvis.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.commands.AlarmController
import com.jarvis.assistant.commands.FlashlightController
import com.jarvis.assistant.commands.PhoneController
import com.jarvis.assistant.commands.ReminderController
import com.jarvis.assistant.commands.SearchController
import com.jarvis.assistant.commands.SystemController
import com.jarvis.assistant.commands.WhatsAppController
import com.jarvis.assistant.data.PreferencesManager
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * Runs a tiny local-network HTTP server (NanoHTTPD) so another phone running this same
 * Jarvis app can send this device commands — e.g. "Samsung telefonda X şarkısını aç".
 *
 * Enabled per-device from Settings ("Bu cihazı uzaktan kontrole aç"). No accounts, no
 * internet exposure — only reachable from devices on the same Wi-Fi/LAN, same trust model
 * as the LG TV connection. There's no authentication beyond "must be on the same network",
 * so only enable this on your own trusted home network.
 *
 * Protocol: POST /command with a JSON body {"action": "...", ...params}. See [handleAction]
 * for the full list of supported actions. Responds with {"ok": true/false, "message": "..."}.
 */
class RemoteCommandServerService : Service() {

    private var server: JarvisHttpServer? = null

    private lateinit var phoneController: PhoneController
    private lateinit var alarmController: AlarmController
    private lateinit var reminderController: ReminderController
    private lateinit var whatsAppController: WhatsAppController
    private lateinit var searchController: SearchController
    private lateinit var flashlightController: FlashlightController
    private lateinit var systemController: SystemController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (server == null) {
            phoneController = PhoneController(this)
            alarmController = AlarmController(this)
            reminderController = ReminderController(this)
            whatsAppController = WhatsAppController(this)
            searchController = SearchController(this)
            flashlightController = FlashlightController(this)
            systemController = SystemController(this)

            startForeground(NOTIFICATION_ID, buildNotification())
            server = JarvisHttpServer().also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    private inner class JarvisHttpServer : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response {
            if (session.method != Method.POST || session.uri != "/command") {
                return json(Response.Status.NOT_FOUND, false, "Bilinmeyen adres.")
            }
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
            } catch (e: Exception) {
                return json(Response.Status.BAD_REQUEST, false, "İstek gövdesi okunamadı.")
            }
            val raw = files["postData"] ?: return json(Response.Status.BAD_REQUEST, false, "Boş istek.")
            val body = try { JSONObject(raw) } catch (e: Exception) {
                return json(Response.Status.BAD_REQUEST, false, "Geçersiz JSON.")
            }
            return try {
                val message = handleAction(body)
                json(Response.Status.OK, true, message)
            } catch (e: Exception) {
                json(Response.Status.INTERNAL_ERROR, false, "Komut çalıştırılamadı: ${e.message}")
            }
        }

        private fun json(status: Response.Status, ok: Boolean, message: String): Response {
            val body = JSONObject().put("ok", ok).put("message", message).toString()
            return newFixedLengthResponse(status, "application/json", body)
        }
    }

    /** Executes one remote command locally on this phone and returns a short Turkish result string. */
    private fun handleAction(body: JSONObject): String {
        val action = body.optString("action").uppercase()
        fun str(key: String): String? = body.optString(key).takeIf { it.isNotBlank() }

        return when (action) {
            "PING" -> "pong (${PreferencesManager.getAssistantName(this)})"

            "PLAY_SONG" -> str("query")?.let { kotlinx.coroutines.runBlocking { searchController.playSong(it) } } ?: "Şarkı adı gelmedi."
            "SEARCH_GOOGLE" -> str("query")?.let { searchController.searchGoogle(it) } ?: "Arama metni gelmedi."
            "SEARCH_YOUTUBE" -> str("query")?.let { searchController.searchYoutube(it) } ?: "Arama metni gelmedi."

            "SET_ALARM" -> {
                val hour = body.optInt("hour", -1)
                val minute = body.optInt("minute", 0)
                if (hour !in 0..23) "Saat bilgisi gelmedi."
                else if (alarmController.setAlarm(hour, minute)) "Alarm $hour:${minute.toString().padStart(2, '0')} için kuruldu."
                else "Alarm kurulamadı."
            }

            "REMINDER" -> {
                val minutes = body.optInt("minutes", -1)
                val message = str("message") ?: "Hatırlatıcı"
                if (minutes <= 0) "Süre bilgisi gelmedi."
                else reminderController.scheduleReminder(message, minutes * 60_000L, "$minutes dakika")
            }

            "CALL" -> {
                val name = str("contact") ?: return "Aranacak kişi belirtilmedi."
                val number = phoneController.findContactNumber(name)
                if (number != null) { phoneController.callNumber(number); "$name aranıyor." }
                else "$name adında bir kişi bulunamadı."
            }

            "SMS" -> {
                val name = str("contact") ?: return "Kişi belirtilmedi."
                val message = str("message") ?: return "Mesaj metni belirtilmedi."
                val number = phoneController.findContactNumber(name)
                if (number != null) { phoneController.sendSms(number, message); "$name kişisine mesaj gönderildi." }
                else "$name adında bir kişi bulunamadı."
            }

            "WHATSAPP" -> {
                val name = str("contact") ?: return "Kişi belirtilmedi."
                val message = str("message") ?: return "Mesaj metni belirtilmedi."
                val number = phoneController.findContactNumber(name)
                if (number != null) whatsAppController.sendMessage(number, message)
                else "$name adında bir kişi bulunamadı."
            }

            "OPEN_APP" -> {
                val app = str("app") ?: return "Uygulama adı belirtilmedi."
                if (phoneController.openApp(app)) "$app açılıyor." else "\"$app\" bulunamadı."
            }

            "FLASHLIGHT_ON" -> if (flashlightController.turnOn()) "Fener açıldı." else "Fener açılamadı."
            "FLASHLIGHT_OFF" -> if (flashlightController.turnOff()) "Fener kapatıldı." else "Fener kapatılamadı."

            "BATTERY_STATUS" -> systemController.batteryStatus()

            "FIND_PHONE" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    0
                )
                val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                RingtoneManager.getRingtone(this, uri)?.play()
                "Telefonu çalıyorum."
            }

            "NOTIFY" -> {
                val message = str("message") ?: return "Bildirim metni belirtilmedi."
                showNotification(message)
                "Bildirim gösterildi."
            }

            else -> "Bilinmeyen komut: $action"
        }
    }

    private fun showNotification(message: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(REMOTE_CHANNEL, "Jarvis Uzak Mesaj", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, REMOTE_CHANNEL)
            .setContentTitle(PreferencesManager.getAssistantName(this))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        nm.notify(REMOTE_MESSAGE_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): android.app.Notification {
        val channelId = "jarvis_remote_server"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Jarvis Uzaktan Kontrol", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis uzaktan kontrole açık")
            .setContentText("Bu cihaz port $PORT üzerinden komut kabul ediyor")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val PORT = 8765
        const val ACTION_STOP = "com.jarvis.assistant.REMOTE_SERVER_STOP"
        private const val NOTIFICATION_ID = 43
        private const val REMOTE_CHANNEL = "jarvis_remote_message"
        private const val REMOTE_MESSAGE_NOTIFICATION_ID = 44
    }
}
