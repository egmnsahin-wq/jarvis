package com.jarvis.assistant.commands

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.jarvis.assistant.ai.AssistantAi
import com.jarvis.assistant.ai.IntentSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Jarvis, şunu yapan bir python kodu yaz": kodu yazar, Download/Jarvis klasörüne doğru uzantıyla
 * kaydeder, panoya kopyalar ve sohbet ekranında gösterir. Sesli okumada kod okunmaz, yalnızca açıklama okunur.
 * "Bunu şöyle değiştir" dersen bir önceki kodu güncelleyerek yeniden yazar.
 */
object CodeController {

    @Volatile private var lastCode: String? = null
    @Volatile private var lastName: String? = null

    private const val MARK_CODE = "===KOD==="
    private const val MARK_EXPLAIN = "===ACIKLAMA==="

    suspend fun write(context: Context, request: String): String {
        if (request.isBlank()) return "Nasıl bir kod yazmamı istediğini anlayamadım."

        val previous = lastCode
        val prompt = buildString {
            append("Sen deneyimli bir yazılımcısın. Kullanıcının isteği: \"$request\"\n")
            if (previous != null) {
                append("\nKullanıcı daha önce şu kodu yazdırmıştı (dosya: ${lastName ?: "kod.txt"}). ")
                append("İstek bu kodu değiştirmek ya da düzeltmekle ilgiliyse kodun TAMAMINI güncelleyerek yaz; ")
                append("yeni bir şey istiyorsa sıfırdan yaz.\n--- ÖNCEKİ KOD ---\n")
                append(previous.take(8000))
                append("\n--- ÖNCEKİ KOD SONU ---\n")
            }
            append("\nÇalışan, eksiksiz kod yaz; kodun içindeki yorumlar Türkçe olsun. ")
            append("Cevabını SADECE şu biçimde ver, başka hiçbir şey ekleme:\n")
            append("DOSYA: <uygun_dosya_adi.uzanti>\n")
            append("$MARK_CODE\n<kodun tamamı, markdown çiti olmadan>\n")
            append("$MARK_EXPLAIN\n<Türkçe, 2-4 düz cümle: ne yaptığı ve nasıl çalıştırılacağı; emoji, markdown, madde işareti yok>")
        }

        val raw = AssistantAi.analyzeTextSmart(context, prompt)
        if (IntentSchema.isErrorReply(raw)) return raw

        val parsed = parse(raw) ?: return "Kodu yazdım ama biçimini çözemedim, bir kez daha söyler misin?"
        val (name, code, explanation) = parsed
        lastCode = code
        lastName = name

        val saved = withContext(Dispatchers.IO) { saveToDownloads(context, name, code) }
        withContext(Dispatchers.Main) { copyToClipboard(context, code) }

        val where = if (saved) "Kod, İndirilenler klasöründeki Jarvis klasörüne $name olarak kaydedildi ve panoya kopyalandı."
        else "Kod panoya kopyalandı."
        return buildString {
            append(explanation.ifBlank { "Kodu yazdım." })
            append(" ")
            append(where)
            append("\n\n```\n")
            append(code)
            append("\n```")
        }
    }

    private fun parse(raw: String): Triple<String, String, String>? {
        val text = raw.trim()
        val codeIdx = text.indexOf(MARK_CODE)
        if (codeIdx >= 0) {
            val head = text.substring(0, codeIdx)
            val afterCode = text.substring(codeIdx + MARK_CODE.length)
            val explainIdx = afterCode.indexOf(MARK_EXPLAIN)
            val codePart = if (explainIdx >= 0) afterCode.substring(0, explainIdx) else afterCode
            val explanation = if (explainIdx >= 0) afterCode.substring(explainIdx + MARK_EXPLAIN.length).trim() else ""
            val name = Regex("""DOSYA:\s*(\S+)""").find(head)?.groupValues?.get(1)?.let { sanitizeName(it) } ?: "kod.txt"
            val code = stripFences(codePart)
            if (code.isNotBlank()) return Triple(name, code, explanation)
        }
        // Model biçimi bozduysa ilk kod bloğunu yakala.
        val fenced = Regex("""```[a-zA-Z0-9+#]*\n([\s\S]*?)```""").find(text)
        if (fenced != null) {
            val before = text.substring(0, fenced.range.first).trim()
            return Triple("kod.txt", fenced.groupValues[1].trim('\n'), before.take(400))
        }
        return null
    }

    private fun stripFences(s: String): String {
        var t = s.trim('\n', '\r', ' ')
        if (t.startsWith("```")) t = t.substringAfter('\n', "")
        if (t.endsWith("```")) t = t.removeSuffix("```")
        return t.trim('\n', '\r')
    }

    private fun sanitizeName(name: String): String {
        val cleaned = name.trim('`', '"', '\'', '*', ' ').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.isBlank() || !cleaned.contains('.')) "kod.txt" else cleaned
    }

    private fun saveToDownloads(context: Context, name: String, content: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                // octet-stream: Android .py/.kt gibi uzantıların sonuna .txt eklemesin.
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Jarvis")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun copyToClipboard(context: Context, code: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Jarvis kodu", code))
        } catch (e: Exception) { /* pano kullanılamıyorsa sorun değil */ }
    }
}
