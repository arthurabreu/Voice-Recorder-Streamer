package com.mercantil.assistant.actionEvent

sealed interface AssistantCallEvent {
    object NavigateBack : AssistantCallEvent
    data class ExecutePushDeeplink(val entrada: String) : AssistantCallEvent
    data class ShowError(val message: String) : AssistantCallEvent
}