package com.jarvis.assistant.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.commands.IncomingMessage
import com.jarvis.assistant.commands.IncomingMessageHub
import com.jarvis.assistant.data.PreferencesManager

/**
 * WhatsApp bildirimlerini izler (Ayarlar > WhatsApp Mesaj Asistanı açıksa ve kullanıcı
 * "Bildirim erişimi" iznini verdiyse) ve gelen mesajı sesli diyalog için [IncomingMessageHub]'a iletir.
 */
class JarvisNotificationListener : NotificationListenerService() {

    private val seen = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            handle(sbn)
        } catch (e: Exception) {
            // Bildirim biçimi beklenmedikse sessizce geç; servis çökmesin
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (!PreferencesManager.isWhatsAppAssistantEnabled(this)) return
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category == Notification.CATEGORY_CALL) return

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) ?: return
        val last = style.messages.lastOrNull() ?: return
        val text = last.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        val userName = style.user.name?.toString()
        val senderName = last.person?.name?.toString()
        if (senderName != null && senderName == userName) return          // kendi mesajımız
        if (IncomingMessageHub.wasSentByUs(text)) return                   // az önce biz yolladık

        val id = "${sbn.key}|${last.timestamp}|$text"
        synchronized(seen) {
            if (!seen.add(id)) return                                      // aynı mesaj tekrar bildirildi
            while (seen.size > 60) seen.remove(seen.first())
        }

        val isGroup = style.isGroupConversation
        val group = if (isGroup) style.conversationTitle?.toString() else null
        val sender = senderName ?: n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Birisi"
        val action = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }

        IncomingMessageHub.publish(IncomingMessage(sbn.key, sender, group, text, action))
    }
}
