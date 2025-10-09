package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.FakeVoiceWsClient
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.KtorVoiceWsClient
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import io.ktor.client.HttpClient

/**
 * Default production/fake switch for [VoiceSocketFactory].
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
        return if (emulate) {
            FakeVoiceWsClient()
        } else {
            KtorVoiceWsClient(httpClient, url, tokenProvider)
        }
    }
}
