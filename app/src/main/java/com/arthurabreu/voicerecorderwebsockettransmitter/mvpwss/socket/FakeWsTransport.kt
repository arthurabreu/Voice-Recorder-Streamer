package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.socket

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow

/**
 * Fake transport that simulates a server for testing purposes.
 */
class FakeWsTransport : WsTransport {

    private val _events = MutableSharedFlow<SocketEvent>()
    private var isConnected = false

    override fun connect(url: String, headers: Map<String, String>): Flow<SocketEvent> = flow {
        isConnected = true
        emit(SocketEvent.Connected)
        _events.collect { emit(it) }
    }

    override suspend fun sendText(text: String) {
        if (!isConnected) return
        
        // Simulate server logic
        if (text.contains("handshake")) {
            delay(500)
            _events.emit(SocketEvent.TextMessage("""{"type":"handshake_ack","status":"ok"}"""))
        } else if (text.contains("start_stream")) {
            delay(200)
            _events.emit(SocketEvent.TextMessage("""{"type":"server_response","msg":"Streaming started on server"}"""))
        }
    }

    override suspend fun sendBytes(data: ByteArray) {
        // Just consume bytes in fake
    }

    override suspend fun close() {
        isConnected = false
        _events.emit(SocketEvent.Closed("Closed by client"))
    }
}
