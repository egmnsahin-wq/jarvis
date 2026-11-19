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
 * Client for the Gemini API.
 *
 * Uses gemini-2.5-flash-lite rather than the plain 2.5-flash model: on the free tier this gives
 * roughly 3x the requests-per-minute and requests-per-day headroom (a voice assistant fires off
 * many small classification calls, so the higher ceiling matters more than the small quality
 * difference for this structured-JSON task).
 */
object GeminiClient {

    private const val MODEL = "gemini-2.5-flash-lite"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(context: Context): Boolean = PreferencesManager.getApiKey(context).isNotBlank()

    suspend fun classify(context: Context, userInput: String, history: List<Pair<String, String>>): JarvisIntent =
        withContext(Dispatchers.IO) {
            val apiKey = PreferencesManager.getApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext JarvisIntent(
                    intent = "CHAT",
                    reply = "Gemini API anahtarı girilmemiş. Ayarlar sekmesinden ekleyebilirsin."
                )
            }

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
                    put("maxOutputTokens", 500)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext JarvisIntent(intent = "CHAT", reply = IntentSchema.friendlyErrorMessage(response.code, "Gemini"))
                    }
                    val json = JSONObject(raw)
                    val text = json.optJSONArray("candidates")
                        ?.optJSONObject(0)?.optJSONObject("content")
                        ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")

                    if (text.isNullOrBlank()) {
                        return@withContext JarvisIntent(intent = "CHAT", reply = "Bir cevap alamadım, tekrar dener misin?")
                    }
                    IntentSchema.parseIntentJson(text)
                }
            } catch (e: Exception) {
                JarvisIntent(intent = "CHAT", reply = "Bağlantı hatası: ${e.message}")
            }
        }

    /** Free-form text prompt, no JSON schema constraint — used for file analysis / content generation. */
    suspend fun analyzeText(context: Context, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = PreferencesManager.getApiKey(context)
        if (apiKey.isBlank()) return@withContext IntentSchema.markError("Gemini API anahtarı girilmemiş. Ayarlar sekmesinden ekleyebilirsin.")

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
        callGenerateContent(apiKey, body)
    }

    /** Free-form prompt + a single image (JPEG/PNG, base64) — used for "what's on my screen" style questions. */
    suspend fun analyzeImage(context: Context, prompt: String, imageBase64: String, mimeType: String = "image/jpeg"): String =
        withContext(Dispatchers.IO) {
            val apiKey = PreferencesManager.getApiKey(context)
            if (apiKey.isBlank()) return@withContext IntentSchema.markError("Gemini API anahtarı girilmemiş. Ayarlar sekmesinden ekleyebilirsin.")

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
                    put("maxOutputTokens", 1024)
                })
            }
            callGenerateContent(apiKey, body)
        }

    private fun callGenerateContent(apiKey: String, body: JSONObject): String {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return IntentSchema.markError(IntentSchema.friendlyErrorMessage(response.code, "Gemini"))
                val json = JSONObject(raw)
                json.optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                    ?.takeIf { it.isNotBlank() } ?: IntentSchema.markError("Bir cevap alamadım, tekrar dener misin?")
            }
        } catch (e: Exception) {
            IntentSchema.markError("Bağlantı hatası: ${e.message}")
        }
    }
}
