package com.mercantil.assistant.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mercantil.commons.view.SingleLiveEvent
import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mercantil.assistant.domain.AssistantSessionManager
import com.mercantil.assistant.actionEvent.AssistantCallEvent
import com.mercantil.assistant.data.repository.AssistantCallRepository
import com.mercantil.assistant.data.response.AssistantCallResponse
import com.mercantil.core.data.ResultWrapper
import com.mercantil.core.viewmodel.BaseViewModel
import com.mercantil.core.websockets.state.WsUiState
import com.mercantil.design_system.compose.screens.AssistantCallFinishButton
import com.mercantil.design_system.compose.screens.AssistantCallMbMessage
import com.mercantil.design_system.compose.screens.AssistantCallScreenData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.pow

class AssistantCallViewModel(
    private val repository: AssistantCallRepository,
    private val sessionManager: AssistantSessionManager
) : BaseViewModel<AssistantCallResponse>() {

    val events: SingleLiveEvent<AssistantCallEvent> = SingleLiveEvent()
    val assistantState: MutableState<AssistantCallScreenData> = mutableStateOf(AssistantCallScreenData())

    val streamingState: StateFlow<WsUiState> = sessionManager.state.stateIn(viewModelScope, SharingStarted.Lazily, WsUiState())

    private fun boostLevel(level: Float): Float {
        val gamma = 0.8f
        val gained = (level.coerceIn(0f, 1f).toDouble().pow(gamma.toDouble()).toFloat() * 1.6f)
        return gained.coerceIn(0f, 1f)
    }

    init {
        viewModelScope.launch {
            streamingState.collect { st ->
                val last = if (st.levels.size <= 24) st.levels else st.levels.takeLast(24)
                if (last.isNotEmpty()) {
                    val boosted = last.map { boostLevel(it) }
                    assistantState.value = assistantState.value.copy(levels = boosted)
                }
            }
        }
        viewModelScope.launch {
            streamingState
                .map { it.finalEntrada }
                .filterNotNull()
                .distinctUntilChanged { old, new ->
                    old.idContextoIA == new.idContextoIA && old == new
                }
                .collect { finalEntrada ->
                    val json = Gson().toJson(finalEntrada)
                    events.postValue(AssistantCallEvent.ExecutePushDeeplink(json))
                }
        }

        viewModelScope.launch {
            streamingState
                .map { it.errorMessage }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { message ->
                    events.postValue(AssistantCallEvent.ShowError(message))
                }
        }
    }

    override fun getInitialData() {
        carRequest {
            val data = repository.getInitialData(defaultRequestData)
            if (data is ResultWrapper.Success) {
                assistantState.value = mapToUi(data.value)
            } else {
                sessionManager.stopSession()
            }
            data
        }
    }

    fun mapToUi(resp: AssistantCallResponse): AssistantCallScreenData {
        return AssistantCallScreenData(
            title = resp.title,
            mbMessage = AssistantCallMbMessage(resp.mbMessage.title, resp.mbMessage.value),
            isResponding = resp.respondendoOuvindo.contains("ouvindo"),
            respondingLabel = resp.respondendoOuvindo,
            finishButton = AssistantCallFinishButton(text = resp.mbButton.title),
            levels = List(24) { i -> (kotlin.math.abs(kotlin.math.sin(i / 4f)) * 0.8f) }
        )
    }

    fun updateMicPermission(granted: Boolean) {
        viewModelScope.launch { sessionManager.updateMicPermission(granted) }
    }

    fun startStreaming(language: String = "pt-BR") {
        sessionManager.startSession(language)
    }

    private fun buildPayloadWithEmprestimosAtivos(payload: String?): String? {
        if (payload.isNullOrBlank()) return payload
        val emprestimosAtivosStr: String = (composeScreenData.value ?: screenData.value)?.emprestimosAtivos ?: ""
        if (emprestimosAtivosStr.isBlank()) return payload
        return try {
            val gson = Gson()
            val root: JsonObject = gson.fromJson(payload, JsonObject::class.java)
            val emprestimosElement: JsonElement = try {
                gson.fromJson(emprestimosAtivosStr, JsonElement::class.java) ?: JsonPrimitive(emprestimosAtivosStr)
            } catch (e: Exception) {
                JsonPrimitive(emprestimosAtivosStr)
            }
            root.add("EmprestimosAtivos", emprestimosElement)
            gson.toJson(root)
        } catch (e: Exception) {
            payload
        }
    }

    fun setPayloadAssistenteIa(payload: String?) {
        val newPayload = buildPayloadWithEmprestimosAtivos(payload)
        viewModelScope.launch { sessionManager.setExtraPayload(newPayload) }
    }

    fun stopStreaming() {
        sessionManager.stopSession()
    }
}