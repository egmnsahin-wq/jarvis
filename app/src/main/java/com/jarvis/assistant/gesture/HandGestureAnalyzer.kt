package com.jarvis.assistant.gesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt

/** El modeli (hand_landmarker.task): önce assets'te arar, yoksa ilk seferde indirip saklar. */
private object HandModel {
    private const val FILE_NAME = "hand_landmarker.task"
    private const val URL =
        "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"

    fun load(context: Context): ByteBuffer? {
        val bytes = try {
            context.assets.open(FILE_NAME).use { it.readBytes() }
        } catch (e: Exception) {
            val local = File(context.filesDir, FILE_NAME)
            if (!local.exists() || local.length() < 1_000_000) download(local)
            if (local.exists() && local.length() > 1_000_000) local.readBytes() else null
        } ?: return null
        return ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }
    }

    private fun download(target: File) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(URL).build()).execute().use { r ->
                if (!r.isSuccessful) return
                val tmp = File(target.parentFile, "$FILE_NAME.tmp")
                r.body?.byteStream()?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (tmp.length() > 1_000_000) tmp.renameTo(target) else tmp.delete()
            }
        } catch (e: Exception) { /* internet yoksa model hazır olmaz; üst katman durumu bildirir */ }
    }
}

/**
 * Kamera karelerinden elin hareketini yorumlar:
 *  - Açık el + hızlı yatay hareket   -> NEXT / PREVIOUS
 *  - İşaret parmağı + el büyümesi    -> OPEN_DETAIL (kameraya yaklaşma)
 *  - İşaret parmağı + el küçülmesi   -> CLOSE_DETAIL (geri çekme)
 *
 * [mirror] ön kamerada true: görüntü ayna gibi çevrilir, böylece "kullanıcının sağdan sola"
 * hareketi her zaman x'in azalması olur.
 */
class HandGestureAnalyzer(
    context: Context,
    private val mirror: Boolean,
    private val onGesture: (HandGesture) -> Unit,
    private val onStatus: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private data class Sample(
        val t: Long, val cx: Float, val cy: Float, val size: Float,
        val open: Boolean, val pointing: Boolean
    )

    private val appContext = context.applicationContext
    private val lock = Any()
    @Volatile private var landmarker: HandLandmarker? = null
    @Volatile private var closed = false
    private var lastTs = 0L
    private var lastStatus = ""
    private var cooldownUntil = 0L
    private val history = ArrayDeque<Sample>()

    init {
        thread(name = "hand-model-init") {
            try {
                status("El modeli hazırlanıyor…")
                val buffer = HandModel.load(appContext)
                if (buffer == null) {
                    status("El modeli indirilemedi (internet gerekli)")
                    return@thread
                }
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buffer).build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
                val lm = HandLandmarker.createFromOptions(appContext, options)
                synchronized(lock) {
                    if (closed) lm.close() else landmarker = lm
                }
                status("Elini kameraya göster")
            } catch (e: Throwable) {
                status("El takibi başlatılamadı")
            }
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            landmarker?.close()
            landmarker = null
        }
    }

    private fun status(s: String) {
        if (s != lastStatus) { lastStatus = s; onStatus(s) }
    }

    override fun analyze(image: ImageProxy) {
        try {
            synchronized(lock) {
                val lm = landmarker ?: return
                val raw = image.toBitmap()
                val matrix = Matrix().apply {
                    postRotate(image.imageInfo.rotationDegrees.toFloat())
                    if (mirror) postScale(-1f, 1f)
                }
                val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                val now = SystemClock.uptimeMillis()
                val ts = if (now <= lastTs) lastTs + 1 else now
                lastTs = ts
                val result = lm.detectForVideo(BitmapImageBuilder(bmp).build(), ts)
                val hands = result.landmarks()
                if (hands.isEmpty()) {
                    history.clear()
                    status("El görünmüyor")
                } else {
                    process(hands[0], bmp.width, bmp.height, ts)
                }
            }
        } catch (e: Throwable) {
            // tek kare hatası takibi durdurmasın
        } finally {
            image.close()
        }
    }

    private fun process(pts: List<NormalizedLandmark>, w: Int, h: Int, now: Long) {
        fun d(a: NormalizedLandmark, b: NormalizedLandmark): Float {
            val dx = (a.x() - b.x()) * w
            val dy = (a.y() - b.y()) * h
            return sqrt(dx * dx + dy * dy)
        }
        val wrist = pts[0]
        fun extended(tip: Int, pip: Int) = d(pts[tip], wrist) > d(pts[pip], wrist) * 1.15f

        val index = extended(8, 6)
        val middle = extended(12, 10)
        val ring = extended(16, 14)
        val pinky = extended(20, 18)
        val open = listOf(index, middle, ring, pinky).count { it } >= 3
        val pointing = index && !middle && !ring && !pinky

        val size = d(pts[0], pts[9]) / h
        val cx = (pts[0].x() + pts[5].x() + pts[9].x() + pts[13].x() + pts[17].x()) / 5f
        val cy = (pts[0].y() + pts[5].y() + pts[9].y() + pts[13].y() + pts[17].y()) / 5f

        status(when { open -> "Açık el"; pointing -> "İşaret parmağı"; else -> "El algılandı" })

        history.addLast(Sample(now, cx, cy, size, open, pointing))
        while (history.isNotEmpty() && now - history.first().t > 700) history.removeFirst()
        if (now < cooldownUntil) return

        // --- Kaydırma: açık el, ~0.45 sn içinde belirgin yatay hareket ---
        if (open) {
            val recent = history.filter { it.open && now - it.t <= 450 }
            if (recent.size >= 3) {
                val a = recent.first(); val b = recent.last()
                val dx = b.cx - a.cx
                val dy = b.cy - a.cy
                val dtSec = (b.t - a.t) / 1000f
                if (dtSec >= 0.12f && abs(dx) >= 0.22f && abs(dy) < abs(dx) * 0.7f && abs(dx) / dtSec >= 0.5f) {
                    fire(if (dx < 0) HandGesture.NEXT else HandGesture.PREVIOUS, now)
                    return
                }
            }
        }

        // --- Yaklaşma / uzaklaşma: işaret parmağı pozunda el boyutu değişimi ---
        if (pointing) {
            val recent = history.filter { it.pointing && now - it.t <= 700 }
            if (recent.size >= 4) {
                val cur = recent.last().size
                val minS = recent.minOf { it.size }
                val maxS = recent.maxOf { it.size }
                if (minS > 0f && cur / minS >= 1.4f && recent.indexOfFirst { it.size == minS } < recent.lastIndex) {
                    fire(HandGesture.OPEN_DETAIL, now)
                    return
                }
                if (maxS > 0f && cur / maxS <= 0.7f && recent.indexOfFirst { it.size == maxS } < recent.lastIndex) {
                    fire(HandGesture.CLOSE_DETAIL, now)
                    return
                }
            }
        }
    }

    private fun fire(g: HandGesture, now: Long) {
        history.clear()
        cooldownUntil = now + 900
        onGesture(g)
    }
}
