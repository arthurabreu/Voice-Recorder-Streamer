package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.FakeVoiceWsClient
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceWsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class LiveStreamingViewModel : ViewModel() {
    // Exposed list of saved recordings discovered on device (cache/files)
    private val _savedItems = MutableStateFlow<List<File>>(emptyList())
    val savedItems: StateFlow<List<File>> = _savedItems
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

    // Emulation toggle and WS config
    private var emulate: Boolean = true
    private var wsUrl: String = "wss://example.com/adk/voice"
    private var tokenProvider: suspend () -> String = { "" }

    private var wsClient: VoiceSocket = if (emulate) FakeVoiceWsClient() else VoiceWsClient(wsUrl, tokenProvider)

    private var streamer: VoiceStreamer = VoiceStreamer(wsClient, viewModelScope).apply {
        setOnLevelListener { level -> pushLevel(level) }
    }

    // Track whether the user initiated a stop, so we don't reset UI to Idle on normal close
    private var isStopping: Boolean = false

    // Recording helper
    private var tempFile: File? = null

    fun setEmulationMode(enabled: Boolean) {
        emulate = enabled
        wsClient.close()
        wsClient = if (emulate) FakeVoiceWsClient() else VoiceWsClient(wsUrl, tokenProvider)
        streamer = VoiceStreamer(wsClient, viewModelScope).apply {
            setOnLevelListener { level -> pushLevel(level) }
        }
    }

    fun setWebSocketUrl(url: String) { wsUrl = url }
    fun setTokenProvider(provider: suspend () -> String) { tokenProvider = provider }

    fun start(language: String = "pt-BR", outputDir: File) {
        // Prepare temp file
        tempFile = File(outputDir, "stream_${System.currentTimeMillis()}.wav")
        _savedFile.value = null

        _status.value = if (emulate) "Streaming (fake WS)" else "Connecting..."
        _uiState.value = UiState.Streaming

        wsClient.connect(
            scope = viewModelScope,
            onOpen = {
                _status.value = "Streaming"
                showBalloon("Streaming started", Balloon.Type.Success)
                tempFile?.let { streamer.startRecordingTo(it) }
                streamer.startStreaming(language)
            },
            onMessage = { msg -> _lastServerMessage.value = msg },
            onFailure = { t ->
                _status.value = "Error: ${t.message}"
                if (isStopping) {
                    // If we are stopping, do not override the Stopped state; swallow failure
                    isStopping = false
                } else {
                    _uiState.value = UiState.Idle
                    showBalloon("Error: ${t.message}", Balloon.Type.Error)
                }
            },
            onClosed = { _, reason ->
                _status.value = "Closed"
                if (isStopping) {
                    // We initiated the stop; keep UI in Stopped state so Save/Cancel are visible
                    isStopping = false
                } else {
                    _uiState.value = UiState.Idle
                    showBalloon("Closed: $reason", Balloon.Type.Error)
                }
            }
        )
    }

    fun stop() {
        isStopping = true
        streamer.stopStreaming()
        _status.value = "Stopped"
        _uiState.value = UiState.Stopped
    }

    fun cancel() {
        // Discard recording and reset
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
            streamer.stopRecording()
            _savedFile.value = tempFile
            _uiState.value = UiState.Saved
            showBalloon("Saved recording", Balloon.Type.Success)
            // Show player overlay
            _showPlayerOverlay.value = tempFile
            // Add to saved items list
            tempFile?.let { f ->
                if (f.exists()) {
                    val current = _savedItems.value.toMutableList()
                    // avoid duplicates
                    if (current.none { it.absolutePath == f.absolutePath }) {
                        current.add(0, f)
                        _savedItems.value = current.sortedByDescending { it.lastModified() }
                    }
                }
            }
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

    fun showBalloon(message: String, type: Balloon.Type) {
        _balloon.value = Balloon(message, type)
    }

    fun consumeBalloon() { _balloon.value = null }

    fun dismissPlayerOverlay() { _showPlayerOverlay.value = null }

    // Refresh saved items by scanning given directories for audio files (.wav, .m4a)
    fun refreshSaved(vararg dirs: File) {
        val found = mutableListOf<File>()
        dirs.forEach { dir ->
            dir.listFiles()?.forEach { f ->
                val name = f.name.lowercase()
                if (f.isFile && (name.endsWith(".wav") || name.endsWith(".m4a"))) {
                    found.add(f)
                }
            }
        }
        _savedItems.value = found.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
    }

    // Delete a saved recording from disk and update list
    fun deleteFile(file: File) {
        val ok = try { file.delete() } catch (_: Throwable) { false }
        if (ok) {
            _savedItems.value = _savedItems.value.filterNot { it.absolutePath == file.absolutePath }
            showBalloon("Deleted", Balloon.Type.Success)
        } else {
            showBalloon("Failed to delete", Balloon.Type.Error)
        }
    }

    // Show the mini player overlay for a given file
    fun preview(file: File) {
        _showPlayerOverlay.value = file
    }
}