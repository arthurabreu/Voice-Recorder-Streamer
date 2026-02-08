package com.arthurabreu.voicerecorderwebsockettransmitter.mvpwss.protocol

/**
 * Mock protocol for testing the generic WebSocket flow.
 */
class MockWsProtocol : WsProtocol<MockData> {

    override fun onOpen(): String = 
        """{"type":"handshake","client":"android_test"}"""

    override fun onMessage(text: String): ProtocolResult<MockData> {
        return when {
            text.contains("handshake_ack") -> {
                ProtocolResult(
                    data = MockData("Handshake Successful"),
                    nextMessages = listOf("""{"type":"start_stream"}"""),
                    readyToStream = true
                )
            }
            text.contains("server_response") -> {
                ProtocolResult(
                    data = MockData("Received server response: $text")
                )
            }
            else -> {
                ProtocolResult(error = "Unknown message format")
            }
        }
    }
}

data class MockData(val message: String)
