package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state

/**
 * Transient UI message (aka toast/snackbar) shown by the streaming screen.
 *
 * @param message Human-readable message to show.
 * @param type Visual style/severity of the message.
 */
data class Balloon(val message: String, val type: BalloonType)
