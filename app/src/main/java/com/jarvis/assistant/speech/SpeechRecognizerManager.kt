package com.jarvis.assistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.assistant.data.PeopleStore
import com.jarvis.assistant.speaker.SpeakerIdController
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Wraps Android's built-in (free) SpeechRecognizer for push-to-talk voice input.
 *
 * If at least one person has a voice enrolled (Settings > Kişiler), a parallel mic capture runs
 * during each listening session so the speaker can be identified before the recognized text is
 * handed on — [identifyFilter] lets a caller skip identification for utterances that aren't
 * commands (e.g. background chatter heard while waiting for the wake word).
 */
class SpeechRecognizerManager(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onAmplitude: (Float) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onListeningStateChanged: (Boolean) -> Unit = {},
    private val identifyFilter: ((String) -> Boolean)? = null
) {
    private val appContext = context.applicationContext
    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val identifyExecutor = Executors.newSingleThreadExecutor()

    // True while a parallel capture started for the current listening session.
    @Volatile private var capturingSpeaker = false
    // Some devices give the recognizer exclusive mic access; if a capture ever coincides with an
    // audio error we stop trying (voice commands keep working, just without speaker detection).
    @Volatile private var captureBroken = false

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                // Normalize roughly to 0..1 for the UI animation.
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                onAmplitude(normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onListeningStateChanged(false)
            }

            override fun onError(error: Int) {
                onListeningStateChanged(false)
                if (capturingSpeaker) {
                    SpeakerIdController.cancelCapture()
                    capturingSpeaker = false
                    if (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        captureBroken = true
                    }
                }
                onError(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                onListeningStateChanged(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()

                val pcm = if (capturingSpeaker) SpeakerIdController.stopCapture() else null
                capturingSpeaker = false

                if (text.isBlank()) return
                if (pcm != null && (identifyFilter?.invoke(text) != false)) {
                    // Parmak izi hesabı ana thread'i yormasın diye arka planda; bitince metin iletilir.
                    identifyExecutor.execute {
                        try {
                            SpeakerIdController.identifyAndApply(appContext, pcm)
                        } catch (e: Exception) {
                            // tanıma başarısız olsa da komut yine çalışmalı
                        }
                        mainHandler.post { onResult(text) }
                    }
                } else {
                    onResult(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        if (!captureBroken && PeopleStore.getForcedSpeakerId(appContext) == null &&
            PeopleStore.hasEnrolledPeople(appContext)
        ) {
            capturingSpeaker = SpeakerIdController.startCapture(appContext)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("tr", "TR"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer.stopListening()
    }

    fun destroy() {
        if (capturingSpeaker) SpeakerIdController.cancelCapture()
        capturingSpeaker = false
        identifyExecutor.shutdown()
        recognizer.destroy()
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Seni anlayamadım, tekrar eder misin?"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses algılanamadı."
        SpeechRecognizer.ERROR_NETWORK -> "Ağ hatası."
        SpeechRecognizer.ERROR_AUDIO -> "Ses kaydı hatası."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekli."
        else -> "Ses tanıma hatası ($error)"
    }
}
