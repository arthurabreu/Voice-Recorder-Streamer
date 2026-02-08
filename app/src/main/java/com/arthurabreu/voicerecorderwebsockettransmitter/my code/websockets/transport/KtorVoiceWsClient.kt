package com.mercantil.core.websockets.transport

import com.mercantil.core.websockets.headers.WsHeadersProvider
import com.mercantil.core.websockets.util.AssistantWsLog
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch

class KtorVoiceWsClient(
    private val client: HttpClient,
    private val url: String,
    private val headersProvider: WsHeadersProvider,
    private val outboundCapacity: Int = 256
) : VoiceSocket {
    private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendChannel = Channel<Frame>(capacity = outboundCapacity)

    @Volatile
    private var session: WebSocketSession? = null

    override val events: Flow<WsEvent> = callbackFlow {
        val headers = runCatching { headersProvider.getHeaders() }.getOrDefault(emptyMap())
        val hasAuth = headers["Authorization"].orEmpty().isNotBlank()
        AssistantWsLog.i(this@KtorVoiceWsClient, "events", "Connecting to %s (auth=%s)", url, if (hasAuth) "present" else "absent")

        try {
            client.webSocket(urlString = url, request = {
                headers.forEach { (k, v) -> if (v.isNotBlank()) header(k, v) }
            }) {
                session = this as WebSocketSession
                AssistantWsLog.i(this@KtorVoiceWsClient, "events", "WebSocket opened")
                trySend(WsEvent.Open)

                val sender = sendScope.launch(Dispatchers.IO) {
                    for (frame in sendChannel) {
                        runCatching { send(frame) }
                            .onFailure { t ->
                                if (session != null) trySend(WsEvent.Failure(t))
                            }
                    }
                }

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.data.decodeToString()
                            if (AssistantWsLog.shouldLog()) {
                                val preview = if (text.length > 500) text.take(500) + "…" else text
                                AssistantWsLog.d(this@KtorVoiceWsClient, "events", "recebendo TEXT %s", preview)
                            }
                            trySend(WsEvent.Text(text))
                        }
                        is Frame.Binary -> {
                            if (AssistantWsLog.shouldLog()) {
                                AssistantWsLog.d(this@KtorVoiceWsClient, "events", "recebendo BIN %d bytes", frame.data.size)
                            }
                            trySend(WsEvent.Binary(frame.data))
                        }
                        is Frame.Close -> {
                            val cr = frame.readReason()
                            val code = cr?.code?.toInt() ?: 1000
                            val reason = cr?.message ?: ""
                            AssistantWsLog.i(this@KtorVoiceWsClient, "events", "Closed by server code=%d reason=%s", code, reason)
                            trySend(WsEvent.Closed(code, reason))
                            break
                        }
                        else -> Unit
                    }
                }

                sender.cancel()
            }
        } catch (t: Throwable) {
            AssistantWsLog.e(this@KtorVoiceWsClient, "events", t, "WS connect/session error")
            trySend(WsEvent.Failure(t))
        } finally {
            session = null
            close()
            close()
        }

        awaitClose {
            // Ensure channel and session are closed when collector is cancelled
            sendScope.launch {
                kotlin.runCatching { session?.close(CloseReason(1000.toShort(), "cancelled")) }
                session = null
                sendChannel.close()
            }
        }
    }

    // No-op: lifecycle is driven by collecting `events`
    override suspend fun connect() { /* no-op when using callbackFlow */ }

    override suspend fun sendText(json: String): Boolean {
        val preview = if (json.length > 500) json.take(500) + "…" else json
        if (AssistantWsLog.shouldLog()) {
            AssistantWsLog.d(this, "sendText", "enviando TEXT %s", preview)
        }
        return sendChannel.trySend(Frame.Text(json)).isSuccess
    }

    override suspend fun sendBinary(bytes: ByteArray): Boolean {
        if (AssistantWsLog.shouldLog()) {
            AssistantWsLog.d(this, "sendBinary", "enviando BIN %d bytes", bytes.size)
        }
        return sendChannel.trySend(Frame.Binary(true, bytes)).isSuccess
    }

    override suspend fun close(code: Int, reason: String) {
        AssistantWsLog.i(this, "close", "Client closing WS code=%d reason=%s", code, reason)
        kotlin.runCatching { session?.close(CloseReason(code.toShort(), reason)) }
        session = null
        sendChannel.close()
    }
}