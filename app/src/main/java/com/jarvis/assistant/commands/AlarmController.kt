package com.jarvis.assistant.commands

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import com.jarvis.assistant.receiver.InternalAlarmReceiver
import java.util.Calendar

/**
 * Sets alarms/timers. Tries the device's default Clock app first (via public intents); if no
 * such app exists (common on tablets / industrial handhelds with no Clock app installed), it
 * falls back to Jarvis's own internal alarm (AlarmManager + a notification/sound), so alarms
 * always work regardless of what's installed on the device.
 */
class AlarmController(private val context: Context) {

    fun setAlarm(hour: Int, minute: Int, label: String = "Jarvis"): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (resolvesToAnApp(intent) && safeStart(intent)) return true
        return scheduleInternalAlarm(hour, minute, label)
    }

    fun setTimer(seconds: Int, label: String = "Jarvis"): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (resolvesToAnApp(intent) && safeStart(intent)) return true
        return scheduleInternalTimer(seconds, label)
    }

    fun showAlarms(): Boolean {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return resolvesToAnApp(intent) && safeStart(intent)
    }

    private fun resolvesToAnApp(intent: Intent): Boolean =
        intent.resolveActivity(context.packageManager) != null

    private fun safeStart(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun scheduleInternalAlarm(hour: Int, minute: Int, label: String): Boolean {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }
        return scheduleInternal(calendar.timeInMillis, label)
    }

    private fun scheduleInternalTimer(seconds: Int, label: String): Boolean {
        val triggerAt = System.currentTimeMillis() + seconds * 1000L
        return scheduleInternal(triggerAt, label)
    }

    private fun scheduleInternal(triggerAtMillis: Long, label: String): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = triggerAtMillis.toInt()
        val intent = Intent(context, InternalAlarmReceiver::class.java).apply {
            putExtra(InternalAlarmReceiver.EXTRA_LABEL, label)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            true
        } catch (e: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                true
            } catch (e2: Exception) {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
