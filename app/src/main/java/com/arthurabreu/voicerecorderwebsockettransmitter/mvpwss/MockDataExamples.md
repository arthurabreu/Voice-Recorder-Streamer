### Mock WebSocket Handshake & Flow

#### 1. Client Handshake (Sent by App)
```json
{
  "type": "handshake",
  "client": "android_test",
  "version": "1.0.0"
}
```

#### 2. Server Handshake Ack (Received by App)
```json
{
  "type": "handshake_ack",
  "status": "ok",
  "session_id": "mock_session_123"
}
```

#### 3. Client Start Streaming (Sent by App after Handshake Ack)
```json
{
  "type": "start_stream",
  "codec": "pcm_16bit",
  "sample_rate": 16000
}
```

#### 4. Server Response (Received during Streaming)
```json
{
  "type": "server_response",
  "msg": "Recognized: 'Hello world'",
  "confidence": 0.98
}
```

#### 5. Server Error (Simulated)
```json
{
  "type": "error",
  "code": 500,
  "message": "Internal server error"
}
```
