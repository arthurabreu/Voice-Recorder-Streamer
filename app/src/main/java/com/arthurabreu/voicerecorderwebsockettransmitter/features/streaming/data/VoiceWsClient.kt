package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString

class VoiceWsClient(
    private val url: String,
    private val authTokenProvider: suspend () -> String
) : VoiceSocket {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    override fun connect(
        scope: CoroutineScope,
        onOpen: () -> Unit,
        onMessage: (String) -> Unit,
        onBinary: (ByteArray) -> Unit,
        onClosed: (code: Int, reason: String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val token = authTokenProvider()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) { onOpen() }
                    override fun onMessage(ws: WebSocket, text: String) { onMessage(text) }
                    override fun onMessage(ws: WebSocket, bytes: ByteString) { onBinary(bytes.toByteArray()) }
                    override fun onClosing(ws: WebSocket, code: Int, reason: String) { ws.close(code, reason) }
                    override fun onClosed(ws: WebSocket, code: Int, reason: String) { onClosed(code, reason) }
                    override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) { onFailure(t) }
                })
            } catch (t: Throwable) {
                onFailure(t)
            }
        }
    }

    override fun sendText(json: String): Boolean = webSocket?.send(json) == true
    override fun sendBinary(bytes: ByteArray): Boolean = webSocket?.send(ByteString.of(*bytes)) == true
    override fun close(code: Int, reason: String) { webSocket?.close(code, reason) }
}