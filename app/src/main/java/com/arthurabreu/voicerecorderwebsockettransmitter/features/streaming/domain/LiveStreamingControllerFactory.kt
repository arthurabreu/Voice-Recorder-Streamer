package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import kotlinx.coroutines.CoroutineScope

/**
 * Factory for creating instances of [LiveStreamingController].
 *
 * Prefer injecting this factory instead of the concrete implementation to respect DIP.
 *
 * @see LiveStreamingController
 */
interface LiveStreamingControllerFactory {
    /**
     * Creates a new [LiveStreamingController] bound to the provided [scope].
     *
     * @param scope Coroutine scope that will own controller jobs.
     * @return A configured [LiveStreamingController].
     */
    fun create(scope: CoroutineScope): LiveStreamingController
}
