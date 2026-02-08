package com.mercantil.core.websockets.voice

import com.mercantil.core.websockets.audio.VoiceStreamer

/**
 * Implementação padrão de `VoiceStreamerFactory`.
 * Cria um `VoiceStreamer` para captura de microfone com configuração padrão (16kHz/mono/16-bit).
 */
class DefaultVoiceStreamerFactory : VoiceStreamerFactory {
    override fun create(): VoiceStreamer {
        return VoiceStreamer()
    }
}
