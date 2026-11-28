package com.jarvis.assistant.commands

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.content.FileProvider
import com.jarvis.assistant.ai.AssistantAi
import com.jarvis.assistant.ai.IntentSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jarvis'in seçilen dosyayı okuyup analiz etmesi:
 * - metin tabanlı dosyalar (.txt, .md, .csv, .json, kod dosyaları) doğrudan okunur,
 * - .docx dosyalarının içindeki metin çıkarılır,
 * - PDF'in ilk sayfaları görüntüye çevrilip vision ile okunur (taranmış PDF'ler de çalışır),
 * - fotoğraflar vision hattına gider.
 */
object FileAnalysisController {

    private const val MAX_CONTENT_CHARS = 12000 // keeps the prompt (and Gemini's free-tier usage) reasonable
    private const val MAX_PDF_PAGES = 4 // her sayfa 1 vision isteği harcar

    /** Reads the picked file's text and asks Gemini to analyze/summarize it. */
    suspend fun analyzeFile(context: Context, uri: Uri, question: String?): String {
        val name = fileName(context, uri) ?: "dosya"
        val lower = name.lowercase(Locale.ROOT)
        val mime = try { context.contentResolver.getType(uri) } catch (e: Exception) { null } ?: ""
        var note = ""
        val content: String = when {
            mime.startsWith("image/") -> return CameraAskController.askImage(context, uri, question)
            lower.endsWith(".pdf") || mime == "application/pdf" -> {
                val pdf = readPdf(context, uri)
                if (pdf.error != null) return pdf.error
                note = pdf.note
                pdf.text.orEmpty()
            }
            lower.endsWith(".docx") ->
                readDocx(context, uri) ?: return "\"$name\" Word dosyasının içindeki metni çıkaramadım."
            else -> readText(context, uri)
                ?: return "\"$name\" dosyasını okuyamadım. Metin tabanlı bir dosya değil gibi görünüyor."
        }
        if (content.isBlank()) return "\"$name\" dosyasında okunabilir bir metin bulamadım."

        val trimmed = if (content.length > MAX_CONTENT_CHARS) content.take(MAX_CONTENT_CHARS) + "\n[...devamı kısaltıldı...]" else content
        val prompt = buildString {
            append("Aşağıda \"$name\" adlı bir dosyanın içeriği var. ")
            if (question.isNullOrBlank()) {
                append("Bu dosyanın ne işe yaradığını, içeriğini ve amacını Türkçe, anlaşılır ve kısa-orta uzunlukta özetle.")
            } else {
                append("Kullanıcının sorusunu bu dosyaya göre cevapla: \"$question\"")
            }
            if (note.isNotBlank()) append(" ($note)")
            append(" Cevapta emoji, markdown, yıldız, tire ya da madde işareti kullanma; cevap sesli okunacak, düz ve akıcı cümleler yaz.")
            append("\n\n--- DOSYA İÇERİĞİ ---\n")
            append(trimmed)
        }
        return AssistantAi.analyzeTextSmart(context, prompt)
    }

    /** Analyzes a file and writes the analysis into a NEW file, then opens the share sheet. */
    suspend fun analyzeFileToNewFile(context: Context, uri: Uri): String {
        val name = fileName(context, uri) ?: "dosya"
        val analysis = analyzeFile(context, uri, null)
        val outName = "analiz_${name.substringBeforeLast('.', name)}.txt"
        return writeAndShare(context, outName, "\"$name\" dosyası analizi\n\n$analysis")
    }

    /** Generates fresh content from a free-form request and saves it as a .txt file. */
    suspend fun createFile(context: Context, request: String, suggestedName: String?): String {
        val content = AssistantAi.analyzeTextSmart(
            context,
            "Kullanıcı şunu istiyor: \"$request\". Bunun için düzgün, kullanılabilir bir dosya " +
                "içeriği yaz (sadece içerik, başka açıklama ekleme)."
        )
        val fileName = (suggestedName?.takeIf { it.isNotBlank() } ?: "jarvis_dosya") + ".txt"
        return writeAndShare(context, fileName, content)
    }

    // ---------- PDF / Word ----------

    private class Extract(val text: String? = null, val error: String? = null, val note: String = "")

    private suspend fun readPdf(context: Context, uri: Uri): Extract {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return Extract(error = "PDF dosyasını açamadım.")
            val sb = StringBuilder()
            var note = ""
            pfd.use { fd ->
                val renderer = PdfRenderer(fd)
                try {
                    val total = renderer.pageCount
                    val pages = minOf(total, MAX_PDF_PAGES)
                    for (i in 0 until pages) {
                        val b64 = withContext(Dispatchers.Default) {
                            val page = renderer.openPage(i)
                            try {
                                val width = 1400
                                val height = (page.height * (width.toFloat() / page.width)).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                CameraAskController.encodeJpeg(bmp, 1600)
                            } finally {
                                page.close()
                            }
                        }
                        val pageText = AssistantAi.analyzeVision(
                            context,
                            "Bu bir PDF sayfasının görüntüsü. Sayfadaki tüm metni olduğu gibi, düzenini koruyarak yaz. " +
                                "Yorum ekleme. Sayfada metin yoksa görselin ne gösterdiğini tek cümleyle anlat.",
                            b64
                        )
                        if (IntentSchema.isErrorReply(pageText)) return Extract(error = pageText)
                        sb.append("--- Sayfa ${i + 1} ---\n").append(pageText).append("\n")
                    }
                    if (total > pages) note = "PDF'in ilk $pages sayfası okundu, toplam $total sayfa"
                } finally {
                    renderer.close()
                }
            }
            Extract(text = sb.toString(), note = note)
        } catch (e: SecurityException) {
            Extract(error = "Bu PDF şifreli, açamadım.")
        } catch (e: Exception) {
            Extract(error = "PDF'i okuyamadım: ${e.message ?: "bilinmeyen hata"}")
        }
    }

    private fun readDocx(context: Context, uri: Uri): String? {
        return try {
            var result: String? = null
            context.contentResolver.openInputStream(uri)?.use { input ->
                val zip = ZipInputStream(input)
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        result = docxXmlToText(zip.readBytes().toString(Charsets.UTF_8))
                        break
                    }
                    entry = zip.nextEntry
                }
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun docxXmlToText(xml: String): String =
        xml.replace("</w:p>", "\n")
            .replace("<w:tab/>", "\t")
            .replace("<w:br/>", "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()

    // ---------- Helpers ----------

    private fun fileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readText(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeAndShare(context: Context, fileName: String, content: String): String {
        return try {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale("tr")).format(Date())
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._\\-ğüşıöçĞÜŞİÖÇ ]"), "_")
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "${stamp}_$safeName")
            file.writeText(content)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Dosyayı paylaş/kaydet").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "\"$fileName\" dosyasını oluşturdum, kaydetmen için paylaşma ekranını açtım."
        } catch (e: Exception) {
            "Dosyayı oluştururken bir sorun oldu: ${e.message}"
        }
    }
}
