package com.mercantil.core.websockets.voice

import com.mercantil.core.websockets.audio.VoiceStreamer

/**
 * Fábrica para criar instâncias de `VoiceStreamer` (captura de microfone).
 *
 * Responsabilidades:
 * - Isolar criação/configuração do capturador de áudio da camada de domínio.
 * - Facilitar testes trocando a implementação via DI.
 */
interface VoiceStreamerFactory {
    fun create(): VoiceStreamer
}
