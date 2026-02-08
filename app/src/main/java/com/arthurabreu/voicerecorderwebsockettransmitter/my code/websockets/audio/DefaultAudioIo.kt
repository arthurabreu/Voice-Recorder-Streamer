package com.mercantil.core.websockets.audio

import kotlinx.coroutines.flow.Flow
import com.mercantil.core.websockets.util.AssistantWsLog

class DefaultAudioIo(
    private val streamer: VoiceStreamer,
    private val player: AudioPlayer = PcmAudioPlayer()
) : AudioIo {
    override val levels: Flow<Float> = streamer.levels
    override fun frames(language: String): Flow<ByteArray> = streamer.frames(language)
    override fun startPlayer() { player.start() }
    override fun pushForPlayback(bytes: ByteArray) {
        // size of each incoming frame from the server. If frames are around 20 ms, 640 suggests 16 kHz; 960 suggests 24 kHz.
        AssistantWsLog.d(this, "pushForPlayback", "payload_size_bytes=%d", bytes.size)
        player.play(bytes)
    }
    override fun stop() { player.stop() }
}
