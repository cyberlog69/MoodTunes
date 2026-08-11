package com.moodtunes.app.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moodtunes.app.service.VisualizerMode
import kotlin.math.*
import kotlin.random.Random

private data class Particle(
    var x: Float = 0f,
    var y: Float = 0f,
    var speedY: Float = 0f,
    var radius: Float = 0f,
    var alpha: Float = 0f,
    var angle: Float = 0f
)

@Composable
fun AudioVisualizerView(
    mode: VisualizerMode,
    fftBands: FloatArray,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    turntableDiameter: Dp = 260.dp
) {
    if (mode == VisualizerMode.OFF) return

    val infiniteTransition = rememberInfiniteTransition(label = "viz_anim")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "pulsePhase"
    )

    // Pre-allocate persistent particle list for PARTICLES mode
    val particles = remember {
        List(45) {
            Particle(
                speedY = Random.nextFloat() * 1.5f + 0.8f,
                radius = Random.nextFloat() * 4f + 2f,
                alpha = Random.nextFloat() * 0.7f + 0.3f,
                angle = Random.nextFloat() * 360f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radiusPx = turntableDiameter.toPx() / 2f

        when (mode) {
            VisualizerMode.BARS -> {
                drawNeonBars(center, radiusPx, fftBands, primaryColor, secondaryColor)
            }
            VisualizerMode.PULSE_AURA -> {
                drawPulseAura(center, radiusPx, fftBands, pulsePhase, primaryColor, secondaryColor)
            }
            VisualizerMode.PARTICLES -> {
                drawFloatingParticles(center, radiusPx, fftBands, particles, primaryColor, secondaryColor)
            }
            VisualizerMode.OFF -> {}
        }
    }
}

private fun DrawScope.drawNeonBars(
    center: Offset,
    innerRadius: Float,
    fftBands: FloatArray,
    primaryColor: Color,
    secondaryColor: Color
) {
    val barCount = fftBands.size.coerceAtMost(32)
    val maxBarHeight = 65.dp.toPx()
    val angleStep = (2f * PI.toFloat()) / barCount

    for (i in 0 until barCount) {
        val magnitude = fftBands[i].coerceIn(0.05f, 1f)
        val barHeight = magnitude * maxBarHeight
        val angle = i * angleStep

        val cosA = cos(angle)
        val sinA = sin(angle)

        val startX = center.x + (innerRadius + 6f) * cosA
        val startY = center.y + (innerRadius + 6f) * sinA

        val endX = center.x + (innerRadius + 6f + barHeight) * cosA
        val endY = center.y + (innerRadius + 6f + barHeight) * sinA

        val colorFraction = i.toFloat() / barCount
        val barColor = lerp(primaryColor, secondaryColor, colorFraction)

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    barColor.copy(alpha = 0.95f),
                    secondaryColor.copy(alpha = 0.4f)
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY)
            ),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Outer neon glow tip
        drawCircle(
            color = barColor.copy(alpha = 0.85f * magnitude),
            radius = 3.5.dp.toPx(),
            center = Offset(endX, endY)
        )
    }
}

private fun DrawScope.drawPulseAura(
    center: Offset,
    innerRadius: Float,
    fftBands: FloatArray,
    pulsePhase: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    val bassAvg = (fftBands.take(6).average().toFloat()).coerceIn(0f, 1f)
    val midAvg = (fftBands.drop(6).take(12).average().toFloat()).coerceIn(0f, 1f)

    // Inner glowing ring
    val innerPulse = innerRadius + 10.dp.toPx() * (1f + bassAvg * 1.5f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.55f * (0.6f + bassAvg * 0.4f)),
                primaryColor.copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = center,
            radius = innerPulse + 30.dp.toPx()
        ),
        radius = innerPulse + 30.dp.toPx(),
        center = center
    )

    // Middle resonant ripple
    val rippleRadius = innerRadius + 40.dp.toPx() + sin(pulsePhase) * 15.dp.toPx() + midAvg * 30.dp.toPx()
    drawCircle(
        color = secondaryColor.copy(alpha = (0.35f * (1f - (rippleRadius - innerRadius) / (innerRadius + 80.dp.toPx()))).coerceIn(0.05f, 0.4f)),
        radius = rippleRadius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
    )

    // Outer cosmic wave
    val outerRadius = innerRadius + 75.dp.toPx() + cos(pulsePhase * 0.7f) * 18.dp.toPx() + bassAvg * 20.dp.toPx()
    drawCircle(
        color = primaryColor.copy(alpha = (0.22f * (1f - (outerRadius - innerRadius) / (innerRadius + 120.dp.toPx()))).coerceIn(0.02f, 0.25f)),
        radius = outerRadius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
    )
}

private fun DrawScope.drawFloatingParticles(
    center: Offset,
    innerRadius: Float,
    fftBands: FloatArray,
    particles: List<Particle>,
    primaryColor: Color,
    secondaryColor: Color
) {
    val overallEnergy = (fftBands.average().toFloat()).coerceIn(0.1f, 1f)

    particles.forEachIndexed { i, particle ->
        particle.angle = (particle.angle + particle.speedY * (0.8f + overallEnergy * 1.5f)) % 360f
        val rad = Math.toRadians(particle.angle.toDouble()).toFloat()

        val bandIndex = (i % fftBands.size)
        val bandVal = fftBands[bandIndex]

        val distance = innerRadius + 12.dp.toPx() + (i * 2.2f) + bandVal * 45.dp.toPx()
        val x = center.x + distance * cos(rad)
        val y = center.y + distance * sin(rad)

        val particleColor = if (i % 2 == 0) primaryColor else secondaryColor
        val dynamicAlpha = (particle.alpha * (0.4f + bandVal * 0.6f)).coerceIn(0.1f, 1f)

        drawCircle(
            color = particleColor.copy(alpha = dynamicAlpha),
            radius = (particle.radius * (1f + bandVal * 0.8f)).dp.toPx(),
            center = Offset(x, y)
        )
    }
}
