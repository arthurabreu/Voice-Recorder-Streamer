package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

/**
 * Headless PCM audio player used to play back bytes received over WebSocket.
 *
 * Assumes 16 kHz, mono, 16-bit little-endian PCM by default, matching the
 * capture side of the streaming feature.
 */
class PcmAudioPlayer(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var track: AudioTrack? = null
    private val buffer = Channel<ByteArray>(capacity = Channel.UNLIMITED)

    fun prepare() {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf.coerceAtLeast(4 * 1024))
            .build()
        track?.play()
    }

    suspend fun run() {
        try {
            withContext(Dispatchers.IO) {
                for (chunk in buffer) {
                    val t = track ?: break
                    var offset = 0
                    while (offset < chunk.size) {
                        val written = t.write(chunk, offset, chunk.size - offset)
                        if (written <= 0) break
                        offset += written
                    }
                }
            }
        } catch (_: CancellationException) {
            // normal on stop()
        } finally {
            try { track?.stop() } catch (_: Throwable) {}
            try { track?.release() } catch (_: Throwable) {}
            track = null
        }
    }

    fun offerPcm(bytes: ByteArray) {
        buffer.trySend(bytes)
    }

    fun stop() {
        buffer.close()
    }
}
