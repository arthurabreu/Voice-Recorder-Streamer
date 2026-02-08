package com.mercantil.assistant.data.repository

import com.mercantil.core.data.ResultWrapper
import com.mercantil.core.network.call
import com.mercantil.assistant.data.AssistantCallService
import com.mercantil.assistant.data.response.AssistantCallResponse
import com.mercantil.assistant.data.response.AssistantIaTokenResponse
import com.mercantil.core.data.BaseOptionsRequest
import com.mercantil.commons.util.SharedPreferencesUtil
import com.mercantil.core.data.BaseResponse
import com.mercantil.core.network.getMessageError
import com.mercantil.core.websockets.util.AssistantWsLog

class AssistantCallRepositoryImpl(
    private val service: AssistantCallService
) : AssistantCallRepository {
    override suspend fun getInitialData(request: BaseOptionsRequest): ResultWrapper<AssistantCallResponse> {
        return call {
            service.getInitialData(request)
        }
    }

    override suspend fun prefetchAssistantIaTokenAndStore(request: BaseOptionsRequest): ResultWrapper<AssistantIaTokenResponse> {
        return try {
            val resp = service.getAssistantIaToken(request)
            storeTokenLocally(resp)
            ResultWrapper.Success(resp)
        } catch (t: Throwable) {
            val base = BaseResponse().apply {
                descricaoErro = getMessageError(Exception(t))
                descricaoErroMobile = descricaoErro
            }
            ResultWrapper.Error(base)
        }
    }

    private fun storeTokenLocally(resp: AssistantIaTokenResponse) {
        try {
            val token = resp.token.orEmpty()
            AssistantWsLog.i(this, "storeTokenLocally", "rawToken=%s", token)
            if (token.isNotEmpty()) {
                SharedPreferencesUtil.setAssistantIaWsToken(token)
            } else {
                AssistantWsLog.w(this, "storeTokenLocally", "Empty authorization field in /ws-token response")
            }
        } catch (t: Throwable) {
            AssistantWsLog.e(this, "storeTokenLocally", t, "Failed to prefetch Assistant IA token")
        }
    }
}