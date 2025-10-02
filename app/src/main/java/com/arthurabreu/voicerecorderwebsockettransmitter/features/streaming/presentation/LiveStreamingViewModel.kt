package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.PcmWavWriter
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceWsClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class LiveStreamingViewModel : ViewModel() {
    enum class UiState { Idle, Streaming, Stopped, Saving, Saved }
    data class Balloon(val message: String, val type: Type) {
        enum class Type { Success, Error }
    }

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status

    private val _uiState = MutableStateFlow(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val _lastServerMessage = MutableStateFlow("")
    val lastServerMessage: StateFlow<String> = _lastServerMessage

    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = _levels

    private val _balloon = MutableStateFlow<Balloon?>(null)
    val balloon: StateFlow<Balloon?> = _balloon

    private val _savedFile: MutableStateFlow<File?> = MutableStateFlow(null)
    val savedFile: StateFlow<File?> = _savedFile

    private val _showPlayerOverlay = MutableStateFlow<File?>(null)
    val showPlayerOverlay: StateFlow<File?> = _showPlayerOverlay

    // Emulation flag: set to true to simulate streaming without a real server
    private val emulate = true
    private var emulateJob: Job? = null

    // Recording helpers
    private var tempFile: File? = null
    private var emuWriter: PcmWavWriter? = null
    private var emuPhase: Double = 0.0

    // TODO: Replace with your ADK endpoint and token provider when not emulating
    private val wsClient = VoiceWsClient(
        url = "wss://example.com/adk/voice",
        authTokenProvider = { "" } // Plug your token provider here
    )

    private val streamer = VoiceStreamer(wsClient, viewModelScope).apply {
        setOnLevelListener { level -> pushLevel(level) }
    }

    fun start(language: String = "pt-BR", outputDir: File) {
        // Prepare temp file
        tempFile = File(outputDir, "stream_${System.currentTimeMillis()}.wav")
        _savedFile.value = null
        if (emulate) {
            startEmulationRecording()
            return
        }
        _status.value = "Connecting..."
        _uiState.value = UiState.Streaming
        wsClient.connect(
            scope = viewModelScope,
            onOpen = {
                _status.value = "Streaming"
                showBalloon("Streaming started", Balloon.Type.Success)
            },
            onMessage = { msg -> _lastServerMessage.value = msg },
            onFailure = { t ->
                _status.value = "Error: ${t.message}"
                _uiState.value = UiState.Idle
                showBalloon("Error: ${t.message}", Balloon.Type.Error)
            },
            onClosed = { _, reason ->
                _status.value = "Closed"
                _uiState.value = UiState.Idle
                showBalloon("Closed: $reason", Balloon.Type.Error)
            }
        )
        tempFile?.let { streamer.startRecordingTo(it) }
        streamer.startStreaming(language)
    }

    fun stop() {
        if (emulate) {
            emulateJob?.cancel()
            emulateJob = null
            _status.value = "Stopped"
            _uiState.value = UiState.Stopped
            return
        }
        streamer.stopStreaming()
        _status.value = "Stopped"
        _uiState.value = UiState.Stopped
    }

    fun cancel() {
        // Discard recording and reset
        try { emuWriter?.close() } catch (_: Throwable) {}
        emuWriter = null
        tempFile?.delete()
        tempFile = null
        _savedFile.value = null
        _uiState.value = UiState.Idle
        _status.value = "Idle"
        _levels.value = emptyList()
    }

    fun save() {
        _uiState.value = UiState.Saving
        try {
            if (emulate) {
                emuWriter?.close()
                emuWriter = null
            } else {
                streamer.stopRecording()
            }
            _savedFile.value = tempFile
            _uiState.value = UiState.Saved
            showBalloon("Saved recording", Balloon.Type.Success)
            // Show player overlay
            _showPlayerOverlay.value = tempFile
        } catch (t: Throwable) {
            showBalloon("Save failed: ${t.message}", Balloon.Type.Error)
            _uiState.value = UiState.Stopped
        }
    }

    private fun pushLevel(level: Float) {
        val list = _levels.value.toMutableList()
        list += level
        val max = 60
        if (list.size > max) repeat(list.size - max) { list.removeAt(0) }
        _levels.value = list
    }

    private fun startEmulationRecording() {
        if (emulateJob != null) return
        _status.value = "Streaming (emulated)"
        _uiState.value = UiState.Streaming
        _lastServerMessage.value = ""
        showBalloon("Streaming started (emulated)", Balloon.Type.Success)
        // Open WAV writer
        tempFile?.let {
            val w = PcmWavWriter(16000, 1)
            w.open(it)
            emuWriter = w
        }
        emulateJob = viewModelScope.launch {
            var t = 0.0
            var sent = 0
            val sampleRate = 16000
            val frameSamples = 640 // ~40ms
            val buf = ShortArray(frameSamples)
            while (true) {
                // Generate a smooth sine wave with slight noise
                val level = (0.5 + 0.5 * sin(2 * PI * 0.8 * t)).toFloat()
                val noisy = (level + (Math.random().toFloat() - 0.5f) * 0.2f).coerceIn(0f, 1f)
                pushLevel(noisy)
                // Synthesize audio matching the level
                val amp = (noisy.toDouble() * 0.6 + 0.2).coerceIn(0.0, 1.0)
                val freq = 440.0
                for (i in 0 until frameSamples) {
                    val sample = kotlin.math.sin(2 * PI * freq * (emuPhase / sampleRate))
                    val s = (sample * amp * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buf[i] = s.toShort()
                    emuPhase += 1
                }
                emuWriter?.write(buf)
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

    fun dismissPlayerOverlay() { _showPlayerOverlay.value = null }
}