package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state

import java.io.File

data class StreamingState(
    val status: String = "Idle",
    val uiState: UiState = UiState.Idle,
    val lastServerMessage: String = "",
    val levels: List<Float> = emptyList(),
    val balloon: Balloon? = null,
    val savedFile: File? = null,
    val showPlayerOverlay: File? = null,
    val savedItems: List<File> = emptyList()
)