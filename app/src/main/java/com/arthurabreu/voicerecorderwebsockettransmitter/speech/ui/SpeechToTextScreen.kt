package com.arthurabreu.voicerecorderwebsockettransmitter.speech.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.presentation.SpeechToTextViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SpeechToTextScreen(
    viewModel: SpeechToTextViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val micPermission = rememberMicrophonePermission()

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                ControlButton(
                    isListening = uiState.isListening,
                    hasAudioPermission = micPermission.hasPermission,
                    requestPermission = { micPermission.requestPermission { viewModel.onStart() } },
                    onStart = { viewModel.onStart() },
                    onStop = { viewModel.onStop() }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scrollable content area takes remaining space
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header()
                uiState.error?.let { ErrorText(it) }
                TranscriptionCard(finalText = uiState.finalText, partial = uiState.partialText)
            }
        }
    }
}

@Composable
private fun Header() {
    Text(
        text = "Voice to Text",
        style = MaterialTheme.typography.headlineMedium
    )
}

@Composable
private fun ErrorText(message: String) {
    Text(text = message, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun TranscriptionCard(finalText: String, partial: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Transcription", style = MaterialTheme.typography.titleMedium)
            Text(text = finalText)
            if (partial.isNotBlank()) {
                Text(
                    text = "… $partial",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    isListening: Boolean,
    hasAudioPermission: Boolean,
    requestPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Button(onClick = {
        if (!hasAudioPermission) {
            requestPermission()
        } else if (isListening) {
            onStop()
        } else {
            onStart()
        }
    }, modifier = Modifier.fillMaxWidth()) {
        Text(if (isListening) "Stop Listening" else "Start Listening")
    }
}
