package com.arthurabreu.voicerecorderwebsockettransmitter.di

import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.SpeechToTextManager
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.domain.SpeechToTextService
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.presentation.SpeechToTextViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // Service provider
    factory { SpeechToTextManager(androidContext()) } bind SpeechToTextService::class

    // ViewModel depends directly on the service
    viewModel {
        val service: SpeechToTextService = get()
        SpeechToTextViewModel(service)
    }
}
