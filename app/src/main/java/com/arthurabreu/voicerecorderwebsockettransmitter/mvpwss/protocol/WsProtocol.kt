package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.protocol

/**
 * Generic interface for defining the WebSocket protocol.
 * Decouples the service from specific JSON structures.
 */
interface WsProtocol<T> {
    /**
     * Called when the socket opens to provide the initial handshake message.
     */
    fun onOpen(): String?

    /**
     * Processes an incoming text message and returns a result.
     */
    fun onMessage(text: String): ProtocolResult<T>

    /**
     * Builds any additional messages that need to be sent (e.g., after handshake ack).
     */
    fun getHandshakeFollowUp(): List<String> = emptyList()
}

/**
 * Result of processing a protocol message.
 */
data class ProtocolResult<T>(
    val data: T? = null,
    val error: String? = null,
    val nextMessages: List<String> = emptyList(),
    val readyToStream: Boolean = false
)
