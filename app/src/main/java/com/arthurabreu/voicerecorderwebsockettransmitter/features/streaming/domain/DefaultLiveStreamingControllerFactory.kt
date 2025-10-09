package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import kotlinx.coroutines.CoroutineScope

/**
 * Default implementation of [LiveStreamingControllerFactory].
 *
 * @property socketFactory Abstraction to create a [com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceSocket].
 * @property streamerFactory Abstraction to create a [com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceStreamer].
 */
class DefaultLiveStreamingControllerFactory(
    private val socketFactory: VoiceSocketFactory,
    private val streamerFactory: VoiceStreamerFactory
) : LiveStreamingControllerFactory {

    /**
     * Creates a [DefaultLiveStreamingController] configured with provided dependencies.
     *
     * @param scope Coroutine scope that will own controller jobs.
     */
    override fun create(scope: CoroutineScope): LiveStreamingController {
        return DefaultLiveStreamingController(socketFactory, streamerFactory, scope)
    }
}
