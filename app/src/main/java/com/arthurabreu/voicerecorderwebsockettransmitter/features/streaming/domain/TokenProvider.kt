package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

/**
 * Abstraction for providing an authorization token.
 * This makes networking code independent from how the token is retrieved.
 */
interface TokenProvider {
    /** @return a valid token string or throws on failure. */
    suspend fun getToken(): String
}
