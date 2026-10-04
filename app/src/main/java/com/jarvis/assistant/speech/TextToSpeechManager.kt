package com.jarvis.assistant.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

/**
 * Wraps Android's built-in TextToSpeech engine and automatically picks the most
 * natural-sounding available voice (prefers network/"quality" voices, which on most
 * devices with Google's TTS engine installed are the WaveNet-quality Google voices —
 * completely free, no API key needed, just requires the "Google Text-to-Speech"
 * engine + voice data to be installed on the device, which is on by default).
 */
class TextToSpeechManager(
    context: Context,
    private val onStart: () -> Unit = {},
    private val onDone: () -> Unit = {}
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("tr", "TR")
                pickBestVoice()
                ready = true
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onStart()
            override fun onDone(utteranceId: String?) = onDone()
            override fun onError(utteranceId: String?) = onDone()
        })
    }

    private fun pickBestVoice() {
        val engine = tts ?: return
        val voices = engine.voices ?: return
        // Prefer: not requiring the network-only flag issue, high quality, Turkish locale.
        val best = voices
            .filter { it.locale.language == "tr" }
            .filterNot { it.isNetworkConnectionRequired && it.quality < Voice.QUALITY_HIGH }
            .maxByOrNull { it.quality }
        if (best != null) {
            engine.voice = best
        }
    }

    fun speak(text: String) {
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
