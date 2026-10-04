package com.jarvis.assistant.ai

import android.content.Context
import com.jarvis.assistant.data.PreferencesManager

/**
 * Single entry point for all AI work. Two providers, routed by TASK, with automatic fallback:
 *
 *  - Normal chat / command understanding -> the provider chosen in Settings (default Groq: fast,
 *    roomy free limits), then the other one.
 *  - Screen questions (images)           -> Gemini first, then Groq vision.
 *  - File analysis / file creation       -> Gemini first (huge context), then Groq.
 *
 * "Fallback" = ANY failure of the first provider (limit, server error, retired model, no
 * internet...), not just 429. Only providers that have an API key are tried.
 */
object AssistantAi {

    private enum class Provider { GEMINI, GROQ }

    // Shared across providers so conversation context survives a fallback swap.
    private val history = mutableListOf<Pair<String, String>>()

    private const val NO_KEY_MESSAGE =
        "Henüz hiçbir yapay zeka anahtarı girilmemiş. Ayarlar'dan Groq veya Gemini anahtarı ekler misin?"

    private fun hasKey(context: Context, p: Provider): Boolean = when (p) {
        Provider.GEMINI -> GeminiClient.hasApiKey(context)
        Provider.GROQ -> GroqClient.hasApiKey(context)
    }

    private fun order(context: Context, vararg wanted: Provider): List<Provider> =
        wanted.filter { hasKey(context, it) }

    private suspend fun <T> firstSuccess(providers: List<Provider>, call: suspend (Provider) -> T): T {
        var last: AiException? = null
        for (p in providers) {
            try {
                return call(p)
            } catch (e: AiException) {
                last = e
            }
        }
        throw last ?: AiException(0, "Kullanılabilir yapay zeka yok")
    }

    suspend fun classify(context: Context, userInput: String): JarvisIntent {
        val groqFirst = PreferencesManager.getAiProvider(context) != "gemini"
        val providers = if (groqFirst) order(context, Provider.GROQ, Provider.GEMINI)
        else order(context, Provider.GEMINI, Provider.GROQ)
        if (providers.isEmpty()) return JarvisIntent(intent = "CHAT", reply = NO_KEY_MESSAGE)

        val result = try {
            firstSuccess(providers) { p ->
                when (p) {
                    Provider.GROQ -> GroqClient.classify(context, userInput, history)
                    Provider.GEMINI -> GeminiClient.classify(context, userInput, history)
                }
            }
        } catch (e: AiException) {
            return JarvisIntent(intent = "CHAT", reply = IntentSchema.friendlyErrorMessage(e.code, "Yapay zeka"))
        }

        history.add("user" to userInput)
        history.add("model" to result.reply)
        while (history.size > 12) history.removeAt(0)
        return result
    }

    /** File analysis / content generation. Gemini first, Groq as backup. */
    suspend fun analyzeText(context: Context, prompt: String): String {
        val providers = order(context, Provider.GEMINI, Provider.GROQ)
        if (providers.isEmpty()) return NO_KEY_MESSAGE
        return try {
            firstSuccess(providers) { p ->
                when (p) {
                    Provider.GEMINI -> GeminiClient.analyzeText(context, prompt)
                    Provider.GROQ -> GroqClient.analyzeText(context, prompt)
                }
            }
        } catch (e: AiException) {
            IntentSchema.friendlyErrorMessage(e.code, "Yapay zeka")
        }
    }

    /** Screen / image questions. Gemini first, Groq vision as backup. */
    suspend fun analyzeImage(context: Context, prompt: String, imageBase64: String): String {
        val providers = order(context, Provider.GEMINI, Provider.GROQ)
        if (providers.isEmpty()) return NO_KEY_MESSAGE
        return try {
            firstSuccess(providers) { p ->
                when (p) {
                    Provider.GEMINI -> GeminiClient.analyzeImage(context, prompt, imageBase64)
                    Provider.GROQ -> GroqClient.analyzeImage(context, prompt, imageBase64)
                }
            }
        } catch (e: AiException) {
            IntentSchema.friendlyErrorMessage(e.code, "Yapay zeka")
        }
    }

    fun resetConversation() {
        history.clear()
    }

    /** Read-only copy of the recent conversation turns, for features like exporting the chat. */
    fun getHistorySnapshot(): List<Pair<String, String>> = history.toList()
}
