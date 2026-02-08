package com.mercantil.assistant.data

import com.mercantil.assistant.data.response.AssistantCallResponse
import com.mercantil.assistant.data.response.AssistantIaTokenResponse
import com.mercantil.core.data.BaseOptionsRequest
import com.mercantil.core.data.Session
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AssistantCallService {
    @POST("AssistenteIA/CarIntArtMel")
    suspend fun getInitialData(
        @Body request: BaseOptionsRequest,
        @Header("Cookie") cookie: String = Session.cookieSession
    ): AssistantCallResponse

    @POST("AssistenteIA/EmitirTokenGoogleMelIa")
    suspend fun getAssistantIaToken(
        @Body request: BaseOptionsRequest,
        @Header("Cookie") cookie: String = Session.cookieSession
    ): AssistantIaTokenResponse
}