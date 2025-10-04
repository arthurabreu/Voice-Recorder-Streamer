package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FakeVoiceWsClient : VoiceSocket {
    @Volatile private var isOpen: Boolean = false
    private var sentBytes: Long = 0

    private var onClosedCb: ((Int, String) -> Unit)? = null
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    override fun connect(
        scope: CoroutineScope,
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
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            var tick = 0
            try {
                while (isOpen) {
                    delay(1000)
                    tick++
                    onMessage("fake: tick=$tick, sentBytes=$sentBytes")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when closing; do not propagate as failure
            } catch (t: Throwable) {
                onFailure(t)
            }
        }
    }

    override fun sendText(json: String): Boolean {
        if (!isOpen) return false
        // Accept any text; could parse for {"type":"stop"} to auto-close if desired
        return true
    }

    override fun sendBinary(bytes: ByteArray): Boolean {
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
