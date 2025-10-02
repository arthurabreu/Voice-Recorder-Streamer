package com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.presentation.SpeechToTextViewModel
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun SpeechToTextScreen(
    viewModel: SpeechToTextViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val micPermission = rememberMicrophonePermission()

    // Bottom bar now has a language selector beside the Start/Stop button
    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                // Keep the selected language locally in the screen
                val options = listOf(
                    LanguageOption("Português (Brasil)", "pt-BR"),
                    LanguageOption("Español", "es-ES"),
                    LanguageOption("English", "en-US")
                )
                val defaultTag = Locale.getDefault().toLanguageTag()
                val defaultOption = options.firstOrNull { opt ->
                    // match by language code prefix (pt, es, en)
                    defaultTag.startsWith(opt.tag.substring(0, 2), ignoreCase = true)
                } ?: options.last()
                val selectedState = remember { mutableStateOf(defaultOption) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LanguageSelectorButton(
                        selected = selectedState.value,
                        options = options,
                        onSelected = { selectedState.value = it },
                        modifier = Modifier.weight(1f)
                    )
                    ControlButton(
                        isListening = uiState.isListening,
                        hasAudioPermission = micPermission.hasPermission,
                        requestPermission = { micPermission.requestPermission { viewModel.onStart(selectedState.value.tag) } },
                        onStart = { viewModel.onStart(selectedState.value.tag) },
                        onStop = { viewModel.onStop() },
                        modifier = Modifier.weight(1f)
                    )
                }
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
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(onClick = {
        if (!hasAudioPermission) {
            requestPermission()
        } else if (isListening) {
            onStop()
        } else {
            onStart()
        }
    }, modifier = modifier.fillMaxWidth()) {
        Text(if (isListening) "Stop Listening" else "Start Listening")
    }
}


// Represent a language choice with label and BCP-47 language tag
private data class LanguageOption(val label: String, val tag: String)

@Composable
private fun LanguageSelectorButton(
    selected: LanguageOption,
    options: List<LanguageOption>,
    onSelected: (LanguageOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Button(onClick = { expanded.value = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label)
        }
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded.value = false
                    }
                )
            }
        }
    }
}
