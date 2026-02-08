package com.mercantil.core.websockets.voice

import com.mercantil.core.websockets.headers.WsHeadersProvider
import com.mercantil.core.websockets.transport.VoiceSocket

/**
 * Fábrica para criação de `VoiceSocket` conforme ambiente/configuração.
 *
 * Responsabilidades:
 * - Abstrair a tecnologia de transporte (Ktor/OkHttp) da camada de domínio.
 * - Permitir embrulhar o socket com decoradores (ex.: `AnalyzingVoiceSocket`).
 */
interface VoiceSocketFactory {
    fun create(emulate: Boolean, url: String, headersProvider: WsHeadersProvider): VoiceSocket
}
