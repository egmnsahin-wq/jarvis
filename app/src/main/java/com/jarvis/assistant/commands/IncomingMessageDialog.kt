package com.jarvis.assistant.commands

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.Locale

/** WhatsApp bildiriminden çıkarılan tek bir gelen mesaj. */
data class IncomingMessage(
    val key: String,
    val sender: String,
    val groupName: String?,
    val text: String,
    val replyAction: Notification.Action?
)

/**
 * Bildirim dinleyicisi ile konuşan taraf (uygulama ekranı ya da arka plan servisi) arasındaki köprü.
 * Aynı anda tek taraf konuşsun diye arka plan servisi varsa ona, yoksa uygulamaya teslim edilir.
 */
object IncomingMessageHub {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile var serviceHost: ((IncomingMessage) -> Unit)? = null
    @Volatile var appHost: ((IncomingMessage) -> Unit)? = null

    // Kendi gönderdiğimiz cevaplar bildirimde "yeni mesaj" gibi geri dönmesin diye hatırlanır.
    private val recentlySent = ArrayDeque<String>()

    @Synchronized fun rememberSent(text: String) {
        recentlySent.addLast(text.trim())
        while (recentlySent.size > 10) recentlySent.removeFirst()
    }

    @Synchronized fun wasSentByUs(text: String): Boolean = recentlySent.contains(text.trim())

    fun publish(msg: IncomingMessage) {
        val host = serviceHost ?: appHost ?: return
        mainHandler.post { host(msg) }
    }
}

/**
 * Gelen WhatsApp mesajı için sesli diyalog:
 *   "X mesaj gönderdi, okumamı ister misin?" -> (evet) mesajı okur -> "Cevap göndermek ister misin?"
 *   -> (evet) "Ne yazmamı istersin?" -> söylenen metin -> "Göndereyim mi?" -> (evet) bildirimden yanıtlar.
 * Konuşma asıl komut hattından geçer ([handle] null dönerse komut normal akışa devam eder).
 */
class IncomingMessageDialog(private val context: Context) {

    private enum class Stage { NONE, ASK_READ, ASK_REPLY, COMPOSE, CONFIRM }

    private var stage = Stage.NONE
    private var current: IncomingMessage? = null
    private var draft = ""
    private var confirmRetries = 0
    private val queue = ArrayDeque<IncomingMessage>()

    /** Kullanıcının sesli cevabı bekleniyor mu? (Bekleniyorsa konuşma bitince mikrofon açılır.) */
    val awaitingAnswer: Boolean get() = stage != Stage.NONE

    /** Yeni mesaj geldi. Sorulacak cümleyi döndürür; şu an başka diyalog sürüyorsa kuyruğa alır ve null döner. */
    fun onIncoming(msg: IncomingMessage): String? {
        if (stage != Stage.NONE) {
            if (queue.size < 5) queue.addLast(msg)
            return null
        }
        return begin(msg)
    }

    fun cancel() {
        stage = Stage.NONE
        current = null
        draft = ""
        queue.clear()
    }

    private fun who(msg: IncomingMessage) =
        if (msg.groupName != null) "${msg.groupName} grubunda ${msg.sender}" else msg.sender

    private fun begin(msg: IncomingMessage): String {
        current = msg
        draft = ""
        confirmRetries = 0
        stage = Stage.ASK_READ
        return "${who(msg)} sana WhatsApp'tan mesaj gönderdi. Okumamı ister misin?"
    }

    private fun finish(prefix: String): String {
        stage = Stage.NONE
        current = null
        draft = ""
        val next = queue.removeFirstOrNull()
        return if (next != null) "$prefix Bekleyen başka bir mesajın daha var. " + begin(next) else prefix
    }

    private fun normalize(t: String): String =
        t.lowercase(Locale("tr")).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun has(t: String, words: List<String>): Boolean {
        val tokens = t.split(" ")
        return words.any { w -> if (w.contains(' ')) t.contains(w) else tokens.any { it == w } }
    }

    private val noWords = listOf(
        "hayır", "hayir", "yok", "okuma", "istemiyorum", "gerek yok", "gerek", "iptal", "vazgeç", "vazgectim",
        "sonra", "olmaz", "gönderme", "göndermesin", "yazma", "hayır teşekkürler"
    )
    private val yesWords = listOf(
        "evet", "tabii", "tabi", "olur", "tamam", "oku", "okur musun", "okuyabilirsin", "gönder", "aynen",
        "istiyorum", "isterim", "hay hay", "peki", "olabilir", "yes", "he", "okusana", "yaz", "göndersin", "gönderebilirsin"
    )
    private val redoWords = listOf("değiştir", "yeniden", "tekrar", "baştan", "farklı")

    private fun isNo(t: String) = has(t, noWords)
    private fun isYes(t: String) = !isNo(t) && has(t, yesWords)

    /** Diyalog sürüyorsa cevabı döndürür; sürmüyorsa ya da cümle diyalogla ilgisizse null (normal komut akışı). */
    fun handle(rawText: String): String? {
        if (stage == Stage.NONE) return null
        val msg = current ?: run { cancel(); return null }
        val t = normalize(rawText)
        when (stage) {
            Stage.ASK_READ -> {
                return when {
                    isYes(t) -> {
                        stage = Stage.ASK_REPLY
                        "${who(msg)} şöyle yazdı: ${msg.text}. Cevap göndermek ister misin?"
                    }
                    isNo(t) -> finish("Tamam, okumuyorum.")
                    else -> { cancel(); null }
                }
            }
            Stage.ASK_REPLY -> {
                return when {
                    isYes(t) -> { stage = Stage.COMPOSE; "Ne yazmamı istersin?" }
                    isNo(t) -> finish("Tamam, cevap göndermiyorum.")
                    else -> { cancel(); null }
                }
            }
            Stage.COMPOSE -> {
                if (isNo(t) && t.split(" ").size <= 3) return finish("Tamam, vazgeçtim.")
                draft = rawText.trim()
                stage = Stage.CONFIRM
                confirmRetries = 0
                return "${msg.sender} kişisine şunu göndereceğim: $draft. Göndereyim mi?"
            }
            Stage.CONFIRM -> {
                return when {
                    has(t, redoWords) -> { stage = Stage.COMPOSE; "Tamam, ne yazmamı istersin?" }
                    isYes(t) -> finish(send(msg, draft))
                    isNo(t) -> finish("Tamam, göndermedim.")
                    confirmRetries < 1 -> { confirmRetries++; "Göndereyim mi? Evet ya da hayır de." }
                    else -> finish("Anlayamadım, mesajı göndermedim.")
                }
            }
            Stage.NONE -> return null
        }
        @Suppress("UNREACHABLE_CODE")
        return null
    }

    /** WhatsApp bildirimindeki "Yanıtla" eylemine metni yazıp tetikler. */
    private fun send(msg: IncomingMessage, text: String): String {
        val action = msg.replyAction
        val inputs = action?.remoteInputs
        if (action == null || inputs.isNullOrEmpty()) {
            return "Bu mesaja bildirimden cevap veremiyorum. WhatsApp'ı açıp kendin yanıtlaman gerekecek."
        }
        return try {
            val intent = Intent()
            val bundle = Bundle()
            inputs.forEach { bundle.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(inputs, intent, bundle)
            IncomingMessageHub.rememberSent(text)
            action.actionIntent.send(context, 0, intent)
            "Cevabını gönderdim."
        } catch (e: PendingIntent.CanceledException) {
            "Gönderemedim, bildirim kapanmış olabilir. WhatsApp'tan kendin yanıtla."
        } catch (e: Exception) {
            "Cevabı gönderirken bir sorun çıktı: ${e.message}"
        }
    }
}
