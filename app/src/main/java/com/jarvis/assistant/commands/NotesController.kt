package com.jarvis.assistant.commands

import android.content.Context
import org.json.JSONArray

/** Very simple local notes list, persisted with SharedPreferences (no cloud, no account needed). */
class NotesController(context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_notes", Context.MODE_PRIVATE)

    fun addNote(text: String): String {
        val notes = getNotes().toMutableList()
        notes.add(text)
        saveNotes(notes)
        return "Not edildi: \"$text\""
    }

    fun readNotes(): String {
        val notes = getNotes()
        return if (notes.isEmpty()) {
            "Kayıtlı bir notun yok."
        } else {
            "Notların: " + notes.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString(". ")
        }
    }

    fun clearNotes(): String {
        prefs.edit().remove(KEY).apply()
        return "Tüm notlar silindi."
    }

    private fun getNotes(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun saveNotes(notes: List<String>) {
        val array = JSONArray()
        notes.forEach { array.put(it) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "notes_list"
    }
}
