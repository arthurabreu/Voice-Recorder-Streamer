package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.socket

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Ktor implementation of [WsTransport].
 */
class KtorWsTransport(private val client: HttpClient) : WsTransport {
    private var session: WebSocketSession? = null

    override fun connect(url: String, headers: Map<String, String>): Flow<SocketEvent> = flow {
        try {
            session = client.webSocketSession {
                url(url)
                headers.forEach { (k, v) -> header(k, v) }
            }
            emit(SocketEvent.Connected)

            while (session?.isActive == true) {
                val frame = session?.incoming?.receive() ?: break
                when (frame) {
                    is Frame.Text -> emit(SocketEvent.TextMessage(frame.readText()))
                    is Frame.Binary -> emit(SocketEvent.BinaryMessage(frame.readBytes()))
                    else -> {}
                }
            }
            emit(SocketEvent.Closed("Session inactive"))
        } catch (e: Exception) {
            emit(SocketEvent.Error(e))
        }
    }

    override suspend fun sendText(text: String) {
        session?.send(Frame.Text(text))
    }

    override suspend fun sendBytes(data: ByteArray) {
        session?.send(Frame.Binary(true, data))
    }

    override suspend fun close() {
        session?.close()
        session = null
    }
}
