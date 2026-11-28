package com.jarvis.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * YouTube/Instagram gibi uygulamalarda Paylaş > "Jarvis'e kaydet" ile biriktirilen bağlantılar.
 * "Videoları Ali'ye gönder" komutu bekleyenleri tek mesajda yollar.
 */
object SharedLinksStore {
    private const val PREFS = "jarvis_shared_links"
    private const val KEY = "items"
    private const val MAX_ITEMS = 200
    private val urlRegex = Regex("""https?://\S+""")

    data class Item(val url: String, val title: String, val source: String, val sent: Boolean, val time: Long)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Item> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Item(
                    url = o.getString("u"),
                    title = o.optString("t"),
                    source = o.optString("s", "Bağlantı"),
                    sent = o.optBoolean("x", false),
                    time = o.optLong("d", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun pending(context: Context): List<Item> = all(context).filter { !it.sent }

    /** Paylaşılan metinden bağlantıyı ayıklayıp ekler. Bağlantı yoksa null döner. */
    fun add(context: Context, sharedText: String): Item? {
        val url = urlRegex.find(sharedText)?.value?.trimEnd('.', ',', ')', ';') ?: return null
        val title = sharedText.replace(urlRegex, "").trim().replace(Regex("\\s+"), " ").take(100)
        val lower = url.lowercase()
        val source = when {
            "youtu.be" in lower || "youtube.com" in lower -> "YouTube"
            "instagram.com" in lower -> "Instagram"
            "tiktok.com" in lower -> "TikTok"
            else -> "Bağlantı"
        }
        val current = all(context)
        if (current.any { it.url == url && !it.sent }) return current.first { it.url == url && !it.sent }
        val item = Item(url, title, source, false, System.currentTimeMillis())
        save(context, (current + item).takeLast(MAX_ITEMS))
        return item
    }

    fun markSent(context: Context, urls: Collection<String>) {
        val set = urls.toSet()
        save(context, all(context).map { if (it.url in set) it.copy(sent = true) else it })
    }

    fun clear(context: Context) = save(context, emptyList())

    fun summary(context: Context): String {
        val p = pending(context)
        if (p.isEmpty()) return "Jarvis'e kaydedilmiş bekleyen video yok."
        val parts = p.groupingBy { it.source }.eachCount().entries.joinToString(", ") { "${it.value} ${it.key}" }
        return "Bekleyen ${p.size} bağlantı var: $parts."
    }

    private fun save(context: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("u", it.url).put("t", it.title).put("s", it.source).put("x", it.sent).put("d", it.time))
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }
}
