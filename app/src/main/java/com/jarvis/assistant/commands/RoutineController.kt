package com.jarvis.assistant.commands

import android.content.Context
import com.jarvis.assistant.data.PreferencesManager
import com.jarvis.assistant.data.Routine
import java.util.Locale

/**
 * Runs the actions inside a user-defined [Routine] (configured in Settings > Özel Senaryolar)
 * and returns one combined confirmation. Matching a routine is a simple "does the sentence
 * contain the trigger phrase" check — same approach as the registered-remote-phone-name
 * matching, kept deliberately simple and predictable rather than another AI call.
 */
object RoutineController {

    fun findMatchingRoutines(context: Context, text: String): List<Routine> {
        val lower = text.lowercase(Locale("tr"))
        return PreferencesManager.getRoutines(context).filter {
            it.trigger.isNotBlank() && lower.contains(it.trigger.trim().lowercase(Locale("tr")))
        }
    }

    suspend fun execute(context: Context, routine: Routine, phoneController: PhoneController, searchController: SearchController): String {
        if (routine.actions.isEmpty()) return "\"${routine.name}\" senaryosunda hiç eylem tanımlı değil, Ayarlar'dan ekleyebilirsin."

        for (action in routine.actions) {
            when (action.type) {
                "PLAY_SONG" -> if (action.service.equals("youtube", ignoreCase = true))
                    SongPlaybackController.playOnYoutube(context, action.value, searchController)
                else
                    SongPlaybackController.playOnSpotify(context, action.value, searchController)

                "OPEN_APP" -> phoneController.openApp(action.value)

                "PC_OPEN_APP" -> PcController.openApp(context, action.value)
            }
        }
        return "\"${routine.name}\" başlatıldı."
    }
}
