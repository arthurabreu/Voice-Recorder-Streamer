package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.di

import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.audio.AudioHandler
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.audio.DefaultAudioHandler
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.protocol.MockWsProtocol
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.protocol.WsProtocol
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.service.WsEngine
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.socket.FakeWsTransport
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.socket.KtorWsTransport
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.socket.WsTransport
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val mvpwssModule = module {
    // Shared components
    single<AudioHandler> { DefaultAudioHandler(androidContext()) }
    
    // Choose between Production and Mock by commenting/uncommenting
    
    // --- PRODUCTION ---
    // single<WsTransport> { KtorWsTransport(get()) }
    // single<WsProtocol<Any>> { ... your concrete protocol ... }
    
    // --- MOCK / TESTING ---
    single<WsTransport> { FakeWsTransport() }
    single<WsProtocol<Any>> { MockWsProtocol() as WsProtocol<Any> }

    // Core Engine
    single<WsEngine<Any>> { WsEngine(get(), get(), get()) }
}
