package com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.ui

import androidx.compose.runtime.Composable
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.ui.SpeechToTextScreen

// Wrapper to keep existing implementation intact but expose under the features.transcription package
@Composable
fun TranscriptionScreen() {
    SpeechToTextScreen()
}