package com.jarvis.assistant.commands

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings

/**
 * Controls device-level settings: volume, screen brightness, battery info, silent mode.
 *
 * Brightness and Do-Not-Disturb (silent mode) both require *special* permissions that
 * can't be granted through the normal runtime permission dialog — the user has to flip
 * a switch in system Settings once. `canControlBrightness()` / `canControlSilentMode()`
 * check this, and `requestBrightnessPermission()` / `requestSilentModePermission()` open
 * the right settings screen for the user to enable it.
 */
class SystemController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // --- Volume (no special permission needed) ---
    fun volumeUp(): String {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        return "Ses seviyesi artırıldı."
    }

    fun volumeDown(): String {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        return "Ses seviyesi azaltıldı."
    }

    // --- Silent / normal mode (needs Notification Policy Access) ---
    fun canControlSilentMode(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun requestSilentModePermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun setSilent(silent: Boolean): String {
        if (!canControlSilentMode()) {
            requestSilentModePermission()
            return "Sessiz modu değiştirmek için bir kerelik izin ekranı açıyorum, izni verdikten sonra tekrar söyle."
        }
        audioManager.ringerMode = if (silent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        return if (silent) "Sessiz moda geçildi." else "Normal moda dönüldü."
    }

    // --- Brightness (needs WRITE_SETTINGS) ---
    fun canControlBrightness(): Boolean = Settings.System.canWrite(context)

    fun requestBrightnessPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun adjustBrightness(increase: Boolean): String {
        if (!canControlBrightness()) {
            requestBrightnessPermission()
            return "Parlaklığı değiştirmek için bir kerelik izin ekranı açıyorum, izni verdikten sonra tekrar söyle."
        }
        val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 125)
        val step = 40
        val newValue = (if (increase) current + step else current - step).coerceIn(10, 255)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newValue)
        return if (increase) "Ekran parlaklığı artırıldı." else "Ekran parlaklığı azaltıldı."
    }

    /** Sets brightness to an exact 0-100 percentage. */
    fun setBrightnessPercent(percent: Int): String {
        if (!canControlBrightness()) {
            requestBrightnessPermission()
            return "Parlaklığı değiştirmek için bir kerelik izin ekranı açıyorum, izni verdikten sonra tekrar söyle."
        }
        val clamped = percent.coerceIn(0, 100)
        val value = (clamped * 255 / 100).coerceIn(1, 255)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        return "Ekran parlaklığı yüzde $clamped olarak ayarlandı."
    }

    // --- Wi-Fi / Bluetooth ---
    // Modern Android (10+) no longer lets normal apps silently flip Wi-Fi/Bluetooth on or off —
    // only the user (or the Settings app) can. The most honest thing Jarvis can do is jump
    // straight to the right quick-settings panel so it's one tap instead of three.
    fun openWifiSettings(): String {
        val intent = Intent(Settings.Panel.ACTION_WIFI).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        return try {
            context.startActivity(intent)
            "Wi-Fi ayarlarını açtım, açıp kapatmak için dokunman yeterli."
        } catch (e: Exception) {
            "Wi-Fi ayarlarını açamadım."
        }
    }

    fun openBluetoothSettings(): String {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        return try {
            context.startActivity(intent)
            "Bluetooth ayarlarını açtım, açıp kapatmak için dokunman yeterli."
        } catch (e: Exception) {
            "Bluetooth ayarlarını açamadım."
        }
    }

    // --- Find this phone (rings at max volume, even if on silent) ---
    fun ringToFind(): String {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            0
        )
        val uri = android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE)
        android.media.RingtoneManager.getRingtone(context, uri)?.play()
        return "Buradayım, çalıyorum!"
    }

    // --- Battery (no permission needed) ---
    fun batteryStatus(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return "Pil yüzde $level" + if (charging) ", şu anda şarj oluyor." else "."
    }
}
