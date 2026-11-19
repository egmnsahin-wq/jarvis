package com.jarvis.assistant.commands

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Tek bir haber kaydı (başlık, kaynak, bağlantı, zaman). */
data class NewsItem(
    val title: String,
    val source: String,
    val link: String,
    val timeLabel: String,
    val description: String
)

/**
 * Reads news headlines from Google News' public RSS feed — free, no API key, no sign-up.
 * (Google News RSS is a stable, widely-used public feed; if it ever changes shape this is
 * the one place to update.)
 */
object NewsController {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun headlines(topic: String? = null, count: Int = 5): String = withContext(Dispatchers.IO) {
        val url = if (topic.isNullOrBlank())
            "https://news.google.com/rss?hl=tr&gl=TR&ceid=TR:tr"
        else
            "https://news.google.com/rss/search?q=${java.net.URLEncoder.encode(topic, "UTF-8")}&hl=tr&gl=TR&ceid=TR:tr"

        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Haberleri şu an alamadım, biraz sonra tekrar dener misin?"
                val xml = response.body?.string().orEmpty()
                val titles = Regex("<title>(.*?)</title>").findAll(xml)
                    .map { it.groupValues[1].replace("&#39;", "'").replace("&amp;", "&").replace("&quot;", "\"") }
                    .drop(1) // first <title> is the feed's own title, not a headline
                    .take(count)
                    .toList()
                if (titles.isEmpty()) "Şu an için haber bulamadım."
                else "İşte son başlıklar: " + titles.joinToString(". ")
            }
        } catch (e: Exception) {
            "Haberleri alırken bir bağlantı hatası oldu."
        }
    }

    /** Başlıkları yapılandırılmış liste olarak döndürür (haber paneli için). Hata olursa null. */
    suspend fun fetchItems(topic: String? = null, count: Int = 15): List<NewsItem>? = withContext(Dispatchers.IO) {
        val url = if (topic.isNullOrBlank())
            "https://news.google.com/rss?hl=tr&gl=TR&ceid=TR:tr"
        else
            "https://news.google.com/rss/search?q=${java.net.URLEncoder.encode(topic, "UTF-8")}&hl=tr&gl=TR&ceid=TR:tr"
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parseItems(response.body?.string().orEmpty()).take(count)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseItems(xml: String): List<NewsItem> {
        val out = mutableListOf<NewsItem>()
        try {
            val p = Xml.newPullParser()
            p.setInput(StringReader(xml))
            var inItem = false
            var title = ""; var link = ""; var pub = ""; var source = ""; var desc = ""
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "item" -> { inItem = true; title = ""; link = ""; pub = ""; source = ""; desc = "" }
                        "title" -> if (inItem) title = p.nextText()
                        "link" -> if (inItem) link = p.nextText()
                        "pubDate" -> if (inItem) pub = p.nextText()
                        "source" -> if (inItem) source = p.nextText()
                        "description" -> if (inItem) desc = p.nextText()
                    }
                } else if (event == XmlPullParser.END_TAG && p.name == "item") {
                    inItem = false
                    var t = title.replace("&#39;", "'").replace("&amp;", "&").replace("&quot;", "\"").trim()
                    if (source.isNotBlank() && t.endsWith(" - $source")) t = t.removeSuffix(" - $source").trim()
                    val d = try {
                        Html.fromHtml(desc, Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00a0', ' ').trim()
                    } catch (e: Exception) { "" }
                    if (t.isNotBlank()) out.add(NewsItem(t, source.trim(), link.trim(), timeLabel(pub), d))
                }
                event = p.next()
            }
        } catch (e: Exception) {
            // yarım kalan listeyi döndür
        }
        return out
    }

    private fun timeLabel(pubDate: String): String {
        if (pubDate.isBlank()) return ""
        return try {
            val d = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(pubDate) ?: return ""
            val mins = ((System.currentTimeMillis() - d.time) / 60000).toInt()
            when {
                mins < 1 -> "az önce"
                mins < 60 -> "$mins dk önce"
                mins < 1440 -> "${mins / 60} sa önce"
                else -> "${mins / 1440} gün önce"
            }
        } catch (e: Exception) { "" }
    }
}
