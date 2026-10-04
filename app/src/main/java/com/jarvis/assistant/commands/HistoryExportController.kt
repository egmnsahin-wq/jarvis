package com.jarvis.assistant.commands

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes the recent conversation to a .txt file and opens Android's share sheet for it. */
object HistoryExportController {

    fun export(context: Context, history: List<Pair<String, String>>): String {
        if (history.isEmpty()) return "Henüz dışa aktaracak bir konuşma geçmişi yok."

        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale("tr")).format(Date())
        val fileName = "jarvis_konusma_$stamp.txt"
        val body = buildString {
            appendLine("Jarvis konuşma geçmişi — $stamp")
            appendLine()
            history.forEach { (role, text) ->
                appendLine(if (role == "user") "Sen: $text" else "Jarvis: $text")
            }
        }

        return try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(body)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Konuşmayı paylaş").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "Konuşmayı bir dosyaya kaydedip paylaşma ekranını açtım."
        } catch (e: Exception) {
            "Dışa aktarırken bir sorun oldu: ${e.message}"
        }
    }
}
