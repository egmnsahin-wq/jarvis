package com.jarvis.assistant.commands

import android.content.Context
import com.jarvis.assistant.ai.AssistantAi
import com.jarvis.assistant.service.JarvisAccessibilityService

object ScreenUnderstandingController {

    suspend fun ask(context: Context, question: String?): String {
        if (!JarvisAccessibilityService.isEnabled()) {
            return "Ekranı görebilmem için önce Ayarlar'dan Erişilebilirlik iznini açman lazım."
        }
        val imageBase64 = JarvisAccessibilityService.captureScreenBase64()
            ?: return "Ekran görüntüsü alamadım — cihazın bunu desteklemiyor olabilir (Android 11 ve üzeri gerekiyor)."

        val prompt = if (question.isNullOrBlank())
            "Bu bir telefon ekran görüntüsü. Ekranda ne olduğunu Türkçe, kısa ve net şekilde anlat."
        else
            "Bu bir telefon ekran görüntüsü. Kullanıcının sorusunu bu ekrana bakarak cevapla: \"$question\""

        return AssistantAi.analyzeImage(context, prompt, imageBase64)
    }
}
