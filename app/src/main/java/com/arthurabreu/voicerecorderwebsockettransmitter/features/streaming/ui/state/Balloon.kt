package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state

data class Balloon(val message: String, val type: Type) {
    enum class Type { Success, Error }
}
