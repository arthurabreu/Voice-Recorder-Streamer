package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.PcmAudioPlayer
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.deeplink.DeeplinkEnvelope
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.deeplink.DeeplinkPayload
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.StreamingState
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.UiState
import kotlinx.coroutines.Job
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
    private val streamerFactory: VoiceStreamerFactory
) : LiveStreamingController {

    private val _state = MutableStateFlow(StreamingState())
    override val state: StateFlow<StreamingState> = _state

    private var emulate: Boolean = false
    private var wsUrl: String = "ws://192.168.18.18:8080"
    private var tokenProvider: TokenProvider = LambdaTokenProvider { "" }

    private var wsClient: VoiceSocket = socketFactory.create(emulate, wsUrl, tokenProvider)

    private var streamer: VoiceStreamer = streamerFactory.create(wsClient)

    private var levelsJob: Job? = null

    // Headless playback for downlink audio (from WS binary frames)
    private var downlinkPlayer: PcmAudioPlayer? = null

    private suspend fun attachLevels() {
        try {
            streamer.levels.collect { level -> pushLevel(level) }
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun pushLevel(level: Float) {
        val current = _state.value.levels
        val max = 60
        val trimmed = if (current.size >= max) current.drop(current.size - (max - 1)) else current
        _state.value = _state.value.copy(levels = trimmed + level)
    }

    private var isStopping: Boolean = false

    override fun setEmulationMode(enabled: Boolean) {
        emulate = enabled
        wsClient.close()
        wsClient = socketFactory.create(emulate, wsUrl, tokenProvider)
        streamer = streamerFactory.create(wsClient)
    }

    override fun setWebSocketUrl(url: String) {
        wsUrl = url
        wsClient.close()
        wsClient = socketFactory.create(emulate, wsUrl, tokenProvider)
        streamer = streamerFactory.create(wsClient)
    }

    override fun setTokenProvider(provider: suspend () -> String) {
        tokenProvider = LambdaTokenProvider(provider)
        wsClient.close()
        wsClient = socketFactory.create(emulate, wsUrl, tokenProvider)
        streamer = streamerFactory.create(wsClient)
    }

    override suspend fun start(language: String, outputDir: File) {
        _state.value = _state.value.copy(status = if (emulate) "Streaming Local WebSocket" else "Connecting...",
            uiState = UiState.Streaming)

        wsClient.connect(
                    onOpen = { _state.value = _state.value.copy(status = "Streaming") },
                    onMessage = { msg ->
                        val env = DeeplinkEnvelope.parse(msg)
                        if (env != null) {
                            val summary = buildString {
                                append("Deeplink(sucesso=")
                                append(env.sucesso)
                                append(", passo=")
                                append(env.proximoPasso ?: "-")
                                append(")")
                            }
                            _state.value = _state.value.copy(lastServerMessage = summary)
                        } else {
                            _state.value = _state.value.copy(lastServerMessage = msg)
                        }
                    },
                    onClosed = { _, _ ->
                        _state.value = _state.value.copy(status = "Closed")
                        downlinkPlayer?.stop(); downlinkPlayer = null
                        if (isStopping) {
                            isStopping = false
                        } else {
                            _state.value = _state.value.copy(uiState = UiState.Idle)
                        }
                    },
                    onFailure = { t ->
                        _state.value = _state.value.copy(status = "Error: ${'$'}{t.message}")
                        downlinkPlayer?.stop(); downlinkPlayer = null
                        if (isStopping) {
                            isStopping = false
                        } else {
                            _state.value = _state.value.copy(uiState = UiState.Idle)
                        }
                    },
                    onBinary = { bytes ->
                        downlinkPlayer?.offerPcm(bytes)
                    }
                )

        attachLevels()
        streamer.startStreaming(language)
        downlinkPlayer = PcmAudioPlayer().also { it.prepare() }
        downlinkPlayer?.run()
    }

    override suspend fun stop() {
        isStopping = true
        streamer.stopStreaming()
        downlinkPlayer?.stop(); downlinkPlayer = null
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

    override suspend fun sendDeeplink(
        payload: DeeplinkPayload,
        sucesso: Boolean,
        link: String,
        proximoPasso: Int
    ): Boolean {
        val envelope = DeeplinkEnvelope(
            acao = "deeplink",
            sucesso = sucesso,
            payloadDeeplinkRaw = payload.toJsonString(),
            link = link,
            proximoPasso = proximoPasso
        )
        return wsClient.sendText(envelope.toJsonString())
    }
}
