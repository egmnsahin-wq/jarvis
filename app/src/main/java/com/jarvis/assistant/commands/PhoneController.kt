package com.jarvis.assistant.commands

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager

/**
 * Handles phone-related actions: placing calls, sending SMS, opening installed apps.
 * Requires CALL_PHONE / SEND_SMS / READ_CONTACTS runtime permissions to already be granted
 * (request them from MainActivity before invoking these).
 */
class PhoneController(private val context: Context) {

    fun callNumber(number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** Looks up a contact by display name and returns their primary phone number, if found. */
    fun findContactNumber(name: String): String? {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")

        resolver.query(uri, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return cursor.getString(numberIndex)
            }
        }
        return null
    }

    fun sendSms(number: String, message: String) {
        val smsManager = context.getSystemService(SmsManager::class.java)
        smsManager?.sendTextMessage(number, null, message, null, null)
    }

    /** Opens an installed app by its human-readable label, e.g. "Spotify", "Chrome". */
    fun openApp(appLabel: String): Boolean {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolvedApps = pm.queryIntentActivities(launcherIntent, 0)

        val cleanedLabel = appLabel.trim().lowercase()
        val match = resolvedApps.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase()
            label == cleanedLabel
        } ?: resolvedApps.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase()
            label.contains(cleanedLabel) || cleanedLabel.contains(label)
        } ?: return false

        return try {
            val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                true
            } else false
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Returns display names of all launchable apps — useful for debugging voice-match issues. */
    fun listLaunchableAppNames(): List<String> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, 0).map { it.loadLabel(pm).toString() }
    }
}
