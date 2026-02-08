package com.mercantil.core.websockets.audio

import android.Manifest
import android.media.AudioRecord
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

class VoiceStreamer {
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
        val norm = (rms / 32768.0).coerceIn(0.0, 1.0)
        return norm.toFloat()
    }

    /**
     * Produz frames PCM 16-bit/16kHz/mono de ~20ms cada como Flow.
     * Emite níveis em [levels] em paralelo.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun frames(language: String = "pt-BR"): Flow<ByteArray> = flow {
        var recorder: AudioRecord? = null
        try {
            var permissionDenied = false
            recorder = createAudioRecord().also {
                try {
                    val sessionId = it.audioSessionId
                    if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId)
                    if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId)
                    it.startRecording()
                } catch (_: SecurityException) {
                    permissionDenied = true
                }
            }
            if (permissionDenied) return@flow

            val buf = ByteArray(AudioCaptureConfig.FRAME_BYTES_20MS)
            while (true) {
                if (!coroutineContext.isActive) break
                val read = recorder.read(buf, 0, buf.size) ?: -1
                if (read > 0) {
                    val level = computeLevel(buf, read)
                    _levels.emit(level)
                    emit(if (read == buf.size) buf.copyOf() else buf.copyOf(read))
                } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                    break
                }
            }
        } finally {
            recorder?.stop()
            recorder?.release()
        }
    }
}