package com.jarvis.assistant.commands

import android.content.Context
import com.jarvis.assistant.data.PreferencesManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

/**
 * Controls an LG webOS TV over the local network via SSAP (the WebSocket-based protocol
 * LG TVs expose). Mirrors [SmartHomeController]'s shape (singleton object, suspend functions
 * that return a short spoken Turkish confirmation) so [CommandProcessor] can call it the
 * same way it calls the other controllers.
 *
 * ONE-TIME TV SETUP: Settings > General > Devices > "Mobile TV On" (or "LG Connect Apps") -> ON
 * TV IP: set from the app's Settings screen (Ayarlar > Televizyon IP Adresi).
 *
 * The first successful connect() shows an accept popup on the TV; once accepted, the returned
 * client-key is saved via PreferencesManager so every later connection is silent.
 */
object TvController {
    private const val TAG = "TvController"

    private var webSocket: WebSocket? = null
    private var registered = false
    private var connectingIp: String? = null
    private var connectDeferred: CompletableDeferred<Boolean>? = null
    private val pendingCallbacks = ConcurrentHashMap<String, (JSONObject) -> Unit>()

    private val client: OkHttpClient by lazy { buildTrustAllClient() }

    // Common Turkish/English app names -> webOS app ids. Not exhaustive — if an app doesn't
    // open, say "televizyondaki uygulamaları listele" to get the exact id and add it here.
    private val appIdMap = mapOf(
        "youtube" to "youtube.leanback.v4",
        "netflix" to "netflix",
        "prime video" to "amazon",
        "amazon prime" to "amazon",
        "disney+" to "com.disney.disneyplus-prod",
        "disney plus" to "com.disney.disneyplus-prod",
        "spotify" to "spotify-beehive",
        "tarayıcı" to "com.webos.app.browser",
        "browser" to "com.webos.app.browser",
        "internet" to "com.webos.app.browser"
    )

    // ---------- Public commands (mirrors SmartHomeController's suspend-fun-returns-String shape) ----------

    suspend fun openApp(context: Context, appNameRaw: String, contentId: String? = null): String =
        withContext(Dispatchers.IO) {
            if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
            val appId = resolveAppId(appNameRaw)
            val payload = JSONObject().put("id", appId)
            contentId?.let { payload.put("params", JSONObject().put("contentId", it)) }
            val result = request(context, "ssap://system.launcher/launch", payload)
            if (result != null && result.optString("type") != "error") "$appNameRaw televizyonda açılıyor."
            else "$appNameRaw televizyonda açılamadı, TV'nin açık ve aynı ağda olduğundan emin ol."
        }

    /** Opens a URL in the TV's built-in browser — this is how the Jarvis web arayüzü TV'de gösterilir. */
    suspend fun openUrl(context: Context, url: String): String = withContext(Dispatchers.IO) {
        if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
        val result = request(context, "ssap://system.launcher/open", JSONObject().put("target", url))
        if (result != null && result.optString("type") != "error") "Televizyonda açılıyor."
        else "Sayfayı televizyonda açamadım."
    }

    suspend fun powerOff(context: Context): String = withContext(Dispatchers.IO) {
        if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
        request(context, "ssap://system/turnOff")
        disconnectInternal()
        "Televizyon kapatılıyor."
    }

    suspend fun volumeUp(context: Context): String = simpleCommand(context, "ssap://audio/volumeUp", "Televizyon sesi artırıldı.")
    suspend fun volumeDown(context: Context): String = simpleCommand(context, "ssap://audio/volumeDown", "Televizyon sesi azaltıldı.")

    suspend fun setVolume(context: Context, level: Int): String = withContext(Dispatchers.IO) {
        if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
        request(context, "ssap://audio/setVolume", JSONObject().put("volume", level.coerceIn(0, 100)))
        "Televizyon sesi $level olarak ayarlandı."
    }

    suspend fun mute(context: Context): String = withContext(Dispatchers.IO) {
        if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
        request(context, "ssap://audio/setMute", JSONObject().put("mute", true))
        "Televizyon sessize alındı."
    }

    suspend fun unmute(context: Context): String = withContext(Dispatchers.IO) {
        if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
        request(context, "ssap://audio/setMute", JSONObject().put("mute", false))
        "Televizyon sesi açıldı."
    }

    suspend fun switchInput(context: Context, inputId: String): String = withContext(Dispatchers.IO) {
        if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
        request(context, "ssap://tv/switchInput", JSONObject().put("inputId", inputId))
        "Giriş kaynağı $inputId olarak değiştirildi."
    }

    suspend fun showToast(context: Context, message: String): String = withContext(Dispatchers.IO) {
        if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
        request(context, "ssap://system.notifications/createToast", JSONObject().put("message", message))
        "Televizyon ekranında gösterildi."
    }

    /** Lists installed apps as "Ad (id)" lines — useful for fixing appIdMap when an app name doesn't resolve. */
    suspend fun listInstalledApps(context: Context): String = withContext(Dispatchers.IO) {
        if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
        val result = request(context, "ssap://com.webos.applicationManager/listApps") ?: return@withContext "Uygulama listesi alınamadı."
        val apps = result.optJSONObject("payload")?.optJSONArray("apps") ?: JSONArray()
        if (apps.length() == 0) return@withContext "Televizyonda uygulama bulunamadı."
        val lines = (0 until apps.length()).mapNotNull { i ->
            val app = apps.optJSONObject(i) ?: return@mapNotNull null
            "${app.optString("title")} (${app.optString("id")})"
        }
        lines.take(25).joinToString("\n")
    }

    private suspend fun simpleCommand(context: Context, uri: String, confirmation: String): String =
        withContext(Dispatchers.IO) {
            if (!ensureConnected(context)) return@withContext notConnectedMessage(context)
            request(context, uri)
            confirmation
        }

    private fun notConnectedMessage(context: Context): String {
        val ip = PreferencesManager.getTvIp(context)
        return if (ip.isBlank())
            "Televizyonun IP adresi ayarlanmamış. Ayarlar'dan Televizyon IP Adresi'ni girer misin?"
        else
            "Televizyona bağlanamadım. TV'nin açık ve aynı Wi-Fi ağında olduğundan emin ol."
    }

    private fun resolveAppId(appNameRaw: String): String {
        val key = appNameRaw.trim().lowercase(Locale("tr"))
        return appIdMap[key] ?: appNameRaw.trim()
    }

    // ---------- Connection handling ----------

    private suspend fun ensureConnected(context: Context): Boolean {
        val ip = PreferencesManager.getTvIp(context)
        if (ip.isBlank()) return false

        if (registered && webSocket != null && connectingIp == ip) return true

        val inFlight = connectDeferred
        if (inFlight != null && !inFlight.isCompleted && connectingIp == ip) {
            return withTimeoutOrNull(12_000) { inFlight.await() } ?: false
        }

        val deferred = CompletableDeferred<Boolean>()
        connectDeferred = deferred
        connectingIp = ip
        connect(context, ip)
        return withTimeoutOrNull(12_000) { deferred.await() } ?: false
    }

    private fun connect(context: Context, ip: String) {
        val request = Request.Builder().url("wss://$ip:3001").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                sendRegisterHandshake(context, ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(context, text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                registered = false
                connectDeferred?.takeIf { !it.isCompleted }?.complete(false)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                registered = false
            }
        })
    }

    private fun disconnectInternal() {
        webSocket?.close(1000, "power off")
        webSocket = null
        registered = false
    }

    private fun sendRegisterHandshake(context: Context, ws: WebSocket) {
        val savedKey = PreferencesManager.getTvClientKey(context)
        val payload = JSONObject().apply {
            put("forcePairing", false)
            put("pairingType", "PROMPT")
            if (!savedKey.isNullOrBlank()) put("client-key", savedKey)
            put("manifest", buildManifest())
        }
        val envelope = JSONObject().apply {
            put("type", "register")
            put("id", "register_0")
            put("payload", payload)
        }
        ws.send(envelope.toString())
    }

    private fun handleIncomingMessage(context: Context, text: String) {
        val json = try { JSONObject(text) } catch (e: Exception) { return }

        when (json.optString("type")) {
            "registered" -> {
                registered = true
                val key = json.optJSONObject("payload")?.optString("client-key")
                if (!key.isNullOrEmpty()) PreferencesManager.setTvClientKey(context, key)
                connectDeferred?.takeIf { !it.isCompleted }?.complete(true)
            }
            "response", "error" -> {
                val id = json.optString("id")
                pendingCallbacks.remove(id)?.invoke(json)
            }
        }
    }

    /** Sends an SSAP request and suspends until the TV replies (or times out after 6s). */
    private suspend fun request(context: Context, uri: String, payload: JSONObject = JSONObject()): JSONObject? =
        withTimeoutOrNull(6_000) {
            suspendCancellableCoroutine { cont ->
                val ws = webSocket
                if (ws == null) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                val id = "req_${UUID.randomUUID()}"
                pendingCallbacks[id] = { result -> if (cont.isActive) cont.resume(result) }
                val envelope = JSONObject().apply {
                    put("type", "request")
                    put("id", id)
                    put("uri", uri)
                    put("payload", payload)
                }
                ws.send(envelope.toString())
                cont.invokeOnCancellation { pendingCallbacks.remove(id) }
            }
        }

    private fun buildManifest(): JSONObject {
        val permissions = JSONArray(
            listOf(
                "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO",
                "CONTROL_DISPLAY", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV",
                "CONTROL_POWER", "READ_APP_STATUS", "READ_CURRENT_CHANNEL",
                "READ_RUNNING_APPS", "WRITE_NOTIFICATION_TOAST", "CONTROL_TV_SCREEN",
                "CONTROL_TV_STANBY", "READ_INSTALLED_APPS", "CLOSE"
            )
        )
        return JSONObject().apply {
            put("manifestVersion", 1)
            put("appVersion", "1.0")
            put("signed", JSONObject().apply {
                put("created", "20240101")
                put("appId", "com.jarvis.assistant")
                put("vendorId", "com.jarvis")
                put("localizedAppNames", JSONObject().apply { put("", "Jarvis") })
                put("permissions", permissions)
                put("serial", UUID.randomUUID().toString())
            })
            put("permissions", permissions)
        }
    }

    // ---------- TLS (LG TV uses a self-signed cert on the local network) ----------

    private fun buildTrustAllClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL").apply { init(null, trustAllCerts, SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}
