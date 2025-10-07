package com.arthurabreu.voicerecorderwebsockettransmitter.features.saveandsend.data

import android.media.MediaRecorder
import java.io.File

class FileRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    companion object {
        private const val AUDIO_SAMPLE_RATE = 16_000
        private const val AUDIO_CHANNELS = 1
        private const val FILE_PREFIX = "gravacao_"
        private const val FILE_EXTENSION = ".m4a"
        private const val OUTPUT_FORMAT = MediaRecorder.OutputFormat.MPEG_4
        private const val AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC
    }

    fun start(outputDir: File): File {
        // Evita sessões concorrentes
        if (recorder != null) {
            stop()
        }

        val file = createOutputFile(outputDir)
        val newRecorder = createConfiguredRecorder(file)

        try {
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            outputFile = file
            return file
        } catch (t: Throwable) {
            // Garante liberação nativa em caso de falha
            runCatching { newRecorder.release() }
            throw t
        }
    }

    fun stop(): File? {
        return try {
            recorder?.stop()
            outputFile
        } catch (_: Throwable) {
            outputFile
        } finally {
            runCatching { recorder?.release() }
            recorder = null
            outputFile = null
        }
    }

    private fun createOutputFile(outputDir: File): File {
        val name = "$FILE_PREFIX${System.currentTimeMillis()}$FILE_EXTENSION"
        return File(outputDir, name)
    }

    private fun createConfiguredRecorder(file: File): MediaRecorder {
        return MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(OUTPUT_FORMAT)
            setAudioEncoder(AUDIO_ENCODER)
            setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            setAudioChannels(AUDIO_CHANNELS)
            setOutputFile(file.absolutePath)
        }
    }
}