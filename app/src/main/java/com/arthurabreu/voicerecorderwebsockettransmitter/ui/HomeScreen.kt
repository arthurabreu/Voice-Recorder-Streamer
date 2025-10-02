package com.arthurabreu.voicerecorderwebsockettransmitter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.LiveStreamingScreen
import com.arthurabreu.voicerecorderwebsockettransmitter.features.saveandsend.ui.SaveAndSendScreen
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.ui.TranscriptionScreen

enum class HomeNav { HOME, TRANSCRIPTION, LIVE, SAVE_SEND }

@Composable
fun HomeScreen() {
    var screen by remember { mutableStateOf(HomeNav.HOME) }

    when (screen) {
        HomeNav.HOME -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Selecione uma opção", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { screen = HomeNav.TRANSCRIPTION }) { Text("Transcription") }
            Button(onClick = { screen = HomeNav.LIVE }) { Text("Live streaming") }
            Button(onClick = { screen = HomeNav.SAVE_SEND }) { Text("Save and send audio") }
        }
        HomeNav.TRANSCRIPTION -> TranscriptionScreen()
        HomeNav.LIVE -> LiveStreamingScreen(onBack = { screen = HomeNav.HOME })
        HomeNav.SAVE_SEND -> SaveAndSendScreen(onBack = { screen = HomeNav.HOME })
    }
}