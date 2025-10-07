package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.Balloon
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.StreamingState
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.UiState

interface LiveStreamingController {
    val state: StateFlow<StreamingState>

    fun setEmulationMode(enabled: Boolean)
    fun setWebSocketUrl(url: String)
    fun setTokenProvider(provider: suspend () -> String)

    fun start(language: String = "pt-BR", outputDir: File)
    fun stop()
    fun cancel()
    fun save()

    fun refreshSaved(vararg dirs: File)
    fun deleteFile(file: File)
    fun preview(file: File)

    fun consumeBalloon()
    fun dismissPlayerOverlay()
}

interface LiveStreamingControllerFactory {
    fun create(scope: CoroutineScope): LiveStreamingController
}

class DefaultLiveStreamingControllerFactory(
    private val socketFactory: VoiceSocketFactory,
    private val streamerFactory: VoiceStreamerFactory
) : LiveStreamingControllerFactory {
    override fun create(scope: CoroutineScope): LiveStreamingController {
        return DefaultLiveStreamingController(socketFactory, streamerFactory, scope)
    }
}

private class DefaultLiveStreamingController(
    private val socketFactory: VoiceSocketFactory,
    private val streamerFactory: VoiceStreamerFactory,
    private val scope: CoroutineScope
) : LiveStreamingController {

    // Unified streaming state
    private val _state = MutableStateFlow(StreamingState())
    override val state: StateFlow<StreamingState> = _state

    // Emulation toggle and WS config
    private var emulate: Boolean = false
    private var wsUrl: String = "ws://192.168.18.18:8080"
    private var tokenProvider: TokenProvider = LambdaTokenProvider { "" }

    private var wsClient: VoiceSocket = socketFactory.create(emulate, wsUrl, tokenProvider)

    private var streamer: VoiceStreamer = streamerFactory.create(wsClient, scope).apply {
        setOnLevelListener { level -> pushLevel(level) }
    }

    // Track whether the user initiated a stop, so we don't reset UI to Idle on normal close
    private var isStopping: Boolean = false

    // Recording helper
    private var tempFile: File? = null

    override fun setEmulationMode(enabled: Boolean) {
        emulate = enabled
        wsClient.close()
        wsClient = socketFactory.create(emulate, wsUrl, tokenProvider)
        streamer = streamerFactory.create(wsClient, scope).apply {
            setOnLevelListener { level -> pushLevel(level) }
        }
    }

    override fun setWebSocketUrl(url: String) {
        wsUrl = url
        wsClient.close()
        wsClient = socketFactory.create(emulate, wsUrl, tokenProvider)
        streamer = streamerFactory.create(wsClient, scope).apply {
            setOnLevelListener { level -> pushLevel(level) }
        }
    }

    override fun setTokenProvider(provider: suspend () -> String) {
        tokenProvider = LambdaTokenProvider(provider)
        wsClient.close()
        wsClient = socketFactory.create(emulate, wsUrl, tokenProvider)
        streamer = streamerFactory.create(wsClient, scope).apply {
            setOnLevelListener { level -> pushLevel(level) }
        }
    }

    override fun start(language: String, outputDir: File) {
        // Prepare temp file
        tempFile = File(outputDir, "stream_${System.currentTimeMillis()}.wav")
        _state.value = _state.value.copy(savedFile = null)

        _state.value = _state.value.copy(status = if (emulate) "Streaming (fake WS)" else "Connecting...")
        _state.value = _state.value.copy(uiState = UiState.Streaming)

        wsClient.connect(
            scope = scope,
            onOpen = {
                _state.value = _state.value.copy(status = "Streaming")
                showBalloon("Streaming started", Balloon.Type.Success)
                tempFile?.let { streamer.startRecordingTo(it) }
                streamer.startStreaming(language)
            },
            onMessage = { msg -> _state.value = _state.value.copy(lastServerMessage = msg) },
            onFailure = { t ->
                _state.value = _state.value.copy(status = "Error: ${t.message}")
                if (isStopping) {
                    // If we are stopping, do not override the Stopped state; swallow failure
                    isStopping = false
                } else {
                    _state.value = _state.value.copy(uiState = UiState.Idle)
                    showBalloon("Error: ${t.message}", Balloon.Type.Error)
                }
            },
            onClosed = { _, reason ->
                _state.value = _state.value.copy(status = "Closed")
                if (isStopping) {
                    // We initiated the stop; keep UI in Stopped state so Save/Cancel are visible
                    isStopping = false
                } else {
                    _state.value = _state.value.copy(uiState = UiState.Idle)
                    showBalloon("Closed: $reason", Balloon.Type.Error)
                }
            }
        )
    }

    override fun stop() {
        isStopping = true
        streamer.stopStreaming()
        _state.value = _state.value.copy(status = "Stopped", uiState = UiState.Stopped)
    }

    override fun cancel() {
        // Discard recording and reset
        tempFile?.delete()
        tempFile = null
        _state.value = _state.value.copy(
            savedFile = null,
            uiState = UiState.Idle,
            status = "Idle",
            levels = emptyList()
        )
    }

    override fun save() {
        _state.value = _state.value.copy(uiState = UiState.Saving)
        try {
            streamer.stopRecording()
            _state.value = _state.value.copy(savedFile = tempFile, uiState = UiState.Saved)
            showBalloon("Saved recording", Balloon.Type.Success)
            // Show player overlay
            _state.value = _state.value.copy(showPlayerOverlay = tempFile)
            // Add to saved items list
            tempFile?.let { f ->
                if (f.exists()) {
                    val current = _state.value.savedItems.toMutableList()
                    // avoid duplicates
                    if (current.none { it.absolutePath == f.absolutePath }) {
                        current.add(0, f)
                        _state.value = _state.value.copy(
                            savedItems = current.sortedByDescending { it.lastModified() }
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            showBalloon("Save failed: ${t.message}", Balloon.Type.Error)
            _state.value = _state.value.copy(uiState = UiState.Stopped)
        }
    }

    private fun pushLevel(level: Float) {
        val list = _state.value.levels.toMutableList()
        list += level
        val max = 60
        if (list.size > max) repeat(list.size - max) { list.removeAt(0) }
        _state.value = _state.value.copy(levels = list)
    }

    private fun showBalloon(message: String, type: Balloon.Type) {
        _state.value = _state.value.copy(balloon = Balloon(message, type))
    }

    override fun consumeBalloon() { _state.value = _state.value.copy(balloon = null) }

    override fun dismissPlayerOverlay() { _state.value = _state.value.copy(showPlayerOverlay = null) }

    override fun refreshSaved(vararg dirs: File) {
        val found = mutableListOf<File>()
        dirs.forEach { dir ->
            dir.listFiles()?.forEach { f ->
                val name = f.name.lowercase()
                if (f.isFile && (name.endsWith(".wav") || name.endsWith(".m4a"))) {
                    found.add(f)
                }
            }
        }
        _state.value = _state.value.copy(
            savedItems = found.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
        )
    }

    override fun deleteFile(file: File) {
        val ok = try { file.delete() } catch (_: Throwable) { false }
        if (ok) {
            _state.value = _state.value.copy(
                savedItems = _state.value.savedItems.filterNot { it.absolutePath == file.absolutePath }
            )
            showBalloon("Deleted", Balloon.Type.Success)
        } else {
            showBalloon("Failed to delete", Balloon.Type.Error)
        }
    }

    override fun preview(file: File) {
        _state.value = _state.value.copy(showPlayerOverlay = file)
    }
}
