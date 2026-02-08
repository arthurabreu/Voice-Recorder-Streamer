package com.mercantil.core.websockets.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.mercantil.core.websockets.util.AssistantWsLog

/**
 * Configuração padronizada de captura de áudio usada em todo o app para o fluxo de voz do usuario para o wss.
 * Mantém taxa de amostragem, canais e formato em um único lugar.
 */
object AudioCaptureConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val FRAME_BYTES_20MS = 640 // 20ms @ 16kHz mono 16-bit
}

@RequiresPermission(Manifest.permission.RECORD_AUDIO)
/**
 * Constrói um `AudioRecord` pronto para captura no padrão do Assistente IA
 * (16kHz/mono/16-bit) com buffer mínimo ampliado para maior estabilidade.
 */
fun createAudioRecord(): AudioRecord {
    val minBuf = AudioRecord.getMinBufferSize(
        AudioCaptureConfig.SAMPLE_RATE,
        AudioCaptureConfig.CHANNEL_CONFIG,
        AudioCaptureConfig.AUDIO_FORMAT
    )
    val bufferSize = (minBuf * 2).coerceAtLeast(3200)
    AssistantWsLog.d(AudioCaptureConfig::class.java, "createAudioRecord",
        "Recorder config → desired_sample_rate=%d, channel_mask=0x%X, audio_format=%d, min_buffer_bytes=%d, buffer_bytes=%d",
        AudioCaptureConfig.SAMPLE_RATE, AudioCaptureConfig.CHANNEL_CONFIG, AudioCaptureConfig.AUDIO_FORMAT, minBuf, bufferSize)
    val rec = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        AudioCaptureConfig.SAMPLE_RATE,
        AudioCaptureConfig.CHANNEL_CONFIG,
        AudioCaptureConfig.AUDIO_FORMAT,
        bufferSize
    )
    AssistantWsLog.d(AudioCaptureConfig::class.java, "createAudioRecord",
        "Recorder built → actual_sample_rate=%d, recorder_state=%d",
        runCatching { rec.sampleRate }.getOrElse { -1 }, rec.state)
    return rec
}