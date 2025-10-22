package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.domain

/**
 * Arquivo de coisas removidas: histórico de implementações anteriores acopladas à UI.
 *
 * Este arquivo serve apenas como referência. O código abaixo NÃO é utilizado
 * em tempo de execução e permanece comentado para não introduzir dependências
 * de UI na camada de domínio.
 *
 * Itens arquivados:
 * - save() antigo (salvar gravação temporária e exibir balões/overlay)
 * - refreshSaved() antigo (varredura de arquivos salvos)
 * - deleteFile() antigo (exclusão com balões)
 * - preview() antigo (exibir overlay do player)
 * - consumeBalloon()/dismissPlayerOverlay() antigos
 * - pushLevel()/showBalloon() antigos (níveis do waveform e balões)
 */

/*
// Imports originais:
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.Balloon
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.BalloonType
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.UiState

// Trechos anteriores da DefaultLiveStreamingController:

override fun save() {
    _state.value = _state.value.copy(uiState = UiState.Saving)
    try {
        streamer.stopRecording()
        _state.value = _state.value.copy(savedFile = tempFile, uiState = UiState.Saved)
        showBalloon("Saved recording", BalloonType.Success)
        // Show player overlay
        _state.value = _state.value.copy(showPlayerOverlay = tempFile)
        // Add to saved items list
        tempFile?.let { f ->
            if (f.exists()) {
                val current = _state.value.savedItems.toMutableList()
                // avoid duplicates
                if (current.none { it.absolutePath == f.absolutePath }) {
                    current.add(0, f)
                    _state.value = _state.value.copy(
                        savedItems = current.sortedByDescending { it.lastModified() }
                    )
                }
            }
        }
    } catch (t: Throwable) {
        showBalloon("Save failed: ${'$'}{t.message}", BalloonType.Error)
        _state.value = _state.value.copy(uiState = UiState.Stopped)
    }
}

private fun pushLevel(level: Float) {
    val list = _state.value.levels.toMutableList()
    list += level
    val max = 60
    if (list.size > max) repeat(list.size - max) { list.removeAt(0) }
    _state.value = _state.value.copy(levels = list)
}

private fun showBalloon(message: String, type: BalloonType) {
    _state.value = _state.value.copy(balloon = Balloon(message, type))
}

override fun consumeBalloon() { _state.value = _state.value.copy(balloon = null) }

override fun dismissPlayerOverlay() { _state.value = _state.value.copy(showPlayerOverlay = null) }

override fun refreshSaved(vararg dirs: File) {
    val found = mutableListOf<File>()
    dirs.forEach { dir ->
        dir.listFiles()?.forEach { f ->
            val name = f.name.lowercase()
            if (f.isFile && (name.endsWith(".wav") || name.endsWith(".m4a"))) {
                found.add(f)
            }
        }
    }
    _state.value = _state.value.copy(
        savedItems = found.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
    )
}

override fun deleteFile(file: File) {
    val ok = try { file.delete() } catch (_: Throwable) { false }
    if (ok) {
        _state.value = _state.value.copy(
            savedItems = _state.value.savedItems.filterNot { it.absolutePath == file.absolutePath }
        )
        showBalloon("Deleted", BalloonType.Success)
    } else {
        showBalloon("Failed to delete", BalloonType.Error)
    }
}

override fun preview(file: File) {
    _state.value = _state.value.copy(showPlayerOverlay = file)
}

fun startRecordingTo(file: File) {
        if (wavWriter != null) return
        val writer = PcmWavWriter(AudioCaptureConfig.SAMPLE_RATE, 1)
        writer.open(file)
        wavWriter = writer
    }

    fun stopRecording(): File? {
        return try {
            wavWriter?.close()
            null
        } finally {
            wavWriter = null
        }
    }

    fun setOnLevelListener(listener: ((Float) -> Unit)?) {
        onLevel = listener
    }
*/
