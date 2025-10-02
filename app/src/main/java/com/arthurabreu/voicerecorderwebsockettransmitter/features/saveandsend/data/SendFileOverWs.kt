package com.arthurabreu.voicerecorderwebsockettransmitter.features.saveandsend.data

import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.data.VoiceWsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class SendFileOverWs(
    private val ws: VoiceWsClient,
    private val scope: CoroutineScope
) {
    fun send(file: File) {
        scope.launch(Dispatchers.IO) {
            ws.connect(scope)
            ws.sendText("{" + "\"type\":\"start\"" + "}")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    ws.sendBinary(if (n == buf.size) buf else buf.copyOf(n))
                }
            }
            ws.sendText("{" + "\"type\":\"stop\"" + "}")
            ws.close()
        }
    }
}