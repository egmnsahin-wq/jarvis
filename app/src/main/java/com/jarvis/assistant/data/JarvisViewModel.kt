package com.jarvis.assistant.data

import android.app.Application
import android.content.Intent
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.ai.AssistantAi
import com.jarvis.assistant.commands.AlarmController
import com.jarvis.assistant.commands.CommandProcessor
import com.jarvis.assistant.commands.FlashlightController
import com.jarvis.assistant.commands.IncomingMessage
import com.jarvis.assistant.commands.IncomingMessageDialog
import com.jarvis.assistant.commands.IncomingMessageHub
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
    val backgroundListeningEnabled: Boolean = false
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

    /** Gelen WhatsApp mesajı için "okuyayım mı / cevap yazayım mı" diyaloğu. */
    private val messageDialog = IncomingMessageDialog(application)
    private val appMessageHost: (IncomingMessage) -> Unit = { msg -> onIncomingMessage(msg) }

    private var speechRecognizer: SpeechRecognizerManager? = null
    private val tts = TextToSpeechManager(
        application,
        onStart = { _uiState.value = _uiState.value.copy(voiceState = VoiceState.SPEAKING) },
        onDone = {
            _uiState.value = _uiState.value.copy(voiceState = VoiceState.IDLE)
            // WhatsApp diyaloğunda soru sorulduysa cevabı duymak için mikrofonu kendiliğinden aç.
            if (messageDialog.awaitingAnswer) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onMicButtonPressed() }, 350)
            }
        }
    )

    /** Haber paneli (başlık gezme, detay, arama, el hareketi) durumu. */
    val news = NewsSession(viewModelScope) { text -> tts.speak(text) }

    init {
        commandProcessor.newsHandler = { text -> news.handleVoice(text) }
        commandProcessor.newsOpener = { query -> news.open(query) }
        commandProcessor.dialogHandler = { text -> messageDialog.handle(text) }
        IncomingMessageHub.appHost = appMessageHost
        if (!AssistantAi.hasAnyKey(getApplication())) {
            _uiState.value = _uiState.value.copy(
                response = "Merhaba! Başlamak için sağ üstteki dişli simgesinden ücretsiz bir Gemini anahtarı eklemen yeterli, orada nasıl alacağını adım adım yazdım."
            )
        }
    }

    private fun onIncomingMessage(msg: IncomingMessage) {
        if (!PreferencesManager.isWhatsAppAssistantEnabled(getApplication())) return
        val question = messageDialog.onIncoming(msg) ?: return
        _uiState.value = _uiState.value.copy(response = question)
        tts.speak(question)
    }

    fun onMicButtonPressed() {
        if (_uiState.value.backgroundListeningEnabled) {
            // İki ses tanıyıcı aynı anda mikrofonu kullanamaz ("cannot record now as Jarvis is recording").
            toggleBackgroundListening(false)
            _uiState.value = _uiState.value.copy(
                response = "Mikrofonu kullanabilmek için arka plan dinlemeyi kapattım."
            )
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizerManager(
                context = getApplication(),
                onResult = { text -> handleRecognizedSpeech(text) },
                onAmplitude = { amp -> _uiState.value = _uiState.value.copy(amplitude = amp) },
                onError = { message ->
                    if (messageDialog.awaitingAnswer) messageDialog.cancel()
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

    /** Turns the always-on "say Jarvis to wake" background service on/off. */
    fun toggleBackgroundListening(enabled: Boolean) {
        val app = getApplication<Application>()
        val intent = Intent(app, JarvisBackgroundService::class.java)
        if (enabled) {
            // Mikrofon izni yokken mikrofon türünde ön plan servisi başlatmak Android 11+ ta hata verir
            // (bazı telefonlarda uygulamayı çökertir). Önce izni iste, anahtarı açık bırakma.
            if (androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                _uiState.value = _uiState.value.copy(
                    backgroundListeningEnabled = false,
                    response = "Arka plan dinleme için mikrofon izni gerekiyor. İzni verdikten sonra anahtarı tekrar aç."
                )
                return
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    backgroundListeningEnabled = false,
                    response = "Arka plan dinleme başlatılamadı: ${e.message}"
                )
                return
            }
        } else {
            intent.action = JarvisBackgroundService.ACTION_STOP
            app.startService(intent)
        }
        _uiState.value = _uiState.value.copy(backgroundListeningEnabled = enabled)
    }

    /** Typed command from the text input field — goes through the exact same pipeline as voice. */
    fun onTextSubmitted(text: String) {
        if (text.isBlank()) return
        handleRecognizedSpeech(text.trim())
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
        if (IncomingMessageHub.appHost === appMessageHost) IncomingMessageHub.appHost = null
        speechRecognizer?.destroy()
        tts.shutdown()
    }
}
