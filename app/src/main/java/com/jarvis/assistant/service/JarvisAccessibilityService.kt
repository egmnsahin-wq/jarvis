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
 * Only job: take a screenshot when asked, so [com.jarvis.assistant.commands.ScreenUnderstandingController]
 * can send it to Gemini for "ekranımda ne var" style questions. Does not read window content,
 * does not simulate taps, does not automate anything else.
 *
 * The user enables this once from Settings > Accessibility > Jarvis (Settings.openAccessibility()
 * jumps straight there). Nothing works here until they do.
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
                            try {
                                val hardwareBuffer: HardwareBuffer = result.hardwareBuffer
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                                hardwareBuffer.close()
                                if (bitmap == null) {
                                    if (cont.isActive) cont.resume(null)
                                    return
                                }
                                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                val out = ByteArrayOutputStream()
                                softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                                val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                if (cont.isActive) cont.resume(base64)
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(null)
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
