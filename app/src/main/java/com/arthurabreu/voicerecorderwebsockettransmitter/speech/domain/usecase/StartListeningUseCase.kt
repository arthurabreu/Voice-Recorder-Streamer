package com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.usecase

import com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.SpeechToTextService

class StartListeningUseCase(private val service: SpeechToTextService) {
    operator fun invoke(languageTag: String = java.util.Locale.getDefault().toLanguageTag()) {
        service.startListening(languageTag)
    }
}
