package com.jarvis.assistant.commands

import android.content.Context
import com.jarvis.assistant.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends commands to the Windows PC running jarvis_pc_agent.py (see that file for setup).
 * Same trust model as [TvController] and [RemotePhoneController]: local network only, no auth.
 */
object PcController {

    private const val PORT = 8766

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    fun isConfigured(context: Context): Boolean = PreferencesManager.getPcIp(context).isNotBlank()

    suspend fun openApp(context: Context, app: String): String =
        send(context, JSONObject().put("action", "OPEN_APP").put("app", app))

    suspend fun openUrl(context: Context, url: String): String =
        send(context, JSONObject().put("action", "OPEN_URL").put("url", url))

    suspend fun shutdown(context: Context): String = send(context, JSONObject().put("action", "SHUTDOWN"))
    suspend fun restart(context: Context): String = send(context, JSONObject().put("action", "RESTART"))
    suspend fun sleep(context: Context): String = send(context, JSONObject().put("action", "SLEEP"))
    suspend fun lock(context: Context): String = send(context, JSONObject().put("action", "LOCK"))

    suspend fun volumeUp(context: Context): String = send(context, JSONObject().put("action", "VOLUME_UP"))
    suspend fun volumeDown(context: Context): String = send(context, JSONObject().put("action", "VOLUME_DOWN"))
    suspend fun mute(context: Context): String = send(context, JSONObject().put("action", "MUTE"))

    suspend fun mediaPlayPause(context: Context): String = send(context, JSONObject().put("action", "MEDIA_PLAY_PAUSE"))
    suspend fun mediaNext(context: Context): String = send(context, JSONObject().put("action", "MEDIA_NEXT"))
    suspend fun mediaPrev(context: Context): String = send(context, JSONObject().put("action", "MEDIA_PREV"))

    private suspend fun send(context: Context, payload: JSONObject): String = withContext(Dispatchers.IO) {
        val ip = PreferencesManager.getPcIp(context)
        if (ip.isBlank()) return@withContext "Bilgisayarın IP adresi ayarlanmamış. Ayarlar'dan girebilirsin."

        try {
            val request = Request.Builder()
                .url("http://$ip:$PORT/command")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = try { JSONObject(raw) } catch (e: Exception) { null }
                json?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: if (response.isSuccessful) "Bilgisayara komut gönderildi." else "Bilgisayar komutu reddetti."
            }
        } catch (e: Exception) {
            "Bilgisayara ulaşamadım. Aynı Wi-Fi ağında ve pc_agent.py çalışır durumda olduğundan emin ol."
        }
    }
}
