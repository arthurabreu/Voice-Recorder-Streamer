package com.mercantil.core.websockets.token

/**
 * Abstração para obtenção de token de autorização (ex.: ID token/JWT).
 *
 * Responsabilidades:
 * - Fornecer token de forma suspensa (pode fazer I/O/cache/refresh internamente).
 * - Não conhece transporte, cabeçalhos ou UI.
 */
interface TokenProvider {
    suspend fun getToken(): String
}