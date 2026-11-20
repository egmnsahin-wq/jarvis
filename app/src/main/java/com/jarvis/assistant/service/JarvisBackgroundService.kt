package com.jarvis.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.commands.AlarmController
import com.jarvis.assistant.commands.CommandProcessor
import com.jarvis.assistant.commands.FlashlightController
import com.jarvis.assistant.commands.IncomingMessage
import com.jarvis.assistant.commands.IncomingMessageDialog
import com.jarvis.assistant.commands.IncomingMessageHub
import com.jarvis.assistant.commands.NotesController
import com.jarvis.assistant.commands.PhoneController
import com.jarvis.assistant.commands.ReminderController
import com.jarvis.assistant.commands.SearchController
import com.jarvis.assistant.commands.SystemController
import com.jarvis.assistant.commands.WhatsAppController
import com.jarvis.assistant.data.PreferencesManager
import com.jarvis.assistant.speech.SpeechRecognizerManager
import com.jarvis.assistant.speech.TextToSpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground service that keeps listening in the background for the wake word "jarvis".
 * Flow: WAKE_WORD mode (listening for "jarvis") -> on hearing it, either the rest of the
 * sentence is treated as the command directly, or if just "jarvis" was said alone, we switch
 * to COMMAND mode and listen once more for the actual instruction -> process -> speak reply
 * -> back to WAKE_WORD mode, forever, until the service is stopped.
 *
 * Trade-off (documented for the user): this uses Android's free built-in SpeechRecognizer in
 * a restart loop rather than a dedicated offline wake-word engine, so it needs network access
 * and uses more battery than a true low-power wake-word chip would. It's the best fully-free
 * option available without signing up for a third-party SDK.
 */
class JarvisBackgroundService : Service() {

    private enum class Mode { WAKE_WORD, COMMAND }

    private var mode = Mode.WAKE_WORD
    private lateinit var speechRecognizer: SpeechRecognizerManager
    private lateinit var tts: TextToSpeechManager
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var messageDialog: IncomingMessageDialog
    private val hostCallback: (IncomingMessage) -> Unit = { msg -> onIncomingMessage(msg) }
    private var errorStreak = 0
    private val handler = Handler(Looper.getMainLooper())
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var running = false
    private var lowBatteryWarned = false
    private var batteryReceiver: BroadcastReceiver? = null
    private var wifiReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            // Mikrofon izni yoksa mikrofon türünde ön plan servisi başlatılamaz (Android 11+ SecurityException).
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                stopSelf()
                return START_NOT_STICKY
            }
            running = true
            val wakeWord = PreferencesManager.getAssistantName(this)
            try {
                startForeground(NOTIFICATION_ID, buildNotification("$wakeWord dinliyor... \"$wakeWord\" diyerek çağır"))
            } catch (e: Exception) {
                running = false
                stopSelf()
                return START_NOT_STICKY
            }
            setupEngines()
            IncomingMessageHub.serviceHost = hostCallback
            startListeningWakeWord()
            registerBatteryWatcher()
            registerWifiArrivalWatcher()
        }
        return START_STICKY
    }

    /** Announces once, out loud, the first time battery drops to 15% or below (per charge cycle). */
    private fun registerBatteryWatcher() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val charging = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
                if (level < 0 || scale <= 0) return
                val percent = level * 100 / scale
                if (charging) { lowBatteryWarned = false; return }
                if (percent <= 15 && !lowBatteryWarned) {
                    lowBatteryWarned = true
                    if (::tts.isInitialized) tts.speak("Pilin yüzde $percent, şarja takmayı unutma.")
                }
            }
        }
        batteryReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    /** "Eve varınca X hatırlat" / "evden çıkınca X hatırlat" — ev Wi-Fi'sine bağlanma/bağlantının
     * kesilmesi anını izler. wasOnHomeWifi, art arda gelen aynı-durum bildirimlerinde tekrar tekrar
     * tetiklenmemek için GEÇİŞ anını yakalar. */
    private var wasOnHomeWifi: Boolean? = null

    private fun registerWifiArrivalWatcher() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val homeSsid = PreferencesManager.getHomeWifiSsid(this@JarvisBackgroundService)
                if (homeSsid.isBlank()) return
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager ?: return
                val currentSsid = try { wifiManager.connectionInfo?.ssid?.trim('"') } catch (e: SecurityException) { null }
                val isOnHomeWifi = currentSsid != null && currentSsid.equals(homeSsid, ignoreCase = true)

                val previous = wasOnHomeWifi
                wasOnHomeWifi = isOnHomeWifi
                if (previous == isOnHomeWifi) return // durum değişmedi, bir geçiş yok

                if (isOnHomeWifi) {
                    val pending = PreferencesManager.getArrivalReminders(this@JarvisBackgroundService)
                    if (pending.isNotEmpty() && ::tts.isInitialized) {
                        tts.speak("Eve hoş geldin! " + pending.joinToString(". "))
                        PreferencesManager.clearArrivalReminders(this@JarvisBackgroundService)
                    }
                } else if (previous == true) { // evden az önce çıkıldı (ilk okuma değil, gerçek bir ayrılış)
                    val pending = PreferencesManager.getLeaveReminders(this@JarvisBackgroundService)
                    if (pending.isNotEmpty() && ::tts.isInitialized) {
                        tts.speak("İyi günler! " + pending.joinToString(". "))
                        PreferencesManager.clearLeaveReminders(this@JarvisBackgroundService)
                    }
                }
            }
        }
        wifiReceiver = receiver
        registerReceiver(receiver, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
    }

    private fun setupEngines() {
        val phoneController = PhoneController(this)
        val alarmController = AlarmController(this)
        commandProcessor = CommandProcessor(
            context = this,
            phoneController = phoneController,
            alarmController = alarmController,
            flashlightController = FlashlightController(this),
            systemController = SystemController(this),
            searchController = SearchController(this),
            notesController = NotesController(this),
            reminderController = ReminderController(this),
            whatsAppController = WhatsAppController(this),
            getLastKnownLocation = { getLastKnownLocationSafe() }
        )

        messageDialog = IncomingMessageDialog(this)
        commandProcessor.dialogHandler = { text -> messageDialog.handle(text) }

        tts = TextToSpeechManager(
            this,
            onDone = {
                handler.postDelayed({
                    // WhatsApp sorusu sorulduysa cevabı komut modunda dinle, yoksa uyandırma kelimesine dön.
                    if (::messageDialog.isInitialized && messageDialog.awaitingAnswer) startListeningForCommand()
                    else startListeningWakeWord()
                }, 400)
            }
        )

        speechRecognizer = SpeechRecognizerManager(
            context = this,
            onResult = { text -> errorStreak = 0; handleResult(text) },
            onError = { message ->
                if (::messageDialog.isInitialized && messageDialog.awaitingAnswer) messageDialog.cancel()
                // Sessizlik/anlaşılmadı gibi normal hatalar uyandırma döngüsünün parçası: hemen yeniden dinle.
                // Gerçek hatalarda (servis yok, izin yok, meşgul) aralığı büyüt, çok sürerse servisi durdur.
                val hard = message.contains("bulunamadı") || message.contains("izni") ||
                    message.contains("başlatılamadı") || message.contains("meşgul") || message.contains("kaydı hatası")
                if (hard) errorStreak++ else errorStreak = 0
                if (errorStreak >= 8) {
                    updateNotification(message)
                    stopSelf()
                } else {
                    val delay = if (hard) (500L * errorStreak).coerceAtMost(5000L) else 500L
                    handler.postDelayed({ startListeningWakeWord() }, delay)
                }
            },
            onListeningStateChanged = { },
            // Arka plan dinlemede her lafı değil, sadece komutu (uyandırma kelimesi geçen ya da
            // komut modunda söylenen cümleyi) kimin söylediğini belirle.
            identifyFilter = { text ->
                mode == Mode.COMMAND ||
                    text.lowercase(java.util.Locale("tr")).contains(PreferencesManager.getWakeWord(this))
            }
        )
    }

    private fun getLastKnownLocationSafe(): Pair<Double, Double>? {
        return try {
            val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            for (provider in locationManager.getProviders(true)) {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null) return loc.latitude to loc.longitude
            }
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun startListeningWakeWord() {
        mode = Mode.WAKE_WORD
        val wakeWord = PreferencesManager.getAssistantName(this)
        updateNotification("$wakeWord dinliyor... \"$wakeWord\" diyerek çağır")
        speechRecognizer.startListening()
    }

    private fun startListeningForCommand() {
        mode = Mode.COMMAND
        updateNotification("Dinliyorum, komutunu söyle...")
        speechRecognizer.startListening()
    }

    private fun handleResult(text: String) {
        val lower = text.lowercase(Locale("tr"))
        val wakeWord = PreferencesManager.getWakeWord(this)
        when (mode) {
            Mode.WAKE_WORD -> {
                if (lower.contains(wakeWord)) {
                    val remainder = lower.substringAfter(wakeWord).trim()
                    if (remainder.length > 2) {
                        processCommand(remainder)
                    } else {
                        tts.speak("Buyurun")
                        handler.postDelayed({ startListeningForCommand() }, 900)
                    }
                } else {
                    startListeningWakeWord()
                }
            }
            Mode.COMMAND -> processCommand(if (messageDialog.awaitingAnswer) text else lower)
        }
    }

    /** Gelen WhatsApp mesajı: arka plan servisi açıkken soruyu burada sorarız. */
    private fun onIncomingMessage(msg: IncomingMessage) {
        if (!PreferencesManager.isWhatsAppAssistantEnabled(this) || !::messageDialog.isInitialized) return
        val question = messageDialog.onIncoming(msg) ?: return
        updateNotification(question)
        tts.speak(question) // onDone -> cevap dinleme
    }

    private fun processCommand(text: String) {
        updateNotification("İşleniyor: \"$text\"")
        serviceScope.launch {
            val reply = commandProcessor.process(text)
            updateNotification("Jarvis: $reply")
            tts.speak(reply) // onDone -> back to wake-word listening
        }
    }

    private fun buildNotification(content: String): android.app.Notification {
        val channelId = "jarvis_background"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Jarvis Arka Plan", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisBackgroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(PreferencesManager.getAssistantName(this))
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(content))
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        if (IncomingMessageHub.serviceHost === hostCallback) IncomingMessageHub.serviceHost = null
        serviceJob.cancel()
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        if (::tts.isInitialized) tts.shutdown()
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) {} }
        wifiReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) {} }
    }

    companion object {
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP"
        private const val NOTIFICATION_ID = 42
    }
}
