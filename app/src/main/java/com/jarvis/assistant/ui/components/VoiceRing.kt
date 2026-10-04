package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisOrange
import kotlin.math.sin

enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * Iron-Man-style animated ring:
 * - IDLE: slow gentle breathing ring
 * - LISTENING: fast pulsing blue ring reacting to a fake amplitude wave
 * - THINKING: rotating orange arc
 * - SPEAKING: multi-ring blue/orange pulse
 */
@Composable
fun VoiceRing(
    state: VoiceState,
    amplitude: Float = 0f, // 0f..1f, feed real mic amplitude here when LISTENING
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voiceRing")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    Canvas(modifier = modifier.size(220.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 2.8f

        // Classic Jarvis-style rotating segmented ring — always spinning slowly, in every state.
        val outerRadius = baseRadius * 1.35f
        rotate(degrees = rotation, pivot = center) {
            val segmentCount = 10
            val gapDegrees = 10f
            val segmentSweep = (360f / segmentCount) - gapDegrees
            for (i in 0 until segmentCount) {
                drawArc(
                    color = JarvisBlue.copy(alpha = 0.5f),
                    startAngle = i * (360f / segmentCount),
                    sweepAngle = segmentSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        rotate(degrees = -rotation * 0.6f, pivot = center) {
            drawArc(
                color = JarvisOrange.copy(alpha = 0.4f),
                startAngle = 0f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius * 1.05f, center.y - outerRadius * 1.05f),
                size = Size(outerRadius * 2.1f, outerRadius * 2.1f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        when (state) {
            VoiceState.IDLE -> {
                drawCircle(
                    color = JarvisBlue.copy(alpha = 0.35f),
                    radius = baseRadius * breathing,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = JarvisBlue.copy(alpha = 0.15f),
                    radius = baseRadius * breathing * 0.8f,
                    center = center
                )
            }

            VoiceState.LISTENING -> {
                val dynamicPulse = 1f + (0.15f + amplitude * 0.4f) * ((sin(pulsePhase) + 1f) / 2f)
                for (i in 0..2) {
                    val ringScale = 1f - (i * 0.18f)
                    drawCircle(
                        color = JarvisBlue.copy(alpha = 0.55f - i * 0.15f),
                        radius = baseRadius * dynamicPulse * ringScale,
                        center = center,
                        style = Stroke(width = (4 - i).dp.toPx())
                    )
                }
                drawCircle(
                    color = JarvisBlue.copy(alpha = 0.25f),
                    radius = baseRadius * 0.5f * dynamicPulse,
                    center = center
                )
            }

            VoiceState.THINKING -> {
                rotate(degrees = rotation, pivot = center) {
                    drawArc(
                        color = JarvisOrange,
                        startAngle = 0f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                        size = androidx.compose.ui.geometry.Size(baseRadius * 2, baseRadius * 2),
                        style = Stroke(width = 5.dp.toPx())
                    )
                }
                drawCircle(
                    color = JarvisOrange.copy(alpha = 0.12f),
                    radius = baseRadius * 0.6f,
                    center = center
                )
            }

            VoiceState.SPEAKING -> {
                val dynamicPulse = 1f + 0.2f * ((sin(pulsePhase * 1.5f) + 1f) / 2f)
                drawCircle(
                    color = JarvisOrange.copy(alpha = 0.45f),
                    radius = baseRadius * dynamicPulse,
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )
                drawCircle(
                    color = JarvisBlue.copy(alpha = 0.35f),
                    radius = baseRadius * 0.7f * (2f - dynamicPulse),
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = JarvisBlue.copy(alpha = 0.2f),
                    radius = baseRadius * 0.4f,
                    center = center
                )
            }
        }
    }
}
