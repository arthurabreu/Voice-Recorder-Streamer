package com.mercantil.core.websockets.protocol

import com.mercantil.core.websockets.state.WsUiState

/**
 * Motor de protocolo (stateless) para o canal WebSocket.
 *
 * Responsabilidades:
 * - Gerar a mensagem de handshake quando o socket abre.
 * - Processar textos recebidos e devolver um `ProtocolOutcome` com ações (mensagens a enviar,
 *   deltas de estado de UI e sinal para iniciar captura de áudio).
 * - Construir mensagens de LPA e injetar payload extra opcional.
 *
 * Não faz I/O de rede nem de áudio. Fácil de testar com strings de entrada/saída.
 */
interface ProtocolEngine {
    fun onSocketOpen(): String // mensagem de handshake a ser enviada
    fun onTextMessage(text: String): ProtocolOutcome
    fun buildLpa(): String
    fun extraPayloadOrNull(): String?
    fun setExtraPayload(payload: String?)
}

 data class ProtocolOutcome(
    val sendText: List<String> = emptyList(),
    val uiDelta: WsUiState? = null,
    val readyToCapture: Boolean = false
)
