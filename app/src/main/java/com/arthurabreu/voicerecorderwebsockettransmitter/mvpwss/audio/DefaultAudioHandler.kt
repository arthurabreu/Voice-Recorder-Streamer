package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.audio

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.sqrt

/**
 * Default implementation of [AudioHandler] using Android's [AudioRecord] and [AudioTrack].
 */
class DefaultAudioHandler(private val context: Context) : AudioHandler {

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isCapturing = false

    private val _audioLevels = MutableSharedFlow<Float>()
    override val audioLevels: Flow<Float> = _audioLevels.asSharedFlow()

    private val sampleRate = 16000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun startCapture(): Flow<ByteArray> = flow {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(3200)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelIn,
            encoding,
            bufferSize
        )

        audioRecord?.startRecording()
        isCapturing = true

        val buffer = ByteArray(640) // 20ms at 16kHz
        while (isCapturing) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            if (read > 0) {
                val chunk = buffer.copyOf(read)
                emit(chunk)
                calculateLevel(chunk)
            } else if (read < 0) {
                break
            }
        }
    }

    override fun stopCapture() {
        isCapturing = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun playAudio(data: ByteArray) {
        if (audioTrack == null) {
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelOut)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize.coerceAtLeast(32000))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        }
        audioTrack?.write(data, 0, data.size)
    }

    override fun stopPlayback() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private suspend fun calculateLevel(data: ByteArray) {
        var sum = 0.0
        for (i in data.indices step 2) {
            val sample = ((data[i+1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()
            sum += sample * sample
        }
        val rms = sqrt(sum / (data.size / 2))
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        _audioLevels.emit(normalized)
    }
}
