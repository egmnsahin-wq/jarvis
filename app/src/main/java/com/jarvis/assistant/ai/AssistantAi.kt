package com.jarvis.assistant.ai

import android.content.Context
import com.jarvis.assistant.data.ConversationStore
import com.jarvis.assistant.data.PeopleStore
import com.jarvis.assistant.data.PreferencesManager
import com.jarvis.assistant.speaker.SpeakerContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single entry point CommandProcessor (and file/screen analysis) calls into. Tries a pool of
 * free AI providers in order and automatically falls through to the next one whenever a call
 * fails or is rate-limited — so running out of one provider's free daily quota doesn't stop
 * Jarvis from answering, as long as at least one other provider has a key configured.
 *
 * Three independent provider orders:
 *  - "simple" tier: used for the frequent, lightweight structured-command classification.
 *    Fast/generous providers go first since this runs on every single utterance.
 *  - "smart" tier: used for heavier free-form reasoning (file analysis, file creation).
 *    The strongest providers go first since quality matters more than speed here.
 *  - "vision" tier: used for image understanding (screen reading, image files). Only providers
 *    that reliably support free-tier vision are included.
 *
 * Add a new free provider by adding one entry to [hasKey] and the relevant call* function —
 * everything else (fallback, ordering) is handled generically.
 */
object AssistantAi {

    // Guests (nobody recognized) keep a short in-memory history only; recognized people get a
    // persistent per-person history via PeopleStore.
    // Artık diske yazılıyor (ConversationStore): uygulama kapanıp açılsa da hatırlanır.

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profileUpdating = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private const val PROFILE_EVERY_TURNS = 6

    // Groq (existing)
    private const val GROQ_BASE = "https://api.groq.com/openai/v1"
    private const val GROQ_MODEL = "openai/gpt-oss-120b"

    // OpenRouter — aggregates many providers' free models under one key; "openrouter/free"
    // auto-selects a free model that supports what the request needs (incl. vision).
    private const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"
    private const val OPENROUTER_MODEL = "openrouter/free"

    // GitHub Models — her GitHub hesabı için ücretsiz (günlük limitli), OpenAI uyumlu, görsel destekler.
    private const val GITHUB_BASE = "https://models.github.ai/inference"
    private const val GITHUB_MODEL = "openai/gpt-4.1-mini"

    // NVIDIA NIM — ücretsiz başlangıç kredisi, hızlı, sadece metin.
    private const val NVIDIA_BASE = "https://integrate.api.nvidia.com/v1"
    private const val NVIDIA_MODEL = "meta/llama-3.3-70b-instruct"

    private val simpleTierOrder = listOf("groq", "github", "nvidia", "openrouter", "gemini")
    private val smartTierOrder = listOf("gemini", "openrouter", "groq", "github", "nvidia")
    private val visionTierOrder = listOf("gemini", "openrouter", "github")

    private fun hasKey(context: Context, provider: String): Boolean = when (provider) {
        "gemini" -> GeminiClient.hasApiKey(context)
        "groq" -> GroqClient.hasApiKey(context)
        "openrouter" -> PreferencesManager.getOpenRouterApiKey(context).isNotBlank()
        "github" -> PreferencesManager.getGithubModelsApiKey(context).isNotBlank()
        "nvidia" -> PreferencesManager.getNvidiaApiKey(context).isNotBlank()
        else -> false
    }

    fun configuredProviderCount(context: Context): Int =
        listOf("gemini", "groq", "openrouter", "github", "nvidia").count { hasKey(context, it) }

    // ---------- Structured intent classification ----------

    suspend fun classify(context: Context, userInput: String): JarvisIntent {
        // Captured once up front so the whole request (prompt, history, saving the turn) is about
        // the same person even if identification updates while the network call is in flight.
        val speakerId = SpeakerContext.currentId(context)
        val history = loadHistory(context, speakerId)

        // The user's chosen primary provider (Settings > Ana Yapay Zeka) goes first if it has a
        // key, then the rest of the simple-tier order fills in as fallbacks.
        val primary = PreferencesManager.getAiProvider(context)
        val order = (listOf(primary) + simpleTierOrder).distinct().filter { hasKey(context, it) }

        var result: JarvisIntent? = null
        var rateLimited = false
        for (provider in order) {
            val attempt = callClassify(context, provider, userInput, history)
            if (!IntentSchema.isFailureReply(attempt)) {
                result = attempt
                break
            }
            if (IntentSchema.isRateLimited(attempt.reply)) rateLimited = true
        }
        val finalResult = result ?: JarvisIntent(
            intent = "CHAT",
            reply = if (order.isEmpty())
                "Hiçbir yapay zeka anahtarı girilmemiş. Ayarlar'dan en az birini eklemen lazım."
            else if (rateLimited)
                IntentSchema.ALL_LIMITS_REACHED_REPLY
            else
                "Şu an hiçbir yapay zeka sağlayıcısına ulaşamadım, biraz sonra tekrar dener misin?"
        )

        // Failure replies ("anahtar girilmemiş", rate limit…) aren't real conversation — don't store them.
        if (!IntentSchema.isFailureReply(finalResult)) recordTurn(context, speakerId, userInput, finalResult.reply)
        return finalResult
    }

    private fun loadHistory(context: Context, personId: String?): List<Pair<String, String>> =
        if (personId == null) ConversationStore.load(context) else PeopleStore.getHistory(context, personId)

    private fun recordTurn(context: Context, personId: String?, userText: String, reply: String) {
        if (personId == null) {
            ConversationStore.append(context, "user", userText)
            ConversationStore.append(context, "model", reply)
        } else {
            PeopleStore.appendHistory(context, personId, "user", userText)
            PeopleStore.appendHistory(context, personId, "model", reply)
            maybeUpdatePersonality(context, personId)
        }
    }

    /**
     * Every few turns, asks the AI to (re)write a short profile of how this person talks and what
     * they care about, based ONLY on their own conversations with Jarvis. The result shows up
     * editable in Settings; if the person's "otomatik güncelle" switch is off this never runs.
     * The existing profile (including anything the user typed by hand) is passed in so it's kept.
     */
    private fun maybeUpdatePersonality(context: Context, personId: String) {
        val person = PeopleStore.getPerson(context, personId) ?: return
        val turns = PeopleStore.incrementTurns(context, personId)
        if (!person.autoPersonality || turns < PROFILE_EVERY_TURNS) return
        if (!profileUpdating.add(personId)) return

        val appContext = context.applicationContext
        backgroundScope.launch {
            try {
                val convo = PeopleStore.getHistory(appContext, personId).takeLast(20)
                    .joinToString("\n") { (role, text) -> (if (role == "user") person.name else "Jarvis") + ": " + text }
                val prompt = "Aşağıda ${person.name} adlı kişinin sesli asistanla yaptığı son konuşmalar var.\n" +
                    "Mevcut profil: \"${person.personality.ifBlank { "(henüz yok)" }}\"\n\n" +
                    "Bu konuşmalara dayanarak ${person.name} kişisinin nasıl biri olduğunu 3-4 cümleyle, Türkçe özetle: " +
                    "iletişim tarzı (resmi/samimi, kısa/uzun cevap tercihi), ilgi alanları, neyi sevip neyi sevmediği, genel ruh hali. " +
                    "Mevcut profildeki bilgileri (özellikle kullanıcının elle yazdığı kısımları) koru, yeni bilgilerle güncelle. " +
                    "Sadece konuşmalardan çıkarabildiklerini yaz, tahmin yürütme; sağlık, din, cinsel yönelim, siyasi görüş gibi hassas konularda çıkarım yapma. " +
                    "Sadece profil metnini yaz, başka açıklama ekleme.\n\n--- KONUŞMALAR ---\n$convo"
                val profile = analyzeTextOrNull(appContext, prompt)?.trim()
                if (!profile.isNullOrBlank()) {
                    PeopleStore.setPersonality(appContext, personId, profile.take(700))
                    PeopleStore.resetTurns(appContext, personId)
                }
            } catch (e: Exception) {
                // sessizce vazgeç; bir sonraki turda yeniden denenir
            } finally {
                profileUpdating.remove(personId)
            }
        }
    }

    private suspend fun callClassify(
        context: Context, provider: String, userInput: String, history: List<Pair<String, String>>
    ): JarvisIntent = when (provider) {
        "gemini" -> GeminiClient.classify(context, userInput, history)
        "groq" -> GroqClient.classify(context, userInput, history)
        "openrouter" -> OpenAiCompatibleClient.classify(
            OPENROUTER_BASE, PreferencesManager.getOpenRouterApiKey(context), OPENROUTER_MODEL,
            "OpenRouter", IntentSchema.buildSystemPrompt(context), userInput, history
        )
        "github" -> OpenAiCompatibleClient.classify(
            GITHUB_BASE, PreferencesManager.getGithubModelsApiKey(context), GITHUB_MODEL,
            "GitHub Models", IntentSchema.buildSystemPrompt(context), userInput, history
        )
        "nvidia" -> OpenAiCompatibleClient.classify(
            NVIDIA_BASE, PreferencesManager.getNvidiaApiKey(context), NVIDIA_MODEL,
            "NVIDIA", IntentSchema.buildSystemPrompt(context), userInput, history
        )
        else -> JarvisIntent(intent = "CHAT", reply = "Bilinmeyen sağlayıcı.")
    }

    // ---------- Free-form text reasoning (file analysis, file creation) ----------

    /** Same provider fallback as [analyzeTextSmart], but returns null instead of an error sentence. */
    suspend fun analyzeTextOrNull(context: Context, prompt: String): String? {
        lastRoundRateLimited = false
        for (provider in smartTierOrder.filter { hasKey(context, it) }) {
            val attempt = callAnalyzeText(context, provider, prompt)
            if (!IntentSchema.isErrorReply(attempt)) return attempt
            if (IntentSchema.isRateLimited(attempt)) lastRoundRateLimited = true
        }
        return null
    }

    @Volatile private var lastRoundRateLimited = false

    suspend fun analyzeTextSmart(context: Context, prompt: String): String {
        if (smartTierOrder.none { hasKey(context, it) })
            return "Hiçbir yapay zeka anahtarı girilmemiş. Ayarlar'dan en az birini eklemen lazım."
        return analyzeTextOrNull(context, prompt)
            ?: if (lastRoundRateLimited) IntentSchema.ALL_LIMITS_REACHED_REPLY
            else "Şu an hiçbir yapay zeka sağlayıcısına ulaşamadım, biraz sonra tekrar dener misin?"
    }

    private suspend fun callAnalyzeText(context: Context, provider: String, prompt: String): String = when (provider) {
        "gemini" -> GeminiClient.analyzeText(context, prompt)
        "openrouter" -> OpenAiCompatibleClient.analyzeText(
            OPENROUTER_BASE, PreferencesManager.getOpenRouterApiKey(context), OPENROUTER_MODEL, "OpenRouter", prompt
        )
        "groq" -> OpenAiCompatibleClient.analyzeText(
            GROQ_BASE, PreferencesManager.getGroqApiKey(context), GROQ_MODEL, "Groq", prompt
        )
        "github" -> OpenAiCompatibleClient.analyzeText(
            GITHUB_BASE, PreferencesManager.getGithubModelsApiKey(context), GITHUB_MODEL, "GitHub Models", prompt
        )
        "nvidia" -> OpenAiCompatibleClient.analyzeText(
            NVIDIA_BASE, PreferencesManager.getNvidiaApiKey(context), NVIDIA_MODEL, "NVIDIA", prompt
        )
        else -> IntentSchema.markError("Bilinmeyen sağlayıcı.")
    }

    // ---------- Vision (screen understanding, image file analysis) ----------

    suspend fun analyzeVision(context: Context, prompt: String, imageBase64: String): String {
        val order = visionTierOrder.filter { hasKey(context, it) }
        if (order.isEmpty())
            return "Görsel analiz için yapılandırılmış bir sağlayıcı yok — Ayarlar'dan Gemini, OpenRouter ya da GitHub Models anahtarı ekleyebilirsin."
        for (provider in order) {
            val attempt = callAnalyzeImage(context, provider, prompt, imageBase64)
            if (!IntentSchema.isErrorReply(attempt)) return attempt
        }
        return "Şu an hiçbir görsel-destekli sağlayıcıya ulaşamadım, biraz sonra tekrar dener misin?"
    }

    private suspend fun callAnalyzeImage(context: Context, provider: String, prompt: String, imageBase64: String): String = when (provider) {
        "gemini" -> GeminiClient.analyzeImage(context, prompt, imageBase64)
        "openrouter" -> OpenAiCompatibleClient.analyzeImage(
            OPENROUTER_BASE, PreferencesManager.getOpenRouterApiKey(context), OPENROUTER_MODEL, "OpenRouter", prompt, imageBase64
        )
        "github" -> OpenAiCompatibleClient.analyzeImage(
            GITHUB_BASE, PreferencesManager.getGithubModelsApiKey(context), GITHUB_MODEL, "GitHub Models", prompt, imageBase64
        )
        else -> IntentSchema.markError("Bilinmeyen sağlayıcı.")
    }

    fun resetConversation(context: Context) {
        ConversationStore.clear(context)
    }

    /** Read-only copy of the current speaker's recent conversation turns, for exporting the chat. */
    fun getHistorySnapshot(context: Context): List<Pair<String, String>> =
        loadHistory(context, SpeakerContext.currentId(context))
}
