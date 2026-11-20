package com.jarvis.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kimse tanınmadığında (misafir/varsayılan kullanıcı) bile konuşma geçmişini kalıcı saklar,
 * böylece uygulama kapanıp açılsa da Jarvis önceki konuşmaları hatırlar.
 */
object ConversationStore {
    private const val PREFS = "jarvis_conversation"
    private const val KEY = "guest_history"
    private const val MAX_STORED = 300

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context): List<Pair<String, String>> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getString("r") to o.getString("t")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun append(context: Context, role: String, text: String) {
        val updated = (load(context) + (role to text)).takeLast(MAX_STORED)
        val arr = JSONArray()
        updated.forEach { (r, t) -> arr.put(JSONObject().put("r", r).put("t", t)) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}
