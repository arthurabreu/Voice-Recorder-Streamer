package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer
import kotlinx.coroutines.CoroutineScope

interface VoiceStreamerFactory {
    fun create(socket: VoiceSocket, scope: CoroutineScope): VoiceStreamer
}

class DefaultVoiceStreamerFactory : VoiceStreamerFactory {
    override fun create(socket: VoiceSocket, scope: CoroutineScope): VoiceStreamer {
        return VoiceStreamer(socket, scope)
    }
}
