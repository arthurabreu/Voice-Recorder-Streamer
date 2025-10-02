package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceWsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LiveStreamingViewModel : ViewModel() {
    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status

    private val _lastServerMessage = MutableStateFlow("")
    val lastServerMessage: StateFlow<String> = _lastServerMessage

    // TODO: Replace with your ADK endpoint and token provider
    private val wsClient = VoiceWsClient(
        url = "wss://example.com/adk/voice",
        authTokenProvider = { "" } // Plug your token provider here
    )

    private val streamer = VoiceStreamer(wsClient, viewModelScope)

    fun start(language: String = "pt-BR") {
        _status.value = "Connecting..."
        wsClient.connect(
            scope = viewModelScope,
            onOpen = { _status.value = "Streaming" },
            onMessage = { msg -> _lastServerMessage.value = msg },
            onFailure = { t -> _status.value = "Error: ${t.message}" },
            onClosed = { _, _ -> _status.value = "Closed" }
        )
        streamer.startStreaming(language)
    }

    fun stop() {
        streamer.stopStreaming()
        _status.value = "Stopped"
    }
}