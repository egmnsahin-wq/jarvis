package com.jarvis.assistant.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.jarvis.assistant.commands.CameraBridge
import com.jarvis.assistant.ui.theme.HudCyan
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Tam çözünürlüklü tek kare çeker (yazı okumak için ön izleme bitmap'inden çok daha net). */
private suspend fun takeFrame(context: Context, capture: ImageCapture): Bitmap? =
    suspendCancellableCoroutine { cont ->
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp: Bitmap? = try {
                        val raw = image.toBitmap()
                        val rotation = image.imageInfo.rotationDegrees
                        if (rotation != 0) {
                            val m = Matrix().apply { postRotate(rotation.toFloat()) }
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                        } else raw
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }
                    if (cont.isActive) cont.resume(bmp)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }

@Composable
private fun CameraPreview(front: Boolean, onError: (String?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(front, lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                CameraBridge.grabber = { takeFrame(context, capture) }
                onError(null)
            } catch (e: Exception) {
                onError("Kamera açılamadı")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            CameraBridge.grabber = null
            provider?.unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
fun LiveCameraPanel(
    onClose: () -> Unit,
    onAsk: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var front by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    HudPanel(title = "Canlı kamera", onClose = onClose, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
        ) {
            if (granted) {
                CameraPreview(front = front, onError = { error = it }, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val len = 18.dp.toPx()
                    val sw = Stroke(width = 2.dp.toPx())
                    val c = HudCyan.copy(alpha = 0.85f)
                    val w = size.width
                    val h = size.height
                    // dört köşe işareti
                    drawLine(c, Offset(0f, 0f), Offset(len, 0f), sw.width)
                    drawLine(c, Offset(0f, 0f), Offset(0f, len), sw.width)
                    drawLine(c, Offset(w, 0f), Offset(w - len, 0f), sw.width)
                    drawLine(c, Offset(w, 0f), Offset(w, len), sw.width)
                    drawLine(c, Offset(0f, h), Offset(len, h), sw.width)
                    drawLine(c, Offset(0f, h), Offset(0f, h - len), sw.width)
                    drawLine(c, Offset(w, h), Offset(w - len, h), sw.width)
                    drawLine(c, Offset(w, h), Offset(w, h - len), sw.width)
                }
                HudIconButton(
                    icon = Icons.Filled.FlipCameraAndroid,
                    description = "Kamerayı çevir",
                    tint = HudCyan,
                    onClick = { front = !front },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
                error?.let {
                    Text(it, color = JarvisAmber, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Kamera izni gerekiyor", color = JarvisTextSecondary, fontSize = 13.sp)
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("İzin ver", color = HudCyan)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CameraChip("Oku", Modifier.weight(1f)) {
                onAsk("Görüntüdeki tüm yazıları, mümkünse sıra ve düzenini bozmadan oku.")
            }
            CameraChip("Çevir", Modifier.weight(1f)) {
                onAsk("Görüntüdeki yazıyı Türkçeye çevir. Yazı zaten Türkçeyse kısaca ne anlattığını söyle.")
            }
            CameraChip("Anlat", Modifier.weight(1f)) { onAsk(null) }
        }
        HudInputRow(
            placeholder = "Ne görüyorsun? / soru yaz",
            onSend = { onAsk(it.takeIf { q -> q.isNotBlank() }) },
            allowBlank = true,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun CameraChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(HudCyan.copy(alpha = 0.10f))
            .border(1.dp, HudCyan.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HudCyan, fontSize = 13.sp)
    }
}
