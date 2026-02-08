package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.audio

import kotlinx.coroutines.flow.Flow

/**
 * Generic interface for audio operations.
 */
interface AudioHandler {
    /**
     * Starts capturing audio and returns a flow of audio chunks.
     */
    fun startCapture(): Flow<ByteArray>

    /**
     * Stops audio capture.
     */
    fun stopCapture()

    /**
     * Plays received audio data.
     */
    fun playAudio(data: ByteArray)

    /**
     * Stops audio playback.
     */
    fun stopPlayback()

    /**
     * Flow of audio power levels for visualization.
     */
    val audioLevels: Flow<Float>
}
