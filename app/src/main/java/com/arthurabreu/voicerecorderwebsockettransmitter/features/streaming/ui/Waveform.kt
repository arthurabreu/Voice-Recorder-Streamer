package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.runtime.Composable
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

@Composable
fun Waveform(levels: List<Float>, barCount: Int, barWidth: Dp, barGap: Dp) {
    val bars = if (levels.isEmpty()) List(barCount) { 0f } else {
        val src = if (levels.size >= barCount) levels.takeLast(barCount) else List(barCount - levels.size) { 0f } + levels
        src
    }
    val maxHeight = 120.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(maxHeight).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101010)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(barGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { lvl ->
            val h = (lvl.coerceIn(0f, 1f) * maxHeight.value).dp
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(h)
                    .background(Color(0xFF64B5F6), RoundedCornerShape(4.dp))
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
