package com.jarvis.assistant.commands

import android.content.Context
import android.util.Base64
import com.jarvis.assistant.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Finds a specific track via Spotify's / YouTube's search APIs and opens a deep link that
 * actually starts playing it — as opposed to [SearchController]'s plain "open a search screen"
 * links, which is all a `spotify:search:...` / youtube results URL can do on its own.
 *
 * Both APIs are free but need credentials the user sets up once in Settings:
 *  - Spotify: a Client ID + Secret from developer.spotify.com/dashboard (Client Credentials
 *    flow — no user login needed, just app credentials).
 *  - YouTube: an API key from console.cloud.google.com (enable "YouTube Data API v3").
 * Without credentials configured, both fall back to the old "just open search" behaviour with
 * an honest message about why.
 */
object SongPlaybackController {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedSpotifyToken: String? = null
    private var cachedSpotifyTokenExpiry: Long = 0L

    suspend fun playOnSpotify(context: Context, query: String, searchController: SearchController): String =
        withContext(Dispatchers.IO) {
            val clientId = PreferencesManager.getSpotifyClientId(context)
            val clientSecret = PreferencesManager.getSpotifyClientSecret(context)
            if (clientId.isBlank() || clientSecret.isBlank()) {
                return@withContext searchController.openSpotifySearch(query) +
                    " (Otomatik çalması için Ayarlar'dan Spotify API bilgilerini girebilirsin.)"
            }

            val token = getSpotifyToken(clientId, clientSecret)
                ?: return@withContext searchController.openSpotifySearch(query) + " (Spotify'a bağlanamadım.)"

            val trackUri = searchSpotifyTrack(token, query)
                ?: return@withContext searchController.openSpotifySearch(query) + " (\"$query\" için tam eşleşme bulamadım.)"

            searchController.openUri(trackUri)
            "\"$query\" Spotify'da çalıyor."
        }

    suspend fun playOnYoutube(context: Context, query: String, searchController: SearchController): String =
        withContext(Dispatchers.IO) {
            val apiKey = PreferencesManager.getYoutubeApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext searchController.searchYoutube(query) +
                    " (Otomatik oynatması için Ayarlar'dan YouTube API anahtarını girebilirsin.)"
            }

            val videoId = searchYoutubeVideoId(apiKey, query)
                ?: return@withContext searchController.searchYoutube(query) + " (\"$query\" için video bulamadım.)"

            searchController.openUri("https://www.youtube.com/watch?v=$videoId")
            "\"$query\" YouTube'da çalıyor."
        }

    // ---------- Spotify ----------

    private fun getSpotifyToken(clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()
        cachedSpotifyToken?.let { if (now < cachedSpotifyTokenExpiry) return it }

        val credentials = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .addHeader("Authorization", "Basic $credentials")
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                val token = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
                val expiresIn = json.optInt("expires_in", 3600)
                cachedSpotifyToken = token
                cachedSpotifyTokenExpiry = now + (expiresIn - 60) * 1000L
                token
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun searchSpotifyTrack(token: String, query: String): String? {
        val url = "https://api.spotify.com/v1/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&type=track&limit=1"
        val request = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                val items = json.optJSONObject("tracks")?.optJSONArray("items")
                if (items == null || items.length() == 0) return null
                items.getJSONObject(0).optString("uri").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------- YouTube ----------

    private fun searchYoutubeVideoId(apiKey: String, query: String): String? {
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=1&type=video" +
            "&q=${java.net.URLEncoder.encode(query, "UTF-8")}&key=$apiKey"
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                val items = json.optJSONArray("items")
                if (items == null || items.length() == 0) return null
                items.getJSONObject(0).optJSONObject("id")?.optString("videoId")?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }
}
