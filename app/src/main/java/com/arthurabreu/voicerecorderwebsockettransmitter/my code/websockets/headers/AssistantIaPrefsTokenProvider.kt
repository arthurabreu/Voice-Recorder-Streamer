package com.mercantil.core.websockets.headers

import com.mercantil.commons.util.SharedPreferencesUtil
import com.mercantil.core.websockets.token.TokenProvider

/**
 * Implementação simples de `TokenProvider` que lê o token do SharedPreferences.
 *
 * Responsabilidades:
 * - Fornecer o token armazenado para autenticação do WebSocket.
 * - Não sabe como o token é usado; apenas o retorna.
 */
class AssistantIaPrefsTokenProvider : TokenProvider {
    override suspend fun getToken(): String {
        return SharedPreferencesUtil.getAssistantIaWsToken().orEmpty().trim()
    }
}