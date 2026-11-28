package com.jarvis.assistant.commands

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * "Son arayan numarayı Ahmet diye kaydet": arama geçmişindeki, rehberde olmayan en son numarayı bulur
 * ve istenen adla rehbere ekler. Kaydettiği numarayı söyler, yanlışsa rehberden silinebilir.
 */
class ContactSaveController(private val context: Context) {

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun saveLastUnknownCaller(rawName: String?): String {
        val name = rawName?.trim().orEmpty()
            .split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase(Locale("tr")) } }
        if (name.isBlank()) return "Numarayı hangi adla kaydedeyim?"
        if (!has(Manifest.permission.READ_CALL_LOG)) {
            return "Arama geçmişini okumak için izin vermen lazım, uygulama ayarlarından Telefon iznini açabilirsin."
        }
        if (!has(Manifest.permission.WRITE_CONTACTS) || !has(Manifest.permission.READ_CONTACTS)) {
            return "Rehbere kaydedebilmem için izin vermen lazım, uygulama ayarlarından Kişiler iznini açabilirsin."
        }

        val unknown = recentUnknownNumbers()
        val number = unknown.firstOrNull() ?: return "Son aramalarının hepsi rehberde kayıtlı görünüyor."

        return try {
            insertContact(name, number)
            val others = unknown.size - 1
            buildString {
                append("$number numarasını $name adıyla rehbere kaydettim.")
                if (others > 0) append(" Rehberde olmayan $others numara daha var.")
            }
        } catch (e: Exception) {
            "Rehbere kaydetme başarısız oldu: ${e.message ?: "bilinmeyen hata"}"
        }
    }

    /** Arama geçmişindeki (en yeniden eskiye) rehberde olmayan farklı numaralar. */
    private fun recentUnknownNumbers(): List<String> {
        val result = LinkedHashSet<String>()
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE)
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            var scanned = 0
            while (cursor.moveToNext() && scanned < 60) {
                scanned++
                val number = cursor.getString(numberIdx)?.trim().orEmpty()
                if (number.count { it.isDigit() } < 7) continue // gizli/özel numaraları atla
                if (number in result) continue
                if (!isInContacts(number)) result.add(number)
            }
        }
        return result.toList()
    }

    private fun isInContacts(number: String): Boolean {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun insertContact(name: String, number: String) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }
}
