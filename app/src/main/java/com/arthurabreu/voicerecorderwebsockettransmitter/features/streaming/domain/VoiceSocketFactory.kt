package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket

/**
 * Abstract factory that creates a [VoiceSocket] implementation.
 *
 * Notes:
 * - Keeps domain layer decoupled from the concrete WS client (Ktor, OkHttp, etc.).
 * - Allows swapping a fake implementation for previews/tests (emulation).
 */
interface VoiceSocketFactory {
    /**
     * Create a [VoiceSocket] according to the given parameters.
     *
     * @param emulate When true, returns a local fake client.
     * @param url Server WebSocket URL to connect.
     * @param tokenProvider Strategy to fetch auth token lazily.
     */
    fun create(emulate: Boolean, url: String, tokenProvider: TokenProvider): VoiceSocket
}
