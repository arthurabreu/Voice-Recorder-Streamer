package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer
import kotlinx.coroutines.CoroutineScope

/**
 * Factory for creating [VoiceStreamer]s bound to a socket and coroutine scope.
 */
interface VoiceStreamerFactory {
    /**
     * @param socket Data-layer socket abstraction used to send audio frames.
     * @param scope Coroutine scope that will own streaming jobs.
     */
    fun create(socket: VoiceSocket, scope: CoroutineScope): VoiceStreamer
}
