package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data

interface VoiceSocket {
    suspend fun connect(
        onOpen: () -> Unit = {},
        onMessage: (String) -> Unit = {},
        onBinary: (ByteArray) -> Unit = {},
        onClosed: (code: Int, reason: String) -> Unit = { _, _ -> },
        onFailure: (Throwable) -> Unit = {}
    )
    suspend fun sendText(json: String): Boolean
    suspend fun sendBinary(bytes: ByteArray): Boolean
    fun close(code: Int = 1000, reason: String = "")
}
