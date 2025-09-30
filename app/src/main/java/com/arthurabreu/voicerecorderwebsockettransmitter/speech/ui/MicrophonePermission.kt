package com.arthurabreu.voicerecorderwebsockettransmitter.speech.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Simple, reusable permission helper for RECORD_AUDIO.
 * Keeps Android permission API in the UI layer and out of ViewModel.
 */
@Composable
fun rememberMicrophonePermission(): MicrophonePermissionState {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var onGrantedCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) onGrantedCallback?.invoke()
        onGrantedCallback = null
    }

    return remember(hasPermission) {
        MicrophonePermissionState(
            hasPermission = hasPermission,
            requestPermission = { onGranted ->
                onGrantedCallback = onGranted
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
    }
}

class MicrophonePermissionState(
    val hasPermission: Boolean,
    val requestPermission: (onGranted: () -> Unit) -> Unit
)
