package com.mercantil.core.websockets.voice

import com.mercantil.commons.data.model.RequestResponseData
import com.mercantil.commons.util.EnvironmentUtil
import com.mercantil.commons.util.SharedPreferencesUtil
import com.mercantil.core.network.RequestResponseList
import com.mercantil.core.websockets.transport.VoiceSocket
import com.mercantil.core.websockets.transport.WsEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Decorador de `VoiceSocket` para análise/telemetria em ambientes de desenvolvimento.
 *
 * Responsabilidades:
 * - Registrar eventos WS (OPEN/TEXT/BIN/CLOSED/ERROR) em `RequestResponseList` quando habilitado.
 * - Não altera o comportamento do socket; apenas observa e anota.
 */
internal class AnalyzingVoiceSocket(
    private val delegate: VoiceSocket,
    private val url: String
) : VoiceSocket {
    private var binSendCount: Long = 0

    override val events: Flow<WsEvent> = delegate.events.onEach { ev ->
        if (!shouldLog()) return@onEach
        when (ev) {
            is WsEvent.Open -> add(
                status = 0,
                response = "WebSocket OPEN",
                method = "WS-OPEN"
            )
            is WsEvent.Text -> {
                val snippet = ev.value.safeTake(500)
                add(
                    status = 0,
                    response = snippet,
                    method = "WS-TEXT recebendo texto -> $snippet"
                )
            }
            is WsEvent.Binary -> add(
                status = 0,
                response = "${ev.bytes.size} bytes",
                method = "WS-BIN recebendo bytes binarios -> ${ev.bytes.size} bytes"
            )
            is WsEvent.Closed -> add(
                status = 0,
                response = "Wss fechou, razao: ${ev.reason}",
                method = "WS-CLOSED ${ev.code}"
            )
            is WsEvent.Failure -> {
                val details = " Falha no wss, causa: ${ev.error.cause} , localizedMessage: ${ev.error.localizedMessage} , message: ${ev.error.message.orEmpty()} "
                val snippet = details.replace("\n", " ").safeTake(200)
                add(
                    status = 0,
                    response = details.safeTake(),
                    method = "WS-ERROR $snippet"
                )
            }
        }
    }

    override suspend fun connect() {
        if (shouldLog()) add(status = 200, response = "connect()", method = "WS-connect")
        delegate.connect()
    }

    override suspend fun sendText(json: String): Boolean {
        if (shouldLog()) add(status = 200, request = json.safeTake(), method = "WS-TEXT->")
        return delegate.sendText(json)
    }

    override suspend fun sendBinary(bytes: ByteArray): Boolean {
        if (shouldLog()) {
            val count = ++binSendCount
            if (count % 50L == 1L) {
                add(
                    status = 200,
                    request = "${bytes.size} bytes",
                    method = "WS-BIN-Recebendo-Audio-Mel-IA->"
                )
            }
        }
        return delegate.sendBinary(bytes)
    }

    override suspend fun close(code: Int, reason: String) {
        if (shouldLog()) add(status =code, response = "close($code, $reason)", method = "WS-CLOSE")
        delegate.close(code, reason)
    }

    private fun String.safeTake(max: Int = 2000): String = this.take(max)

    private fun shouldLog(): Boolean = !EnvironmentUtil.isProduction && SharedPreferencesUtil.getNetworkAnalyzer()

    private fun add(
        status: Int,
        request: String = "",
        response: String = "",
        method: String
    ) {
        RequestResponseList.requestsResponses.add(
            RequestResponseData(
                statusCode = status,
                url = url,
                request = request,
                response = response,
                requestHeaders = emptyList(),
                responseHeaders = emptyList(),
                method = method
            )
        )
    }
}
