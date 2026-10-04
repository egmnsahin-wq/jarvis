package com.jarvis.assistant.data

import android.content.Context
import com.jarvis.assistant.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * All user-configurable settings (API key, assistant name/persona) live here,
 * stored locally on-device with SharedPreferences — nothing hardcoded, nothing
 * sent anywhere except directly to Gemini when you talk to it.
 */
object PreferencesManager {
    private const val PREFS = "jarvis_settings"
    private const val KEY_API = "gemini_api_key"
    private const val KEY_NAME = "assistant_name"
    private const val KEY_PERSONALITY = "assistant_personality"
    private const val KEY_TV_IP = "lg_tv_ip"
    private const val KEY_TV_CLIENT_KEY = "lg_tv_client_key"
    private const val KEY_PC_IP = "pc_ip"
    private const val KEY_REMOTE_SERVER_ENABLED = "remote_server_enabled"
    private const val REMOTE_PHONE_SLOTS = 3
    private const val KEY_AI_PROVIDER = "ai_provider"
    private const val KEY_GROQ_API_KEY = "groq_api_key"
    private const val KEY_YOUTUBE_API_KEY = "youtube_api_key"
    private const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
    private const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"
    private const val KEY_MEMORY_FACTS = "memory_facts"
    private const val MEMORY_SEPARATOR = "\u0001"
    private const val KEY_ROUTINES = "routines_json"
    private const val KEY_CONTACT_ALIASES = "contact_aliases_json"
    private const val KEY_QUICK_REPLIES = "quick_replies_json"
    private const val KEY_TODOS = "todos_json"
    private const val KEY_HOME_WIFI_SSID = "home_wifi_ssid"

    const val DEFAULT_NAME = "Jarvis"
    const val DEFAULT_PERSONALITY =
        "Kibar, kısa ve öz, hafif esprili ama profesyonel bir sesli asistansın. Iron Man'deki Jarvis'e benzer bir tavrın var."

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String {
        val stored = prefs(context).getString(KEY_API, null)
        return if (!stored.isNullOrBlank()) stored else BuildConfig.GEMINI_API_KEY
    }

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API, key.trim()).apply()
    }

    fun getAssistantName(context: Context): String =
        prefs(context).getString(KEY_NAME, DEFAULT_NAME)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME

    fun setAssistantName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_NAME, name.trim()).apply()
    }

    fun getPersonality(context: Context): String =
        prefs(context).getString(KEY_PERSONALITY, DEFAULT_PERSONALITY) ?: DEFAULT_PERSONALITY

    fun setPersonality(context: Context, personality: String) {
        prefs(context).edit().putString(KEY_PERSONALITY, personality.trim()).apply()
    }

    /** Wake word for the background listening service — always the assistant's current name, lowercased. */
    fun getWakeWord(context: Context): String = getAssistantName(context).lowercase(Locale("tr"))

    // --- LG webOS TV ---
    fun getTvIp(context: Context): String = prefs(context).getString(KEY_TV_IP, "") ?: ""

    fun setTvIp(context: Context, ip: String) {
        prefs(context).edit().putString(KEY_TV_IP, ip.trim()).apply()
    }

    /** Saved pairing key from the TV — once set, reconnecting won't show the on-screen accept popup again. */
    fun getTvClientKey(context: Context): String? = prefs(context).getString(KEY_TV_CLIENT_KEY, null)

    fun setTvClientKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_TV_CLIENT_KEY, key).apply()
    }

    // --- Windows PC agent ---
    fun getPcIp(context: Context): String = prefs(context).getString(KEY_PC_IP, "") ?: ""
    fun setPcIp(context: Context, ip: String) {
        prefs(context).edit().putString(KEY_PC_IP, ip.trim()).apply()
    }

    // --- Remote phone control (up to 3 other phones running this same app) ---
    data class RemotePhone(val name: String, val ip: String)

    /** Up to 3 registered (name, ip) pairs, in slot order; blank slots are skipped. */
    fun getRemotePhones(context: Context): List<RemotePhone> {
        val p = prefs(context)
        return (1..REMOTE_PHONE_SLOTS).mapNotNull { slot ->
            val name = p.getString("remote_phone_${slot}_name", "") ?: ""
            val ip = p.getString("remote_phone_${slot}_ip", "") ?: ""
            if (name.isNotBlank() && ip.isNotBlank()) RemotePhone(name.trim(), ip.trim()) else null
        }
    }

    fun getRemotePhoneSlot(context: Context, slot: Int): RemotePhone {
        val p = prefs(context)
        return RemotePhone(
            p.getString("remote_phone_${slot}_name", "") ?: "",
            p.getString("remote_phone_${slot}_ip", "") ?: ""
        )
    }

    fun setRemotePhoneSlot(context: Context, slot: Int, name: String, ip: String) {
        prefs(context).edit()
            .putString("remote_phone_${slot}_name", name.trim())
            .putString("remote_phone_${slot}_ip", ip.trim())
            .apply()
    }

    /** Whether this device should run the local HTTP server that lets OTHER Jarvis phones control it. */
    fun isRemoteServerEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_REMOTE_SERVER_ENABLED, false)

    fun setRemoteServerEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMOTE_SERVER_ENABLED, enabled).apply()
    }

    // --- AI provider ("gemini" or "groq") ---
    fun getAiProvider(context: Context): String = prefs(context).getString(KEY_AI_PROVIDER, "groq") ?: "groq"

    fun setAiProvider(context: Context, provider: String) {
        prefs(context).edit().putString(KEY_AI_PROVIDER, provider).apply()
    }

    fun getGroqApiKey(context: Context): String = prefs(context).getString(KEY_GROQ_API_KEY, "") ?: ""

    fun setGroqApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_GROQ_API_KEY, key.trim()).apply()
    }

    // --- Song-playback credentials (optional — without these, playSong falls back to just
    // opening a search screen instead of actually starting a specific track) ---
    fun getYoutubeApiKey(context: Context): String = prefs(context).getString(KEY_YOUTUBE_API_KEY, "") ?: ""
    fun setYoutubeApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_YOUTUBE_API_KEY, key.trim()).apply()
    }

    fun getSpotifyClientId(context: Context): String = prefs(context).getString(KEY_SPOTIFY_CLIENT_ID, "") ?: ""
    fun setSpotifyClientId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_SPOTIFY_CLIENT_ID, id.trim()).apply()
    }

    fun getSpotifyClientSecret(context: Context): String = prefs(context).getString(KEY_SPOTIFY_CLIENT_SECRET, "") ?: ""
    fun setSpotifyClientSecret(context: Context, secret: String) {
        prefs(context).edit().putString(KEY_SPOTIFY_CLIENT_SECRET, secret.trim()).apply()
    }

    // --- Long-term memory: facts the user explicitly asked Jarvis to remember, persisted
    // across app restarts (unlike the in-memory conversation history AssistantAi keeps). ---
    fun getMemories(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_MEMORY_FACTS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(MEMORY_SEPARATOR).filter { it.isNotBlank() }
    }

    fun addMemory(context: Context, fact: String) {
        val trimmed = fact.trim()
        if (trimmed.isBlank()) return
        val updated = (getMemories(context) + trimmed).takeLast(50) // keep it bounded
        prefs(context).edit().putString(KEY_MEMORY_FACTS, updated.joinToString(MEMORY_SEPARATOR)).apply()
    }

    fun clearMemories(context: Context) {
        prefs(context).edit().remove(KEY_MEMORY_FACTS).apply()
    }

    // --- Routines / scenes ("çalışma ortamını ayarla" -> play a song + open apps) ---
    fun getRoutines(context: Context): List<Routine> {
        val raw = prefs(context).getString(KEY_ROUTINES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val actions = o.getJSONArray("actions")
                val actionList = (0 until actions.length()).map { j ->
                    val a = actions.getJSONObject(j)
                    RoutineAction(
                        type = a.optString("type"),
                        value = a.optString("value"),
                        service = a.optString("service", "")
                    )
                }
                Routine(name = o.optString("name"), trigger = o.optString("trigger"), actions = actionList)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setRoutines(context: Context, routines: List<Routine>) {
        val arr = JSONArray()
        routines.forEach { r ->
            val actions = JSONArray()
            r.actions.forEach { a ->
                actions.put(JSONObject().put("type", a.type).put("value", a.value).put("service", a.service))
            }
            arr.put(JSONObject().put("name", r.name).put("trigger", r.trigger).put("actions", actions))
        }
        prefs(context).edit().putString(KEY_ROUTINES, arr.toString()).apply()
    }

    // --- Contact aliases: "karım" -> "Ayşe Yılmaz" (resolved before contact lookup) ---
    fun getContactAliases(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_CONTACT_ALIASES, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.optString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setContactAlias(context: Context, alias: String, realName: String) {
        val current = getContactAliases(context).toMutableMap()
        current[alias.trim().lowercase(Locale("tr"))] = realName.trim()
        prefs(context).edit().putString(KEY_CONTACT_ALIASES, JSONObject(current as Map<String, Any?>).toString()).apply()
    }

    // --- Quick-reply templates: a short name -> the message text it sends ---
    fun getQuickReplies(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_QUICK_REPLIES, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.optString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setQuickReply(context: Context, name: String, message: String) {
        val current = getQuickReplies(context).toMutableMap()
        current[name.trim().lowercase(Locale("tr"))] = message.trim()
        prefs(context).edit().putString(KEY_QUICK_REPLIES, JSONObject(current as Map<String, Any?>).toString()).apply()
    }

    // --- Todo list (separate from throwaway Notes — has a done/not-done state) ---
    data class TodoItem(val text: String, val done: Boolean)

    fun getTodos(context: Context): List<TodoItem> {
        val raw = prefs(context).getString(KEY_TODOS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                TodoItem(o.optString("text"), o.optBoolean("done", false))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveTodos(context: Context, todos: List<TodoItem>) {
        val arr = JSONArray()
        todos.forEach { arr.put(JSONObject().put("text", it.text).put("done", it.done)) }
        prefs(context).edit().putString(KEY_TODOS, arr.toString()).apply()
    }

    fun addTodo(context: Context, text: String) {
        saveTodos(context, getTodos(context) + TodoItem(text.trim(), false))
    }

    /** Marks the first not-yet-done item whose text contains [query] as done. Returns it, if found. */
    fun completeTodo(context: Context, query: String): String? {
        val todos = getTodos(context)
        val idx = todos.indexOfFirst { !it.done && it.text.contains(query, ignoreCase = true) }
        if (idx == -1) return null
        val updated = todos.toMutableList()
        val item = updated[idx]
        updated[idx] = item.copy(done = true)
        saveTodos(context, updated)
        return item.text
    }

    fun clearCompletedTodos(context: Context) {
        saveTodos(context, getTodos(context).filterNot { it.done })
    }

    // --- Home Wi-Fi SSID: simple "I'm home" proxy, no location/geofencing permission needed ---
    fun getHomeWifiSsid(context: Context): String = prefs(context).getString(KEY_HOME_WIFI_SSID, "") ?: ""

    fun setHomeWifiSsid(context: Context, ssid: String) {
        prefs(context).edit().putString(KEY_HOME_WIFI_SSID, ssid.trim()).apply()
    }

    // --- Arrival reminders: fire when the phone connects to the configured home Wi-Fi ---
    private const val KEY_ARRIVAL_REMINDERS = "arrival_reminders"

    fun getArrivalReminders(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ARRIVAL_REMINDERS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(MEMORY_SEPARATOR).filter { it.isNotBlank() }
    }

    fun addArrivalReminder(context: Context, message: String) {
        val updated = getArrivalReminders(context) + message.trim()
        prefs(context).edit().putString(KEY_ARRIVAL_REMINDERS, updated.joinToString(MEMORY_SEPARATOR)).apply()
    }

    fun clearArrivalReminders(context: Context) {
        prefs(context).edit().remove(KEY_ARRIVAL_REMINDERS).apply()
    }
}
