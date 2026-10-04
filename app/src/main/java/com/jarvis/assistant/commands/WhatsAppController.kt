package com.jarvis.assistant.commands

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a WhatsApp chat with a phone number and a pre-filled message via the wa.me deep link.
 *
 * Note: Android/WhatsApp do not allow apps to silently send a WhatsApp message on the user's
 * behalf (this is intentional, anti-spam). This opens the chat with the text already typed —
 * you still tap the Send button yourself, same as every other WhatsApp-automation approach
 * that doesn't rely on a risky Accessibility-Service auto-click hack.
 */
class WhatsAppController(private val context: Context) {

    fun sendMessage(phoneNumber: String, message: String): String {
        val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        return try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(message)}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "WhatsApp'ı mesajla birlikte açtım, göndermek için Gönder'e basman yeterli."
        } catch (e: Exception) {
            "WhatsApp açılamadı, kurulu olduğundan emin ol."
        }
    }
}
