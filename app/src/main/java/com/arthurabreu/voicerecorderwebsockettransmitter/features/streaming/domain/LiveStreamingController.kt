package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

import kotlinx.coroutines.flow.StateFlow
import java.io.File
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.StreamingState

/**
 * Application-level controller for the live streaming feature.
 *
 * What it does (quick view):
 * - Exposes the immutable [state] used by UI to render the screen.
 * - Coordinates streaming lifecycle: start, stop, cancel, save, preview, and saved items management.
 *
 * Notes for juniors/seniors:
 * - This is a domain boundary. It hides data-layer details (WS, audio capture) behind abstractions.
 * - Keep methods small and side-effect free where possible; delegate to collaborators.
 */
interface LiveStreamingController {
    /** UI state as a single source of truth for the feature. */
    val state: StateFlow<StreamingState>

    /** Enable/disable local emulation mode (fake WebSocket). */
    fun setEmulationMode(enabled: Boolean)

    /** Configure the WebSocket server URL. */
    fun setWebSocketUrl(url: String)

    /** Provide a lazy token source used by the WS client. */
    fun setTokenProvider(provider: suspend () -> String)

    /**
     * Start streaming audio to the server.
     *
     * @param language BCP‑47 tag, e.g., "pt-BR".
     * @param outputDir Directory to store a temporary WAV recording while streaming.
     */
    fun start(language: String = "pt-BR", outputDir: File)

    /** Request a graceful stop, keeping the temporary recording available for Save. */
    fun stop()

    /** Cancel the session and discard any temporary recording. */
    fun cancel()

    /** Persist the temporary recording and update state accordingly. */
    fun save()

    /** Re-scan provided directories for saved recordings. */
    fun refreshSaved(vararg dirs: File)

    /** Delete a previously saved file. */
    fun deleteFile(file: File)

    /** Show the player overlay for a specific file. */
    fun preview(file: File)

    /** Consume any transient UI balloon/message. */
    fun consumeBalloon()

    /** Hide player overlay. */
    fun dismissPlayerOverlay()
}
