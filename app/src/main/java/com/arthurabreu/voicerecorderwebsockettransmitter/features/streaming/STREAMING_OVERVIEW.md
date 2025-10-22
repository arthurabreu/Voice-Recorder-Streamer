### Visão geral do pacote `streaming`
O pacote `features.streaming` implementa o “envio de áudio ao vivo” para um backend (o ADK do banco, por exemplo) via WebSocket. Ele usa:
- `AudioRecord` do Android para capturar áudio cru (PCM) do microfone.
- `OkHttp` para a conexão WebSocket (envio de mensagens de controle em JSON e frames binários de áudio).
- Coroutines (`viewModelScope`, `Dispatchers.IO`) para rodar a captura/streaming fora da UI.

Principais arquivos e papéis:
- `domain/AudioCapture.kt`: define a configuração de áudio e cria o `AudioRecord`.
- `data/VoiceWsClient.kt`: cliente WebSocket genérico (conectar, enviar texto/binário, fechar).
- `data/VoiceStreamer.kt`: orquestra a sessão de streaming (abre WS, envia `start`, captura e envia frames de áudio, envia `stop`).
- `presentation/LiveStreamingViewModel.kt`: expõe estado para a UI e aciona `start`/`stop`.
- `ui/LiveStreamingScreen.kt`: tela com botões Start/Stop e status.

---

### `AudioCapture.kt`: configuração e criação do `AudioRecord`
Trechos-chave:
```kotlin
object AudioCaptureConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val FRAME_BYTES_20MS = 1280 // 20ms @ 16kHz mono 16-bit
}

fun createAudioRecord(): AudioRecord {
    val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    val bufferSize = (minBuf * 2).coerceAtLeast(3200)
    return AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize
    )
}
```
O que isso significa:
- `SAMPLE_RATE = 16000`: taxa de amostragem de 16 kHz, padrão em ASR (voz) por ser leve e suficiente.
- `CHANNEL_IN_MONO`: 1 canal (mono). Para fala, mono é o mais comum.
- `ENCODING_PCM_16BIT`: cada amostra tem 16 bits (2 bytes), PCM linear sem compressão.
- `MediaRecorder.AudioSource.VOICE_RECOGNITION`: fonte pensada para reconhecimento de voz (pode aplicar AGC/NS/AEC conforme dispositivo), melhor que `MIC` na maioria dos casos de fala.
- `bufferSize`: usa o mínimo dobrado para reduzir underflows/overflows.

Observação importante sobre `FRAME_BYTES_20MS`:
- O comentário diz “20ms @ 16kHz 16-bit mono”, mas o valor `1280` bytes corresponde a ~40 ms, não 20 ms. Cálculo:
  - 16 kHz → 16.000 amostras/seg.
  - 20 ms = 0,02 s → 16.000 × 0,02 = 320 amostras.
  - 16-bit mono = 2 bytes por amostra → 320 × 2 = 640 bytes.
  - Logo, 20 ms deveria ser `640` bytes. `1280` bytes é ~40 ms (ou 20 ms em estéreo). O código está enviando frames de ~40 ms. Não é um erro funcional (40 ms é ok), mas vale alinhar o comentário ou o tamanho do frame.

---

### `VoiceWsClient.kt`: WebSocket com OkHttp
```kotlin
class VoiceWsClient(
    private val url: String,
    private val authTokenProvider: suspend () -> String
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect(
        scope: CoroutineScope,
        onOpen: () -> Unit = {},
        onMessage: (String) -> Unit = {},
        onBinary: (ByteArray) -> Unit = {},
        onClosed: (code: Int, reason: String) -> Unit = { _, _ -> },
        onFailure: (Throwable) -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            val token = authTokenProvider()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) { onOpen() }
                override fun onMessage(ws: WebSocket, text: String) { onMessage(text) }
                override fun onMessage(ws: WebSocket, bytes: ByteString) { onBinary(bytes.toByteArray()) }
                override fun onClosing(ws: WebSocket, code: Int, reason: String) { ws.close(code, reason) }
                override fun onClosed(ws: WebSocket, code: Int, reason: String) { onClosed(code, reason) }
                override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) { onFailure(t) }
            })
        }
    }

    fun sendText(json: String): Boolean = webSocket?.send(json) == true
    fun sendBinary(bytes: ByteArray): Boolean = webSocket?.send(ByteString.of(*bytes)) == true
    fun close(code: Int = 1000, reason: String = "") { webSocket?.close(code, reason) }
}
```
O que faz:
- Abre uma conexão `wss://` (ou `ws://`) para `url`, adicionando header `Authorization: Bearer <token>` usando um `authTokenProvider` assíncrono.
- Expõe callbacks de ciclo de vida e de mensagens recebidas (texto JSON e binário).
- `sendText` envia mensagens de controle JSON (ex.: `start`/`stop`).
- `sendBinary` envia frames binários (os bytes do áudio PCM).

Tecnologia usada: `OkHttp 4.12` para WebSocket; é estável e comum no Android.

---

### `VoiceStreamer.kt`: orquestra o streaming de áudio
```kotlin
class VoiceStreamer(
    private val ws: VoiceWsClient,
    private val ioScope: CoroutineScope
) {
    private var recorder: AudioRecord? = null
    private var streamingJob: Job? = null

    fun startStreaming(language: String = "pt-BR") {
        if (streamingJob != null) return

        ws.connect(
            scope = ioScope,
            onOpen = {
                val start = """
                {"type":"start","sessionId":"${java.util.UUID.randomUUID()}",
                 "audio":{"encoding":"LINEAR16","sampleRate":${AudioCaptureConfig.SAMPLE_RATE},"channels":1},
                 "language":"$language"}
                """.trimIndent()
                ws.sendText(start)

                recorder = createAudioRecord().also { it.startRecording() }

                streamingJob = ioScope.launch(Dispatchers.IO) {
                    val buf = ByteArray(AudioCaptureConfig.FRAME_BYTES_20MS)
                    var running = true
                    while (isActive && running) {
                        val read = recorder?.read(buf, 0, buf.size) ?: -1
                        if (read > 0) {
                            if (!ws.sendBinary(if (read == buf.size) buf else buf.copyOf(read))) {
                                running = false
                            }
                        } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                            running = false
                        }
                    }
                }
            },
            onFailure = { _ -> stopStreaming() },
            onClosed = { _, _ -> stopStreaming() }
        )
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        recorder?.let {
            try { it.stop() } catch (_: Throwable) { }
            it.release()
        }
        recorder = null
        ws.sendText("{" + "\"type\":\"stop\"" + "}")
        ws.close()
    }
}
```
Fluxo:
1) `ws.connect(...)`: abre a conexão WS e registra callbacks.
2) `onOpen`: quando o socket abre, envia uma mensagem JSON `start` com metadados da sessão: `sessionId`, `audio` e `language`.
   - Exemplo de JSON enviado:
     ```json
     {
       "type": "start",
       "sessionId": "<uuid>",
       "audio": {"encoding": "LINEAR16", "sampleRate": 16000, "channels": 1},
       "language": "pt-BR"
     }
     ```
3) Inicia o `AudioRecord` e entra em um loop de leitura/envio:
   - Lê blocos de áudio em `buf` e chama `ws.sendBinary(...)` para transmitir o payload binário no WebSocket.
   - Se `sendBinary` retornar `false` (buffer cheio ou socket fechado) ou se a leitura falhar, sai do loop.
4) `stopStreaming()`: encerra a captura, envia `{"type":"stop"}` ao servidor e fecha o WebSocket.

Por que enviar JSON + binário?
- É um protocolo comum: controle/metadata em JSON (fácil de depurar), áudio em binário puro (sem Base64), o que reduz latência e overhead.

Backpressure/robustez:
- O retorno booleano de `sendBinary` dá um sinal simples de backpressure: se `false`, você pode aguardar/reconectar. Aqui, ele apenas encerra o loop.

---

### `LiveStreamingViewModel.kt`: estado e controle
```kotlin
class LiveStreamingViewModel : ViewModel() {
    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status

    private val _lastServerMessage = MutableStateFlow("")
    val lastServerMessage: StateFlow<String> = _lastServerMessage

    // TODO: substituir pelo endpoint/token reais
    private val wsClient = VoiceWsClient(
        url = "wss://example.com/adk/voice",
        authTokenProvider = { "" }
    )

    private val streamer = VoiceStreamer(wsClient, viewModelScope)

    fun start(language: String = "pt-BR") {
        _status.value = "Connecting..."
        wsClient.connect(
            scope = viewModelScope,
            onOpen = { _status.value = "Streaming" },
            onMessage = { msg -> _lastServerMessage.value = msg },
            onFailure = { t -> _status.value = "Error: ${'$'}{t.message}" },
            onClosed = { _, _ -> _status.value = "Closed" }
        )
        streamer.startStreaming(language)
    }

    fun stop() {
        streamer.stopStreaming()
        _status.value = "Stopped"
    }
}
```
- Expõe `status` e `lastServerMessage` para a UI.
- Cria o `VoiceWsClient` com URL/token placeholders.
- Observação: há uma duplicação de `connect()` aqui e em `VoiceStreamer.startStreaming()`. Isso pode abrir duas conexões quase ao mesmo tempo (o segundo `connect` sobrescreve o `webSocket` interno). Funciona em alguns casos, mas não é a intenção usual. Duas formas de ajustar no futuro:
  - A) Deixar o `VoiceStreamer` ser o único responsável por conectar e receber callbacks (passar lambdas para ele atualizar o estado), e remover o `wsClient.connect(...)` do ViewModel.
  - B) Conectar no ViewModel e passar o socket já aberto ao `VoiceStreamer` (que só faz a captura e o envio). Por ora, como objetivo era demonstrar, vale apenas ficar ciente.

---

### `LiveStreamingScreen.kt`: UI
```kotlin
@Composable
fun LiveStreamingScreen(onBack: (() -> Unit)? = null) {
    val vm: LiveStreamingViewModel = viewModel()
    val status by vm.status.collectAsState()
    val serverMsg by vm.lastServerMessage.collectAsState()

    // Botões Start/Stop e exibição de status/mensagens do servidor
    Button(onClick = { vm.start("pt-BR") }) { Text("Start streaming") }
    Button(onClick = { vm.stop() }) { Text("Stop") }
}
```
- Tela simples para acionar o streaming e ver o status e a última mensagem vinda do servidor.

---

### O que significa `"audio": { "encoding": "LINEAR16", ... }`
Esse campo faz parte do JSON de `start` enviado ao servidor para declarar como os bytes do áudio virão.
- `encoding: "LINEAR16"`:
  - “Linear PCM 16-bit” (também chamado de PCM signed 16-bit little‑endian). Cada amostra ocupa 2 bytes.
  - É um formato cru, sem compressão, fácil de processar por engines de ASR e de DSP. No Android (`AudioRecord`), os bytes são little‑endian por padrão.
- `sampleRate: 16000`:
  - 16.000 amostras por segundo. 16 kHz é suficiente para a banda de fala (até ~8 kHz) e reduz a banda/latência.
- `channels: 1`:
  - Mono. Para reconhecimento de fala e envio por rede, mono é padrão e economiza 50% do tráfego comparado a estéreo.

Cálculo de tamanho de frame (útil para entender o tráfego):
- Tamanho (bytes) = `sampleRate * duration_s * bytes_por_amostra * canais`.
- Para 20 ms a 16 kHz mono 16-bit: `16000 * 0.02 * 2 * 1 = 640` bytes.
- Para 40 ms: `1280` bytes (que é o que o código envia hoje).

Quando usar compressão (Opus, por exemplo)?
- PCM é simples e tem latência mínima, porém consome mais banda (~256 kbps em 16 kHz 16-bit mono). Se a rede for restrita, considerar Opus (CBR ~24–32 kbps) reduz bastante a banda, ao custo de adicionar um encoder/decoder e um pouco de latência.

---

### Como o fluxo inteiro funciona, passo a passo
1) Usuário toca “Start streaming”.
2) `LiveStreamingViewModel.start()` chama `connect` e `startStreaming`.
3) Ao abrir a conexão, `VoiceStreamer` envia um JSON `start` com `encoding=LINEAR16`, `sampleRate=16000`, `channels=1`, `language="pt-BR"`.
4) `VoiceStreamer` inicia o `AudioRecord` e, em loop, lê blocos de bytes do microfone e envia cada bloco binário pela mesma conexão WebSocket.
5) O servidor ADK pode enviar respostas parciais/finais em JSON (ex.: propostas). Essas mensagens chegam em `onMessage` e são exibidas em `LiveStreamingScreen`.
6) “Stop” encerra a captura, envia um `{"type":"stop"}` e fecha o WebSocket.

---

### Pontos de atenção e sugestões
- Permissões: `RECORD_AUDIO` e `INTERNET` já estão no `AndroidManifest.xml`.
- Token: o `authTokenProvider` hoje retorna `""`. Substitua por uma função que busque um Bearer token do seu backend.
- Frame size: decida entre 20 ms (640 bytes) ou 40 ms (1280 bytes). 20–40 ms é um bom compromisso entre latência e overhead.
- Duplicidade de `connect`: escolha um único lugar para abrir a conexão (ViewModel ou `VoiceStreamer`) para evitar condições de corrida.
- Tratamento de erro/backoff: hoje, ao falhar o `sendBinary`, a captura é encerrada. Em produção, implemente fila/espera/reconexão.
- Timestamps/cabeçalho: se o ADK exigir, você pode prefixar cada frame com um cabeçalho leve (ex.: contador de frames ou `timestamp` em nanos) antes de enviar.
- Fonte de áudio: `VOICE_RECOGNITION` costuma ser melhor para fala; mas teste em devices diferentes.

---

### TL;DR
- O pacote `streaming` captura áudio PCM 16 kHz 16‑bit mono com `AudioRecord` e envia em frames binários pelo WebSocket do `OkHttp`.
- O protocolo usa mensagens JSON (`start`/`stop`) e payload binário para o áudio (`encoding=LINEAR16`).
- O buffer `FRAME_BYTES_20MS` está configurado como `1280` bytes, que na prática é ~40 ms; ajuste para `640` se quiser 20 ms reais.
- Substitua a URL e o `authTokenProvider` pelo endpoint/autenticação do seu ADK e considere unificar o ponto de conexão (`connect`) para evitar duplicidade.

### Visão geral do pacote `streaming`
O pacote `features.streaming` implementa o “envio de áudio ao vivo” para um backend (o ADK do banco, por exemplo) via WebSocket. Ele usa:
- `AudioRecord` do Android para capturar áudio cru (PCM) do microfone.
- `OkHttp` para a conexão WebSocket (envio de mensagens de controle em JSON e frames binários de áudio).
- Coroutines (`viewModelScope`, `Dispatchers.IO`) para rodar a captura/streaming fora da UI.

Principais arquivos e papéis:
- `domain/AudioCapture.kt`: define a configuração de áudio e cria o `AudioRecord`.
- `data/VoiceWsClient.kt`: cliente WebSocket genérico (conectar, enviar texto/binário, fechar).
- `data/VoiceStreamer.kt`: orquestra a sessão de streaming (abre WS, envia `start`, captura e envia frames de áudio, envia `stop`).
- `presentation/LiveStreamingViewModel.kt`: expõe estado para a UI e aciona `start`/`stop`.
- `ui/LiveStreamingScreen.kt`: tela com botões Start/Stop, status, visualização de áudio e balões de sucesso/erro.

---

### Novidades: Emulação, Visualização do Áudio e Balões Animados
Para facilitar o desenvolvimento sem um endpoint real e melhorar o feedback visual:
- Emulação de streaming: o ViewModel possui um modo de emulação (flag interna `emulate = true`) que simula o streaming, gerando níveis de áudio e mensagens de "bytes enviados" sem abrir WebSocket.
- Visualização do áudio: a tela `LiveStreamingScreen` mostra uma barra de "ondas" (vários retângulos verticais) que reagem ao nível de áudio (0..1). Em modo real, o nível vem do microfone; em emulação, é gerado.
- Balões (toast) no topo: ao iniciar com sucesso aparece um balão verde animado; erros/fechamentos mostram um balão vermelho. Eles fazem slide‑in a partir do topo e somem automaticamente após ~2,5s.

Como usar:
- Basta abrir a tela de Live Streaming e tocar "Start streaming". Com `emulate = true` (padrão atual), você verá as barras animando e um balão verde.
- "Stop" interrompe a emulação (ou o fluxo real, se `emulate = false`).
- Para usar com servidor real: no `LiveStreamingViewModel`, defina `emulate = false`, configure a URL e o `authTokenProvider` do `VoiceWsClient` e garanta a permissão de microfone.

---

### `AudioCapture.kt`: configuração e criação do `AudioRecord`
Trechos-chave:
```kotlin
object AudioCaptureConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val FRAME_BYTES_20MS = 1280 // 20ms @ 16kHz mono 16-bit
}

fun createAudioRecord(): AudioRecord {
    val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    val bufferSize = (minBuf * 2).coerceAtLeast(3200)
    return AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize
    )
}
```
O que isso significa:
- `SAMPLE_RATE = 16000`: taxa de amostragem de 16 kHz, padrão em ASR (voz) por ser leve e suficiente.
- `CHANNEL_IN_MONO`: 1 canal (mono). Para fala, mono é o mais comum.
- `ENCODING_PCM_16BIT`: cada amostra tem 16 bits (2 bytes), PCM linear sem compressão.
- `MediaRecorder.AudioSource.VOICE_RECOGNITION`: fonte pensada para reconhecimento de voz (pode aplicar AGC/NS/AEC conforme dispositivo), melhor que `MIC` na maioria dos casos de fala.
- `bufferSize`: usa o mínimo dobrado para reduzir underflows/overflows.

Observação importante sobre `FRAME_BYTES_20MS`:
- O comentário diz “20ms @ 16kHz 16-bit mono”, mas o valor `1280` bytes corresponde a ~40 ms, não 20 ms. Cálculo:
  - 16 kHz → 16.000 amostras/seg.
  - 20 ms = 0,02 s → 16.000 × 0,02 = 320 amostras.
  - 16-bit mono = 2 bytes por amostra → 320 × 2 = 640 bytes.
  - Logo, 20 ms deveria ser `640` bytes. `1280` bytes é ~40 ms (ou 20 ms em estéreo). O código está enviando frames de ~40 ms. Não é um erro funcional (40 ms é ok), mas vale alinhar o comentário ou o tamanho do frame.

---

### `VoiceWsClient.kt`: WebSocket com OkHttp
```kotlin
class VoiceWsClient(
    private val url: String,
    private val authTokenProvider: suspend () -> String
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect(
        scope: CoroutineScope,
        onOpen: () -> Unit = {},
        onMessage: (String) -> Unit = {},
        onBinary: (ByteArray) -> Unit = {},
        onClosed: (code: Int, reason: String) -> Unit = { _, _ -> },
        onFailure: (Throwable) -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            val token = authTokenProvider()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) { onOpen() }
                override fun onMessage(ws: WebSocket, text: String) { onMessage(text) }
                override fun onMessage(ws: WebSocket, bytes: ByteString) { onBinary(bytes.toByteArray()) }
                override fun onClosing(ws: WebSocket, code: Int, reason: String) { ws.close(code, reason) }
                override fun onClosed(ws: WebSocket, code: Int, reason: String) { onClosed(code, reason) }
                override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) { onFailure(t) }
            })
        }
    }

    fun sendText(json: String): Boolean = webSocket?.send(json) == true
    fun sendBinary(bytes: ByteArray): Boolean = webSocket?.send(ByteString.of(*bytes)) == true
    fun close(code: Int = 1000, reason: String = "") { webSocket?.close(code, reason) }
}
```
O que faz:
- Abre uma conexão `wss://` (ou `ws://`) para `url`, adicionando header `Authorization: Bearer <token>` usando um `authTokenProvider` assíncrono.
- Expõe callbacks de ciclo de vida e de mensagens recebidas (texto JSON e binário).
- `sendText` envia mensagens de controle JSON (ex.: `start`/`stop`).
- `sendBinary` envia frames binários (os bytes do áudio PCM).

Tecnologia usada: `OkHttp 4.12` para WebSocket; é estável e comum no Android.

---

### `VoiceStreamer.kt`: orquestra o streaming de áudio
```kotlin
class VoiceStreamer(
    private val ws: VoiceWsClient,
    private val ioScope: CoroutineScope
) {
    private var recorder: AudioRecord? = null
    private var streamingJob: Job? = null
    private var onLevel: ((Float) -> Unit)? = null

    fun setOnLevelListener(listener: ((Float) -> Unit)?) { onLevel = listener }

    private fun computeLevel(bytes: ByteArray, length: Int): Float { /* ... RMS 0..1 ... */ }

    fun startStreaming(language: String = "pt-BR") { /* ... abre WS, inicia AudioRecord, envia binário e chama onLevel ... */ }
    fun stopStreaming() { /* ... encerra tudo ... */ }
}
```
Fluxo:
1) `ws.connect(...)`: abre a conexão WS e registra callbacks.
2) `onOpen`: envia JSON `start`, inicia captura com `AudioRecord` e começa o loop de leitura.
3) A cada frame, envia binário com `sendBinary(...)` e calcula o nível RMS chamando `onLevel?.invoke(...)` para a UI.
4) `stopStreaming()`: encerra captura, envia `{"type":"stop"}` e fecha o WS.

---

### `LiveStreamingViewModel.kt`: estado, emulação e balões
Adições principais:
- `levels: StateFlow<List<Float>>`: histórico curto (até 60) de níveis 0..1 para a visualização.
- `Balloon`: dados do balão (mensagem + tipo `Success`/`Error`), expostos via `balloon: StateFlow<Balloon?>`.
- Emulação (`emulate = true`): quando ativo, o `start()` não conecta ao servidor; em vez disso cria um loop que emite níveis simulados (onda senoidal com ruído) ~30 FPS e atualiza mensagens "bytes enviados".
- Em modo real (`emulate = false`), integra com `VoiceStreamer` e mostra balões em `onOpen`/`onFailure`/`onClosed`.

---

### `LiveStreamingScreen.kt`: UI com ondas e balões
- Mostra título, status, última mensagem do servidor.
- `Waveform(...)`: desenha uma "parede" de barras verticais cujo tamanho varia com o nível. Usa `Row` com `Box` para simplicidade.
- Balão superior: `TopBalloon(...)` aparece com slide‑in do topo (verde para sucesso, vermelho para erro) e some após ~2,5s.

Controles:
- "Start streaming" chama `vm.start("pt-BR")`.
- "Stop" chama `vm.stop()`.
- Botão "Voltar" (quando fornecido) navega de volta.

---

### Como o fluxo inteiro funciona, passo a passo
1) Usuário toca “Start streaming”.
2) Se emulação ativa: aparece balão verde e a visualização de ondas começa a animar; mensagens de bytes são atualizadas periodicamente.
3) Se modo real: abre WebSocket, envia `start`, começa captura de áudio e mostrará níveis reais do microfone.
4) “Stop” encerra a emulação (ou a captura real) e o status muda para "Stopped".

---

### Pontos de atenção e sugestões
- Permissões: `RECORD_AUDIO` e `INTERNET` no `AndroidManifest.xml` (modo real).
- Token/URL: substitua a URL `wss://example.com/adk/voice` e implemente `authTokenProvider` quando for usar servidor real.
- Tamanho de frame: decidir entre 640 (20 ms) ou 1280 (40 ms) bytes.
- Duplicidade de `connect`: em produção, centralize a abertura de WS em um único lugar.
- Acessibilidade/UI: cores dos balões e contraste podem ser ajustados no tema.

---

### TL;DR
- Agora a tela de streaming mostra ondas reativas e balões de feedback.
- Sem backend? Deixe `emulate = true` no ViewModel e teste a UI com ondas animadas.
- Com backend? Desative a emulação, configure URL/token e teste com áudio real.

### Organização do pacote de Streaming (atualizada)

Para facilitar a manutenção (Clean Code) e respeitar SOLID, o pacote `features.streaming` foi reorganizado em subpacotes por responsabilidade:

- presentation
  - LiveStreamingViewModel.kt (ponto de entrada da tela)
- domain
  - AudioCapture.kt (config e criação do AudioRecord)
  - LiveStreamingController.kt (contrato do caso de uso)
  - DefaultLiveStreamingController.kt (orquestrador da feature)
  - LiveStreamingControllerFactory.kt / DefaultLiveStreamingControllerFactory.kt (fábricas do controller)
  - VoiceSocketFactory.kt / DefaultVoiceSocketFactory.kt (fábricas de sockets)
  - VoiceStreamerFactory.kt / DefaultVoiceStreamerFactory.kt (fábricas do streamer)
  - TokenProvider.kt / LambdaTokenProvider.kt (autenticação)
  - socket
    - VoiceSocket.kt (abstração de WebSocket no domínio)
- data
  - websocket
    - KtorVoiceWsClient.kt (implementação real baseada em Ktor WebSockets)
    - VoiceWsClient.kt (implementação baseada em OkHttp WebSocket)
    - [migrado para pacote fake] FakeVoiceWsClient.kt (implementação fake para emulação/local)
  - voicestreamer
    - VoiceStreamer.kt (captura áudio e envia frames para o socket)
    - io
      - PcmWavWriter.kt (utilitário para gravar WAV opcionalmente durante o streaming)
- ui
  - ... componentes Compose e estados (StreamingState, UiState, Balloon etc.)

Por que assim?
- SRP (Single Responsibility Principle): cada arquivo/pacote tem uma responsabilidade clara (ex.: `data.websocket` lida apenas com WebSocket, `data.voicestreamer` apenas com captura/envio de áudio). 
- DIP (Dependency Inversion Principle): o domínio depende de abstrações (`VoiceSocket`) e de fábricas, não das implementações concretas (Ktor/OkHttp). Implementações ficam no `data` e são injetadas via factories.
- Open/Closed: para adicionar um novo cliente WS (ex.: Jetty), crie outro arquivo em `data.websocket` sem mudar o domínio.

---

### Organograma (fluxo de chamadas)

1) LiveStreamingViewModel (presentation)
   - É o ponto central onde o streaming começa para a UI. Ele recebe um `LiveStreamingControllerFactory` e cria um `LiveStreamingController` ligado ao `viewModelScope`.
   - O ViewModel expõe `state: StateFlow<StreamingState>` (imutável) para a UI.
   - Métodos públicos do ViewModel delegam diretamente para o controller: `start/stop/cancel/save`, `setWebSocketUrl`, `setEmulationMode`, `setTokenProvider`, e ações de UI como `preview`, `dismissPlayerOverlay`, etc.

2) LiveStreamingControllerFactory (domain)
   - controllerFactory.create(scope) → cria o orquestrador `DefaultLiveStreamingController` com as dependências certas (fábricas de socket e streamer).

3) DefaultLiveStreamingController (domain)
   - Responsável por coordenar todo o ciclo de vida do streaming e manter o `StreamingState` único para a UI.
   - Ao configurar:
     - setEmulationMode(enabled) → recria o socket via `VoiceSocketFactory` (fake ou real) e recria o `VoiceStreamer` via `VoiceStreamerFactory`.
     - setWebSocketUrl(url) → idem, atualiza a configuração de conexão.
     - setTokenProvider(provider) → envolve o provider em `LambdaTokenProvider` e recria o socket.
   - Ao iniciar:
     - start(language, outputDir) → define estado para Streaming/Connecting, cria arquivo temporário para gravação WAV opcional e chama `wsClient.connect(...)` com callbacks.
     - onOpen → atualiza estado, mostra balloon de sucesso, inicia `VoiceStreamer.startRecordingTo(file)` e `VoiceStreamer.startStreaming(language)`.
     - onMessage → atualiza `lastServerMessage` no estado.
     - onFailure/onClosed → ajusta estado (Stopped/Idle), mostra balloon de erro quando apropriado.
   - Ao parar/cancelar/salvar → encerra streamer, fecha socket e gerencia arquivo temporário.

4) VoiceSocketFactory (domain)
   - create(emulate, url, tokenProvider) → devolve um `VoiceSocket` (abstração de socket no domínio):
     - FakeVoiceWsClient (features.streaming.fake) disponível para emulação/local, mas atualmente não está conectado pelo factory (DefaultVoiceSocketFactory sempre retorna o cliente Ktor).
     - KtorVoiceWsClient (data.websocket) para produção (usa HttpClient + plugin WebSockets). Alternativamente, `VoiceWsClient` (OkHttp) pode ser usado conforme necessidade.

5) VoiceStreamerFactory (domain)
   - create(socket, scope) → devolve um `VoiceStreamer` (data.voicestreamer) já conectado ao `VoiceSocket` e ao escopo IO.

6) VoiceStreamer (data.voicestreamer)
   - Ao iniciar: envia JSON "start" com metadados (encoding LINEAR16, sampleRate 16000, channels 1, language), cria `AudioRecord` via `domain.AudioCapture`, e entra num loop de leitura de frames PCM.
   - Para cada frame: calcula nível RMS (0..1) para UI e envia binário via `VoiceSocket.sendBinary(bytes)`; opcionalmente persiste no WAV via `PcmWavWriter`.
   - Ao parar: cancela job, para/libera `AudioRecord`, envia `{"type":"stop"}` e fecha o socket.

7) data.websocket.* (implementações do socket)
   - KtorVoiceWsClient: usa `HttpClient.webSocket` e integra callbacks (onOpen/onMessage/onBinary/onClosed/onFailure). Adiciona header Authorization com Bearer token de `TokenProvider`.
   - VoiceWsClient (OkHttp): alternativa com OkHttp WebSocket.
   - FakeVoiceWsClient (features.streaming.fake): simula heartbeat e aceita binários, útil para desenvolvimento sem backend. Atualmente permanece no código sem ser utilizado pelo factory.

Resumo do fluxo: ViewModel → ControllerFactory → Controller → (VoiceSocketFactory → VoiceSocket) + (VoiceStreamerFactory → VoiceStreamer) → WebSocket abre → VoiceStreamer envia start → captura áudio e envia binário → UI atualiza com nível/status → stop/cancel fecham tudo.

---

### Notas de Clean Code/SOLID aplicadas
- Abstrações no domínio (interfaces e factories) e implementações no data.
- Uma única fonte de verdade para o estado da UI (`StreamingState`).
- Classes pequenas com nomes claros e comentários direcionados para orientar novos contribuidores.
- Pontos de extensão: novas implementações de WebSocket ou formatos de áudio podem ser adicionadas em `data` sem tocar no `domain`.

---

As seções abaixo mantêm a documentação original detalhada (com trechos de código) para referência técnica. Elas foram preservadas e complementadas pela organização acima.



---

### Documentação das classes (complementar)

As seções abaixo descrevem, de forma breve, as classes que fazem parte dos pacotes `domain` e `data` e que ainda não estavam explicadas anteriormente.

- domain/socket/VoiceSocket.kt
  - O que é: Abstração de WebSocket no domínio. Padroniza operações de `connect`, `sendText`, `sendBinary` e `close` com callbacks para eventos.
  - Por que existe: Permite que o domínio dependa de uma interface estável enquanto as implementações reais (Ktor/OkHttp/Fake) ficam no `data`.

- domain/LiveStreamingController.kt
  - O que é: Contrato do caso de uso principal da feature (streaming ao vivo). Expõe `state: StateFlow<StreamingState>` e métodos `start/stop/cancel/save`, além de utilitários de UI.
  - Por que existe: Isola a lógica de orquestração para que a UI (ViewModel/Compose) apenas observe estado e despache intenções.

- domain/LiveStreamingControllerFactory.kt e domain/DefaultLiveStreamingControllerFactory.kt
  - O que são: Fábricas para criar o `LiveStreamingController` já configurado com as dependências necessárias (factories de socket e streamer) e o `CoroutineScope` do chamador.
  - Por que existem: Facilita injeção de dependências e testes, evitando acoplamento da UI ao construtor concreto.

- domain/DefaultLiveStreamingController.kt
  - O que é: Implementação padrão do `LiveStreamingController`. Coordena ciclo de vida do WebSocket, `VoiceStreamer`, arquivo temporário WAV e atualiza o `StreamingState`.
  - Destaques: Recria socket/streamer ao mudar `emulate`, `url` ou `tokenProvider`. Em `start`, conecta o socket, inicia captura e envia mensagens `start/stop` conforme necessário.

- domain/VoiceSocketFactory.kt e domain/DefaultVoiceSocketFactory.kt
  - O que são: Fábrica abstrata e sua implementação padrão para criar um `VoiceSocket`.
  - Implementação atual: `DefaultVoiceSocketFactory` está configurada para SEMPRE retornar `KtorVoiceWsClient` (ignora `emulate` por enquanto). O `FakeVoiceWsClient` permanece no pacote `features.streaming.fake`, mas não é usado automaticamente.

- domain/VoiceStreamerFactory.kt e domain/DefaultVoiceStreamerFactory.kt
  - O que são: Fábrica abstrata e implementação para criar `VoiceStreamer` associado a um `VoiceSocket` e a um `CoroutineScope`.
  - Por que existem: Mantêm o domínio desacoplado da construção do componente de captura/streaming.

- domain/TokenProvider.kt e domain/LambdaTokenProvider.kt
  - O que são: Abstração para fornecimento de token de autenticação e um adaptador simples baseado em lambda para uso prático.
  - Por que existem: Evitam passar Strings cruas e permitem estratégias assíncronas de obtenção de token.

- data/websocket/KtorVoiceWsClient.kt
  - O que é: Implementação real de `VoiceSocket` usando Ktor WebSockets. Abre sessão, envia/recebe `Frame.Text` e `Frame.Binary`, e propaga eventos via callbacks.
  - Quando usar: Produção. Integra bem com `HttpClient` compartilhado e plugin `WebSockets`.

- data/voicestreamer/io/PcmWavWriter.kt
  - O que é: Gravador mínimo de WAV (16-bit PCM mono). Escreve cabeçalho RIFF/WAVE e amostras em little‑endian.
  - Quando é utilizado: Opcionalmente pelo `VoiceStreamer` para salvar um arquivo temporário durante a sessão, permitindo `save/cancel` no fluxo.

- features/streaming/fake/FakeVoiceWsClient.kt
  - O que é: Implementação fake de `VoiceSocket` para desenvolvimento/local. Simula abertura, envia "heartbeats" e aceita binários para teste de UI/fluxo.
  - Situação atual: Está disponível mas não é instanciada por `DefaultVoiceSocketFactory`. Útil para testes manuais caso conectado explicitamente.
