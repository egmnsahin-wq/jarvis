package com.jarvis.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One person Jarvis can recognize by voice. [id] is stable; [name] can be renamed freely. */
data class Person(
    val id: String,
    val name: String,
    val fingerprints: List<FloatArray>,
    val personality: String,
    val autoPersonality: Boolean,
    val turnsSinceProfile: Int
)

/**
 * Everything per-person: name, voice fingerprints, personality profile, persisted conversation
 * history. Stored in its own SharedPreferences file as JSON. Keyed by a stable id so renaming a
 * person never orphans their history/memories.
 */
object PeopleStore {
    private const val PREFS = "jarvis_people"
    private const val KEY_PEOPLE = "people_json"
    private const val KEY_FORCED = "forced_speaker_id"
    private const val KEY_SENSITIVITY = "speaker_sensitivity"
    private const val HISTORY_PREFIX = "history_"
    private const val MAX_FINGERPRINTS = 9
    private const val MAX_HISTORY = 40

    private val lock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- People ----------

    fun getPeople(context: Context): List<Person> {
        val raw = prefs(context).getString(KEY_PEOPLE, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val fpArr = o.optJSONArray("fingerprints") ?: JSONArray()
                val prints = (0 until fpArr.length()).map { j ->
                    val v = fpArr.getJSONArray(j)
                    FloatArray(v.length()) { k -> v.getDouble(k).toFloat() }
                }
                Person(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    fingerprints = prints,
                    personality = o.optString("personality"),
                    autoPersonality = o.optBoolean("auto", true),
                    turnsSinceProfile = o.optInt("turns", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, people: List<Person>) {
        val arr = JSONArray()
        people.forEach { p ->
            val fp = JSONArray()
            p.fingerprints.forEach { f ->
                val a = JSONArray()
                f.forEach { v -> a.put(if (v.isFinite()) v.toDouble() else 0.0) }
                fp.put(a)
            }
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("fingerprints", fp)
                    .put("personality", p.personality)
                    .put("auto", p.autoPersonality)
                    .put("turns", p.turnsSinceProfile)
            )
        }
        prefs(context).edit().putString(KEY_PEOPLE, arr.toString()).apply()
    }

    private fun update(context: Context, id: String, transform: (Person) -> Person) {
        synchronized(lock) {
            save(context, getPeople(context).map { if (it.id == id) transform(it) else it })
        }
    }

    fun getPerson(context: Context, id: String): Person? = getPeople(context).firstOrNull { it.id == id }

    fun hasEnrolledPeople(context: Context): Boolean = getPeople(context).any { it.fingerprints.isNotEmpty() }

    /** Returns the new person, or null if the name is blank. Duplicate names are allowed but discouraged. */
    fun addPerson(context: Context, name: String): Person? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val person = Person(UUID.randomUUID().toString(), trimmed, emptyList(), "", true, 0)
        synchronized(lock) { save(context, getPeople(context) + person) }
        return person
    }

    fun setName(context: Context, id: String, name: String) = update(context, id) { it.copy(name = name) }
    fun setPersonality(context: Context, id: String, text: String) = update(context, id) { it.copy(personality = text) }
    fun setAutoPersonality(context: Context, id: String, on: Boolean) = update(context, id) { it.copy(autoPersonality = on) }

    fun addFingerprints(context: Context, id: String, newPrints: List<FloatArray>) =
        update(context, id) { it.copy(fingerprints = (it.fingerprints + newPrints).takeLast(MAX_FINGERPRINTS)) }

    fun clearFingerprints(context: Context, id: String) = update(context, id) { it.copy(fingerprints = emptyList()) }

    /** Counts one more conversation turn since the personality profile was last refreshed; returns the new count. */
    fun incrementTurns(context: Context, id: String): Int {
        var result = 0
        update(context, id) { p -> p.copy(turnsSinceProfile = p.turnsSinceProfile + 1).also { result = it.turnsSinceProfile } }
        return result
    }

    fun resetTurns(context: Context, id: String) = update(context, id) { it.copy(turnsSinceProfile = 0) }

    fun deletePerson(context: Context, id: String) {
        synchronized(lock) { save(context, getPeople(context).filterNot { it.id == id }) }
        prefs(context).edit().remove(HISTORY_PREFIX + id).apply()
        PreferencesManager.clearMemoriesForPerson(context, id)
        if (getForcedSpeakerId(context) == id) setForcedSpeakerId(context, null)
    }

    // ---------- Conversation history (per person, persisted) ----------

    fun getHistory(context: Context, id: String): List<Pair<String, String>> {
        val raw = prefs(context).getString(HISTORY_PREFIX + id, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONArray(i)
                o.getString(0) to o.getString(1)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun appendHistory(context: Context, id: String, role: String, text: String) {
        synchronized(lock) {
            val updated = (getHistory(context, id) + (role to text)).takeLast(MAX_HISTORY)
            val arr = JSONArray()
            updated.forEach { (r, t) -> arr.put(JSONArray().put(r).put(t)) }
            prefs(context).edit().putString(HISTORY_PREFIX + id, arr.toString()).apply()
        }
    }

    fun clearHistory(context: Context, id: String) {
        prefs(context).edit().remove(HISTORY_PREFIX + id).apply()
    }

    // ---------- Speaker selection / recognition settings ----------

    /** null = automatic (identify by voice). Otherwise this person is always treated as the speaker. */
    fun getForcedSpeakerId(context: Context): String? = prefs(context).getString(KEY_FORCED, null)

    fun setForcedSpeakerId(context: Context, id: String?) {
        prefs(context).edit().apply { if (id == null) remove(KEY_FORCED) else putString(KEY_FORCED, id) }.apply()
    }

    /** 0 = esnek (accepts looser matches), 1 = dengeli (default), 2 = sıkı (only close matches). */
    fun getSensitivity(context: Context): Int = prefs(context).getInt(KEY_SENSITIVITY, 1)

    fun setSensitivity(context: Context, level: Int) {
        prefs(context).edit().putInt(KEY_SENSITIVITY, level.coerceIn(0, 2)).apply()
    }
}
