package com.jarvis.assistant.commands

import android.content.Context
import com.jarvis.assistant.ai.AssistantAi
import com.jarvis.assistant.ai.JarvisIntent
import com.jarvis.assistant.data.PreferencesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Two-layer intent router:
 * 1) Fast keyword rules for the most common exact phrasings (instant, no network call).
 * 2) If nothing matches, the raw sentence is sent to Gemini's classify() which understands
 *    natural, loosely-phrased Turkish ("su içmem lazım hatırlatmayı unutma") and returns a
 *    structured intent that gets dispatched to the same controllers.
 */
class CommandProcessor(
    private val context: Context,
    private val phoneController: PhoneController,
    private val alarmController: AlarmController,
    private val flashlightController: FlashlightController,
    private val systemController: SystemController,
    private val searchController: SearchController,
    private val notesController: NotesController,
    private val reminderController: ReminderController,
    private val whatsAppController: WhatsAppController,
    private val getLastKnownLocation: () -> Pair<Double, Double>?
) {
    private val callLogController by lazy { CallLogController(context) }

    /** Set by the UI right after the user picks a file — lets voice commands like
     * "bu dosyayı analiz et" refer to "the file I just picked" without needing a filename. */
    var lastPickedFileUri: android.net.Uri? = null

    suspend fun process(rawText: String): String {
        return try {
            processInternal(rawText)
        } catch (e: Exception) {
            "Komutu işlerken bir hata oldu: ${e.message ?: e::class.simpleName}"
        }
    }

    private suspend fun processInternal(rawText: String): String {
        val text = rawText.trim().lowercase(Locale("tr"))

        // A user-defined routine's trigger phrase always wins first — most specific match.
        val matchingRoutines = RoutineController.findMatchingRoutines(context, text)
        if (matchingRoutines.isNotEmpty()) {
            val results = mutableListOf<String>()
            for (routine in matchingRoutines) {
                results.add(RoutineController.execute(context, routine, phoneController, searchController))
            }
            return results.joinToString(" ")
        }

        // If the sentence names one of the registered remote phones, skip the fast keyword
        // rules entirely (they only know about THIS device) and let Gemini's REMOTE_* intent
        // classification handle it — natural phrasing here varies too much for safe regex.
        val remotePhoneNames = PreferencesManager.getRemotePhones(context).map { it.name.lowercase(Locale("tr")) }
        if (remotePhoneNames.isNotEmpty() && remotePhoneNames.any { text.contains(it) }) {
            return dispatchAiIntent(rawText)
        }

        return when {
            // --- Time ---
            text.contains("saat kaç") || text.contains("saati söyle") -> {
                val sdf = SimpleDateFormat("HH:mm", Locale("tr"))
                "Şu anda saat ${sdf.format(Calendar.getInstance().time)}."
            }

            // --- Weather ---
            text.contains("hava durumu") || text.contains("hava nasıl") -> {
                val loc = getLastKnownLocation()
                if (loc == null) "Konum alınamadı, konum izni verildiğinden emin ol."
                else WeatherService.getCurrentWeather(loc.first, loc.second)
            }

            // --- Flashlight ---
            text.contains("feneri aç") || text.contains("fenerı aç") -> {
                if (flashlightController.turnOn()) "Fener açıldı." else "Fener açılamadı, bu cihazda flaş olmayabilir."
            }
            text.contains("feneri kapat") || text.contains("fenerı kapat") -> {
                if (flashlightController.turnOff()) "Fener kapatıldı." else "Fener kapatılamadı."
            }

            // --- Volume ---
            text.contains("sesi aç") || text.contains("sesi artır") || text.contains("ses seviyesini artır") ->
                systemController.volumeUp()
            text.contains("sesi kıs") || text.contains("sesi azalt") || text.contains("ses seviyesini azalt") ->
                systemController.volumeDown()
            text.contains("sessize al") || text.contains("sessiz moda geç") ->
                systemController.setSilent(true)
            text.contains("sesli moda geç") || text.contains("sessiz modu kapat") ->
                systemController.setSilent(false)

            // --- Brightness ---
            text.contains("parlaklığı artır") || text.contains("ekranı parlat") ->
                systemController.adjustBrightness(increase = true)
            text.contains("parlaklığı azalt") || text.contains("ekranı karart") ->
                systemController.adjustBrightness(increase = false)

            // --- Battery ---
            text.contains("pil durumu") || text.contains("pil yüzde") || text.contains("şarj durumu") ->
                systemController.batteryStatus()

            // --- Search ---
            text.contains("youtube'da") && text.contains("ara") -> {
                val query = extractSearchQuery(text, "youtube'da", "ara")
                searchController.searchYoutube(query)
            }
            text.contains("google'da") && text.contains("ara") -> {
                val query = extractSearchQuery(text, "google'da", "ara")
                searchController.searchGoogle(query)
            }

            // --- Notes ---
            text.startsWith("not al") || text.startsWith("not ekle") -> {
                val note = rawText.substringAfter(":", "").ifBlank {
                    rawText.replace(Regex("(?i)not al|not ekle"), "").trim()
                }
                if (note.isBlank()) "Ne not almamı istersin?" else notesController.addNote(note.trim())
            }
            text.contains("notlarımı oku") || text.contains("notları oku") || text.contains("notlarım ne") ->
                notesController.readNotes()
            text.contains("notlarımı sil") || text.contains("notları temizle") ->
                notesController.clearNotes()

            // --- Reminders: "1 saat sonra hatırlat: su iç" / "20 dakika sonra hatırlat: ..." ---
            text.contains("sonra hatırlat") -> {
                val (delayMillis, humanReadable) = parseReminderDelay(text)
                val message = rawText.substringAfter(":", "").trim().ifBlank { "Hatırlatıcı" }
                if (delayMillis != null) {
                    reminderController.scheduleReminder(message, delayMillis, humanReadable ?: "Biraz")
                } else {
                    "Ne kadar süre sonra olduğunu anlayamadım, örneğin \"20 dakika sonra hatırlat: su iç\" diyebilirsin."
                }
            }

            // --- Alarm: "7 30 da alarm kur" ---
            text.contains("alarm kur") -> {
                val time = extractTime(text)
                if (time != null) {
                    val ok = alarmController.setAlarm(time.first, time.second)
                    if (ok) "Alarm ${time.first}:${time.second.toString().padStart(2, '0')} için kuruldu."
                    else "Alarm kuramadım."
                } else {
                    "Saat kaçta olduğunu anlayamadım, örneğin \"7 30'da alarm kur\" diyebilirsin."
                }
            }
            text.contains("alarmları göster") || text.contains("alarmları listele") -> {
                if (alarmController.showAlarms()) "Alarmları açıyorum."
                else "Cihazda alarmları gösterecek bir Saat uygulaması bulamadım."
            }

            // --- Timer ---
            text.contains("sayaç kur") || text.contains("zamanlayıcı kur") -> {
                val minutes = Regex("(\\d+)\\s*dakika").find(text)?.groupValues?.get(1)?.toIntOrNull()
                if (minutes != null) {
                    if (alarmController.setTimer(minutes * 60)) "$minutes dakikalık sayaç kuruldu."
                    else "Sayaç kuramadım."
                } else "Kaç dakikalık olduğunu anlayamadım."
            }

            // --- TV: "televizyonu kapat" / sesle ilgili tek kelimelik komutlar (uygulama adı gerektirmeyenler) ---
            text.contains("televizyonu kapat") || text.contains("tv'yi kapat") || text.contains("tv yi kapat") ->
                TvController.powerOff(context)
            text.contains("televizyonun sesini aç") || text.contains("tv sesini aç") || text.contains("tv'nin sesini artır") ->
                TvController.volumeUp(context)
            text.contains("televizyonun sesini kıs") || text.contains("tv sesini kıs") || text.contains("tv'nin sesini azalt") ->
                TvController.volumeDown(context)
            text.contains("televizyonu sessize al") || text.contains("tv'yi sessize al") ->
                TvController.mute(context)
            text.contains("televizyonun sesini geri aç") || text.contains("tv'nin sesini geri aç") ->
                TvController.unmute(context)

            // --- Fallback: let the AI understand the free-form sentence and dispatch it.
            // (Calling someone, sending SMS/WhatsApp, opening an app or a TV app, and smart-home
            // on/off all live here now — anything with a free-text name/message benefits far more
            // from real language understanding than from rigid keyword+colon parsing.) ---
            else -> dispatchAiIntent(rawText)
        }
    }

    /** Sends the sentence to Gemini for natural-language intent understanding, then executes it. */
    private suspend fun dispatchAiIntent(rawText: String): String {
        val result = AssistantAi.classify(context, rawText)
        return when (result.intent) {
            "TIME" -> {
                val sdf = SimpleDateFormat("HH:mm", Locale("tr"))
                "Şu anda saat ${sdf.format(Calendar.getInstance().time)}."
            }
            "WEATHER" -> {
                val loc = getLastKnownLocation()
                if (loc == null) "Konum alınamadı." else WeatherService.getCurrentWeather(loc.first, loc.second)
            }
            "FLASHLIGHT_ON" -> if (flashlightController.turnOn()) result.reply else "Fener açılamadı."
            "FLASHLIGHT_OFF" -> if (flashlightController.turnOff()) result.reply else "Fener kapatılamadı."
            "VOLUME_UP" -> systemController.volumeUp()
            "VOLUME_DOWN" -> systemController.volumeDown()
            "SILENT_ON" -> systemController.setSilent(true)
            "SILENT_OFF" -> systemController.setSilent(false)
            "BRIGHTNESS_UP" -> systemController.adjustBrightness(true)
            "BRIGHTNESS_DOWN" -> systemController.adjustBrightness(false)
            "BATTERY" -> systemController.batteryStatus()
            "SEARCH_GOOGLE" -> result.query?.let { searchController.searchGoogle(it) } ?: result.reply
            "SEARCH_YOUTUBE" -> result.query?.let { searchController.searchYoutube(it) } ?: result.reply
            "PLAY_SONG" -> {
                val songQuery = result.song ?: result.query
                if (songQuery != null) {
                    if (rawText.lowercase(Locale("tr")).contains("youtube"))
                        SongPlaybackController.playOnYoutube(context, songQuery, searchController)
                    else
                        SongPlaybackController.playOnSpotify(context, songQuery, searchController)
                } else result.reply
            }
            "NOTE_ADD" -> result.note?.let { notesController.addNote(it) } ?: result.reply
            "NOTES_READ" -> notesController.readNotes()
            "NOTES_CLEAR" -> notesController.clearNotes()
            "REMINDER" -> {
                val minutes = result.minutes?.takeIf { it > 0 } ?: 30
                val message = result.message ?: result.note ?: "Hatırlatıcı"
                reminderController.scheduleReminder(message, minutes * 60_000L, "$minutes dakika")
            }
            "ALARM" -> {
                val h = result.hour
                val m = result.minute ?: 0
                if (h != null) {
                    if (alarmController.setAlarm(h, m)) "Alarm $h:${m.toString().padStart(2, '0')} için kuruldu."
                    else "Alarm kuramadım."
                } else result.reply
            }
            "TIMER" -> {
                val minutes = result.minutes
                if (minutes != null) {
                    if (alarmController.setTimer(minutes * 60)) "$minutes dakikalık sayaç kuruldu."
                    else "Sayaç kuramadım."
                } else result.reply
            }
            "CALL" -> {
                val name = result.contact?.let { resolveAlias(it) }
                if (name != null) {
                    val number = phoneController.findContactNumber(name)
                    if (number != null) {
                        phoneController.callNumber(number)
                        result.reply
                    } else "$name adında bir kişi bulamadım."
                } else result.reply
            }
            "SMS" -> {
                val name = result.contact?.let { resolveAlias(it) }
                val msg = result.message
                if (name != null && msg != null) {
                    val number = phoneController.findContactNumber(name)
                    if (number != null) {
                        phoneController.sendSms(number, msg)
                        result.reply
                    } else "$name adında bir kişi bulamadım."
                } else result.reply
            }
            "WHATSAPP" -> {
                val name = result.contact?.let { resolveAlias(it) }
                val msg = result.message
                if (name != null && msg != null) {
                    val number = phoneController.findContactNumber(name)
                    if (number != null) whatsAppController.sendMessage(number, msg)
                    else "$name adında bir kişi bulamadım."
                } else result.reply
            }
            "OPEN_APP" -> {
                val app = result.app
                if (app != null) {
                    if (phoneController.openApp(app)) result.reply else "\"$app\" uygulamasını bulamadım."
                } else result.reply
            }
            "SMART_HOME_ON" -> result.device?.let { SmartHomeController.turnOn(it) } ?: result.reply
            "SMART_HOME_OFF" -> result.device?.let { SmartHomeController.turnOff(it) } ?: result.reply
            "TV_OPEN_APP" -> result.tvApp?.let { TvController.openApp(context, it) } ?: result.reply
            "TV_OPEN_URL" -> result.tvUrl?.let { TvController.openUrl(context, it) } ?: result.reply
            "TV_POWER_OFF" -> TvController.powerOff(context)
            "TV_VOLUME_UP" -> TvController.volumeUp(context)
            "TV_VOLUME_DOWN" -> TvController.volumeDown(context)
            "TV_SET_VOLUME" -> result.tvVolume?.let { TvController.setVolume(context, it) } ?: result.reply
            "TV_MUTE" -> TvController.mute(context)
            "TV_UNMUTE" -> TvController.unmute(context)
            "REMOTE_PLAY_SONG" -> remoteDispatch(result) { phone -> RemotePhoneController.playSong(context, phone, result.song ?: result.query ?: "") }
            "REMOTE_SEARCH_GOOGLE" -> remoteDispatch(result) { phone -> RemotePhoneController.searchGoogle(context, phone, result.query ?: "") }
            "REMOTE_SEARCH_YOUTUBE" -> remoteDispatch(result) { phone -> RemotePhoneController.searchYoutube(context, phone, result.query ?: "") }
            "REMOTE_SET_ALARM" -> remoteDispatch(result) { phone -> RemotePhoneController.setAlarm(context, phone, result.hour ?: 0, result.minute ?: 0) }
            "REMOTE_REMINDER" -> remoteDispatch(result) { phone -> RemotePhoneController.reminder(context, phone, result.message ?: "Hatırlatıcı", result.minutes ?: 30) }
            "REMOTE_CALL" -> remoteDispatch(result) { phone -> result.contact?.let { RemotePhoneController.call(context, phone, it) } ?: result.reply }
            "REMOTE_SMS" -> remoteDispatch(result) { phone -> RemotePhoneController.sms(context, phone, result.contact ?: "", result.message ?: "") }
            "REMOTE_WHATSAPP" -> remoteDispatch(result) { phone -> RemotePhoneController.whatsapp(context, phone, result.contact ?: "", result.message ?: "") }
            "REMOTE_OPEN_APP" -> remoteDispatch(result) { phone -> RemotePhoneController.openApp(context, phone, result.app ?: "") }
            "REMOTE_FLASHLIGHT_ON" -> remoteDispatch(result) { phone -> RemotePhoneController.flashlightOn(context, phone) }
            "REMOTE_FLASHLIGHT_OFF" -> remoteDispatch(result) { phone -> RemotePhoneController.flashlightOff(context, phone) }
            "REMOTE_BATTERY" -> remoteDispatch(result) { phone -> RemotePhoneController.batteryStatus(context, phone) }
            "REMOTE_FIND_PHONE" -> remoteDispatch(result) { phone -> RemotePhoneController.findPhone(context, phone) }
            "REMOTE_NOTIFY" -> remoteDispatch(result) { phone -> RemotePhoneController.notify(context, phone, result.message ?: "") }
            "MEMORY_SAVE" -> {
                val fact = result.note ?: result.message
                if (fact != null) {
                    PreferencesManager.addMemory(context, fact)
                    result.reply
                } else "Ne hatırlamamı istediğini anlayamadım."
            }
            "MEMORY_READ" -> {
                val memories = PreferencesManager.getMemories(context)
                if (memories.isEmpty()) "Şu an hakkında hatırladığım bir şey yok."
                else "Şunları hatırlıyorum: " + memories.joinToString(", ")
            }
            "MEMORY_CLEAR" -> {
                PreferencesManager.clearMemories(context)
                "Hatırladığım her şeyi sildim."
            }
            "BRIGHTNESS_SET" -> result.brightnessPercent?.let { systemController.setBrightnessPercent(it) } ?: result.reply
            "WIFI_SETTINGS" -> systemController.openWifiSettings()
            "BLUETOOTH_SETTINGS" -> systemController.openBluetoothSettings()
            "FIND_MY_PHONE" -> systemController.ringToFind()
            "ALARM_LIST" -> if (alarmController.showAlarms()) "Alarmları açıyorum."
                            else "Cihazda alarmları gösterecek bir Saat uygulaması bulamadım."
            "MISSED_CALLS" -> callLogController.missedCallsToday()
            "REMOTE_BATTERY_ALL" -> {
                val phones = PreferencesManager.getRemotePhones(context)
                if (phones.isEmpty()) "Kayıtlı başka telefon yok."
                else {
                    val results = mutableListOf<String>()
                    for (p in phones) results.add(RemotePhoneController.batteryStatus(context, p.name))
                    results.joinToString(" ")
                }
            }
            "TODO_ADD" -> {
                val item = result.note ?: result.message
                if (item != null) { PreferencesManager.addTodo(context, item); result.reply }
                else "Ne eklememi istediğini anlayamadım."
            }
            "TODO_DONE" -> {
                val query = result.note ?: result.query ?: result.message
                val done = query?.let { PreferencesManager.completeTodo(context, it) }
                if (done != null) "\"$done\" işaretlendi, tebrikler!" else "Bu işi listemde bulamadım."
            }
            "TODO_LIST" -> {
                val todos = PreferencesManager.getTodos(context).filterNot { it.done }
                if (todos.isEmpty()) "Yapılacak listende bir şey yok."
                else "Yapılacaklar: " + todos.joinToString(", ") { it.text }
            }
            "TODO_CLEAR_DONE" -> {
                PreferencesManager.clearCompletedTodos(context)
                "Tamamlananları listeden sildim."
            }
            "ALIAS_SET" -> {
                val alias = result.aliasName
                val target = result.aliasTarget
                if (alias != null && target != null) {
                    PreferencesManager.setContactAlias(context, alias, target)
                    "Tamam, \"$alias\" dediğimde artık $target'i anlayacağım."
                } else "Takma adı ve kimin için olduğunu tam anlayamadım."
            }
            "TEMPLATE_SET" -> {
                val name = result.templateName
                val msg = result.message
                if (name != null && msg != null) {
                    PreferencesManager.setQuickReply(context, name, msg)
                    "\"$name\" şablonunu kaydettim."
                } else "Şablon adını ve metnini tam anlayamadım."
            }
            "TEMPLATE_SEND" -> {
                val name = result.contact?.let { resolveAlias(it) }
                val templateName = result.templateName
                val template = templateName?.let { PreferencesManager.getQuickReplies(context)[it.trim().lowercase(Locale("tr"))] }
                if (name == null || template == null) "Kişiyi ya da şablonu bulamadım."
                else {
                    val number = phoneController.findContactNumber(name)
                    if (number != null) whatsAppController.sendMessage(number, template)
                    else "$name adında bir kişi bulamadım."
                }
            }
            "CALCULATE" -> result.query?.let { CalculatorController.evaluate(it) } ?: result.reply
            "CURRENCY" -> result.query?.let { CurrencyController.lookup(it) } ?: result.reply
            "NEWS" -> NewsController.headlines(result.query)
            "DAILY_SUMMARY" -> buildDailySummary()
            "CONVERSATION_EXPORT" -> HistoryExportController.export(context, AssistantAi.getHistorySnapshot())
            "HOME_WIFI_SET" -> {
                val ssid = result.wifiSsid
                if (ssid != null) { PreferencesManager.setHomeWifiSsid(context, ssid); "Ev Wi-Fi ağını \"$ssid\" olarak kaydettim." }
                else "Ağ adını anlayamadım."
            }
            "ARRIVAL_REMINDER" -> {
                val msg = result.message
                val homeSsid = PreferencesManager.getHomeWifiSsid(context)
                when {
                    msg == null -> "Ne hatırlatmamı istediğini anlayamadım."
                    homeSsid.isBlank() -> "Önce ev Wi-Fi ağını ayarlamam lazım, \"ev wifi ağımı [ağ adı] olarak ayarla\" diyebilirsin."
                    else -> { PreferencesManager.addArrivalReminder(context, msg); "Eve varınca hatırlatacağım." }
                }
            }
            "PC_OPEN_APP" -> result.pcApp?.let { PcController.openApp(context, it) } ?: result.reply
            "PC_OPEN_URL" -> result.pcUrl?.let { PcController.openUrl(context, it) } ?: result.reply
            "PC_SHUTDOWN" -> PcController.shutdown(context)
            "PC_RESTART" -> PcController.restart(context)
            "PC_SLEEP" -> PcController.sleep(context)
            "PC_LOCK" -> PcController.lock(context)
            "PC_VOLUME_UP" -> PcController.volumeUp(context)
            "PC_VOLUME_DOWN" -> PcController.volumeDown(context)
            "PC_MUTE" -> PcController.mute(context)
            "PC_MEDIA_PLAY_PAUSE" -> PcController.mediaPlayPause(context)
            "PC_MEDIA_NEXT" -> PcController.mediaNext(context)
            "PC_MEDIA_PREV" -> PcController.mediaPrev(context)
            "FILE_ANALYZE" -> {
                val uri = lastPickedFileUri
                if (uri == null) "Önce analiz etmemi istediğin dosyayı seçmen lazım."
                else FileAnalysisController.analyzeFile(context, uri, result.query)
            }
            "FILE_ANALYZE_TO_FILE" -> {
                val uri = lastPickedFileUri
                if (uri == null) "Önce analiz etmemi istediğin dosyayı seçmen lazım."
                else FileAnalysisController.analyzeFileToNewFile(context, uri)
            }
            "CREATE_FILE" -> {
                val topic = result.note ?: result.message ?: result.query
                if (topic == null) "Ne hakkında bir dosya oluşturmamı istediğini anlayamadım."
                else FileAnalysisController.createFile(context, topic, null)
            }
            "SCREEN_ASK" -> ScreenUnderstandingController.ask(context, result.query)
            else -> result.reply // CHAT or unrecognized
        }
    }

    /** Runs [action] against result.targetPhone, or falls back to the AI's reply if no phone name was given. */
    private suspend fun remoteDispatch(result: JarvisIntent, action: suspend (String) -> String): String {
        val phone = result.targetPhone
        return if (phone.isNullOrBlank()) result.reply else action(phone)
    }

    /** "karım" -> "Ayşe" if that alias was configured; otherwise returns the name unchanged. */
    private fun resolveAlias(name: String): String {
        val aliases = PreferencesManager.getContactAliases(context)
        return aliases[name.trim().lowercase(Locale("tr"))] ?: name
    }

    private suspend fun buildDailySummary(): String {
        val parts = mutableListOf<String>()
        val loc = getLastKnownLocation()
        if (loc != null) {
            val weather = WeatherService.getCurrentWeather(loc.first, loc.second)
            parts.add(weather)
        }
        val todos = PreferencesManager.getTodos(context).filterNot { it.done }
        parts.add(
            if (todos.isEmpty()) "Yapılacak listende bir şey yok."
            else "Yapılacaklar: " + todos.joinToString(", ") { it.text }
        )
        return parts.joinToString(" ")
    }

    private fun extractSearchQuery(text: String, siteWord: String, actionWord: String): String {
        return text.substringAfter(siteWord).substringBefore(actionWord).trim()
    }

    /** Extracts "7 30" / "07:30" / "saat 7" style times from Turkish speech text. */
    private fun extractTime(text: String): Pair<Int, Int>? {
        val hm = Regex("(\\d{1,2})[:.\\s](\\d{2})").find(text)
        if (hm != null) {
            val h = hm.groupValues[1].toIntOrNull() ?: return null
            val m = hm.groupValues[2].toIntOrNull() ?: return null
            return h to m
        }
        val hOnly = Regex("saat\\s+(\\d{1,2})").find(text)
        if (hOnly != null) {
            val h = hOnly.groupValues[1].toIntOrNull() ?: return null
            return h to 0
        }
        return null
    }

    /** Parses "20 dakika sonra hatırlat" / "2 saat sonra hatırlat" into millis + label. */
    private fun parseReminderDelay(text: String): Pair<Long?, String?> {
        val minuteMatch = Regex("(\\d+)\\s*dakika\\s*sonra").find(text)
        if (minuteMatch != null) {
            val minutes = minuteMatch.groupValues[1].toIntOrNull() ?: return null to null
            return (minutes * 60_000L) to "$minutes dakika"
        }
        val hourMatch = Regex("(\\d+)\\s*saat\\s*sonra").find(text)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toIntOrNull() ?: return null to null
            return (hours * 3_600_000L) to "$hours saat"
        }
        return null to null
    }
}
