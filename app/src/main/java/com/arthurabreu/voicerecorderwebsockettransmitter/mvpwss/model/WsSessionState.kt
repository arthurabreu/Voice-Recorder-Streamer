package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.model

/**
 * Generic state for the WebSocket session.
 */
data class WsSessionState<T>(
    val status: SessionStatus = SessionStatus.Idle,
    val lastData: T? = null,
    val lastRawMessage: String? = null,
    val errorMessage: String? = null,
    val audioLevels: List<Float> = emptyList(),
    val isRecording: Boolean = false
)

enum class SessionStatus {
    Idle,
    Connecting,
    Connected,
    Streaming,
    Closing,
    Closed,
    Error
}
