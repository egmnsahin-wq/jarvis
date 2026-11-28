package com.jarvis.assistant.commands

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** Sesle harcama defteri: "markette 250 lira harcadım" kaydeder, "bu hafta ne kadar harcadım" özet verir. */
class ExpenseController(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("jarvis_expenses", Context.MODE_PRIVATE)

    private data class Entry(val amount: Double, val category: String, val time: Long)

    fun add(amountText: String?, category: String?): String {
        val amount = parseAmount(amountText)
            ?: return "Kaç lira olduğunu duyamadım, tutarı söyleyerek tekrar dener misin?"
        val cat = (category ?: "").trim().lowercase(Locale("tr")).ifBlank { "diğer" }.take(24)
        val now = System.currentTimeMillis()
        save((load() + Entry(amount, cat, now)).takeLast(5000))
        val today = load().filter { it.time >= startOfDay() }.sumOf { it.amount }
        return "${fmt(amount)} lira $cat harcaması kaydedildi. Bugün toplam ${fmt(today)} lira."
    }

    fun report(period: String?): String {
        val p = (period ?: "").lowercase(Locale("tr"))
        val (label, since) = when {
            p.contains("bug") || p.contains("gün") -> "Bugün" to startOfDay()
            p.contains("haf") -> "Son 7 günde" to startOfDay() - 6 * 24 * 3600_000L
            p.contains("hep") || p.contains("tüm") -> "Toplamda" to 0L
            else -> "Bu ay" to startOfMonth()
        }
        val list = load().filter { it.time >= since }
        if (list.isEmpty()) return "$label için kayıtlı harcama yok."
        val total = list.sumOf { it.amount }
        val top = list.groupBy { it.category }
            .map { it.key to it.value.sumOf { e -> e.amount } }
            .sortedByDescending { it.second }
            .take(4)
            .joinToString(", ") { "${it.first} ${fmt(it.second)}" }
        return "$label toplam ${fmt(total)} lira harcamışsın. En çok: $top."
    }

    private fun parseAmount(text: String?): Double? {
        var t = text?.trim()?.filter { it.isDigit() || it == '.' || it == ',' } ?: return null
        if (t.isBlank()) return null
        t = when {
            t.contains('.') && t.contains(',') -> t.replace(".", "").replace(',', '.')
            t.contains(',') -> t.replace(',', '.')
            Regex("""\d{1,3}(\.\d{3})+""").matches(t) -> t.replace(".", "")
            else -> t
        }
        val v = t.toDoubleOrNull() ?: return null
        return if (v > 0 && v < 100_000_000) v else null
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) String.format(Locale("tr"), "%,.0f", v) else String.format(Locale("tr"), "%,.2f", v)

    private fun startOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun load(): List<Entry> {
        val raw = prefs.getString("entries", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getDouble("a"), o.optString("c", "diğer"), o.getLong("t"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(JSONObject().put("a", it.amount).put("c", it.category).put("t", it.time)) }
        prefs.edit().putString("entries", arr.toString()).apply()
    }
}
