package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.StreamingState
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Default implementation of [LiveStreamingController].
 *
 * This version has been decoupled from UI concerns:
 * - No balloons, overlays, or saved-items management.
 * - No waveform level tracking.
 * - No temp file recording orchestration here (capture still happens inside VoiceStreamer as needed).
 *
 * You can plug this controller into any ViewModel in this or another project
 * without bringing UI classes into the streaming flow.
 */
internal class DefaultLiveStreamingController(
    private val socketFactory: VoiceSocketFactory,
    private val streamerFactory: VoiceStreamerFactory,
    private val scope: CoroutineScope
) : LiveStreamingController {

    // Kept for API compatibility; not mutated by WS events anymore
    private val _state = MutableStateFlow(StreamingState())
    override val state: StateFlow<StreamingState> = _state

    // Emulation toggle and WS config
    private var emulate: Boolean = false
    private var wsUrl: String = "ws://192.168.18.18:8080"
    private var tokenProvider: TokenProvider = LambdaTokenProvider { "" }

    private var wsClient: VoiceSocket = socketFactory.create(emulate, wsUrl, tokenProvider)

    private var streamer: VoiceStreamer = streamerFactory.create(wsClient, scope)

    private var levelsJob: Job? = null

    private fun attachLevels() {
        levelsJob?.cancel()
        levelsJob = scope.launch {
            try {
                streamer.levels.collect { level -> pushLevel(level) }
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun pushLevel(level: Float) {
        val current = _state.value.levels
        val max = 60
        val trimmed = if (current.size >= max) current.drop(current.size - (max - 1)) else current
        _state.value = _state.value.copy(levels = trimmed + level)
    }

    // Track whether the user initiated a stop
    private var isStopping: Boolean = false

    init {
        attachLevels()
    }

    override fun setEmulationMode(enabled: Boolean) {
        emulate = enabled
        wsClient.close()
        wsClient = socketFactory.create(emulate, wsUrl, tokenProvider)
        streamer = streamerFactory.create(wsClient, scope)
        attachLevels()
    }

    override fun setWebSocketUrl(url: String) {
        wsUrl = url
        wsClient.close()
        wsClient = socketFactory.create(emulate, wsUrl, tokenProvider)
        streamer = streamerFactory.create(wsClient, scope)
        attachLevels()
    }

    override fun setTokenProvider(provider: suspend () -> String) {
        tokenProvider = LambdaTokenProvider(provider)
        wsClient.close()
        wsClient = socketFactory.create(emulate, wsUrl, tokenProvider)
        streamer = streamerFactory.create(wsClient, scope)
        attachLevels()
    }

    override fun start(language: String, outputDir: File) {
        // No temp file handling here; streaming is independent of UI or storage
        // Minimal UI-agnostic state to keep current screen working
        _state.value = _state.value.copy(status = if (emulate) "Streaming Local WebSocket" else "Connecting...",
            uiState = UiState.Streaming)

        wsClient.connect(
            scope = scope,
            onOpen = {
                _state.value = _state.value.copy(status = "Streaming")
                streamer.startStreaming(language)
            },
            onMessage = { msg ->
                _state.value = _state.value.copy(lastServerMessage = msg)
            },
            onFailure = { t ->
                _state.value = _state.value.copy(status = "Error: ${'$'}{t.message}")
                if (isStopping) {
                    isStopping = false
                } else {
                    _state.value = _state.value.copy(uiState = UiState.Idle)
                }
            },
            onClosed = { _, _ ->
                _state.value = _state.value.copy(status = "Closed")
                if (isStopping) {
                    isStopping = false
                } else {
                    _state.value = _state.value.copy(uiState = UiState.Idle)
                }
            },
            onBinary = {
                // no-op
            }
        )
    }

    override fun stop() {
        isStopping = true
        streamer.stopStreaming()
        _state.value = _state.value.copy(status = "Stopped", uiState = UiState.Stopped)
    }

    override fun cancel() {
        // No-op: no temp recording to discard here
    }

    override fun save() {
        // No-op: saving was removed from the controller; see RemovedStreamingUiAndSave.kt for previous implementation
    }

    override fun consumeBalloon() { /* no-op */ }

    override fun dismissPlayerOverlay() { /* no-op */ }

    override fun refreshSaved(vararg dirs: File) { /* no-op */ }

    override fun deleteFile(file: File) { /* no-op */ }

    override fun preview(file: File) { /* no-op */ }
}
