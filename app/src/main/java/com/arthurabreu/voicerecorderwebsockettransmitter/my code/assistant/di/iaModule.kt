package com.mercantil.assistant.di

import com.mercantil.assistant.data.AssistantCallService
import com.mercantil.assistant.data.repository.AssistantCallRepository
import com.mercantil.assistant.data.repository.AssistantCallRepositoryImpl
import com.mercantil.assistant.domain.AssistantSessionManager
import com.mercantil.assistant.service.AssistantNotificationHelper
import com.mercantil.assistant.viewmodel.AssistantCallViewModel
import com.mercantil.core.network.assistantia.AssistantIaOkHttpClientProvider
import com.mercantil.core.network.createWebService
import com.mercantil.core.websockets.audio.DefaultAudioIo
import com.mercantil.core.websockets.headers.AssistantIaPrefsTokenProvider
import com.mercantil.core.websockets.headers.DefaultWsHeadersProvider
import com.mercantil.core.websockets.protocol.DefaultProtocolEngine
import com.mercantil.core.websockets.protocol.ProtocolEngine
import com.mercantil.core.websockets.service.DefaultWsService
import com.mercantil.core.websockets.service.WsService
import com.mercantil.core.websockets.token.TokenProvider
import com.mercantil.core.websockets.voice.DefaultVoiceSocketFactory
import com.mercantil.core.websockets.voice.DefaultVoiceStreamerFactory
import com.mercantil.core.websockets.voice.VoiceSocketFactory
import com.mercantil.core.websockets.voice.VoiceStreamerFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val iaModule = module {
    factory<AssistantCallService> { createWebService() }
    factory<AssistantCallRepository> { AssistantCallRepositoryImpl(get()) }

    single<TokenProvider> { AssistantIaPrefsTokenProvider() }
    single<ProtocolEngine> { DefaultProtocolEngine() }

    factory<VoiceSocketFactory> { DefaultVoiceSocketFactory(get()) }
    factory<VoiceStreamerFactory> { DefaultVoiceStreamerFactory() }

    single<HttpClient> {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(90, TimeUnit.SECONDS)
                    readTimeout(90, TimeUnit.SECONDS)
                    writeTimeout(90, TimeUnit.SECONDS)
                    pingInterval(0, TimeUnit.SECONDS)
                }
                preconfigured = AssistantIaOkHttpClientProvider().create()
            }
            install(WebSockets) {
                this.pingInterval = 0L
            }
        }
    }

    single<WsService> {
        val socketFactory: VoiceSocketFactory = get()
        val streamerFactory: VoiceStreamerFactory = get()
        val tokenProvider: TokenProvider = get()
        DefaultWsService(
            socketFactory = socketFactory,
            protocol = get(),
            audioIoFactory = { DefaultAudioIo(streamerFactory.create()) },
            headersProviderFactory = { sName, sValue, deviceId ->
                DefaultWsHeadersProvider(
                    sessionHeaderNameProvider = { sName },
                    sessionHeaderValueProvider = { sValue },
                    deviceIdProvider = { deviceId },
                    tokenProvider = tokenProvider
                )
            }
        )
    }

    single { AssistantNotificationHelper(get()) }
    single { AssistantSessionManager(get(), get()) }

    viewModel { AssistantCallViewModel(get(), get()) }
}
