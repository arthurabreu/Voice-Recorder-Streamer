package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.AudioCaptureConfig
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.createAudioRecord
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import java.util.UUID
import kotlin.math.sqrt

class VoiceStreamer(
    private val ws: VoiceSocket
) {
    private var recorder: AudioRecord? = null

    private val _levels = MutableSharedFlow<Float>(extraBufferCapacity = 64)
    val levels: SharedFlow<Float> = _levels

    private fun computeLevel(bytes: ByteArray, length: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt()
            val sample = (hi shl 8) or lo
            val s = if (sample > 32767) sample - 65536 else sample
            sum += (s * s).toDouble()
            count++
            i += 2
        }
        val rms = if (count > 0) sqrt(sum / count) else 0.0
        // normalize RMS roughly: 0..(max ~ 32767)
        val norm = (rms / 32768.0).coerceIn(0.0, 1.0)
        return norm.toFloat()
    }

    @SuppressLint("MissingPermission")
    suspend fun startStreaming(language: String = "pt-BR") {
        val start = """
        {"type":"start","sessionId":"${UUID.randomUUID()}",
         "audio":{"encoding":"LINEAR16","sampleRate":${AudioCaptureConfig.SAMPLE_RATE},"channels":1},
         "language":"$language"}
        """.trimIndent()
        ws.sendText(start)

        recorder = createAudioRecord().also {
            try {
                val sessionId = it.audioSessionId
                if (NoiseSuppressor.isAvailable()) {
                    try { NoiseSuppressor.create(sessionId) } catch (_: Throwable) {}
                }
                if (AutomaticGainControl.isAvailable()) {
                    try { AutomaticGainControl.create(sessionId) } catch (_: Throwable) {}
                }
                it.startRecording()
            } catch (_: SecurityException) { /* permission handled by UI */ }
        }

        val buf = ByteArray(AudioCaptureConfig.FRAME_BYTES_20MS)
        try {
            var running = true
            while (currentCoroutineContext().isActive && running) {
                val read = recorder?.read(buf, 0, buf.size) ?: -1
                if (read > 0) {
                    val level = computeLevel(buf, read)
                    _levels.tryEmit(level)

                    val frame = if (read == buf.size) buf else buf.copyOf(read)
                    val ok = ws.sendBinary(frame)
                    if (!ok) running = false
                } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                    running = false
                }
            }
        } catch (_: Throwable) {
            // ignore, controller handles callbacks
        }
    }

    suspend fun stopStreaming() {
        recorder?.run {
            try { stop() } catch (_: Throwable) {}
            release()
        }
        recorder = null
        ws.sendText("{" + "\"type\":\"stop\"" + "}")
        ws.close()
    }
}