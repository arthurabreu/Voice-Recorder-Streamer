package com.arthurabreu.voicerecorderwebsockettransmitter.features.saveandsend.data

import android.media.MediaRecorder
import java.io.File

class FileRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(outputDir: File): File {
        val file = File(outputDir, "gravacao_${System.currentTimeMillis()}.m4a")
        val r = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = r
        outputFile = file
        return file
    }

    fun stop(): File? {
        return try {
            recorder?.stop()
            outputFile
        } finally {
            recorder?.release(); recorder = null
        }
    }
}