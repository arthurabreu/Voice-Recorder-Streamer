package com.mercantil.core.websockets.audio

/**
 * Simple audio output abstraction for clean architecture.
 * Start once, feed PCM frames with [play], and stop to release resources.
 */
interface AudioPlayer {
    fun start()
    fun play(bytes: ByteArray)
    fun stop()
}
