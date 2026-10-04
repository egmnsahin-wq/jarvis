package com.jarvis.assistant.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Reads today's missed calls from the device's call log — names/numbers only, never content. */
class CallLogController(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun missedCallsToday(): String {
        if (!hasPermission()) return "Aramaları okumak için izin vermen lazım, telefon ayarlarından izin verebilirsin."

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis

        val projection = arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE)
        val selection = "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?"
        val args = arrayOf(CallLog.Calls.MISSED_TYPE.toString(), startOfDay.toString())

        val names = mutableListOf<String>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, selection, args, "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)
                names.add(if (!name.isNullOrBlank()) name else cursor.getString(numberIdx) ?: "bilinmeyen numara")
            }
        }

        return when {
            names.isEmpty() -> "Bugün cevapsız aramış olan yok."
            names.size == 1 -> "Bugün ${names[0]} aramış."
            else -> "Bugün ${names.size} cevapsız arama var: ${names.joinToString(", ")}."
        }
    }
}
