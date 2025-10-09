# VoiceRecorder WebSocket Transmitter

An Android app built with Kotlin and Jetpack Compose that captures microphone audio (16 kHz, mono, 16‑bit PCM) and streams it to a WebSocket server in real time. It also supports saving the captured audio to WAV and sending audio files over WebSocket.

Recommended main server: https://github.com/arthurabreu/wss_binary_audio_receiver — a reference WebSocket server designed to receive this app’s PCM audio (playback/save ready). See its README for setup and usage.

Inspired by the documentation style of AllThingsAndroid.

---

## Contents
- Features
- Screens
- Architecture & Tech Stack
- Project Structure
- Getting Started
  - Requirements
  - Clone & Open
  - Configure a WebSocket server
  - Run the app (Debug)
- Usage
  - Live streaming (microphone → WebSocket)
  - Send a saved audio file over WebSocket
- Troubleshooting
- Development Notes
- Roadmap / Ideas
- Contributing
- License

---

## Features
- Live capture from microphone using `AudioRecord` (PCM 16 kHz, mono, 16‑bit LE)
- Real‑time streaming via WebSocket using OkHttp
- Optional local WAV recording while streaming (for later playback/inspection)
- Basic in‑app VU level visualization
- Save recordings and preview them
- Pluggable WebSocket endpoint and token provider
- Fake WebSocket client for emulation/testing

---

## Screens

## General Screens

<img width="1080" height="2316" alt="Screenshot_20251004_133037" src="https://github.com/user-attachments/assets/a14e444d-c5f9-47f8-80c2-b2b8d943c23f" />
<img width="1080" height="2316" alt="Screenshot_20251004_132910" src="https://github.com/user-attachments/assets/76c43255-aa50-491c-a6da-610e364ead05" />

> Note: UI is built with Jetpack Compose (Material 3 theme).

---

## Architecture & Tech Stack
- Language: Kotlin
- UI: Jetpack Compose
- Concurrency: Kotlin Coroutines
- Networking: OkHttp WebSocket
- Audio: Android `AudioRecord` (raw PCM)
- DI/Setup: Simple constructors (no heavy DI)

Key components:
- `features/streaming/domain/AudioCapture.kt` — audio params and AudioRecord factory
- `features/streaming/data/VoiceSocket.kt` — WebSocket abstraction
- `features/streaming/data/VoiceWsClient.kt` — OkHttp WebSocket implementation
- `features/streaming/data/FakeVoiceWsClient.kt` — emulated socket for local testing
- `features/streaming/data/VoiceStreamer.kt` — microphone loop, level calc, WS send, optional WAV capture
- `features/streaming/presentation/LiveStreamingViewModel.kt` — coordinates UI state, connects WS, controls streaming
- `features/saveandsend/data/SendFileOverWs.kt` — sends a file over WS (start → binary chunks → stop)

---

## Project Structure
```
app/
  src/
    main/
      java/com/arthurabreu/voicerecorderwebsockettransmitter/
        features/
          streaming/
            data/               # VoiceWsClient, VoiceStreamer, FakeVoiceWsClient, etc.
            domain/             # AudioCaptureConfig, AudioRecord factory
            presentation/       # LiveStreamingViewModel
            ui/                 # LiveStreamingScreen
          saveandsend/
            data/               # FileRecorder, SendFileOverWs
            ui/                 # SaveAndSendScreen
          transcription/        # (optional) Speech-to-text scaffolding
        ui/                     # HomeScreen, app theme
      AndroidManifest.xml
    debug/
      AndroidManifest.xml       # Debug-only: allows cleartext traffic (ws://)
```

---

## Getting Started

### Requirements
- Android Studio (Hedgehog or newer recommended)
- Android SDK 28+ (App targets modern Android; uses network security config defaults)
- Device or emulator with microphone access
- Optional for document generation (docs/agreements_flow):
  - Pandoc installed (for PDF/PPTX/DOCX export)
  - LaTeX (MiKTeX) for high-quality PDF via xelatex (or adjust script to wkhtmltopdf)

### Clone & Open
```
git clone https://github.com/<your-account>/VoiceRecorderWebSocketTransmitter.git
cd VoiceRecorderWebSocketTransmitter
```
Open in Android Studio and let Gradle sync.

### Configure a WebSocket server
You can use any WebSocket server that accepts binary frames. Recommended main server for this app:

- Main server: wss_binary_audio_receiver — https://github.com/arthurabreu/wss_binary_audio_receiver
  - Purpose-built to receive 16 kHz mono 16-bit PCM from this app
  - Provides playback/saving capabilities; follow its README to run (defaults to port 8080)
  - Typical URL from device on same LAN: `ws://<PC_LAN_IP>:8080`

Other options for quick tests:
- Echo server (proof that bytes flow; won’t play audio):
  - wss://echo.websocket.events
  - wss://ws.postman-echo.com/raw
- Local real‑time audio playback using websocat + ffplay (Windows):
  ```
  websocat.exe -s 0.0.0.0:8080 -b | ffplay -f s16le -ar 16000 -ac 1 -i -
  ```
- Node.js server to save as WAV (simplified): see comments in `STREAMING_OVERVIEW.md` or create your own similar to the examples provided in issues.

Important:
- Bind on 0.0.0.0 for LAN access.
- Allow the port through your OS firewall.

### Run the app (Debug)
This project includes a debug‑only manifest that enables cleartext traffic so you can connect to `ws://` endpoints during development.

- Physical device on same Wi‑Fi as PC: `ws://<PC_LAN_IP>:8080`
- Emulator to your host PC: `ws://10.0.2.2:8080`

If you use a public or TLS endpoint, prefer `wss://...`.

---

## Usage

### Live streaming (microphone → WebSocket)
1. Launch the app (Debug build).
2. Go to Live Streaming.
3. Set the WebSocket URL (e.g., `ws://192.168.x.x:8080`).
4. Optionally set a token provider (if your server expects Authorization: Bearer <token>), otherwise leave it blank.
5. Tap Start. On open, the app sends a JSON `{"type":"start"}` then small binary frames (~20ms each, 1280 bytes at 16 kHz).
6. Tap Stop to send `{"type":"stop"}` and close the socket.
7. Optionally Save to keep a WAV of this session.

Behind the scenes, `LiveStreamingViewModel` rebuilds the WS client whenever you change the URL or token, ensuring the new endpoint is used immediately.

### Send a saved audio file over WebSocket
1. Go to Save & Send.
2. Record audio to a file.
3. Provide a WebSocket URL and Start Send. The flow is: connect → `{"type":"start"}` → send file in 8 KB chunks → `{"type":"stop"}` → close.

Audio format: PCM signed 16‑bit little‑endian, 16 kHz, mono.

---

## Docs: Fluxo de Aprovação e Pagamentos de Acordos (PDF/PPTX/DOCX)
Este repositório inclui um artefato documental gerado a partir de um único arquivo-fonte em Markdown.

- Fonte: docs/agreements_flow/fluxo_acordos.md
- Script de geração (Windows/PowerShell): docs/agreements_flow/generate_outputs.ps1
- Saída: docs/agreements_flow/dist/
  - fluxo_acordos.pdf
  - fluxo_acordos.pptx
  - fluxo_acordos.docx

Como gerar:
1) Abra o PowerShell na raiz do projeto
2) Execute:
```
./docs/agreements_flow/generate_outputs.ps1
```
Pré-requisitos: Pandoc no PATH. Para PDF de alta qualidade, instale MiKTeX (ou ajuste o script para outro motor como wkhtmltopdf).

## Troubleshooting
- Error: "CLEARTEXT communication to <ip> not permitted by network security policy"
  - Use the Debug build. The project includes `app/src/debug/AndroidManifest.xml` with `usesCleartextTraffic=true`.
  - Or switch your server to TLS and use `wss://`.
- Can’t connect from device to PC
  - Verify the server binds to `0.0.0.0` or your LAN IP (not just 127.0.0.1).
  - Check Windows/macOS firewall inbound rules for the chosen port.
  - On emulator, use `ws://10.0.2.2:<port>`.
- Audio sounds wrong on the server side
  - Ensure your player/decoder uses the same params: `-f s16le -ar 16000 -ac 1`.
- Not seeing updated URL take effect
  - The ViewModel now recreates the WS client when URL/token changes. Make sure you’re on the latest code and you actually changed the URL in the UI.

---

## Development Notes
- Audio pipeline
  - `AudioRecord` → frames of 1280 bytes (≈20 ms) → OkHttp WebSocket binary frames
  - Optional WAV mirror on device via `PcmWavWriter`
- Message protocol (basic)
  - Text: `{"type":"start"}` before first audio, `{"type":"stop"}` before closing
  - Binary: raw PCM frames
- Emulation
  - `FakeVoiceWsClient` can be used to simulate WS behavior during UI development

---

## Roadmap / Ideas
- Configurable audio sample rate and frame size
- Automatic reconnection/backoff
- Display running bit‑rate and latency metrics
- In‑app playback of last received echo for local loopback testing
- Packaging as a library module for reuse

---

## Contributing
Contributions are welcome! Please open an issue or submit a PR with a clear description of the change and testing notes. For larger changes, discuss in an issue first.

---

## License
This project is licensed under the MIT License. See the LICENSE file if/when available. If none is present, you may treat this as MIT unless the repository owner states otherwise.
