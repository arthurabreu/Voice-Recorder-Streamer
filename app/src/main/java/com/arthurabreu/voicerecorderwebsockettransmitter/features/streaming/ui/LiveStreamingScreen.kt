package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation.LiveStreamingViewModel

@Composable
fun LiveStreamingScreen(onBack: (() -> Unit)? = null) {
    val vm: LiveStreamingViewModel = viewModel()
    val status by vm.status.collectAsState()
    val serverMsg by vm.lastServerMessage.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Live streaming (WebSocket)", style = MaterialTheme.typography.titleLarge)
        Text("Status: $status")
        if (serverMsg.isNotBlank()) Text("Server: $serverMsg")

        Button(onClick = { vm.start("pt-BR") }) { Text("Start streaming") }
        Button(onClick = { vm.stop() }) { Text("Stop") }
        if (onBack != null) Button(onClick = onBack) { Text("Voltar") }
    }
}