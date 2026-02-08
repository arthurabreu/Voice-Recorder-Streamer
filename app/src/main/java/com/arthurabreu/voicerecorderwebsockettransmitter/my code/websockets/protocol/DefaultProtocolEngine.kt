package com.mercantil.core.websockets.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mercantil.core.data.PayloadDeeplink
import com.mercantil.core.websockets.state.WsUiState
import com.mercantil.core.websockets.util.AssistantWsLog
import com.mercantil.commons.util.SharedPreferencesUtil

/**
 * Implementação padrão do `ProtocolEngine` para o canal de voz.
 *
 * Responsabilidades:
 * - Montar handshake/LPA e injetar payload extra quando aplicável.
 * - Processar mensagens de texto do servidor e produzir `ProtocolOutcome`.
 *
 * Não realiza I/O; é facilmente testável com entradas/saídas puras.
 */
class DefaultProtocolEngine(
    private val gson: Gson = Gson()
) : ProtocolEngine {

    private var extraPayload: String? = null

    override fun setExtraPayload(payload: String?) {
        extraPayload = payload
    }

    override fun extraPayloadOrNull(): String? = extraPayload?.takeIf { it.isNotBlank() }

    override fun onSocketOpen(): String =
        """{"message_type":"handshake","sample_rate":16000,"sample_width":2,"channels":1}"""

    override fun buildLpa(): String =
        """{"message_type":"lpa_payload","protocol_version":"v1","payload":{"EstruturaPayloadIA":{"Ofertas":[{"Codigo":"CP_REF","Descricao":"Refinanciamento de Empréstimo","Tipo":"Refinanciamento","ValorEmprestimo":1500.00,"LPA":{"Valor":1800.50},"NumeroParcelas":{"Titulo":"72x","Valor":55.90},"Conta":"12345-6","NumeroBeneficio":"9876543210"}]}}}"""
            .replace("\n", "")

    override fun onTextMessage(text: String): ProtocolOutcome {
        val sends = mutableListOf<String>()
        var uiDelta: WsUiState? = null
        var ready = false

        AssistantWsLog.d(this, "onTextMessage", "MelWS TEXT recebido %s", text)

        if (text.contains("\"message_type\":\"handshake_ack\"")) {
            sends += buildLpa()
            extraPayloadOrNull()?.let { sends += it }
            ready = true
        }

        var finalEntrada: PayloadDeeplink? = null
        var errorMessage: String? = null

        val root = gson.fromJson(text, JsonObject::class.java)
        if (text.contains("\"Entrada\"") && root != null) {
            val inner = root.get("Entrada")?.asString
            if (!inner.isNullOrBlank()) finalEntrada = gson.fromJson(inner, PayloadDeeplink::class.java)
        }

        if (root != null) {
            val descErro = root.get("DescricaoErro")?.asString?.trim().orEmpty()
            val backendError = root.get("MensagemPadraoErroRequisicao")?.asString?.trim().orEmpty()
            if (backendError.isNotEmpty()) {
                errorMessage = backendError
            }
            if (descErro.isNotEmpty()) {
                errorMessage = descErro
            }
        }
        AssistantWsLog.d(this, "onTextMessage", "MelWS payload entrada -> %s", finalEntrada.toString())

        finalEntrada?.idContextoIA.orEmpty().trim().let { id ->
            if (id.isEmpty().not()) {
                SharedPreferencesUtil.setAssistantIaWsIdContextoIA(id)
            }
        }

        uiDelta = WsUiState(lastServerMessage = text, finalEntrada = finalEntrada, errorMessage = errorMessage)
        return ProtocolOutcome(sendText = sends, uiDelta = uiDelta, readyToCapture = ready)
    }
}