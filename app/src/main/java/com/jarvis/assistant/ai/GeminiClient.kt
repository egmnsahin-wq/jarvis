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
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gemini API client.
 *
 * Model names change often (Google retires old ones), so each task has an ORDERED LIST: if the
 * first model returns an error (404 retired, 429 limit, 403 not free...), the next one is tried
 * automatically. To update models later, only edit the three lists below.
 *
 * Every function throws [AiException] on failure; [AssistantAi] decides what to do next.
 */
object GeminiClient {

    private val CHAT_MODELS = listOf("gemini-3.1-flash-lite", "gemini-2.5-flash-lite")
    private val TEXT_MODELS = listOf("gemini-3.1-flash-lite", "gemini-2.5-flash", "gemini-2.5-flash-lite")
    private val VISION_MODELS = listOf("gemini-3-flash-preview", "gemini-3.1-flash-lite", "gemini-2.5-flash")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(context: Context): Boolean = PreferencesManager.getApiKey(context).isNotBlank()

    /** Spoken sentence -> structured intent (JSON). */
    suspend fun classify(context: Context, userInput: String, history: List<Pair<String, String>>): JarvisIntent =
        withContext(Dispatchers.IO) {
            val apiKey = PreferencesManager.getApiKey(context)
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", IntentSchema.buildSystemPrompt(context))))
                })
                history.takeLast(6).forEach { (role, text) ->
                    put(JSONObject().apply {
                        put("role", if (role == "user") "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", text)))
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userInput)))
                })
            }
            val body = JSONObject().apply {
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.6)
                    put("maxOutputTokens", 1024)
                    put("responseMimeType", "application/json")
                })
            }
            IntentSchema.parseIntentJson(generate(apiKey, CHAT_MODELS, body))
        }

    /** Free-form text prompt (file analysis / content generation). */
    suspend fun analyzeText(context: Context, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = PreferencesManager.getApiKey(context)
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 2048)
            })
        }
        generate(apiKey, TEXT_MODELS, body)
    }

    /** Prompt + one image (base64) — "what's on my screen" style questions. */
    suspend fun analyzeImage(context: Context, prompt: String, imageBase64: String, mimeType: String = "image/jpeg"): String =
        withContext(Dispatchers.IO) {
            val apiKey = PreferencesManager.getApiKey(context)
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", mimeType)
                            put("data", imageBase64)
                        }))
                    })
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 1500)
                })
            }
            generate(apiKey, VISION_MODELS, body)
        }

    // ---------- internals ----------

    private fun generate(apiKey: String, models: List<String>, body: JSONObject): String {
        var last: AiException? = null
        for (model in models) {
            try {
                return callOnce(apiKey, model, body)
            } catch (e: AiException) {
                last = e
            }
        }
        throw last ?: AiException(0, "Gemini için model listesi boş")
    }

    private fun callOnce(apiKey: String, model: String, body: JSONObject): String {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey) // header instead of ?key= so the key never lands in URLs/logs
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            val text = client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw AiException(response.code, "Gemini $model HTTP ${response.code}")
                val parts = JSONObject(raw).optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        if (part.optBoolean("thought", false)) continue
                        sb.append(part.optString("text", ""))
                    }
                }
                sb.toString()
            }
            if (text.isBlank()) throw AiException(204, "Gemini boş cevap döndü")
            return text
        } catch (e: IOException) {
            throw AiException(-1, e.message ?: "bağlantı hatası")
        } catch (e: JSONException) {
            throw AiException(-2, e.message ?: "okunamayan cevap")
        }
    }
}
