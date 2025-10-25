package com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.presentation.LiveStreamingViewModel
import com.arthurabreu.voicerecorderwebsockettransmitter.features.streaming.ui.state.UiState
import org.koin.androidx.compose.koinViewModel

/**
 * Assistant call-like screen following the same pattern as LiveStreamingScreen:
 * - Consumes state from LiveStreamingViewModel
 * - Stateless Content composable receives all data/callbacks
 * - Subcomponents: RespondingPill (reuses Waveform) and CenterAvatar (circular AsyncImage)
 */
@Composable
fun AssistantCallScreen(
    onBack: (() -> Unit)? = null,
    avatarUrl: String? = null
) {
    val vm: LiveStreamingViewModel = koinViewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Start streaming as soon as the screen opens so the waveform animates
    LaunchedEffect(Unit) {
        vm.start(language = "pt-BR", outputDir = context.cacheDir)
    }

    val isResponding = state.uiState == UiState.Streaming

    AssistantCallContent(
        title = "Mel",
        isResponding = isResponding,
        levels = state.levels,
        avatarUrl = avatarUrl,
        onBack = onBack,
        onFinish = { vm.stop() }
    )
}

@Composable
fun AssistantCallContent(
    title: String,
    isResponding: Boolean,
    levels: List<Float>,
    avatarUrl: String?,
    onBack: (() -> Unit)? = null,
    onFinish: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFF))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            TopBar(title = title, onBack = onBack)

            Spacer(Modifier.height(12.dp))
            RespondingPill(levels = levels)

            Spacer(Modifier.height(28.dp))
            CenterAvatar(url = avatarUrl, size = 180.dp)

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Fale o que você precisa",
                color = Color(0xFF1576FF),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "e eu encontro a melhor oferta para você.",
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.weight(1f))
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4B3E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Finalizar conversa", color = Color.White)
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAF1FF)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("‹", color = Color(0xFF1576FF))
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(text = title, color = Color(0xFF1576FF), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun RespondingPill(levels: List<Float>) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF1F3F5))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Respondendo", color = Color(0xFF9BA4AE), style = MaterialTheme.typography.labelLarge)
        // Reuse existing Waveform in a compact layout (no background)
        Waveform(
            levels = levels,
            barCount = 12,
            barWidth = 4.dp,
            barGap = 2.dp,
            modifier = Modifier.height(16.dp),
            height = 16.dp,
            barColor = Color(0xFF1576FF)
        )
    }
}

@Composable
private fun CenterAvatar(url: String?, size: Dp) {
    val ring = Brush.sweepGradient(listOf(Color(0xFF9B59FF), Color(0xFF4AC6FF), Color(0xFF9B59FF)))
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size + 24.dp)
                .clip(CircleShape)
                .background(Color(0x331576FF))
        )
        Box(
            modifier = Modifier
                .size(size + 8.dp)
                .clip(CircleShape)
                .border(width = 2.dp, brush = ring, shape = CircleShape)
        )
        AsyncImage(
            model = url ?: "https://via.placeholder.com/256x256.png?text=Mel",
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun AssistantCallPreview() {
    AssistantCallContent(
        title = "Mel",
        isResponding = true,
        levels = List(24) { i -> (kotlin.math.abs(kotlin.math.sin(i / 4f)) * 0.8f).toFloat() },
        avatarUrl = null,
        onFinish = {}
    )
}
