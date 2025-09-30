package com.arthurabreu.voicerecorderwebsockettransmitter.di

import com.arthurabreu.voicerecorderwebsockettransmitter.speech.SpeechToTextManager
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.SpeechToTextService
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.usecase.StartListeningUseCase
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.usecase.StopListeningUseCase
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.presentation.SpeechToTextViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // Service: still a factory, but we'll ensure a single instance is shared within the ViewModel
    factory { SpeechToTextManager(androidContext()) } bind SpeechToTextService::class

    // ViewModel: build use cases with the SAME service instance to avoid mismatched instances
    viewModel {
        val service: SpeechToTextService = get()
        val start = StartListeningUseCase(service)
        val stop = StopListeningUseCase(service)
        SpeechToTextViewModel(service, start, stop)
    }
}
