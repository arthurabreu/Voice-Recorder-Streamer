package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceWsClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class LiveStreamingViewModel : ViewModel() {
    data class Balloon(val message: String, val type: Type) {
        enum class Type { Success, Error }
    }

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status

    private val _lastServerMessage = MutableStateFlow("")
    val lastServerMessage: StateFlow<String> = _lastServerMessage

    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = _levels

    private val _balloon = MutableStateFlow<Balloon?>(null)
    val balloon: StateFlow<Balloon?> = _balloon

    // Emulation flag: set to true to simulate streaming without a real server
    private val emulate = true
    private var emulateJob: Job? = null

    // TODO: Replace with your ADK endpoint and token provider when not emulating
    private val wsClient = VoiceWsClient(
        url = "wss://example.com/adk/voice",
        authTokenProvider = { "" } // Plug your token provider here
    )

    private val streamer = VoiceStreamer(wsClient, viewModelScope).apply {
        setOnLevelListener { level -> pushLevel(level) }
    }

    fun start(language: String = "pt-BR") {
        if (emulate) {
            startEmulation()
            return
        }
        _status.value = "Connecting..."
        wsClient.connect(
            scope = viewModelScope,
            onOpen = {
                _status.value = "Streaming"
                showBalloon("Streaming started", Balloon.Type.Success)
            },
            onMessage = { msg -> _lastServerMessage.value = msg },
            onFailure = { t ->
                _status.value = "Error: ${t.message}"
                showBalloon("Error: ${t.message}", Balloon.Type.Error)
            },
            onClosed = { _, reason ->
                _status.value = "Closed"
                showBalloon("Closed: $reason", Balloon.Type.Error)
            }
        )
        streamer.startStreaming(language)
    }

    fun stop() {
        if (emulate) {
            emulateJob?.cancel()
            emulateJob = null
            _status.value = "Stopped"
            return
        }
        streamer.stopStreaming()
        _status.value = "Stopped"
    }

    private fun pushLevel(level: Float) {
        val list = _levels.value.toMutableList()
        list += level
        val max = 60
        if (list.size > max) repeat(list.size - max) { list.removeAt(0) }
        _levels.value = list
    }

    private fun startEmulation() {
        if (emulateJob != null) return
        _status.value = "Streaming (emulated)"
        _lastServerMessage.value = ""
        showBalloon("Streaming started (emulated)", Balloon.Type.Success)
        emulateJob = viewModelScope.launch {
            var t = 0.0
            var sent = 0
            while (true) {
                // Generate a smooth sine wave with slight noise
                val level = (0.5 + 0.5 * sin(2 * PI * 0.8 * t)).toFloat()
                val noisy = (level + (Math.random().toFloat() - 0.5f) * 0.2f).coerceIn(0f, 1f)
                pushLevel(noisy)
                if (sent % 15 == 0) {
                    _lastServerMessage.value = "emulated bytes sent: ${sent * 1280}"
                }
                sent++
                t += 1.0 / 30.0
                delay(33)
            }
        }
    }

    fun showBalloon(message: String, type: Balloon.Type) {
        _balloon.value = Balloon(message, type)
    }

    fun consumeBalloon() { _balloon.value = null }
}