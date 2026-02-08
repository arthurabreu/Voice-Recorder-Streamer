package com.mercantil.core.websockets.audio

import kotlinx.coroutines.flow.Flow

/**
 * Interface de I/O de áudio desacoplada do transporte e do protocolo.
 *
 * Responsabilidades:
 * - Expor `Flow` de níveis de microfone e frames PCM prontos para envio.
 * - Abstrair inicialização/parada de player e escrita de bytes recebidos do servidor.
 *
 * Não conhece WebSocket nem regras de protocolo. Implementações vivem em `data.websockets.audio`.
 */
interface AudioIo {
    val levels: Flow<Float>
    fun frames(language: String): Flow<ByteArray>
    fun startPlayer()
    fun pushForPlayback(bytes: ByteArray)
    fun stop()
}