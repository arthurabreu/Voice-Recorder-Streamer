package com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.domain

import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Abstraction for speech-to-text operations.
 */
interface SpeechToTextService {
    val partialText: StateFlow<String>
    val finalText: StateFlow<String>
    val isListening: StateFlow<Boolean>
    val error: StateFlow<String?>

    fun startListening(languageTag: String = Locale.getDefault().toLanguageTag())
    fun stopListening()
    fun release()
}
