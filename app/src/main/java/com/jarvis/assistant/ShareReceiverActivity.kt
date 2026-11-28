package com.jarvis.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.jarvis.assistant.data.SharedLinksStore

/**
 * Paylaş menüsünde "Jarvis'e kaydet" olarak görünür. Şeffaftır: bağlantıyı listeye ekler, kısa bir bildirim
 * gösterir ve hemen kapanır, yani YouTube/Instagram'dan çıkmadan devam edersin.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val item = SharedLinksStore.add(this, text)
        val message = if (item == null) {
            "Bu paylaşımda bağlantı bulamadım."
        } else {
            "Jarvis'e eklendi (${SharedLinksStore.pending(this).size} bekleyen)"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
