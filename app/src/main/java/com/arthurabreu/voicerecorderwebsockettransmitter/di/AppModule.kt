package com.arthurabreu.voicerecorderwebsockettransmitter.di

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.DefaultVoiceSocketFactory
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.DefaultVoiceStreamerFactory
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.LiveStreamingControllerFactory
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.DefaultLiveStreamingControllerFactory
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.VoiceSocketFactory
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.VoiceStreamerFactory
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation.LiveStreamingViewModel
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.SpeechToTextManager
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.domain.SpeechToTextService
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.presentation.SpeechToTextViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.engine.cio.CIO
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // Speech To Text
    factory { SpeechToTextManager(androidContext()) } bind SpeechToTextService::class
    viewModel {
        val service: SpeechToTextService = get()
        SpeechToTextViewModel(service)
    }

    // Ktor HttpClient with WebSockets plugin using CIO engine (supports WebSockets)
    single { HttpClient(CIO) { install(WebSockets) } }

    // Factories for streaming feature
    single<VoiceSocketFactory> { DefaultVoiceSocketFactory(get()) }
    single<VoiceStreamerFactory> { DefaultVoiceStreamerFactory() }
    single<LiveStreamingControllerFactory> { DefaultLiveStreamingControllerFactory(get(), get()) }

    // Live streaming ViewModel with DI
    viewModel {
        LiveStreamingViewModel(
            controllerFactory = get()
        )
    }
}
