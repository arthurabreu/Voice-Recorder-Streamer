package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer

/**
 * Default implementation of [VoiceStreamerFactory].
 */
class DefaultVoiceStreamerFactory : VoiceStreamerFactory {
    override fun create(socket: VoiceSocket): VoiceStreamer {
        return VoiceStreamer(socket)
    }
}
