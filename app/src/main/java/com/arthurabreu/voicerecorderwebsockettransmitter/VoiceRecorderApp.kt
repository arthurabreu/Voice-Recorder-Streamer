package com.arthurabreu.voicerecorderwebsockettransmitter

import android.app.Application
import com.arthurabreu.voicerecorderwebsockettransmitter.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VoiceRecorderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VoiceRecorderApp)
            modules(appModule)
        }
    }
}
