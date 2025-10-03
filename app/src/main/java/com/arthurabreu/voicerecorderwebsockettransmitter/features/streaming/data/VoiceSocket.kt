package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data

import kotlinx.coroutines.CoroutineScope

interface VoiceSocket {
    fun connect(
        scope: CoroutineScope,
        onOpen: () -> Unit = {},
        onMessage: (String) -> Unit = {},
        onBinary: (ByteArray) -> Unit = {},
        onClosed: (code: Int, reason: String) -> Unit = { _, _ -> },
        onFailure: (Throwable) -> Unit = {}
    )
    fun sendText(json: String): Boolean
    fun sendBinary(bytes: ByteArray): Boolean
    fun close(code: Int = 1000, reason: String = "")
}
