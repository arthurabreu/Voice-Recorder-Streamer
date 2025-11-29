package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class FakeVoiceWsClient : VoiceSocket {
    @Volatile private var isOpen: Boolean = false
    private var sentBytes: Long = 0

    private var onClosedCb: ((Int, String) -> Unit)? = null
    private var heartbeatJob: Job? = null

    override suspend fun connect(
        onOpen: () -> Unit,
        onMessage: (String) -> Unit,
        onBinary: (ByteArray) -> Unit,
        onClosed: (code: Int, reason: String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        isOpen = true
        onClosedCb = onClosed
        // Immediately signal open
        onOpen()
        // Start simple heartbeat messages
        try {
            withContext(Dispatchers.IO) {
                var tick = 0
                while (isOpen) {
                    delay(1000)
                    tick++
                    onMessage("fake: tick=$tick, sentBytes=$sentBytes")
                }
            }
        } catch (e: CancellationException) {
            // normal
        } catch (t: Throwable) {
            onFailure(t)
        }
    }

    override suspend fun sendText(json: String): Boolean {
        if (!isOpen) return false
        // Accept any text; could parse for {"type":"stop"} to auto-close if desired
        return true
    }

    override suspend fun sendBinary(bytes: ByteArray): Boolean {
        if (!isOpen) return false
        sentBytes += bytes.size
        return true
    }

    override fun close(code: Int, reason: String) {
        if (!isOpen) return
        isOpen = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        onClosedCb?.invoke(code, reason)
    }
}
