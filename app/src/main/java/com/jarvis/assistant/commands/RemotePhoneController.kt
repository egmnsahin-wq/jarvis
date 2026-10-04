package com.jarvis.assistant.commands

import android.content.Context
import com.jarvis.assistant.data.PreferencesManager
import com.jarvis.assistant.service.RemoteCommandServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends remote-control commands to another phone running this same Jarvis app
 * (see [RemoteCommandServerService], which must be enabled on the target device).
 *
 * Up to 3 phones can be registered from Settings, each by a friendly name ("Samsung") and
 * its local IP address. Voice commands like "Samsung telefonda X şarkısını aç" resolve the
 * name to an IP here and POST a small JSON command to it.
 */
object RemotePhoneController {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun playSong(context: Context, phoneName: String, song: String): String =
        send(context, phoneName, JSONObject().put("action", "PLAY_SONG").put("query", song))

    suspend fun searchGoogle(context: Context, phoneName: String, query: String): String =
        send(context, phoneName, JSONObject().put("action", "SEARCH_GOOGLE").put("query", query))

    suspend fun searchYoutube(context: Context, phoneName: String, query: String): String =
        send(context, phoneName, JSONObject().put("action", "SEARCH_YOUTUBE").put("query", query))

    suspend fun setAlarm(context: Context, phoneName: String, hour: Int, minute: Int): String =
        send(context, phoneName, JSONObject().put("action", "SET_ALARM").put("hour", hour).put("minute", minute))

    suspend fun reminder(context: Context, phoneName: String, message: String, minutes: Int): String =
        send(context, phoneName, JSONObject().put("action", "REMINDER").put("message", message).put("minutes", minutes))

    suspend fun call(context: Context, phoneName: String, contact: String): String =
        send(context, phoneName, JSONObject().put("action", "CALL").put("contact", contact))

    suspend fun sms(context: Context, phoneName: String, contact: String, message: String): String =
        send(context, phoneName, JSONObject().put("action", "SMS").put("contact", contact).put("message", message))

    suspend fun whatsapp(context: Context, phoneName: String, contact: String, message: String): String =
        send(context, phoneName, JSONObject().put("action", "WHATSAPP").put("contact", contact).put("message", message))

    suspend fun openApp(context: Context, phoneName: String, app: String): String =
        send(context, phoneName, JSONObject().put("action", "OPEN_APP").put("app", app))

    suspend fun flashlightOn(context: Context, phoneName: String): String =
        send(context, phoneName, JSONObject().put("action", "FLASHLIGHT_ON"))

    suspend fun flashlightOff(context: Context, phoneName: String): String =
        send(context, phoneName, JSONObject().put("action", "FLASHLIGHT_OFF"))

    suspend fun batteryStatus(context: Context, phoneName: String): String =
        send(context, phoneName, JSONObject().put("action", "BATTERY_STATUS"))

    /** Rings the target phone at max volume — handy for "telefonumu bul" style requests. */
    suspend fun findPhone(context: Context, phoneName: String): String =
        send(context, phoneName, JSONObject().put("action", "FIND_PHONE"))

    /** Shows a notification with a custom message on the target phone's screen. */
    suspend fun notify(context: Context, phoneName: String, message: String): String =
        send(context, phoneName, JSONObject().put("action", "NOTIFY").put("message", message))

    // ---------- Core sender ----------

    private suspend fun send(context: Context, phoneName: String, payload: JSONObject): String =
        withContext(Dispatchers.IO) {
            val phones = PreferencesManager.getRemotePhones(context)
            val phone = phones.firstOrNull { it.name.equals(phoneName, ignoreCase = true) }
                ?: phones.firstOrNull {
                    val a = it.name.trim().lowercase()
                    val b = phoneName.trim().lowercase()
                    a.isNotBlank() && (b.contains(a) || a.contains(b))
                }
                ?: return@withContext "\"$phoneName\" adında kayıtlı bir telefon bulamadım. Ayarlar'dan ekleyebilirsin."

            try {
                val request = Request.Builder()
                    .url("http://${phone.ip}:${RemoteCommandServerService.PORT}/command")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val json = try { JSONObject(raw) } catch (e: Exception) { null }
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: if (response.isSuccessful) "${phone.name} telefonuna komut gönderildi."
                        else "${phone.name} telefonu komutu reddetti."
                }
            } catch (e: Exception) {
                "${phone.name} telefonuna ulaşamadım. Aynı Wi-Fi ağında ve Jarvis'te uzaktan kontrol açık olduğundan emin ol."
            }
        }
}
