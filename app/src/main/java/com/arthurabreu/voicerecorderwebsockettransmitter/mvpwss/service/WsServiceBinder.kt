package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.service

import android.os.Binder
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.model.WsSessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Binder for the [WsForegroundService].
 * Allows UI components to observe the state and control the session.
 */
class WsServiceBinder<T>(
    private val engine: WsEngine<T>,
    private val startService: (url: String, headers: Map<String, String>) -> Unit,
    private val stopService: () -> Unit
) : Binder() {
    
    val state: StateFlow<WsSessionState<T>> = engine.state
    
    fun startSession(url: String, headers: Map<String, String> = emptyMap()) {
        startService(url, headers)
    }
    
    fun stopSession() {
        stopService()
    }
}
