package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.AudioCaptureConfig
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.createAudioRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class VoiceStreamer(
    private val ws: VoiceWsClient,
    private val ioScope: CoroutineScope
) {
    private var recorder: android.media.AudioRecord? = null
    private var streamingJob: Job? = null
    private var onLevel: ((Float) -> Unit)? = null
    private var wavWriter: PcmWavWriter? = null

    fun startRecordingTo(file: File) {
        if (wavWriter != null) return
        val writer = PcmWavWriter(AudioCaptureConfig.SAMPLE_RATE, 1)
        writer.open(file)
        wavWriter = writer
    }

    fun stopRecording(): File? {
        return try {
            wavWriter?.close()
            null
        } finally {
            wavWriter = null
        }
    }

    fun setOnLevelListener(listener: ((Float) -> Unit)?) {
        onLevel = listener
    }

    private fun computeLevel(bytes: ByteArray, length: Int): Float {
        // Compute RMS level from 16-bit PCM little-endian, normalize approximately to 0..1
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
        val rms = if (count > 0) Math.sqrt(sum / count) else 0.0
        // normalize RMS roughly: 0..(max ~ 32767)
        val norm = (rms / 32768.0).coerceIn(0.0, 1.0)
        return norm.toFloat()
    }

    fun startStreaming(language: String = "pt-BR") {
        if (streamingJob != null) return

        ws.connect(
            scope = ioScope,
            onOpen = {
                val start = """
                {"type":"start","sessionId":"${java.util.UUID.randomUUID()}",
                 "audio":{"encoding":"LINEAR16","sampleRate":${AudioCaptureConfig.SAMPLE_RATE},"channels":1},
                 "language":"$language"}
                """.trimIndent()
                ws.sendText(start)

                recorder = createAudioRecord().also {
                    try {
                        val sessionId = it.audioSessionId
                        if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                            try { android.media.audiofx.NoiseSuppressor.create(sessionId) } catch (_: Throwable) {}
                        }
                        if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                            try { android.media.audiofx.AutomaticGainControl.create(sessionId) } catch (_: Throwable) {}
                        }
                        it.startRecording()
                    } catch (_: SecurityException) { /* permission handled by UI */ }
                }

                streamingJob = ioScope.launch(Dispatchers.IO) {
                    val buf = ByteArray(AudioCaptureConfig.FRAME_BYTES_20MS)
                    var running = true
                    while (isActive && running) {
                        val read = recorder?.read(buf, 0, buf.size) ?: -1
                        if (read > 0) {
                            onLevel?.invoke(computeLevel(buf, read))
                            val frame = if (read == buf.size) buf else buf.copyOf(read)
                            // Send over WS
                            if (!ws.sendBinary(frame)) {
                                running = false
                            }
                            // Optionally record to WAV
                            wavWriter?.write(frame)
                        } else if (read == android.media.AudioRecord.ERROR_INVALID_OPERATION || read == android.media.AudioRecord.ERROR_BAD_VALUE) {
                            running = false
                        }
                    }
                }
            },
            onFailure = { _ -> stopStreaming() },
            onClosed = { _, _ -> stopStreaming() }
        )
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        recorder?.run {
            try { stop() } catch (_: Throwable) {}
            release()
        }
        recorder = null
        ws.sendText("{" + "\"type\":\"stop\"" + "}")
        ws.close()
    }
}