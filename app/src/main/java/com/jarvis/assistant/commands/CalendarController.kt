package com.jarvis.assistant.commands

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Reads/writes the device's own Calendar (via CalendarContract) — works with whatever account
 * is already synced to the Calendar app (Google, Samsung, local, etc.), no separate OAuth needed.
 */
class CalendarController(private val context: Context) {

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun defaultCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }
        return null
    }

    fun addEvent(title: String, daysFromNow: Int, hour: Int?, minute: Int?): String {
        if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            return "Takvime yazma izni vermen lazım, telefon ayarlarından izin verebilirsin."
        }
        val calendarId = defaultCalendarId() ?: return "Cihazda kullanılabilir bir takvim bulamadım."

        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysFromNow)
            if (hour != null) {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute ?: 0)
                set(Calendar.SECOND, 0)
            } else {
                set(Calendar.HOUR_OF_DAY, 9) // saat belirtilmediyse günün 09:00'ına koy
                set(Calendar.MINUTE, 0)
            }
        }
        val startMillis = cal.timeInMillis
        val endMillis = startMillis + 60 * 60 * 1000 // 1 saatlik varsayılan süre

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                val dayLabel = when (daysFromNow) { 0 -> "bugün"; 1 -> "yarın"; else -> "$daysFromNow gün sonra" }
                "\"$title\" $dayLabel takvime eklendi."
            } else "Takvime eklerken bir sorun oldu."
        } catch (e: Exception) {
            "Takvime eklerken bir sorun oldu: ${e.message}"
        }
    }

    fun eventsToday(): String {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            return "Takvimi okumak için izin vermen lazım, telefon ayarlarından izin verebilirsin."
        }
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startOfDay)
        ContentUris.appendId(builder, endOfDay)

        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale("tr"))
        val events = mutableListOf<String>()

        context.contentResolver.query(builder.build(), projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
            ?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleIdx) ?: "(başlıksız)"
                    val begin = cursor.getLong(beginIdx)
                    events.add("${timeFormat.format(begin)} - $title")
                }
            }

        return if (events.isEmpty()) "Bugün takviminde bir etkinlik yok."
        else "Bugün takviminde: " + events.joinToString(", ")
    }
}
