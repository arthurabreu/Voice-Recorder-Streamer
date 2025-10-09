package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp

@Composable
fun TopBalloon(message: String, color: Color, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(message, color = Color.White)
        }
    }
    // Auto dismiss after a short delay
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(1500)
        onDismiss()
    }
}


@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.runtime.Composable
private fun TopBalloonSuccessPreview() {
    TopBalloon(message = "Salvo com sucesso", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), onDismiss = {})
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.runtime.Composable
private fun TopBalloonErrorPreview() {
    TopBalloon(message = "Falha ao enviar", color = androidx.compose.ui.graphics.Color(0xFFB00020), onDismiss = {})
}
