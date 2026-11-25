package com.jarvis.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.HudCyan
import com.jarvis.assistant.ui.theme.HudGold
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisGreen
import com.jarvis.assistant.ui.theme.JarvisOrange
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun buildSphere(n: Int): FloatArray {
    val out = FloatArray(n * 3)
    val golden = PI.toFloat() * (3f - sqrt(5f))
    for (i in 0 until n) {
        val y = 1f - (i / (n - 1f)) * 2f
        val r = sqrt(1f - y * y)
        val th = golden * i
        out[i * 3] = cos(th) * r
        out[i * 3 + 1] = y
        out[i * 3 + 2] = sin(th) * r
    }
    return out
}

/**
 * Referans görseldeki gibi, canlı dalgalanan parçacık küresi. Ana renk sesli duruma göre değişir
 * (dinlerken yeşil, konuşurken mavi, düşünürken sarı-turuncu); ikinci renk altın/camgöbeği karışımı verir.
 */
@Composable
fun ParticleOrb(
    state: VoiceState,
    amplitude: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 280.dp,
    particleCount: Int = 650
) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    val pts = remember(particleCount) { buildSphere(particleCount) }

    val primaryTarget = when (state) {
        VoiceState.SPEAKING -> JarvisBlue
        VoiceState.THINKING -> JarvisAmber
        VoiceState.IDLE, VoiceState.LISTENING -> JarvisGreen
    }
    val secondaryTarget = when (state) {
        VoiceState.SPEAKING -> HudGold
        VoiceState.THINKING -> JarvisOrange
        VoiceState.IDLE, VoiceState.LISTENING -> HudCyan
    }
    val primary by animateColorAsState(primaryTarget, tween(500), label = "orbPrimary")
    val secondary by animateColorAsState(secondaryTarget, tween(500), label = "orbSecondary")

    Canvas(modifier = modifier.size(diameter)) {
        val t = time.floatValue
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = size.minDimension / 2f * 0.78f

        val pulse = when (state) {
            VoiceState.SPEAKING -> 1f + (0.10f + amplitude * 0.10f) * sin(t * 9f)
            VoiceState.THINKING -> 1f + 0.03f * sin(t * 2f)
            VoiceState.IDLE, VoiceState.LISTENING ->
                1f + 0.035f * sin(t * 1.9f) + 0.02f * sin(t * 3.1f) + amplitude * 0.06f
        }
        val spin = t * when (state) {
            VoiceState.THINKING -> 1.6f
            VoiceState.SPEAKING -> 0.7f
            VoiceState.IDLE, VoiceState.LISTENING -> 0.35f
        }
        val wobble = when (state) {
            VoiceState.SPEAKING -> 0.14f
            VoiceState.THINKING -> 0.10f
            VoiceState.IDLE, VoiceState.LISTENING -> 0.08f
        }
        val cosY = cos(spin)
        val sinY = sin(spin)
        val tilt = 0.45f
        val cosX = cos(tilt)
        val sinX = sin(tilt)
        val scale = baseR * pulse

        // Yumuşak ışıma
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(cx, cy),
                radius = baseR * 1.35f
            ),
            radius = baseR * 1.35f,
            center = Offset(cx, cy)
        )

        // Küre kabuğu
        for (i in 0 until particleCount) {
            val x0 = pts[i * 3]
            val y0 = pts[i * 3 + 1]
            val z0 = pts[i * 3 + 2]
            val d = 1f + wobble * (sin(x0 * 3.1f + t * 1.7f) * cos(y0 * 2.7f - t * 1.3f) + 0.6f * sin(z0 * 4.2f + t * 2.1f))
            val px = x0 * d
            val py = y0 * d
            val pz = z0 * d
            val rx = px * cosY + pz * sinY
            val rz = -px * sinY + pz * cosY
            val ry = py * cosX - rz * sinX
            val rz2 = py * sinX + rz * cosX
            val depth = ((rz2 + 1.3f) / 2.6f).coerceIn(0f, 1f)
            val mix = 0.5f + 0.5f * sin(x0 * 2.2f + y0 * 1.7f + t * 0.8f)
            val color = lerp(secondary, primary, mix)
            drawCircle(
                color = color.copy(alpha = (0.15f + 0.85f * depth).coerceIn(0.1f, 1f)),
                radius = (0.7f + 1.5f * depth) * density,
                center = Offset(cx + rx * scale, cy + ry * scale)
            )
        }

        // Üç parlak halka (görseldeki ışık şeritleri)
        val ringPoints = 90
        for (j in 0 until 3) {
            val ringTilt = 0.9f * j + t * 0.3f * (j + 1) * (if (j % 2 == 0) 1f else -1f)
            val cosR = cos(ringTilt)
            val sinR = sin(ringTilt)
            for (k in 0 until ringPoints) {
                val a = k / ringPoints.toFloat() * 2f * PI.toFloat()
                val px = cos(a) * 1.03f
                val py = sin(a) * cosR * 1.03f
                val pz = sin(a) * sinR * 1.03f
                val rx = px * cosY + pz * sinY
                val rz = -px * sinY + pz * cosY
                val ry = py * cosX - rz * sinX
                val rz2 = py * sinX + rz * cosX
                val depth = ((rz2 + 1.3f) / 2.6f).coerceIn(0f, 1f)
                drawCircle(
                    color = secondary.copy(alpha = (0.25f + 0.75f * depth).coerceIn(0.2f, 1f)),
                    radius = (1.0f + 1.6f * depth) * density,
                    center = Offset(cx + rx * scale, cy + ry * scale)
                )
            }
        }
    }
}
