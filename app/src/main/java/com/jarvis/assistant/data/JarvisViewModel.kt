package com.jarvis.assistant.data

import android.app.Application
import android.content.Intent
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.commands.AlarmController
import com.jarvis.assistant.commands.CommandProcessor
import com.jarvis.assistant.commands.FlashlightController
import com.jarvis.assistant.commands.NotesController
import com.jarvis.assistant.commands.PhoneController
import com.jarvis.assistant.commands.ReminderController
import com.jarvis.assistant.commands.SearchController
import com.jarvis.assistant.commands.SystemController
import com.jarvis.assistant.commands.WhatsAppController
import com.jarvis.assistant.service.JarvisBackgroundService
import com.jarvis.assistant.speech.SpeechRecognizerManager
import com.jarvis.assistant.speech.TextToSpeechManager
import com.jarvis.assistant.ui.components.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class JarvisUiState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val amplitude: Float = 0f,
    val transcript: String = "",
    val response: String = "Hazırım. Konuşmak için mikrofona bas ya da arka plan dinlemeyi aç.",
    val history: List<Pair<String, String>> = emptyList(), // (user, jarvis) pairs
    val backgroundListeningEnabled: Boolean = false,
    val speakTypedReplies: Boolean = false // typed messages get a spoken reply only if this is on
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState

    private val phoneController = PhoneController(application)
    private val alarmController = AlarmController(application)
    private val commandProcessor = CommandProcessor(
        context = application,
        phoneController = phoneController,
        alarmController = alarmController,
        flashlightController = FlashlightController(application),
        systemController = SystemController(application),
        searchController = SearchController(application),
        notesController = NotesController(application),
        reminderController = ReminderController(application),
        whatsAppController = WhatsAppController(application),
        getLastKnownLocation = { getLastKnownLocation() }
    )

    private var speechRecognizer: SpeechRecognizerManager? = null
    private val tts = TextToSpeechManager(
        application,
        onStart = { _uiState.value = _uiState.value.copy(voiceState = VoiceState.SPEAKING) },
        onDone = { _uiState.value = _uiState.value.copy(voiceState = VoiceState.IDLE) }
    )

    fun onMicButtonPressed() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizerManager(
                context = getApplication(),
                onResult = { text -> handleRecognizedSpeech(text) },
                onAmplitude = { amp -> _uiState.value = _uiState.value.copy(amplitude = amp) },
                onError = { message ->
                    _uiState.value = _uiState.value.copy(voiceState = VoiceState.IDLE, response = message)
                },
                onListeningStateChanged = { isListening ->
                    _uiState.value = _uiState.value.copy(
                        voiceState = if (isListening) VoiceState.LISTENING else VoiceState.IDLE
                    )
                }
            )
        }
        _uiState.value = _uiState.value.copy(voiceState = VoiceState.LISTENING, transcript = "")
        speechRecognizer?.startListening()
    }

    /** Message typed in the text box: runs through the exact same pipeline as spoken commands. */
    fun onTextSubmitted(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _uiState.value.voiceState == VoiceState.THINKING) return
        _uiState.value = _uiState.value.copy(transcript = t, voiceState = VoiceState.THINKING)
        viewModelScope.launch {
            val reply = commandProcessor.process(t)
            _uiState.value = _uiState.value.copy(
                response = reply,
                history = _uiState.value.history + (t to reply),
                voiceState = VoiceState.IDLE
            )
            if (_uiState.value.speakTypedReplies) tts.speak(reply)
        }
    }

    fun toggleSpeakTypedReplies() {
        _uiState.value = _uiState.value.copy(speakTypedReplies = !_uiState.value.speakTypedReplies)
    }

    /** Turns the always-on "say Jarvis to wake" background service on/off. */
    fun toggleBackgroundListening(enabled: Boolean) {
        val app = getApplication<Application>()
        val intent = Intent(app, JarvisBackgroundService::class.java)
        if (enabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } else {
            intent.action = JarvisBackgroundService.ACTION_STOP
            app.startService(intent)
        }
        _uiState.value = _uiState.value.copy(backgroundListeningEnabled = enabled)
    }

    private fun handleRecognizedSpeech(text: String) {
        _uiState.value = _uiState.value.copy(transcript = text, voiceState = VoiceState.THINKING)
        viewModelScope.launch {
            val reply = commandProcessor.process(text)
            _uiState.value = _uiState.value.copy(
                response = reply,
                history = _uiState.value.history + (text to reply)
            )
            tts.speak(reply)
        }
    }

    /** Called right after the user picks a file from Settings/Home — analyzes it immediately. */
    fun onFileSelected(uri: android.net.Uri, fileName: String) {
        commandProcessor.lastPickedFileUri = uri
        val label = "\"$fileName\" dosyasını analiz et"
        _uiState.value = _uiState.value.copy(transcript = label, voiceState = VoiceState.THINKING)
        viewModelScope.launch {
            val reply = commandProcessor.process(label)
            _uiState.value = _uiState.value.copy(
                response = reply,
                history = _uiState.value.history + (label to reply),
                voiceState = VoiceState.IDLE
            )
            tts.speak(reply)
        }
    }

    private fun getLastKnownLocation(): Pair<Double, Double>? {
        return try {
            val app = getApplication<Application>()
            val locationManager = app.getSystemService(LocationManager::class.java)
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null) return location.latitude to location.longitude
            }
            null
        } catch (e: SecurityException) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        tts.shutdown()
    }
}
