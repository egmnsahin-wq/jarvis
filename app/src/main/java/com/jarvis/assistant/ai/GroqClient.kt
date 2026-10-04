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
 * Groq (OpenAI-compatible API). Free key: console.groq.com/keys
 *
 * Free-tier limits are PER MODEL, and our intent prompt is long (~4-5k tokens), so a model with a
 * small tokens-per-minute budget runs out fast. Hence ordered lists: when the first model is
 * limited/unavailable, the next is tried. Check your real limits at console.groq.com/settings/limits
 * and reorder the lists if needed.
 */
object GroqClient {

    private val CHAT_MODELS = listOf("meta-llama/llama-4-scout-17b-16e-instruct", "openai/gpt-oss-120b")
    private val TEXT_MODELS = listOf("meta-llama/llama-4-scout-17b-16e-instruct", "openai/gpt-oss-120b")
    private val VISION_MODELS = listOf("meta-llama/llama-4-scout-17b-16e-instruct") // vision-capable

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(context: Context): Boolean = PreferencesManager.getGroqApiKey(context).isNotBlank()

    /** Spoken sentence -> structured intent (JSON). */
    suspend fun classify(context: Context, userInput: String, history: List<Pair<String, String>>): JarvisIntent =
        withContext(Dispatchers.IO) {
            val apiKey = PreferencesManager.getGroqApiKey(context)
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", IntentSchema.buildSystemPrompt(context)))
                history.takeLast(6).forEach { (role, text) ->
                    put(JSONObject().put("role", if (role == "user") "user" else "assistant").put("content", text))
                }
                put(JSONObject().put("role", "user").put("content", userInput))
            }
            IntentSchema.parseIntentJson(
                generate(apiKey, CHAT_MODELS, messages, temperature = 0.6, maxTokens = 1024, jsonMode = true)
            )
        }

    /** Free-form text prompt (file analysis / content generation). */
    suspend fun analyzeText(context: Context, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = PreferencesManager.getGroqApiKey(context)
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        generate(apiKey, TEXT_MODELS, messages, temperature = 0.4, maxTokens = 2048, jsonMode = false)
    }

    /** Prompt + one image (base64 JPEG) — used as a fallback when Gemini can't answer. */
    suspend fun analyzeImage(context: Context, prompt: String, imageBase64: String): String =
        withContext(Dispatchers.IO) {
            val apiKey = PreferencesManager.getGroqApiKey(context)
            val content = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", prompt))
                put(JSONObject().put("type", "image_url").put(
                    "image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")
                ))
            }
            val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))
            generate(apiKey, VISION_MODELS, messages, temperature = 0.3, maxTokens = 1500, jsonMode = false)
        }

    // ---------- internals ----------

    private fun generate(
        apiKey: String, models: List<String>, messages: JSONArray,
        temperature: Double, maxTokens: Int, jsonMode: Boolean
    ): String {
        var last: AiException? = null
        for (model in models) {
            try {
                return callOnce(apiKey, model, messages, temperature, maxTokens, jsonMode)
            } catch (e: AiException) {
                last = e
            }
        }
        throw last ?: AiException(0, "Groq için model listesi boş")
    }

    private fun callOnce(
        apiKey: String, model: String, messages: JSONArray,
        temperature: Double, maxTokens: Int, jsonMode: Boolean
    ): String {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
            // gpt-oss models "think" first and thinking tokens count against this limit; a small
            // limit used to cut the answer off -> empty reply. So: generous limit + low effort.
            put("max_completion_tokens", maxTokens)
            if (model.startsWith("openai/gpt-oss")) put("reasoning_effort", "low")
            if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
        }
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            val text = client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw AiException(response.code, "Groq $model HTTP ${response.code}")
                JSONObject(raw).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            }
            if (text.isBlank()) throw AiException(204, "Groq boş cevap döndü")
            return text
        } catch (e: IOException) {
            throw AiException(-1, e.message ?: "bağlantı hatası")
        } catch (e: JSONException) {
            throw AiException(-2, e.message ?: "okunamayan cevap")
        }
    }
}
