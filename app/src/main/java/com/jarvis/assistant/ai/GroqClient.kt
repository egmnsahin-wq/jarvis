package com.jarvis.assistant.ai

import android.content.Context
import com.jarvis.assistant.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Alternative free AI backend: Groq's OpenAI-compatible API running GPT-OSS 120B.
 *
 * Groq's free tier (no credit card) is roughly 30 requests/minute and 14,400/day — much more
 * headroom than Gemini's free tier for a voice assistant that fires off many small
 * classification calls. Good as either the primary provider (Settings) or as an automatic
 * fallback when the other provider gets rate-limited (see [AssistantAi]).
 *
 * Get a free key at console.groq.com/keys (email/GitHub/Google sign-up, no card).
 */
object GroqClient {

    private const val MODEL = "openai/gpt-oss-120b"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(context: Context): Boolean = PreferencesManager.getGroqApiKey(context).isNotBlank()

    suspend fun classify(context: Context, userInput: String, history: List<Pair<String, String>>): JarvisIntent =
        withContext(Dispatchers.IO) {
            val apiKey = PreferencesManager.getGroqApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext JarvisIntent(
                    intent = "CHAT",
                    reply = "Groq API anahtarı girilmemiş. Ayarlar sekmesinden ekleyebilirsin."
                )
            }

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", IntentSchema.buildSystemPrompt(context)))
                history.takeLast(6).forEach { (role, text) ->
                    put(JSONObject().put("role", if (role == "user") "user" else "assistant").put("content", text))
                }
                put(JSONObject().put("role", "user").put("content", userInput))
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("temperature", 0.6)
                put("max_tokens", 500)
                put("response_format", JSONObject().put("type", "json_object"))
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext JarvisIntent(intent = "CHAT", reply = IntentSchema.friendlyErrorMessage(response.code, "Groq"))
                    }
                    val json = JSONObject(raw)
                    val text = json.optJSONArray("choices")
                        ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")

                    if (text.isNullOrBlank()) {
                        return@withContext JarvisIntent(intent = "CHAT", reply = "Bir cevap alamadım, tekrar dener misin?")
                    }
                    IntentSchema.parseIntentJson(text)
                }
            } catch (e: Exception) {
                JarvisIntent(intent = "CHAT", reply = "Bağlantı hatası: ${e.message}")
            }
        }
}
