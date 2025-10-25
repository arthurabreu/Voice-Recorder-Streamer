package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun Waveform(
    levels: List<Float>,
    barCount: Int,
    barWidth: Dp,
    barGap: Dp,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF101010))
        .padding(12.dp),
    height: Dp = 120.dp,
    barColor: Color = Color(0xFF64B5F6),
    animateWhenSilent: Boolean = true,
    silentThreshold: Float = 0.03f,
    idleSpeedMsPerCycle: Int = 1200
) {
    // Determine if incoming levels are effectively silent/static
    val recent = if (levels.size >= barCount) levels.takeLast(barCount) else List(barCount - levels.size) { 0f } + levels
    val isSilent = recent.isEmpty() || recent.all { it <= silentThreshold }

    // Idle animation phase (0..1) for synthesized bars when silent
    val phase by if (animateWhenSilent) {
        val t = rememberInfiniteTransition(label = "waveIdle")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = idleSpeedMsPerCycle, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        // No animation requested
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    val bars: List<Float> = if (!isSilent) {
        recent
    } else {
        // Synthesize a flowing sine-like pattern for a call-like feel
        val twoPi = (Math.PI * 2).toFloat()
        List(barCount) { i ->
            val x = (i.toFloat() / barCount.toFloat()) * twoPi
            val a = 0.35f // base amplitude
            val m = 0.65f // max additional amplitude
            val p = (phase * twoPi)
            val value = abs(sin(x + p)) // 0..1
            (a + (m * value)).coerceIn(0f, 1f)
        }
    }

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(barGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { lvl ->
            val h = (lvl.coerceIn(0f, 1f) * height.value).dp
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(h)
                    .background(barColor, RoundedCornerShape(4.dp))
            )
        }
    }
}


@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.runtime.Composable
private fun WaveformPreview() {
    val levels = List(48) { i -> (kotlin.math.abs(kotlin.math.sin(i / 5f)) * 0.8f) }
    Waveform(levels = levels, barCount = 48, barWidth = 6.dp, barGap = 2.dp)
}
