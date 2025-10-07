package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

/**
 * Abstraction for providing an authorization token.
 * This makes networking code independent from how the token is retrieved.
 */
interface TokenProvider {
    suspend fun getToken(): String
}

/** Simple adapter around a suspend lambda to avoid breaking existing callers. */
class LambdaTokenProvider(private val block: suspend () -> String) : TokenProvider {
    override suspend fun getToken(): String = block()
}
