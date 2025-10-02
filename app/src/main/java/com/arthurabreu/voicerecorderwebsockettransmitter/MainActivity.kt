package com.arthurabreu.voicerecorderwebsockettransmitter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.arthurabreu.voicerecorderwebsockettransmitter.ui.HomeScreen
import com.arthurabreu.voicerecorderwebsockettransmitter.ui.theme.VoiceRecorderWebSocketTransmitterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceRecorderWebSocketTransmitterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(Modifier.fillMaxSize().padding(innerPadding)) {
                        HomeScreen()
                    }
                }
            }
        }
    }
}