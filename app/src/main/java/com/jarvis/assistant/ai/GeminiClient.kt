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
 * Gemini API istemcisi — tek bir Google (Gemini) anahtarı her şeye yeter.
 *
 * Model adları Google tarafından zamanla kapatılıyor (ör. 2.0 Flash ailesi kapandı). Bu yüzden model
 * sabit değil: önce en son çalışan model, sonra "-latest" takma adları ve bilinen modeller denenir.
 * Bir model 404 (artık yok), 429 (o modelin ücretsiz günlük limiti doldu) ya da 5xx verirse sıradaki
 * modele geçilir — her modelin ücretsiz kotası ayrı olduğundan bu, günlük kullanım payını da artırır.
 * Hepsi başarısız olursa Google'ın model listesi sorgulanıp uygun modeller de denenir.
 */
object GeminiClient {

    private val DEFAULT_MODELS = listOf(
        "gemini-flash-lite-latest",
        "gemini-2.5-flash-lite",
        "gemini-flash-latest",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash"
    )
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private data class Resp(val code: Int, val body: String)

    fun hasApiKey(context: Context): Boolean = PreferencesManager.getApiKey(context).isNotBlank()

    private fun post(model: String, apiKey: String, body: JSONObject): Resp {
        val request = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val resp = client.newCall(request).execute().use { Resp(it.code, it.body?.string().orEmpty()) }
        if (resp.code == 200) AiUsage.record()
        return resp
    }

    private fun shouldTryNextModel(r: Resp): Boolean =
        r.code == 404 || r.code == 429 || r.code in 500..599 ||
            (r.code == 400 && (r.body.contains("not found", true) || r.body.contains("not supported", true)))

    /** Google'ın verdiği model listesinden generateContent destekleyen flash modellerini bulur. */
    private fun discoverModels(apiKey: String): List<String> = try {
        val request = Request.Builder().url("$BASE/models?pageSize=200").header("x-goog-api-key", apiKey).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("models") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val m = arr.getJSONObject(i)
                val name = m.optString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")
                val ok = methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                if (ok && name.contains("flash") &&
                    listOf("image", "tts", "live", "audio", "embedding", "robotics", "computer").none { name.contains(it) }
                ) name else null
            }.sortedWith(compareBy({ !it.contains("lite") }, { it.contains("preview") }, { it }))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun generate(context: Context, apiKey: String, body: JSONObject): Resp {
        val cached = PreferencesManager.getGeminiModel(context)
        val order = (listOf(cached) + DEFAULT_MODELS).filter { it.isNotBlank() }.distinct()
        val tried = mutableSetOf<String>()
        var last = Resp(0, "")

        fun attempt(model: String): Resp? {
            tried += model
            val r = post(model, apiKey, body)
            if (r.code in 200..299) {
                PreferencesManager.setGeminiModel(context, model)
                return r
            }
            // Geçersiz anahtar Google'da 400 döner; mesajı doğru gösterebilmek için 401 say.
            last = if (r.code == 400 && r.body.contains("API key not valid", true)) Resp(401, r.body) else r
            return if (shouldTryNextModel(r)) null else last
        }

        for (m in order) attempt(m)?.let { return it }
        for (m in discoverModels(apiKey).filter { it !in tried }.take(4)) attempt(m)?.let { return it }
        return last
    }

    private fun extractText(raw: String): String? = try {
        JSONObject(raw).optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")
            ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            ?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    /** Ayarlar'daki "Anahtarı test et" düğmesi ve tanılama için: kısa bir istek atar, insan diline çevrilmiş sonuç verir. */
    suspend fun test(context: Context, apiKey: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Önce anahtarı yapıştır."
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "Sadece 'tamam' yaz.")))
            }))
            put("generationConfig", JSONObject().put("maxOutputTokens", 64))
        }
        try {
            val r = generate(context, apiKey.trim(), body)
            when {
                r.code in 200..299 -> "✓ Anahtar çalışıyor (model: ${PreferencesManager.getGeminiModel(context)})."
                r.code == 401 || r.code == 403 -> "✗ Anahtar geçersiz görünüyor. Baştan sona kopyaladığından ve başına/sonuna boşluk girmediğinden emin ol."
                r.code == 429 -> "✗ Anahtar geçerli ama şu an ücretsiz limit dolu. Biraz sonra tekrar dene."
                r.code == 0 -> "✗ Sunucuya ulaşılamadı."
                else -> "✗ Beklenmeyen yanıt (${r.code})."
            }
        } catch (e: Exception) {
            "✗ İnternete ulaşamadım. Bağlantını kontrol edip tekrar dene."
        }
    }

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
                IntentSchema.windowHistory(history).forEach { (role, text) ->
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
                    put("temperature", 0.8)
                    // Yeni modeller "düşünme" için de token harcar; payı geniş tutuyoruz ki cevap boş kalmasın.
                    put("maxOutputTokens", 2000)
                    put("responseMimeType", "application/json")
                })
            }

            try {
                val r = generate(context, apiKey, body)
                if (r.code !in 200..299) {
                    return@withContext JarvisIntent(intent = "CHAT", reply = IntentSchema.friendlyErrorMessage(r.code, "Gemini"))
                }
                val text = extractText(r.body)
                if (text.isNullOrBlank()) {
                    return@withContext JarvisIntent(intent = "CHAT", reply = "Bir cevap alamadım, tekrar dener misin?")
                }
                IntentSchema.parseIntentJson(text)
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
                put("temperature", 0.6)
                put("maxOutputTokens", 4096)
            })
        }
        callGenerateContent(context, apiKey, body)
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
                    put("maxOutputTokens", 2048)
                })
            }
            callGenerateContent(context, apiKey, body)
        }

    private fun callGenerateContent(context: Context, apiKey: String, body: JSONObject): String {
        return try {
            val r = generate(context, apiKey, body)
            if (r.code !in 200..299) return IntentSchema.markError(IntentSchema.friendlyErrorMessage(r.code, "Gemini"))
            extractText(r.body) ?: IntentSchema.markError("Bir cevap alamadım, tekrar dener misin?")
        } catch (e: Exception) {
            IntentSchema.markError("Bağlantı hatası: ${e.message}")
        }
    }
}
