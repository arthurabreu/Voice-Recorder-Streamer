package com.arthurabreu.voicerecorderwebsockettransmitter.features.saveandsend.data

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal WAV writer for 16-bit PCM mono.
 */
class PcmWavWriter(
    private val sampleRate: Int = 16000,
    private val channels: Int = 1,
) {
    private var fos: FileOutputStream? = null
    private var file: File? = null
    private var dataBytes: Long = 0

    fun open(output: File) {
        file = output
        fos = FileOutputStream(output)
        writeHeaderPlaceholder()
    }

    fun write(samples: ShortArray, offset: Int = 0, length: Int = samples.size) {
        val bb = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until length) bb.putShort(samples[offset + i])
        fos?.write(bb.array())
        dataBytes += (length * 2)
    }

    fun write(bytesLE16: ByteArray) {
        fos?.write(bytesLE16)
        dataBytes += bytesLE16.size
    }

    fun close() {
        fos?.flush()
        fos?.fd?.sync()
        fos?.close()
        fos = null
        finalizeHeader()
    }

    private fun writeHeaderPlaceholder() {
        val header = ByteArray(44) { 0 }
        fos?.write(header)
    }

    private fun finalizeHeader() {
        val f = file ?: return
        val totalDataLen = dataBytes
        val totalRiffLen = totalDataLen + 36
        val raf = RandomAccessFile(f, "rw")
        raf.seek(0)
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk
        bb.put("RIFF".toByteArray())
        bb.order(ByteOrder.LITTLE_ENDIAN).putInt(totalRiffLen.toInt())
        bb.put("WAVE".toByteArray())
        // fmt subchunk
        bb.put("fmt ".toByteArray())
        bb.putInt(16) // PCM
        bb.putShort(1) // audio format 1=PCM
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        val byteRate = sampleRate * channels * 2
        bb.putInt(byteRate)
        val blockAlign: Short = (channels * 2).toShort()
        bb.putShort(blockAlign)
        bb.putShort(16) // bits per sample
        // data subchunk
        bb.put("data".toByteArray())
        bb.putInt(totalDataLen.toInt())
        raf.write(bb.array())
        raf.close()
    }
}