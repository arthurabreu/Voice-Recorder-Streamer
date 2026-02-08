package com.mercantil.core.websockets.transport

import kotlinx.coroutines.flow.Flow

sealed interface WsEvent {
    data object Open : WsEvent
    data class Text(val value: String) : WsEvent
    data class Binary(val bytes: ByteArray) : WsEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Binary

            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }
    }

    data class Closed(val code: Int, val reason: String) : WsEvent
    data class Failure(val error: Throwable) : WsEvent
}

/** Starts the WebSocket session and emits events through [events]. Suspends until the session ends. */
interface VoiceSocket {
    val events: Flow<WsEvent>
    suspend fun connect()
    suspend fun sendText(json: String): Boolean
    suspend fun sendBinary(bytes: ByteArray): Boolean
    suspend fun close(code: Int = 1000, reason: String = "")
}
