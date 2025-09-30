package com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.usecase

import com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.SpeechToTextService

class StopListeningUseCase(private val service: SpeechToTextService) {
    operator fun invoke() {
        service.stopListening()
    }
}
