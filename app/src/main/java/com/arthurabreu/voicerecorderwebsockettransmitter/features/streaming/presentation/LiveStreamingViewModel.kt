package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.LiveStreamingController
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain.LiveStreamingControllerFactory
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.StreamingState
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class LiveStreamingViewModel(
    private val controllerFactory: LiveStreamingControllerFactory
) : ViewModel() {

    private val controller: LiveStreamingController = controllerFactory.create(viewModelScope)

    // Unified state for UI
    val state: StateFlow<StreamingState> = controller.state

    fun setEmulationMode(enabled: Boolean) = controller.setEmulationMode(enabled)
    fun setWebSocketUrl(url: String) = controller.setWebSocketUrl(url)
    fun setTokenProvider(provider: suspend () -> String) = controller.setTokenProvider(provider)

    fun start(language: String = "pt-BR", outputDir: File) = controller.start(language, outputDir)
    fun stop() = controller.stop()
    fun cancel() = controller.cancel()
    fun save() = controller.save()

    fun refreshSaved(vararg dirs: File) = controller.refreshSaved(*dirs)
    fun deleteFile(file: File) = controller.deleteFile(file)
    fun preview(file: File) = controller.preview(file)

    fun consumeBalloon() = controller.consumeBalloon()
    fun dismissPlayerOverlay() = controller.dismissPlayerOverlay()
}