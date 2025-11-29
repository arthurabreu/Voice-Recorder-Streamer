package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession

class KtorVoiceWsClient(
    private val client: HttpClient,
    private val url: String,
    private val tokenProvider: TokenProvider
) : VoiceSocket {
    @Volatile
    private var session: WebSocketSession? = null

    override suspend fun connect(
        onOpen: () -> Unit,
        onMessage: (String) -> Unit,
        onBinary: (ByteArray) -> Unit,
        onClosed: (code: Int, reason: String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        try {
            val token = tokenProvider.getToken()
            client.webSocket(urlString = url, request = {
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
            }) {
                session = this as WebSocketSession
                onOpen()
                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> onMessage(frame.data.decodeToString())
                            is Frame.Binary -> onBinary(frame.data)
                            is Frame.Close -> {
                                onClosed(1000, "")
                                break
                            }
                            else -> {}
                        }
                    }
                } catch (t: Throwable) {
                    onFailure(t)
                } finally {
                    session = null
                }
            }
        } catch (t: Throwable) {
            onFailure(t)
        }
    }

    override suspend fun sendText(json: String): Boolean {
        val s = session ?: return false
        s.send(Frame.Text(json))
        return true
    }

    override suspend fun sendBinary(bytes: ByteArray): Boolean {
        val s = session ?: return false
        s.send(Frame.Binary(true, bytes))
        return true
    }

    override fun close(code: Int, reason: String) {
        session = null
    }
}
