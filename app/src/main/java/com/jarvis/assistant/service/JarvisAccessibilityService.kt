package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * İki işi var: (1) istenince ekran görüntüsü almak ([com.jarvis.assistant.commands.ScreenUnderstandingController]),
 * (2) "X'e şunu yaz" komutunda WhatsApp'ta hazır yazılmış mesajın "Gönder" düğmesine kendiliğinden basmak.
 * Başka hiçbir uygulamada dokunma/otomasyon yapmaz.
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

        /**
         * Önde açık olan uygulamada (örn. WhatsApp) "Gönder" düğmesini arar ve basar. Mesaj metni yazılı
         * bir sohbet ekranı açıldıktan sonra çağrılır; ekran yüklenene kadar [timeoutMs] boyunca yeniden dener.
         * Sadece [packagePredicate]'e uyan uygulamanın penceresinde çalışır, başka hiçbir yere dokunmaz.
         */
        suspend fun autoPressSend(packagePredicate: (String) -> Boolean, timeoutMs: Long = 9000): Boolean {
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (SystemClock.uptimeMillis() < deadline) {
                val service = instance ?: return false
                val root = try { service.rootInActiveWindow } catch (e: Exception) { null }
                val pkg = root?.packageName?.toString()
                if (root != null && pkg != null && packagePredicate(pkg)) {
                    val button = findSendButton(root)
                    if (button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
                delay(400)
            }
            return false
        }

        private val SEND_DESCRIPTIONS = setOf("gönder", "send", "mesaj gönder", "send message", "sms gönder", "send sms")

        private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            for (id in listOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")) {
                root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull { it.isEnabled }?.let { return clickableSelfOrParent(it) }
            }
            fun walk(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
                if (node == null || depth > 25) return null
                val desc = node.contentDescription?.toString()?.trim()?.lowercase()
                if (desc != null && desc in SEND_DESCRIPTIONS && node.isEnabled) return clickableSelfOrParent(node)
                for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)?.let { return it }
                return null
            }
            return walk(root, 0)
        }

        private fun clickableSelfOrParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
            var cur: AccessibilityNodeInfo? = node
            var steps = 0
            while (cur != null && steps < 4) {
                if (cur.isClickable) return cur
                cur = cur.parent
                steps++
            }
            return node
        }

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
