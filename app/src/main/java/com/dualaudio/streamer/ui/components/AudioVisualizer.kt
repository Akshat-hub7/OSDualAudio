package com.dualaudio.streamer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualaudio.streamer.ui.theme.AccentPrimary
import com.dualaudio.streamer.ui.theme.AccentSecondary
import com.dualaudio.streamer.ui.theme.StatusGreen
import com.dualaudio.streamer.ui.theme.SurfaceDark
import com.dualaudio.streamer.ui.theme.TextMuted
import kotlin.random.Random

/**
 * Real-time audio waveform / spectrum visualizer bar graph reacting to live PCM amplitude.
 */
@Composable
fun AudioVisualizer(
    amplitude: Float,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 28
    val smoothedAmplitude = remember { Animatable(0f) }

    LaunchedEffect(amplitude) {
        smoothedAmplitude.animateTo(
            targetValue = if (isStreaming) amplitude else 0f,
            animationSpec = tween(durationMillis = 60, easing = LinearEasing)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isStreaming) StatusGreen else TextMuted)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isStreaming) "LIVE AUDIO PIPELINE" else "PIPELINE IDLE",
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }
                Text(
                    text = if (isStreaming) "${(smoothedAmplitude.value * 100).toInt()}% PEAK" else "--",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 8.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val totalSpacing = (barCount - 1) * 4.dp.toPx()
                val barWidth = ((canvasWidth - totalSpacing) / barCount).coerceAtLeast(3f)

                val gradient = Brush.verticalGradient(
                    colors = listOf(
                        AccentSecondary,
                        AccentPrimary,
                        AccentPrimary.copy(alpha = 0.4f)
                    ),
                    startY = 0f,
                    endY = canvasHeight
                )

                for (i in 0 until barCount) {
                    val x = i * (barWidth + 4.dp.toPx())
                    // Wave pattern modifier
                    val distanceCenter = kotlin.math.abs(i - (barCount / 2f)) / (barCount / 2f)
                    val curveFactor = 1.0f - (distanceCenter * 0.4f)

                    val pseudoRand = (kotlin.math.sin((i * 0.8) + (pulsePhase * 6.28)) * 0.3 + 0.7).toFloat()
                    val targetHeight = if (isStreaming) {
                        val dynamicFactor = (smoothedAmplitude.value * 1.5f * pseudoRand * curveFactor).coerceIn(0.08f, 1.0f)
                        canvasHeight * dynamicFactor
                    } else {
                        4.dp.toPx()
                    }

                    val y = canvasHeight - targetHeight

                    drawRoundRect(
                        brush = gradient,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, targetHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
        }
    }
}

