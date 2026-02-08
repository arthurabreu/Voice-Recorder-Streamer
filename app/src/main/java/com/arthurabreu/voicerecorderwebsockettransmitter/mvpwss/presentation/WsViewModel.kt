package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.presentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.model.WsSessionState
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.service.WsForegroundService
import com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.service.WsServiceBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Example ViewModel that binds to [WsForegroundService].
 * Provides a clean way to interact with the service from any screen.
 */
class WsViewModel : ViewModel() {

    private val _binder = MutableStateFlow<WsServiceBinder<Any>?>(null)
    
    val sessionState: StateFlow<WsSessionState<Any>> = _binder
        .flatMapLatest { it?.state ?: MutableStateFlow(WsSessionState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WsSessionState())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            _binder.value = service as? WsServiceBinder<Any>
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _binder.value = null
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, WsForegroundService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        context.unbindService(serviceConnection)
        _binder.value = null
    }

    fun startSession(url: String) {
        _binder.value?.startSession(url)
    }

    fun stopSession() {
        _binder.value?.stopSession()
    }

    override fun onCleared() {
        super.onCleared()
        // Note: Don't unbind here if you want it to persist across VM instances, 
        // but typically you'd unbind in the Activity/Fragment lifecycle.
    }
}
