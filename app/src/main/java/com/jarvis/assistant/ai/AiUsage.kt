package com.jarvis.assistant.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bugün Gemini'ye kaç başarılı istek gönderildiğini sayar (ekranda gösterilir).
 * Google günlük kotayı Pasifik saatine göre sıfırladığı için gün sınırı da oraya göre alınır.
 */
object AiUsage {
    private const val PREFS = "jarvis_ai_usage"
    private val _today = MutableStateFlow(0)
    val today: StateFlow<Int> = _today
    @Volatile private var prefs: SharedPreferences? = null

    private fun dayKey(): String = LocalDate.now(ZoneId.of("America/Los_Angeles")).toString()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _today.value = if (p.getString("day", null) == dayKey()) p.getInt("count", 0) else 0
    }

    @Synchronized
    fun record() {
        val p = prefs ?: return
        val key = dayKey()
        val count = if (p.getString("day", null) == key) p.getInt("count", 0) + 1 else 1
        p.edit().putString("day", key).putInt("count", count).apply()
        _today.value = count
    }
}
