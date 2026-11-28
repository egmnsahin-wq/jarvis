package com.jarvis.assistant.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.ContextCompat
import com.jarvis.assistant.ai.AssistantAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Ana ekrandaki kamera paneli ile sesli/yazılı komutlar arasındaki köprü. */
object CameraBridge {
    /** Kamera paneli açıkken tek bir kare yakalar (ana iş parçacığında çağrılmalı). */
    @Volatile var grabber: (suspend () -> Bitmap?)? = null

    /** Ana ekran, kamera panelini açan fonksiyonu buraya koyar ("kameradan bak" komutu için). */
    @Volatile var panelOpener: (() -> Unit)? = null
}

/** Kameradan anlık görüntü alıp mevcut vision hattına (Gemini/OpenRouter/GitHub) sorar. */
object CameraAskController {

    suspend fun ask(context: Context, question: String?): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return "Kamerayı kullanabilmem için kamera izni lazım. Kamera panelindeki izin düğmesine dokunabilirsin."
        }
        if (CameraBridge.grabber == null) {
            val opener = CameraBridge.panelOpener
                ?: return "Kamerayı açabilmem için uygulamanın ekranda açık olması lazım."
            withContext(Dispatchers.Main) { opener() }
            var waited = 0
            while (CameraBridge.grabber == null && waited < 4000) {
                delay(200)
                waited += 200
            }
        }

        var captured: Bitmap? = null
        var attempts = 0
        while (captured == null && attempts < 3) {
            val grab = CameraBridge.grabber ?: break
            captured = try {
                withContext(Dispatchers.Main) { grab() }
            } catch (e: Exception) {
                null
            }
            if (captured == null) {
                attempts++
                delay(500)
            }
        }
        val bitmap: Bitmap = captured
            ?: return "Kameradan görüntü alamadım. Kamera panelinin açık olduğundan emin ol."

        val base64 = withContext(Dispatchers.Default) { encodeJpeg(bitmap) }
        return AssistantAi.analyzeVision(context, buildPrompt(question, "kullanıcının kamerasından alınmış anlık bir görüntü"), base64)
    }

    /** Galeriden seçilen bir fotoğrafı aynı hatla analiz eder. */
    suspend fun askImage(context: Context, uri: Uri, question: String?): String {
        val bitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        } ?: return "Bu fotoğrafı açamadım."
        val base64 = withContext(Dispatchers.Default) { encodeJpeg(bitmap) }
        return AssistantAi.analyzeVision(context, buildPrompt(question, "kullanıcının seçtiği bir fotoğraf"), base64)
    }

    private fun buildPrompt(question: String?, what: String): String {
        val task = if (question.isNullOrBlank()) {
            "Ne gördüğünü kısa ve net anlat (en fazla 3-4 cümle)."
        } else {
            "Şu soruyu görüntüye bakarak kısa ve net cevapla: \"$question\""
        }
        return "Bu, $what. $task Cevap Türkçe olsun ve sesli okunacak; madde işareti, yıldız ya da markdown kullanma."
    }

    /** Bitmap'i küçültüp JPEG + Base64 yapar (vision isteği için). */
    fun encodeJpeg(src: Bitmap, maxSide: Int = 1024): String {
        val scale = maxSide.toFloat() / maxOf(src.width, src.height)
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        } else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
