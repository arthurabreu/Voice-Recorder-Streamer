package com.mercantil.core.websockets.service

import com.mercantil.core.websockets.state.WsUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Serviço de domínio que orquestra Transporte (WebSocket), Protocolo e Áudio.
 *
 * Responsabilidades:
 * - Expor um `StateFlow<WsUiState>` único para a UI/VM observar.
 * - Controlar o ciclo de vida da sessão (start/stop), permissões e payloads adicionais.
 * - Delegar mensagens/textos ao `ProtocolEngine` e áudio ao `AudioIo`.
 *
 * Não conhece Android UI, Activities/Fragments ou detalhes de DI. Mantém Clean Architecture.
 */
interface WsService {
    val state: StateFlow<WsUiState>
    suspend fun configure(deviceId: String, sessionHeaderName: String, sessionHeaderValue: String)
    suspend fun updateRecordAudioPermission(granted: Boolean)
    suspend fun start(language: String)
    suspend fun stop()
    suspend fun setExtraPayload(payload: String?)
    fun clearFinalEntrada()
}