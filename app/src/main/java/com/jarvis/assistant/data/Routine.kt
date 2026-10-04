package com.jarvis.assistant.data

/**
 * One action inside a [Routine]. type is "PLAY_SONG" or "OPEN_APP" today; "PC_OPEN_APP" is
 * reserved for when computer control ships, so routines created now will already have a slot
 * ready for it.
 */
data class RoutineAction(
    val type: String,
    val value: String,
    val service: String = "" // only used by PLAY_SONG: "spotify" or "youtube"
)

/** A user-defined "when I say [trigger], do these things" scene, configured from Settings. */
data class Routine(
    val name: String,
    val trigger: String,
    val actions: List<RoutineAction>
)
