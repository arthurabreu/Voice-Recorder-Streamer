package com.mercantil.core.websockets.headers

/**
 * Fornecedor de cabeçalhos para a conexão WebSocket.
 *
 * Responsabilidades:
 * - Construir headers de negócio (ex.: `Authorization`, sessão, `X-Device-ID`).
 * - Não aplicar headers em camadas de transporte; apenas fornecer um mapa imutável para o cliente WS.
 * - Isolar origem de token/identificadores (injeção via `TokenProvider` e lambdas), facilitando testes.
 */
interface WsHeadersProvider {
    suspend fun getHeaders(): Map<String, String>
}