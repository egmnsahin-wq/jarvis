package com.jarvis.assistant.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
}
