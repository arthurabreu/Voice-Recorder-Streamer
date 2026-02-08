package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.koin.android.ext.android.get

/**
 * Foreground Service that keeps the WebSocket session alive outside the app lifecycle.
 */
class WsForegroundService : Service() {

    // Using Any for generic type to allow flexibility in the concrete service
    private val engine: WsEngine<Any> by lazy { get() }
    
    private val binder by lazy {
        WsServiceBinder(
            engine = engine,
            startService = { url, headers -> startSession(url, headers) },
            stopService = { stopSession() }
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val headers = emptyMap<String, String>() // In a real app, pass serialized headers
                engine.start(url, headers)
                startForeground(NOTIFICATION_ID, createNotification())
            }
            ACTION_STOP -> {
                engine.stop()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startSession(url: String, headers: Map<String, String>) {
        val intent = Intent(this, WsForegroundService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_URL, url)
            // Note: In a production app, handle headers properly (e.g., via bundle or Repository)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSession() {
        val intent = Intent(this, WsForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        startService(intent)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice WebSocket Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Assistant Active")
            .setContentText("Listening...")
            .setSmallIcon(android.R.drawable.presence_audio_busy)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ws_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "ACTION_START"
        private const val ACTION_STOP = "ACTION_STOP"
        private const val EXTRA_URL = "EXTRA_URL"
    }
}
