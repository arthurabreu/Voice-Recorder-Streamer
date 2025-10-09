package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp

@Composable
fun PlayerOverlay(filePath: String, onDismiss: () -> Unit) {
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val mediaPlayer = remember(isPreview, filePath) {
        if (isPreview) null else try {
            android.media.MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
            }
        } catch (_: Throwable) { null }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var progressMs by remember { mutableIntStateOf(0) }
    val duration = try { mediaPlayer?.duration ?: 0 } catch (_: Throwable) { 0 }

    // Progress updater
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try { progressMs = mediaPlayer?.currentPosition ?: 0 } catch (_: Throwable) {}
            kotlinx.coroutines.delay(200)
        }
    }
    // Auto dismiss after a few seconds
    LaunchedEffect(filePath) {
        kotlinx.coroutines.delay(6000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E))
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Saved recording", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            try {
                                if (mediaPlayer == null) return@Button
                                if (isPlaying) { mediaPlayer.pause(); isPlaying = false } else { mediaPlayer.start(); isPlaying = true }
                            } catch (_: Throwable) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Text(if (isPlaying) "Pause" else "Play", color = Color.White) }
                    Text("${progressMs/1000}s / ${duration/1000}s", color = Color(0xFFEEEEEE))
                }
            }
        }
    }
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer?.release() } catch (_: Throwable) {}
        }
    }
}


@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.runtime.Composable
private fun PlayerOverlayPreview() {
    PlayerOverlay(filePath = "", onDismiss = {})
}
