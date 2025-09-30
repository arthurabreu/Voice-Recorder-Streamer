package com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for speech-to-text operations.
 */
interface SpeechToTextService {
    val partialText: StateFlow<String>
    val finalText: StateFlow<String>
    val isListening: StateFlow<Boolean>
    val error: StateFlow<String?>

    fun startListening(languageTag: String = java.util.Locale.getDefault().toLanguageTag())
    fun stopListening()
    fun release()
}
