package com.jarvis.assistant.ai

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
 * Groq, OpenRouter, Mistral and Cerebras all speak the same OpenAI-compatible
 * `/chat/completions` protocol — this one client handles all of them, parameterized by
 * base URL / API key / model. Keeps [AssistantAi]'s provider list from needing a near-duplicate
 * Kotlin file per provider.
 */
object OpenAiCompatibleClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun classify(
        baseUrl: String,
        apiKey: String,
        model: String,
        providerName: String,
        systemPrompt: String,
        userInput: String,
        history: List<Pair<String, String>>
    ): JarvisIntent = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext JarvisIntent(intent = "CHAT", reply = "$providerName API anahtarı girilmemiş. Ayarlar sekmesinden ekleyebilirsin.")
        }
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            IntentSchema.windowHistory(history).forEach { (role, text) ->
                put(JSONObject().put("role", if (role == "user") "user" else "assistant").put("content", text))
            }
            put(JSONObject().put("role", "user").put("content", userInput))
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.6)
            put("max_tokens", 500)
            put("response_format", JSONObject().put("type", "json_object"))
        }
        val raw = call(baseUrl, apiKey, body, providerName)
        if (IntentSchema.isErrorReply(raw)) JarvisIntent(intent = "CHAT", reply = IntentSchema.stripErrorMarker(raw))
        else IntentSchema.parseIntentJson(raw)
    }

    suspend fun analyzeText(
        baseUrl: String, apiKey: String, model: String, providerName: String, prompt: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "$providerName API anahtarı girilmemiş."
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("temperature", 0.4)
            put("max_tokens", 2048)
        }
        call(baseUrl, apiKey, body, providerName)
    }

    /** image_url with a data: URI — the OpenAI-compatible vision message format most providers (incl. OpenRouter, Mistral) accept. */
    suspend fun analyzeImage(
        baseUrl: String, apiKey: String, model: String, providerName: String, prompt: String, imageBase64: String, mimeType: String = "image/jpeg"
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "$providerName API anahtarı girilmemiş."
        val content = JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", prompt))
            put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mimeType;base64,$imageBase64")))
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            put("temperature", 0.3)
            put("max_tokens", 1024)
        }
        call(baseUrl, apiKey, body, providerName)
    }

    private fun call(baseUrl: String, apiKey: String, body: JSONObject, providerName: String): String {
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return IntentSchema.markError(IntentSchema.friendlyErrorMessage(response.code, providerName))
                }
                val json = JSONObject(raw)
                val text = json.optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                if (text.isNullOrBlank()) IntentSchema.markError("Bir cevap alamadım, tekrar dener misin?") else text
            }
        } catch (e: Exception) {
            IntentSchema.markError("Bağlantı hatası: ${e.message}")
        }
    }
}
