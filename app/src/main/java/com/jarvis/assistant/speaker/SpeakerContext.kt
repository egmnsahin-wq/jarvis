package com.jarvis.assistant.speaker

import android.content.Context
import com.jarvis.assistant.data.PeopleStore

/**
 * Who Jarvis believes is talking right now. A person forced in Settings always wins; otherwise
 * it's whoever the last voice identification picked. null = nobody recognized (guest).
 */
object SpeakerContext {
    @Volatile private var identifiedId: String? = null
    @Volatile private var lastGreetedId: String? = null

    fun setIdentified(id: String?) {
        identifiedId = id
    }

    fun currentId(context: Context): String? {
        val id = PeopleStore.getForcedSpeakerId(context) ?: identifiedId
        // A deleted person must never linger as the "current" speaker.
        return if (id != null && PeopleStore.getPerson(context, id) != null) id else null
    }

    /** "Merhaba <isim>." — only the first time a given person is (re)recognized, not on every reply. */
    fun greetingIfNew(context: Context): String? {
        val id = currentId(context)
        if (id == lastGreetedId) return null
        lastGreetedId = id
        val name = id?.let { PeopleStore.getPerson(context, it)?.name } ?: return null
        return "Merhaba $name."
    }
}
