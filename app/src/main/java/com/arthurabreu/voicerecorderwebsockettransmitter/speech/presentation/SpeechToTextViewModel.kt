package com.arthurabreu.voicerecorderwebsockettransmitter.speech.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.SpeechToTextService
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.usecase.StartListeningUseCase
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.usecase.StopListeningUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Aggregated UI state to simplify Compose collection.
 */
 data class SpeechUiState(
     val isListening: Boolean = false,
     val partialText: String = "",
     val finalText: String = "",
     val error: String? = null
 )

class SpeechToTextViewModel(
    private val service: SpeechToTextService,
    private val startListening: StartListeningUseCase,
    private val stopListening: StopListeningUseCase
) : ViewModel() {

    // Expose a single UI state for Compose
    val uiState: StateFlow<SpeechUiState> = combine(
        service.isListening,
        service.partialText,
        service.finalText,
        service.error
    ) { isListening, partial, finalText, error ->
        SpeechUiState(
            isListening = isListening,
            partialText = partial,
            finalText = finalText,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SpeechUiState()
    )

    fun onStart(languageTag: String = java.util.Locale.getDefault().toLanguageTag()) {
        startListening(languageTag)
    }

    fun onStop() {
        stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        service.release()
    }
}
