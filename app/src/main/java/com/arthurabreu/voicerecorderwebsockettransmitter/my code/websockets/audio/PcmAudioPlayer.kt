package com.mercantil.core.websockets.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import com.mercantil.core.provider.ContextProvider
import com.mercantil.core.websockets.util.AssistantWsLog
import kotlin.math.max
import kotlin.math.pow

/**
 * Player PCM para reprodução de áudio proveniente do WebSocket utilizando o caminho de mídia
 * (USAGE_MEDIA/STREAM_MUSIC) para garantir volume consistente no alto‑falante.
 *
 * Parâmetros (constructor):
 * @param sampleRate Taxa de amostragem do PCM (Hz); deve corresponder ao áudio recebido (ex.: 16000/24000).
 * @param channelConfig Máscara de canal de saída (ex.: AudioFormat.CHANNEL_OUT_MONO) usada pelo AudioTrack.
 * @param audioFormat Formato de codificação do PCM (ex.: AudioFormat.ENCODING_PCM_16BIT) esperado nos bytes.
 * @param outputGainDb Ganho de software (em dB) aplicado a cada frame antes de enviar ao AudioTrack; 0f desabilita.
 * @param enableLoudnessEnhancer Ativa o efeito LoudnessEnhancer na sessão do AudioTrack para reforçar loudness.
 *
 * Principais internos da classe:
 * @param gainLinear: fator linear derivado de outputGainDb (10^(dB/20)) usado para amplificar as amostras.
 * @param track: instância do AudioTrack em modo STREAM que reproduz continuamente os bytes.
 * @param loudness: instância opcional de LoudnessEnhancer aplicada ao audioSessionId do AudioTrack.
 * @param audioManager: gerenciador de áudio obtido via ContextProvider para solicitar/abandonar AudioFocus.
 * @param isStopped: flag volátil de controle de ciclo de vida para evitar corridas entre write() e stop().
 * @param lock: monitor de sincronização para proteger transições de estado (start/stop) e snapshots em write().
 *
 * Fluxo de uso: start() configura o AudioTrack e solicita foco de mídia (quando possível);
 * write() aplica ganho com limitador simples e escreve de forma não bloqueante; stop() libera efeitos,
 * recursos e abandona o foco quando aplicável.
 */
class PcmAudioPlayer(
    private val sampleRate: Int = 24000,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val outputGainDb: Float = 6f,
    private val enableLoudnessEnhancer: Boolean = true
) : AudioPlayer {
    private var track: AudioTrack? = null
    private var loudness: LoudnessEnhancer? = null
    private var audioManager: AudioManager? = null

    @Volatile private var isStopped: Boolean = true
    private val lock = Any()

    private val gainLinear: Float = if (outputGainDb <= 0f) 1f else 10f.pow(outputGainDb / 20f)
    @Volatile private var firstPayloadLogged: Boolean = false

    override fun start() {
        synchronized(lock) {
            if (track != null) return
            isStopped = false
        }

        val appContext = runCatching { ContextProvider.context }.getOrNull()
        audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        @Suppress("DEPRECATION")
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager?.requestAudioFocus(req)
            } else {
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        }

        val nativeSr = try {
            AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        } catch (_: Throwable) { -1 }
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufferSize, 32 * 1024)
        AssistantWsLog.d(this, "start",
            "AudioTrack setup → desired_sample_rate=%d, native_output_sample_rate=%d, channel_mask=0x%X, audio_format=%d, min_buffer_bytes=%d, buffer_bytes=%d",
            sampleRate, nativeSr, channelConfig, audioFormat, minBufferSize, bufferSize)
        val built = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
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
                .setBufferSizeInBytes(bufferSize)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }
        track = built
        AssistantWsLog.d(this, "start",
            "AudioTrack built → track_state=%d, track_play_state=%d, audio_session_id=%d",
            built.state, built.playState, built.audioSessionId)
        built.setVolume(1.0f)

        if (enableLoudnessEnhancer) {
            try {
                loudness = LoudnessEnhancer(built.audioSessionId).apply {
                    setTargetGain(1200) // ~ +12 dB
                    enabled = true
                }
                AssistantWsLog.d(this@PcmAudioPlayer, "start", "LoudnessEnhancer enabled with +12 dB")
            } catch (t: Throwable) {
                loudness = null
                AssistantWsLog.w(this@PcmAudioPlayer, "start", "LoudnessEnhancer not available: %s", t.message ?: "")
            }
        }

        built.play()
        AssistantWsLog.d(this, "start",
            "AudioTrack play() called → track_state=%d, track_play_state=%d",
            built.state, built.playState)
    }

    private fun applyGainWithLimiter(src: ByteArray, dst: ByteArray) {
        if (gainLinear == 1f) {
            System.arraycopy(src, 0, dst, 0, src.size)
            return
        }
        var i = 0
        while (i < src.size) {
            val lo = src[i].toInt() and 0xFF
            val hi = src[i + 1].toInt()
            var sample = (hi shl 8) or lo
            if (sample > Short.MAX_VALUE) sample -= 65536

            var amplified = (sample * gainLinear).toInt()
            if (amplified > Short.MAX_VALUE.toInt()) amplified = Short.MAX_VALUE.toInt()
            if (amplified < Short.MIN_VALUE.toInt()) amplified = Short.MIN_VALUE.toInt()

            dst[i] = (amplified and 0xFF).toByte()
            dst[i + 1] = ((amplified ushr 8) and 0xFF).toByte()
            i += 2
        }
    }

    override fun play(bytes: ByteArray) = write(bytes)

    fun write(bytes: ByteArray) {
        val currentTrack = synchronized(lock) {
            if (isStopped) null else track
        } ?: return

        // One-time dump of the first payload for format inspection
        if (!firstPayloadLogged) {
            firstPayloadLogged = true
            val dumpLen = minOf(32, bytes.size)
            val hex = AssistantWsLog.hexDump(bytes, dumpLen)
            AssistantWsLog.d(this, "write",
                "first_audio_payload → payload_size_bytes=%d, first_bytes_hex[%d]=%s",
                bytes.size, dumpLen, hex)
            if (bytes.size % 2 != 0) {
                AssistantWsLog.w(this, "write", "audio_payload_size_bytes is not a multiple of 2 (expected 16-bit PCM little-endian): payload_size_bytes=%d", bytes.size)
            }
        }

        // If the track is not ready/playing anymore, skip to avoid native crashes
        if (currentTrack.state != AudioTrack.STATE_INITIALIZED || currentTrack.playState == AudioTrack.PLAYSTATE_STOPPED) {
            AssistantWsLog.e(this, "write",
                "AudioTrack not ready: track_state=%d, track_play_state=%d",
                currentTrack.state, currentTrack.playState)
            return
        }

        val out: ByteArray = if (gainLinear != 1f) {
            val buf = ByteArray(bytes.size)
            applyGainWithLimiter(bytes, buf)
            buf
        } else bytes

        var totalWritten = 0
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var offset = 0
                while (offset < out.size) {
                    val written = currentTrack
                        .write(
                            out,
                            offset,
                            out.size - offset,
                            AudioTrack.WRITE_BLOCKING
                        )
                    if (written < 0) break
                    if (written == 0) {
                        // Yield and retry
                        Thread.yield()
                        continue
                    }
                    offset += written
                    totalWritten += written
                }
            } else {
                var offset = 0
                while (offset < out.size) {
                    val written = currentTrack.write(out, offset, out.size - offset)
                    if (written <= 0) break
                    offset += written
                    totalWritten += written
                }
            }
        } catch (t: Throwable) {
            // Swallow write exceptions caused by late stop/release to avoid app crash
            AssistantWsLog.e(this, "write", t, buildString {
                append("Audio error: cause -> ${t.cause} \n ")
                append("localizedMessage -> ${t.localizedMessage}")
                append("message -> ${t.message}\"")
            })
        } finally {
            AssistantWsLog.d(this, "write",
                "AudioTrack write → requested_bytes=%d, written_bytes=%d, track_state=%d, track_play_state=%d",
                out.size, totalWritten, currentTrack.state, currentTrack.playState)
        }
    }

    override fun stop() {
        val toRelease: AudioTrack?
        val toDisable: LoudnessEnhancer?
        synchronized(lock) {
            if (isStopped) {
                toRelease = null
                toDisable = null
            } else {
                isStopped = true
                toRelease = track
                toDisable = loudness
                track = null
                loudness = null
            }
        }

        toDisable?.enabled = false
        toDisable?.release()

        toRelease?.stop()
        toRelease?.release()

        @Suppress("DEPRECATION")
        if (audioManager != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            audioManager?.abandonAudioFocus(null)
        }
        audioManager = null
    }
}
