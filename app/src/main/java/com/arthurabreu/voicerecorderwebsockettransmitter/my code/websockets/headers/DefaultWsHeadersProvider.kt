package com.mercantil.core.websockets.headers

import com.mercantil.core.data.Session
import com.mercantil.core.websockets.token.TokenProvider

/**
 * Default implementation that builds the headers required by the Mel gateway:
 * - Authorization: Bearer <ID_TOKEN>
 * - <WS_SESSION_HEADER>: <sessao>
 * - X-Device-ID: <id-unico>
 */

/**
 * Implementação padrão de `WsHeadersProvider` para o gateway Mel IA.
 *
 * Responsabilidades:
 * - Montar os cabeçalhos necessários para a sessão WS: `Authorization`, cabeçalho da sessão e `X-Device-ID`.
 * - Obter valores de forma desacoplada via `TokenProvider` e lambdas injetadas (facilita testes).
 * - Não faz rede; apenas retorna um `Map<String,String>` para o cliente WS usar.
 */
class DefaultWsHeadersProvider(
    private val sessionHeaderNameProvider: suspend () -> String = { "X-User-Session" },
    private val sessionHeaderValueProvider: suspend () -> String = { "" },
    private val deviceIdProvider: suspend () -> String = { "" },
    private val tokenProvider: TokenProvider
) : WsHeadersProvider {
    override suspend fun getHeaders(): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        val token = tokenProvider.getToken()
        if (token.isNotBlank()) headers["Authorization"] = "Bearer $token"

        val headerName = runCatching { sessionHeaderNameProvider() }.getOrElse { "X-User-Session" }.trim()
        var headerValue = runCatching { sessionHeaderValueProvider() }.getOrElse { "" }.trim()
        if (headerValue.isBlank()) {
            headerValue = Session.getSessionId().ifBlank { "qa-session" }
        }
        if (headerName.isNotBlank() && headerValue.isNotBlank()) headers[headerName] = headerValue

        var deviceId = runCatching { deviceIdProvider() }.getOrElse { "" }.trim()
        if (deviceId.isBlank()) deviceId = Session.retf10
        if (deviceId.isNotBlank()) headers["X-Device-ID"] = deviceId

        return headers
    }
}