package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation.LiveStreamingViewModel
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.StreamingState
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.UiState
import org.koin.androidx.compose.koinViewModel

@Composable
fun LiveStreamingScreen(onBack: (() -> Unit)? = null) {
    val vm: LiveStreamingViewModel = koinViewModel()
    val state by vm.state.collectAsState()
    LiveStreamingContent(
        state = state,
        onStart = { lang, cache -> vm.start(lang, cache) },
        onStop = { vm.stop() },
        onSave = { vm.save() },
        onCancel = { vm.cancel() },
        onBack = onBack,
        onRefreshSaved = { cache, files -> vm.refreshSaved(cache, files) },
        onPlay = { vm.preview(it) },
        onDelete = { vm.deleteFile(it) },
        onConsumeBalloon = { vm.consumeBalloon() },
        onDismissOverlay = { vm.dismissPlayerOverlay() }
    )
}

@Composable
fun LiveStreamingContent(
    state: StreamingState,
    onStart: (String, java.io.File) -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onBack: (() -> Unit)? = null,
    onRefreshSaved: (java.io.File, java.io.File) -> Unit,
    onPlay: (java.io.File) -> Unit,
    onDelete: (java.io.File) -> Unit,
    onConsumeBalloon: () -> Unit,
    onDismissOverlay: () -> Unit
) {
    val status = state.status
    val serverMsg = state.lastServerMessage
    val levels = state.levels
    val balloon = state.balloon
    val savedItems = state.savedItems
    val overlayFile = state.showPlayerOverlay

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

            val uiState = state.uiState
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                AnimatedContent(targetState = uiState, label = "startStopSave") { st ->
                    when (st) {
                        UiState.Streaming -> {
                            Button(
                                onClick = { onStop() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                            ) { Text("Stop Streaming", color = Color.White) }
                        }
                        UiState.Stopped -> {
                            Button(
                                onClick = { onSave() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                            ) { Text("Save", color = Color.White) }
                        }
                        else -> {
                            Button(
                                onClick = { onStart("pt-BR", context.cacheDir) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) { Text("Start Streaming", color = Color.White) }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState == UiState.Stopped) {
                    Button(
                        onClick = { onCancel() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                    ) { Text("Cancel", color = Color.White) }
                }

                if (onBack != null && uiState != UiState.Stopped) Button(onClick = onBack) { Text("Voltar") }
            }
            LaunchedEffect(Unit) {
                // Load any existing files (from before) into the list
                onRefreshSaved(context.cacheDir, context.filesDir)
            }
            AnimatedVisibility(visible = savedItems.isNotEmpty()) {
                SavedRecordingsList(
                    items = savedItems,
                    onPlay = { onPlay(it) },
                    onDelete = { onDelete(it) }
                )
            }
        }

        AnimatedVisibility(
            visible = balloon != null,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(300))
        ) {
            if (balloon != null) {
                TopBalloon(
                    message = balloon.message,
                    color = if (balloon.type == com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.BalloonType.Error) Color(0xFFB00020) else Color(0xFF2E7D32),
                    onDismiss = { onConsumeBalloon() }
                )
            }
        }

        // Player overlay after successful save
        AnimatedVisibility(
            visible = overlayFile != null,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(350)),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(350))
        ) {
            overlayFile?.let { f ->
                PlayerOverlay(filePath = f.absolutePath, onDismiss = { onDismissOverlay() })
            }
        }
    }
}



@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LiveStreamingScreenPreview() {
    val sampleLevels = List(48) { i -> (kotlin.math.abs(kotlin.math.sin(i / 6f)) * 0.9f).toFloat() }
    val sampleFiles = listOf(
        java.io.File("/cache/rec_1.wav"),
        java.io.File("/cache/rec_2.wav")
    )
    LiveStreamingContent(
        state = com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.StreamingState(
            status = "Connected",
            uiState = com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.UiState.Streaming,
            lastServerMessage = "listening...",
            levels = sampleLevels,
            balloon = com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.Balloon(
                message = "Streaming iniciado",
                type = com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.BalloonType.Success
            ),
            savedItems = sampleFiles,
            showPlayerOverlay = null
        ),
        onStart = { _, _ -> },
        onStop = {},
        onSave = {},
        onCancel = {},
        onBack = {},
        onRefreshSaved = { _, _ -> },
        onPlay = {},
        onDelete = {},
        onConsumeBalloon = {},
        onDismissOverlay = {}
    )
}
