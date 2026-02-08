package com.mercantil.assistant.data.repository

import com.mercantil.assistant.data.response.AssistantCallResponse
import com.mercantil.assistant.data.response.AssistantIaTokenResponse
import com.mercantil.core.data.BaseOptionsRequest
import com.mercantil.core.data.ResultWrapper

interface AssistantCallRepository {
    suspend fun getInitialData(request: BaseOptionsRequest): ResultWrapper<AssistantCallResponse>
    suspend fun prefetchAssistantIaTokenAndStore(request: BaseOptionsRequest): ResultWrapper<AssistantIaTokenResponse>
}
