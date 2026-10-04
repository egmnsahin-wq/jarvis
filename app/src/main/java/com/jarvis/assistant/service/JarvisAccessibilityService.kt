package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Only job: take a screenshot when asked, so ScreenUnderstandingController can send it to the AI
 * for "ekranımda ne var" style questions. Does not read window content and does not tap anything.
 *
 * The user enables this once from Settings > Accessibility > Jarvis.
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    companion object {
        @Volatile private var instance: JarvisAccessibilityService? = null

        // Long side is shrunk to this: much faster upload, far fewer tokens, plenty to read the screen.
        private const val MAX_SIDE_PX = 1280

        fun isEnabled(): Boolean = instance != null

        /** Returns a base64 JPEG of the current screen, or null if unavailable/unsupported/denied. */
        suspend fun captureScreenBase64(): String? {
            val service = instance ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null // takeScreenshot() needs API 30+

            return suspendCancellableCoroutine { cont ->
                service.takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                            var buffer: HardwareBuffer? = null
                            try {
                                buffer = result.hardwareBuffer
                                val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                if (hw == null) {
                                    if (cont.isActive) cont.resume(null)
                                    return
                                }
                                // BUG FIX: copy FIRST, close the buffer AFTER (the old code closed the
                                // buffer before copying, so the copy failed and the screenshot was lost).
                                val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
                                hw.recycle()
                                if (soft == null) {
                                    if (cont.isActive) cont.resume(null)
                                    return
                                }
                                val longSide = maxOf(soft.width, soft.height)
                                val scaled = if (longSide > MAX_SIDE_PX) {
                                    val f = MAX_SIDE_PX.toFloat() / longSide
                                    Bitmap.createScaledBitmap(
                                        soft, (soft.width * f).toInt().coerceAtLeast(1),
                                        (soft.height * f).toInt().coerceAtLeast(1), true
                                    )
                                } else soft
                                val out = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                                val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                if (cont.isActive) cont.resume(base64)
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(null)
                            } finally {
                                try { buffer?.close() } catch (_: Exception) {}
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            }
        }
    }
}

private typealias TakeScreenshotCallback = android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
