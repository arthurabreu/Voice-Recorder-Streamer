package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer

/**
 * Factory for creating [VoiceStreamer]s bound to a socket.
 */
interface VoiceStreamerFactory {
    /**
     * @param socket Data-layer socket abstraction used to send audio frames.
     */
    fun create(socket: VoiceSocket): VoiceStreamer
}
