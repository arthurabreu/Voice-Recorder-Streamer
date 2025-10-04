package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission

object AudioCaptureConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val FRAME_BYTES_20MS = 1280 // 20ms @ 16kHz mono 16-bit
}

@RequiresPermission(Manifest.permission.RECORD_AUDIO)
fun createAudioRecord(): AudioRecord {
    val minBuf = AudioRecord.getMinBufferSize(
        AudioCaptureConfig.SAMPLE_RATE,
        AudioCaptureConfig.CHANNEL_CONFIG,
        AudioCaptureConfig.AUDIO_FORMAT
    )
    val bufferSize = (minBuf * 2).coerceAtLeast(3200)
    return AudioRecord(
        MediaRecorder.AudioSource.MIC,
        AudioCaptureConfig.SAMPLE_RATE,
        AudioCaptureConfig.CHANNEL_CONFIG,
        AudioCaptureConfig.AUDIO_FORMAT,
        bufferSize
    )
}