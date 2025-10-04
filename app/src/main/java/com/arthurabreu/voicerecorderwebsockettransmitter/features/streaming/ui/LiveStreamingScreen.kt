package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation.LiveStreamingViewModel
import java.io.File

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

            val uiState by vm.uiState.collectAsState()
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                AnimatedContent(targetState = uiState, label = "startStopSave") { st ->
                    when (st) {
                        LiveStreamingViewModel.UiState.Streaming -> {
                            Button(
                                onClick = { vm.stop() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                            ) { Text("Stop Streaming", color = Color.White) }
                        }
                        LiveStreamingViewModel.UiState.Stopped -> {
                            Button(
                                onClick = { vm.save() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                            ) { Text("Save", color = Color.White) }
                        }
                        else -> {
                            Button(
                                onClick = { vm.start("pt-BR", context.cacheDir) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) { Text("Start Streaming", color = Color.White) }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState == LiveStreamingViewModel.UiState.Stopped) {
                    Button(
                        onClick = { vm.cancel() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                    ) { Text("Cancel", color = Color.White) }
                }

                if (onBack != null && uiState != LiveStreamingViewModel.UiState.Stopped) Button(onClick = onBack) { Text("Voltar") }
            }
            // Saved recordings list
            val saved by vm.savedItems.collectAsState()
            LaunchedEffect(Unit) {
                // Load any existing files (from before) into the list
                vm.refreshSaved(context.cacheDir, context.filesDir)
            }
            AnimatedVisibility(visible = saved.isNotEmpty()) {
                SavedRecordingsList(
                    items = saved,
                    onPlay = { vm.preview(it) },
                    onDelete = { vm.deleteFile(it) }
                )
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

        // Player overlay after successful save
        val overlayFile by vm.showPlayerOverlay.collectAsState()
        AnimatedVisibility(
            visible = overlayFile != null,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(350)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(350))
        ) {
            overlayFile?.let { f ->
                PlayerOverlay(filePath = f.absolutePath, onDismiss = { vm.dismissPlayerOverlay() })
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
private fun SavedRecordingsList(
    items: List<File>,
    onPlay: (File) -> Unit,
    onDelete: (File) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0F0F))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Saved recordings", style = MaterialTheme.typography.titleMedium, color = Color.White)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.absolutePath }) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1C))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, color = Color(0xFFEEEEEE))
                        Text("${file.length()/1024} KB", color = Color(0xFFAAAAAA))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onPlay(file) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                            Text("Play", color = Color.White)
                        }
                        Button(onClick = { onDelete(file) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))) {
                            Text("Delete", color = Color.White)
                        }
                    }
                }
            }
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
        kotlinx.coroutines.delay(1500)
        onDismiss()
    }
}

@Composable
private fun PlayerOverlay(filePath: String, onDismiss: () -> Unit) {
    val mediaPlayer =remember {
        android.media.MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepare()
            } catch (_: Throwable) {}
        }
    }
    var isPlaying by remember {mutableStateOf(false) }
    var progressMs by remember { mutableIntStateOf(0) }
    val duration = try { mediaPlayer.duration } catch (_: Throwable) { 0 }

    // Progress updater
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try { progressMs = mediaPlayer.currentPosition } catch (_: Throwable) {}
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
            try { mediaPlayer.release() } catch (_: Throwable) {}
        }
    }
}
