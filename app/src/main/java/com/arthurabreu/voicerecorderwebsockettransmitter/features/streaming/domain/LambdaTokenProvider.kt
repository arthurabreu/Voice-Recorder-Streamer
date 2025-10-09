package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

/**
 * Simple [TokenProvider] implementation that delegates to a suspend lambda.
 *
 * @param block Suspend function that returns a token when invoked.
 */
class LambdaTokenProvider(private val block: suspend () -> String) : TokenProvider {
    override suspend fun getToken(): String = block()
}
