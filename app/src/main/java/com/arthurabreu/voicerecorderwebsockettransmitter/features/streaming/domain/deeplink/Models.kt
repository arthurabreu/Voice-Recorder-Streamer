package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.deeplink

import org.json.JSONException
import org.json.JSONObject

/** Envelope expected/produced by the backend for deeplink actions over WS. */
data class DeeplinkEnvelope(
    val acao: String,
    val sucesso: Boolean,
    val payloadDeeplinkRaw: String?,
    val link: String?,
    val proximoPasso: Int?
) {
    val payload: DeeplinkPayload? by lazy {
        payloadDeeplinkRaw?.let {
            try { DeeplinkPayload.fromJsonString(it) } catch (_: Throwable) { null }
        }
    }

    fun toJsonString(): String {
        val obj = JSONObject()
            .put("Acao", acao)
            .put("Sucesso", sucesso.toString())
        link?.let { obj.put("Link", it) }
        proximoPasso?.let { obj.put("ProximoPasso", it) }
        payloadDeeplinkRaw?.let { obj.put("PayloadDeeplink", it) }
        return obj.toString()
    }

    companion object {
        fun parse(text: String): DeeplinkEnvelope? {
            return try {
                val obj = JSONObject(text)
                val acao = obj.optString("Acao", null) ?: return null
                if (acao.lowercase() != "deeplink") return null
                val sucessoStr = obj.optString("Sucesso", "true")
                val sucesso = sucessoStr.equals("true", ignoreCase = true)
                val payload = if (obj.has("PayloadDeeplink")) obj.get("PayloadDeeplink").toString() else null
                val link = obj.optString("Link", null)
                val proximoPasso = if (obj.has("ProximoPasso")) obj.optInt("ProximoPasso") else null
                DeeplinkEnvelope(
                    acao = acao,
                    sucesso = sucesso,
                    payloadDeeplinkRaw = payload,
                    link = link,
                    proximoPasso = proximoPasso
                )
            } catch (_: JSONException) {
                null
            }
        }
    }
}

/** Inner payload structure transported as a JSON string in PayloadDeeplink. */
data class DeeplinkPayload(
    val NumDnd: Int,
    val NumCta: Long,
    val NumPes: Long,
    val IdtCat: Int,
    val ExibirAlertaErro: Boolean,
    val IdtAutPendente: Long?,
    val ExigirSessaoAtiva: Boolean
) {
    fun toJsonString(): String = JSONObject()
        .put("NumDnd", NumDnd)
        .put("NumCta", NumCta)
        .put("NumPes", NumPes)
        .put("IdtCat", IdtCat)
        .put("ExibirAlertaErro", ExibirAlertaErro)
        .put("IdtAutPendente", IdtAutPendente)
        .put("ExigirSessaoAtiva", ExigirSessaoAtiva)
        .toString()

    companion object {
        fun fromJsonString(text: String): DeeplinkPayload {
            val obj = JSONObject(text)
            return DeeplinkPayload(
                NumDnd = obj.optInt("NumDnd"),
                NumCta = obj.optLong("NumCta"),
                NumPes = obj.optLong("NumPes"),
                IdtCat = obj.optInt("IdtCat"),
                ExibirAlertaErro = obj.optBoolean("ExibirAlertaErro"),
                IdtAutPendente = if (obj.has("IdtAutPendente")) obj.optLong("IdtAutPendente") else null,
                ExigirSessaoAtiva = obj.optBoolean("ExigirSessaoAtiva")
            )
        }
    }
}
