package com.jarvis.assistant.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Currency exchange rates via frankfurter.app (free, keyless, ECB-sourced) and crypto prices
 * via CoinGecko's public API (also free, keyless). Both are simple GET requests, no auth.
 */
object CurrencyController {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val fiatCodes = setOf("usd", "eur", "gbp", "try", "jpy", "chf", "cad", "aud", "rub", "cny")
    private val cryptoIds = mapOf(
        "bitcoin" to "bitcoin", "btc" to "bitcoin",
        "ethereum" to "ethereum", "eth" to "ethereum",
        "dogecoin" to "dogecoin", "doge" to "dogecoin",
        "solana" to "solana", "sol" to "solana",
        "ripple" to "ripple", "xrp" to "ripple"
    )

    /** Handles free-form queries like "dolar kaç tl", "bitcoin fiyatı", "euro try". */
    suspend fun lookup(query: String): String = withContext(Dispatchers.IO) {
        val q = query.lowercase(Locale("tr"))
            .replace("dolar", "usd").replace("amerikan dolari", "usd")
            .replace("euro", "eur").replace("avro", "eur")
            .replace("sterlin", "gbp").replace("pound", "gbp")
            .replace("tl", "try").replace("lira", "try")

        val cryptoMatch = cryptoIds.keys.firstOrNull { q.contains(it) }
        if (cryptoMatch != null) return@withContext cryptoPrice(cryptoIds.getValue(cryptoMatch), cryptoMatch)

        val mentioned = fiatCodes.filter { q.contains(it) }
        val from = mentioned.getOrNull(0) ?: "usd"
        val to = mentioned.getOrNull(1) ?: "try"
        if (from == to) return@withContext fiatRate("usd", "try")
        fiatRate(from, to)
    }

    private fun fiatRate(from: String, to: String): String {
        val request = Request.Builder()
            .url("https://api.frankfurter.app/latest?from=${from.uppercase()}&to=${to.uppercase()}")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "Kur bilgisini alamadım, biraz sonra tekrar dener misin?"
                val json = JSONObject(response.body?.string().orEmpty())
                val rate = json.optJSONObject("rates")?.optDouble(to.uppercase())
                if (rate == null || rate.isNaN()) "Kur bilgisini alamadım."
                else "1 ${from.uppercase()} = ${String.format("%.2f", rate)} ${to.uppercase()}"
            }
        } catch (e: Exception) {
            "Kur bilgisini alırken bir bağlantı hatası oldu."
        }
    }

    private fun cryptoPrice(coinId: String, displayName: String): String {
        val request = Request.Builder()
            .url("https://api.coingecko.com/api/v3/simple/price?ids=$coinId&vs_currencies=usd,try")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "Fiyat bilgisini alamadım, biraz sonra tekrar dener misin?"
                val json = JSONObject(response.body?.string().orEmpty())
                val coin = json.optJSONObject(coinId)
                val usd = coin?.optDouble("usd")
                val try_ = coin?.optDouble("try")
                if (coin == null) "Fiyat bilgisini alamadım."
                else "${displayName.replaceFirstChar { it.uppercase() }}: $${String.format("%,.0f", usd)} (yaklaşık ${String.format("%,.0f", try_)} TL)"
            }
        } catch (e: Exception) {
            "Fiyat bilgisini alırken bir bağlantı hatası oldu."
        }
    }
}
