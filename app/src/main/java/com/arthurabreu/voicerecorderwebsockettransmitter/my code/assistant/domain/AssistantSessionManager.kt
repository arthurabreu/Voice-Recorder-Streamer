package com.mercantil.assistant.domain

import android.content.Context
import com.mercantil.assistant.service.VoiceWebSocketService
import com.mercantil.core.websockets.service.WsService
import com.mercantil.core.websockets.state.WsUiState
import kotlinx.coroutines.flow.StateFlow

class AssistantSessionManager(
    private val context: Context,
    private val wsService: WsService
) {
    val state: StateFlow<WsUiState> = wsService.state

    suspend fun configure(deviceId: String, sessionHeaderName: String, sessionHeaderValue: String) {
        wsService.configure(deviceId, sessionHeaderName, sessionHeaderValue)
    }

    suspend fun setExtraPayload(payload: String?) {
        wsService.setExtraPayload(payload)
    }

    fun startSession(language: String = "pt-BR") {
        VoiceWebSocketService.start(context, language)
    }

    fun stopSession() {
        VoiceWebSocketService.stop(context)
    }

    suspend fun updateMicPermission(granted: Boolean) {
        wsService.updateRecordAudioPermission(granted)
    }

    fun clearFinalEntrada() {
        wsService.clearFinalEntrada()
    }
}
