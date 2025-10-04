package com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.domain.SpeechToTextService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

/**
 * Aggregated UI state to simplify Compose collection.
 *
 * How uiState works in this ViewModel:
 * - We observe four StateFlows coming from the SpeechToTextService:
 *   isListening, partialText, finalText, and error.
 * - Using Kotlin Flow's `combine`, we merge the latest values from those flows
 *   into a single immutable data object [SpeechUiState]. Any time any of the
 *   source flows emits, the combined object is recomputed.
 * - We then call `stateIn(viewModelScope, WhileSubscribed(...), initialValue)` so
 *   the combined flow becomes a hot StateFlow that the UI can collect without
 *   redoing combine work for each collector, and with a lifecycle-aware sharing
 *   policy that keeps it active only while the UI is subscribed.
 */
 data class SpeechUiState(
     val isListening: Boolean = false,
     val partialText: String = "",
     val finalText: String = "",
     val error: String? = null
 )

class SpeechToTextViewModel(
    private val service: SpeechToTextService
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

    fun onStart(languageTag: String = Locale.getDefault().toLanguageTag()) {
        service.startListening(languageTag)
    }

    fun onStop() {
        service.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        service.release()
    }
}
