package com.arthurabreu.voicerecorderwebsockettransmitter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.SpeechToTextManager
import com.arthurabreu.voicerecorderwebsockettransmitter.ui.theme.VoiceRecorderWebSocketTransmitterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceRecorderWebSocketTransmitterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
                        SpeechToTextScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeechToTextScreen() {
    val context = LocalContext.current
    val speechManager = remember { SpeechToTextManager(context) }
    val isListening by speechManager.isListening.collectAsState()
    val partial by speechManager.partialText.collectAsState()
    val finalText by speechManager.finalText.collectAsState()
    val error by speechManager.error.collectAsState()

    val scope = rememberCoroutineScope()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            speechManager.startListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose { speechManager.release() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(16.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Voice to Text",
            style = MaterialTheme.typography.headlineMedium
        )
        if (error != null) {
            Text(text = error ?: "", color = MaterialTheme.colorScheme.error)
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Transcription", style = MaterialTheme.typography.titleMedium)
                Text(text = finalText)
                if (partial.isNotBlank()) {
                    Text(text = "… $partial", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
        Button(onClick = {
            if (!hasAudioPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else if (isListening) {
                speechManager.stopListening()
            } else {
                speechManager.startListening()
            }
        }) {
            Text(if (isListening) "Stop Listening" else "Start Listening")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeechToTextPreview() {
    VoiceRecorderWebSocketTransmitterTheme {
        Column(Modifier.padding(16.dp)) {
            Text("Voice to Text")
            Button(onClick = { }) { Text("Start Listening") }
        }
    }
}