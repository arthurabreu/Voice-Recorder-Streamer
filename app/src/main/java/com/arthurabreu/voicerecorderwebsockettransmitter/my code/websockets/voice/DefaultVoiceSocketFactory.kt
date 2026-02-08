package com.mercantil.core.websockets.voice

import com.mercantil.commons.util.EnvironmentUtil
import com.mercantil.commons.util.SharedPreferencesUtil
import com.mercantil.core.websockets.headers.WsHeadersProvider
import com.mercantil.core.websockets.transport.KtorVoiceWsClient
import com.mercantil.core.websockets.transport.VoiceSocket
import io.ktor.client.HttpClient

/**
 * Fábrica de `VoiceSocket` que seleciona o cliente WS e, em debug, opcionalmente
 * o embrulha com `AnalyzingVoiceSocket` para telemetria de rede.
 *
 * Responsabilidades:
 * - Criar o `VoiceSocket` concreto (Ktor + OkHttp engine) com headers fornecidos.
 * - Ativar camada de análise quando permitido por preferências e ambiente.
 */
class DefaultVoiceSocketFactory(
    private val httpClient: HttpClient
) : VoiceSocketFactory {
    override fun create(
        emulate: Boolean,
        url: String,
        headersProvider: WsHeadersProvider
    ): VoiceSocket {
        val base: VoiceSocket = KtorVoiceWsClient(httpClient, url, headersProvider)
        return if (!EnvironmentUtil.isProduction && SharedPreferencesUtil.getNetworkAnalyzer()) {
            AnalyzingVoiceSocket(base, url)
        } else base
    }
}
