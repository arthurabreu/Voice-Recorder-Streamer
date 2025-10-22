package com.arthurabreu.voicerecorderwebsockettransmitter.features.saveandsend.ui

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arthurabreu.voicerecorderwebsockettransmitter.features.saveandsend.data.FileRecorder
import com.arthurabreu.voicerecorderwebsockettransmitter.features.saveandsend.data.SendFileOverWs
import com.arthurabreu.voicerecorderwebsockettransmitter.features.saveandsend.data.VoiceWsClient
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SaveAndSendScreen(onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentFile by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("Idle") }

    val recorder = remember { FileRecorder() }

    // TODO: Replace with your ADK endpoint and token provider
    val wsClient = remember {
        VoiceWsClient(
            url = "wss://example.com/adk/voice",
            authTokenProvider = { "" }
        )
    }
    val sender = remember { SendFileOverWs(wsClient, scope) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Salvar e enviar áudio", style = MaterialTheme.typography.titleLarge)
        Text("Status: $status")
        Button(onClick = {
            val dir = ctx.cacheDir
            currentFile = recorder.start(dir)
            status = "Gravando..."
        }) { Text("Gravar") }
        Button(onClick = {
            val f = recorder.stop()
            status = if (f != null) "Gravação salva: ${f.name}" else "Parado"
        }) { Text("Parar") }
        Button(onClick = {
            val f = currentFile
            if (f != null && f.exists()) {
                status = "Enviando..."
                sender.send(f)
                status = "Enviado (aguarde resposta no backend)"
            } else {
                status = "Nenhum arquivo para enviar"
            }
        }) { Text("Enviar arquivo via WebSocket") }
        if (onBack != null) Button(onClick = onBack) { Text("Voltar") }
    }
}