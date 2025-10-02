package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation.LiveStreamingViewModel

@Composable
fun LiveStreamingScreen(onBack: (() -> Unit)? = null) {
    val vm: LiveStreamingViewModel = viewModel()
    val status by vm.status.collectAsState()
    val serverMsg by vm.lastServerMessage.collectAsState()
    val levels by vm.levels.collectAsState()
    val balloon by vm.balloon.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Live streaming (WebSocket)", style = MaterialTheme.typography.titleLarge)
            Text("Status: $status")
            if (serverMsg.isNotBlank()) Text("Server: $serverMsg")

            Waveform(levels = levels, barCount = 48, barWidth = 6.dp, barGap = 2.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.start("pt-BR") }) { Text("Start streaming") }
                Button(onClick = { vm.stop() }) { Text("Stop") }
                if (onBack != null) Button(onClick = onBack) { Text("Voltar") }
            }
        }

        AnimatedVisibility(
            visible = balloon != null,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(300))
        ) {
            val b = balloon
            if (b != null) {
                TopBalloon(
                    message = b.message,
                    color = if (b.type == LiveStreamingViewModel.Balloon.Type.Error) Color(0xFFB00020) else Color(0xFF2E7D32),
                    onDismiss = { vm.consumeBalloon() }
                )
            }
        }
    }
}

@Composable
private fun Waveform(levels: List<Float>, barCount: Int, barWidth: Dp, barGap: Dp) {
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

@Composable
private fun TopBalloon(message: String, color: Color, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(message, color = Color.White)
        }
    }
    // Auto dismiss after a short delay
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2500)
        onDismiss()
    }
}