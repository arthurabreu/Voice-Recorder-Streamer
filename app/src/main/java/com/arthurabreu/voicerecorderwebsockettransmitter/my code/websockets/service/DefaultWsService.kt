package com.mercantil.core.websockets.service

import com.mercantil.commons.util.SharedPreferencesUtil
import com.mercantil.core.websockets.audio.AudioIo
import com.mercantil.core.websockets.headers.WsHeadersProvider
import com.mercantil.core.websockets.protocol.ProtocolEngine
import com.mercantil.core.websockets.state.WsUiState
import com.mercantil.core.websockets.transport.VoiceSocket
import com.mercantil.core.websockets.transport.WsEvent
import com.mercantil.core.websockets.voice.VoiceSocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orquestrador de domínio que integra Transporte (WebSocket), Protocolo e Áudio.
 *
 * Responsabilidades:
 * - Gerenciar o ciclo de vida da sessão (connect → stream → close) e expor `StateFlow<WsUiState>` para a UI/VM.
 * - Delegar parsing/decisões de protocolo ao `ProtocolEngine` e I/O de áudio ao `AudioIo`.
 * - Serializar captura e envio por `Flow` com buffers, mantendo latência sob controle.
 *
 * Não conhece UI, Fragment/Activity ou Android Service. Pode ser usado em testes sem Android.
 */
class DefaultWsService(
    private val socketFactory: VoiceSocketFactory,
    private val protocol: ProtocolEngine,
    private val audioIoFactory: () -> AudioIo,
    private val headersProviderFactory: (sName: String, sValue: String, deviceId: String) -> WsHeadersProvider,
    private val wsUrl: String = "wss://kng-ws.corp.dev.n-mercantil.com.br/ws"
) : WsService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(WsUiState())
    override val state: StateFlow<WsUiState> = _state

    private data class SessionConfig(
        val wsUrl: String,
        val language: String,
        val headersProvider: WsHeadersProvider,
    )

    private data class JobsBag(
        val events: Job? = null,
        val stream: Job? = null,
        val levels: Job? = null,
    ) {
        fun cancelAll() {
            events?.cancel()
            stream?.cancel()
            levels?.cancel()
        }
    }

    private data class RuntimeRefs(
        val socket: VoiceSocket?,
        val audio: AudioIo?,
    )

    private var sessionHeaderName: String = "X-User-Session"
    private var sessionHeaderValue: String = ""
    private var deviceId: String = ""
    private var language: String = "pt-BR"

    @Volatile private var captureStarted = false
    @Volatile private var refs: RuntimeRefs = RuntimeRefs(null, null)
    @Volatile private var jobs: JobsBag = JobsBag()

    override suspend fun configure(deviceId: String, sessionHeaderName: String, sessionHeaderValue: String) {
        this.deviceId = deviceId
        this.sessionHeaderName = sessionHeaderName
        this.sessionHeaderValue = sessionHeaderValue
    }

    override suspend fun setExtraPayload(payload: String?) {
        protocol.setExtraPayload(payload)
    }

    override fun clearFinalEntrada() {
        _state.update { it.copy(finalEntrada = null) }
    }

    override suspend fun updateRecordAudioPermission(granted: Boolean) {
        _state.update { it.copy(needsRecordAudioPermission = !granted) }
        if (granted) startCaptureIfPossible()
    }

    override suspend fun start(language: String) {
        this.language = language
        setState { prev ->
            prev.copy(
                lastServerMessage = null,
                finalEntrada = null,
                errorMessage = null,
                status = if (prev.needsRecordAudioPermission) prev.status else "Idle"
            )
        }
        ensureStarted()
    }

    override suspend fun stop() {
        stopInternal()
        setState { prev -> prev.copy(connected = false, status = "Idle") }
    }

    private fun ensureStarted() {
        if (refs.socket != null) return

        val audio = audioIoFactory().also { it.startPlayer() }
        jobs.levels?.cancel()
        jobs = jobs.copy(
            levels = audio.levels
                .buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                .onEach { level -> pushLevel(level) }
                .launchIn(scope)
        )

        val cfg = buildSessionConfig()
        val socket = socketFactory.create(emulate = false, url = cfg.wsUrl, headersProvider = cfg.headersProvider)
        refs = RuntimeRefs(socket = socket, audio = audio)

        // Start connection (no-op for callbackFlow implementation, but safe for others)
        scope.launch { connectWithRetry { socket.connect() } }

        jobs = jobs.copy(
            events = socket.events
                .buffer(capacity = 128)
                .onEach { handleEvent(it) }
                .launchIn(scope)
        )

        setState { it.copy(status = "Connecting…") }
    }

    private fun pushLevel(level: Float) {
        val current = _state.value.levels
        val max = 60
        val trimmed = if (current.size >= max) current.drop(current.size - (max - 1)) else current
        setState { it.copy(levels = trimmed + level) }
    }

    private suspend fun handleEvent(ev: WsEvent) {
        when (ev) {
            is WsEvent.Open -> {
                _state.update { it.copy(status = "Connected", connected = true) }
                refs.socket?.sendText(protocol.onSocketOpen())
            }
            is WsEvent.Text -> {
                val outcome = protocol.onTextMessage(ev.value)
                outcome.uiDelta?.let { delta ->
                    _state.update { prev -> prev.copy(
                        lastServerMessage = delta.lastServerMessage ?: prev.lastServerMessage,
                        finalEntrada = delta.finalEntrada ?: prev.finalEntrada,
                        status = if (prev.status == "Connecting…") "Streaming" else prev.status,
                        errorMessage = delta.errorMessage ?: prev.errorMessage
                    ) }
                }
                outcome.sendText.forEach { refs.socket?.sendText(it) }
                if (outcome.readyToCapture) startCaptureIfPossible()
            }
            is WsEvent.Binary -> refs.audio?.pushForPlayback(ev.bytes)
            is WsEvent.Closed -> {
                setState { it.copy(status = "Closed", connected = false) }
                captureStarted = false
                stopInternal()
            }
            is WsEvent.Failure -> {
                setState { it.copy(status = "Error: ${ev.error.message}", connected = false) }
                captureStarted = false
                stopInternal()
            }
        }
    }

    private fun startCaptureIfPossible() {
        if (captureStarted || state.value.needsRecordAudioPermission) return
        val a = refs.audio ?: return
        captureStarted = true
        jobs.stream?.cancel()
        jobs = jobs.copy(
            stream = a.frames(language)
                .buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                .onEach { bytes -> refs.socket?.sendBinary(bytes) }
                .launchIn(scope)
        )
    }

    private inline fun setState(reduce: (WsUiState) -> WsUiState) {
        _state.update(reduce)
    }

    private suspend fun stopInternal() {
        captureStarted = false
        jobs.cancelAll()
        withContext(Dispatchers.IO) {
            kotlin.runCatching { refs.socket?.close() }
            kotlin.runCatching { refs.audio?.stop() }
        }
        refs = RuntimeRefs(null, null)
        jobs = JobsBag()
    }

    private fun buildSessionConfig(): SessionConfig {
        val headers = headersProviderFactory(sessionHeaderName, sessionHeaderValue, deviceId)
        return SessionConfig(wsUrl = wsUrl, language = language, headersProvider = headers)
    }

    private suspend fun connectWithRetry(connect: suspend () -> Unit) {
        var delayMs = 500L
        repeat(5) { attempt ->
            try {
                connect()
                return
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (_: Throwable) {
                setState { it.copy(status = "Retrying (${attempt + 1})…") }
                kotlinx.coroutines.delay(delayMs + (0..250).random())
                delayMs = (delayMs * 2).coerceAtMost(10_000)
            }
        }
        setState { it.copy(status = "Failed to connect", connected = false) }
    }
}