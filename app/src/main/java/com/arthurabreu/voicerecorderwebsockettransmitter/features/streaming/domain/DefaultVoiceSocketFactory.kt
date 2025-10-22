package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.KtorVoiceWsClient
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import io.ktor.client.HttpClient

/**
 * Default implementation that currently always returns the production Ktor WebSocket client.
 * The fake client remains available under `features.streaming.fake` but is not wired here.
 *
 * @property httpClient Ktor client used by the real WS client implementation.
 */
class DefaultVoiceSocketFactory(
    private val httpClient: HttpClient
) : VoiceSocketFactory {
    override fun create(
        emulate: Boolean,
        url: String,
        tokenProvider: TokenProvider
    ): VoiceSocket {
        return KtorVoiceWsClient(httpClient, url, tokenProvider)
    }
}
