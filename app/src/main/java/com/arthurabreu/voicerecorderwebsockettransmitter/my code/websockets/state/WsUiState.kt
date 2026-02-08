package com.mercantil.core.websockets.state

import com.mercantil.core.data.PayloadDeeplink

/**
 * Estado observado pela UI/VM para o fluxo de voz via WebSocket.
 *
 * Responsabilidades:
 * - Representar, de forma independente, o status da sessão (conectado, aguardando permissão, etc.).
 * - Carregar últimos níveis de microfone para visualização (UI não calcula nada).
 * - Expor a última mensagem recebida e o payload final de deeplink quando existir.
 *
 * Não conhece transporte (Ktor/OkHttp), protocolo, nem detalhes de áudio. É puro domínio para consumo da UI/VM.
 */
data class WsUiState(
    val status: String = "Idle",
    val connected: Boolean = false,
    val needsRecordAudioPermission: Boolean = false,
    val lastServerMessage: String? = null,
    val levels: List<Float> = emptyList(),
    val finalEntrada: PayloadDeeplink? = null,
    val errorMessage: String? = null
)