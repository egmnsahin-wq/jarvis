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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisGreen
import com.jarvis.assistant.ui.theme.JarvisBlue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Central Jarvis HUD orb — replaces the old ring that sat behind the mic button.
 * Meant to live in the middle of the screen, on its own, not attached to any button.
 *
 * Behaviour per [VoiceState] (as requested):
 *  - IDLE / LISTENING ("aktifken normalce")  -> GREEN, small irregular breathing
 *    (sometimes grows a touch, sometimes shrinks a touch — never a big jump)
 *  - THINKING ("düşünürken")                 -> amber/orange-yellow blend, size fairly
 *    steady, but the inner light-strands spin faster to read as "processing"
 *  - SPEAKING ("konuşurken")                 -> BLUE, pronounced grow/shrink pulse
 */
@Composable
fun JarvisOrb(
    state: VoiceState,
    amplitude: Float = 0f,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 260.dp
) {
    val infinite = rememberInfiniteTransition(label = "jarvisOrb")

    // Slow steady rotation for the outer tick ring — always on, any state.
    val slowRotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "slowRotation"
    )

    // Inner swirl rotation speed depends on state (fastest while thinking).
    val swirlDurationMs = when (state) {
        VoiceState.THINKING -> 1600
        VoiceState.SPEAKING -> 2600
        else -> 5200
    }
    val swirlRotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(swirlDurationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "swirlRotation"
    )
    val swirlRotationReverse by infinite.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween((swirlDurationMs * 1.6f).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "swirlRotationReverse"
    )

    // Two independent slow phases for an organic, non-repetitive breathing pulse.
    val phaseA by infinite.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3100, easing = LinearEasing), RepeatMode.Restart),
        label = "phaseA"
    )
    val phaseB by infinite.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(4700, easing = LinearEasing), RepeatMode.Restart),
        label = "phaseB"
    )
    // Fast phase for the pronounced speaking pulse.
    val phaseFast by infinite.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
        label = "phaseFast"
    )

    val color = when (state) {
        VoiceState.SPEAKING -> JarvisBlue
        VoiceState.THINKING -> JarvisAmber
        VoiceState.IDLE, VoiceState.LISTENING -> JarvisGreen
    }

    // Pulse scale: how much the whole orb grows/shrinks right now.
    val pulseScale = when (state) {
        VoiceState.SPEAKING ->
            1f + (0.18f + amplitude * 0.15f) * sin(phaseFast)
        VoiceState.THINKING ->
            1f + 0.04f * sin(phaseA)
        VoiceState.IDLE, VoiceState.LISTENING ->
            1f + 0.05f * sin(phaseA) + 0.03f * sin(phaseB * 1.7f) + amplitude * 0.08f
    }

    Canvas(modifier = modifier.size(diameter)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 2.2f

        // --- Outer protractor-style tick ring (always faint, any state) ---
        rotate(degrees = slowRotation, pivot = center) {
            val tickRadius = baseRadius * 1.05f
            val tickCount = 48
            for (i in 0 until tickCount) {
                val angle = (i * (360f / tickCount)) * (PI / 180f).toFloat()
                val isMajor = i % 6 == 0
                val inner = tickRadius * (if (isMajor) 0.90f else 0.95f)
                val outer = tickRadius
                val start = Offset(center.x + inner * cos(angle), center.y + inner * sin(angle))
                val end = Offset(center.x + outer * cos(angle), center.y + outer * sin(angle))
                drawLine(
                    color = color.copy(alpha = if (isMajor) 0.35f else 0.15f),
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                )
            }
        }

        // --- Two segmented rotating rings for depth (classic HUD look) ---
        rotate(degrees = swirlRotation, pivot = center) {
            val r = baseRadius * 0.92f
            val segments = 9
            val gap = 8f
            val sweep = (360f / segments) - gap
            for (i in 0 until segments) {
                drawArc(
                    color = color.copy(alpha = 0.4f),
                    startAngle = i * (360f / segments),
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        rotate(degrees = swirlRotationReverse, pivot = center) {
            val r = baseRadius * 0.62f
            drawArc(
                color = color.copy(alpha = 0.5f),
                startAngle = 0f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawArc(
                color = color.copy(alpha = 0.3f),
                startAngle = 150f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // --- Tangled light-strand core: several jittered arcs at different radii/speeds,
        //     which together read as the "chaotic bright ball" from the reference image. ---
        val strandCount = 10
        for (i in 0 until strandCount) {
            val seedAngle = i * 137.5f // golden-angle spacing so strands don't line up
            val speedSign = if (i % 2 == 0) 1f else -1f
            val strandRotation = swirlRotation * speedSign * (0.4f + (i % 4) * 0.15f) + seedAngle
            val radiusJitter = 0.28f + 0.05f * sin(phaseA + i) + 0.03f * sin(phaseB * (1f + i * 0.2f))
            val r = baseRadius * (radiusJitter + i * 0.012f)
            rotate(degrees = strandRotation, pivot = center) {
                drawArc(
                    color = color.copy(alpha = 0.22f + 0.05f * ((i % 3))),
                    startAngle = 0f,
                    sweepAngle = 40f + (i % 5) * 15f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = (1.5f + (i % 3)).dp.toPx())
                )
            }
        }

        // --- Soft glowing core, layered circles standing in for a blur ---
        val coreRadius = baseRadius * 0.42f * pulseScale
        drawCircle(color = color.copy(alpha = 0.10f), radius = coreRadius * 1.8f, center = center)
        drawCircle(color = color.copy(alpha = 0.16f), radius = coreRadius * 1.3f, center = center)
        drawCircle(color = color.copy(alpha = 0.28f), radius = coreRadius, center = center)
        drawCircle(color = color.copy(alpha = 0.55f), radius = coreRadius * 0.55f, center = center)
        drawCircle(color = Color.White.copy(alpha = 0.25f), radius = coreRadius * 0.18f, center = center)
    }
}
