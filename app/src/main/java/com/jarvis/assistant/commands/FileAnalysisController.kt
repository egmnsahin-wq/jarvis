package com.jarvis.assistant.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.jarvis.assistant.ai.AssistantAi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lets Jarvis read a file the user picked (text-based files: .txt, .md, .csv, .json, code files,
 * etc.) and analyze it with Gemini, or generate new content and save it as a shareable file.
 *
 * Binary formats like PDF/Word aren't parsed here — reading raw bytes as text would just
 * produce garbage for the AI to "analyze". If that's needed later, a proper PDF text extractor
 * would be the next step.
 */
object FileAnalysisController {

    private const val MAX_CONTENT_CHARS = 12000 // keeps the prompt (and Gemini's free-tier usage) reasonable

    /** Reads the picked file's text and asks Gemini to analyze/summarize it. */
    suspend fun analyzeFile(context: Context, uri: Uri, question: String?): String {
        val name = fileName(context, uri) ?: "dosya"
        val content = readText(context, uri)
            ?: return "\"$name\" dosyasını okuyamadım — metin tabanlı bir dosya değil gibi görünüyor (PDF/Word gibi formatları henüz açamıyorum)."

        val trimmed = if (content.length > MAX_CONTENT_CHARS) content.take(MAX_CONTENT_CHARS) + "\n[...devamı kısaltıldı...]" else content
        val prompt = buildString {
            append("Aşağıda \"$name\" adlı bir dosyanın içeriği var. ")
            if (question.isNullOrBlank()) {
                append("Bu dosyanın ne işe yaradığını, içeriğini ve amacını Türkçe, anlaşılır ve kısa-orta uzunlukta özetle.")
            } else {
                append("Kullanıcının sorusunu bu dosyaya göre cevapla: \"$question\"")
            }
            append("\n\n--- DOSYA İÇERİĞİ ---\n")
            append(trimmed)
        }
        return AssistantAi.analyzeText(context, prompt)
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
        val content = AssistantAi.analyzeText(
            context,
            "Kullanıcı şunu istiyor: \"$request\". Bunun için düzgün, kullanılabilir bir dosya " +
                "içeriği yaz (sadece içerik, başka açıklama ekleme)."
        )
        val fileName = (suggestedName?.takeIf { it.isNotBlank() } ?: "jarvis_dosya") + ".txt"
        return writeAndShare(context, fileName, content)
    }

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
