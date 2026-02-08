package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.service

import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.audio.AudioHandler
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.model.SessionStatus
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.model.WsSessionState
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.protocol.WsProtocol
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.socket.SocketEvent
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.socket.WsTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Core engine that orchestrates WebSocket transport, Protocol processing, and Audio.
 * Generic over type [T], which represents the domain-specific data model.
 */
class WsEngine<T>(
    private val transport: WsTransport,
    private val protocol: WsProtocol<T>,
    private val audioHandler: AudioHandler,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _state = MutableStateFlow(WsSessionState<T>())
    val state = _state.asStateFlow()

    private var connectionJob: Job? = null
    private var audioJob: Job? = null

    fun start(url: String, headers: Map<String, String> = emptyMap()) {
        if (_state.value.status != SessionStatus.Idle && _state.value.status != SessionStatus.Closed && _state.value.status != SessionStatus.Error) return

        _state.update { it.copy(status = SessionStatus.Connecting, errorMessage = null) }

        connectionJob?.cancel()
        connectionJob = transport.connect(url, headers)
            .onEach { event ->
                handleSocketEvent(event)
            }
            .launchIn(scope)

        // Observe audio levels regardless of status
        audioHandler.audioLevels
            .onEach { level ->
                _state.update { it.copy(audioLevels = (it.audioLevels + level).takeLast(50)) }
            }
            .launchIn(scope)
    }

    private suspend fun handleSocketEvent(event: SocketEvent) {
        when (event) {
            is SocketEvent.Connected -> {
                _state.update { it.copy(status = SessionStatus.Connected) }
                protocol.onOpen()?.let { handshake ->
                    transport.sendText(handshake)
                }
            }
            is SocketEvent.TextMessage -> {
                val result = protocol.onMessage(event.text)
                _state.update { 
                    it.copy(
                        lastData = result.data ?: it.lastData,
                        lastRawMessage = event.text,
                        errorMessage = result.error ?: it.errorMessage
                    )
                }
                
                result.nextMessages.forEach { msg ->
                    transport.sendText(msg)
                }

                if (result.readyToStream) {
                    startStreaming()
                }
            }
            is SocketEvent.BinaryMessage -> {
                audioHandler.playAudio(event.data)
            }
            is SocketEvent.Error -> {
                _state.update { it.copy(status = SessionStatus.Error, errorMessage = event.cause.message) }
                stopInternal()
            }
            is SocketEvent.Closed -> {
                _state.update { it.copy(status = SessionStatus.Closed) }
                stopInternal()
            }
        }
    }

    private fun startStreaming() {
        if (_state.value.status == SessionStatus.Streaming) return
        
        _state.update { it.copy(status = SessionStatus.Streaming, isRecording = true) }
        
        audioJob?.cancel()
        audioJob = audioHandler.startCapture()
            .onEach { chunk ->
                transport.sendBytes(chunk)
            }
            .launchIn(scope)
    }

    fun stop() {
        scope.launch {
            transport.close()
            stopInternal()
        }
    }

    private fun stopInternal() {
        audioJob?.cancel()
        audioHandler.stopCapture()
        audioHandler.stopPlayback()
        _state.update { it.copy(isRecording = false) }
    }
}
