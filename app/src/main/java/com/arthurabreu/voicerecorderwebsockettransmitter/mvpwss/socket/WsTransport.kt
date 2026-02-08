package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.socket

import kotlinx.coroutines.flow.Flow

/**
 * Generic interface for WebSocket transport.
 */
interface WsTransport {
    fun connect(url: String, headers: Map<String, String> = emptyMap()): Flow<SocketEvent>
    suspend fun sendText(text: String)
    suspend fun sendBytes(data: ByteArray)
    suspend fun close()
}

sealed interface SocketEvent {
    object Connected : SocketEvent
    data class TextMessage(val text: String) : SocketEvent
    data class BinaryMessage(val data: ByteArray) : SocketEvent
    data class Error(val cause: Throwable) : SocketEvent
    data class Closed(val reason: String?) : SocketEvent
}
