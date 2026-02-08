package com.mercantil.assistant.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.mercantil.core.websockets.service.WsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class VoiceWebSocketService : Service() {

    private val wsService: WsService by inject()
    private val notificationHelper: AssistantNotificationHelper by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventsJob: Job? = null

    companion object {
        private const val ACTION_START = "com.mercantil.assistant.action.START"
        private const val ACTION_STOP = "com.mercantil.assistant.action.STOP"
        private const val EXTRA_LANGUAGE = "extra_language"

        fun start(context: Context, language: String) {
            val intent = Intent(context, VoiceWebSocketService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LANGUAGE, language)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceWebSocketService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "pt-BR"
                startForegroundService()
                observeEvents()
                serviceScope.launch {
                    wsService.start(language)
                }
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
        }
        return START_STICKY
    }

    private fun observeEvents() {
        eventsJob?.cancel()
        eventsJob = wsService.state
            .onEach { state ->
                if (state.status == "Closed" || state.status.startsWith("Error")) {
                    stopForegroundService()
                }
            }
            .launchIn(serviceScope)
    }

    @SuppressLint("InlinedApi")
    private fun startForegroundService() {
        val notification = notificationHelper.createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AssistantNotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(AssistantNotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        serviceScope.launch {
            wsService.stop()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
