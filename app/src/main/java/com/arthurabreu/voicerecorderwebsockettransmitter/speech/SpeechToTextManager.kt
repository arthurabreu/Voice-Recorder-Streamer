package com.arthurabreu.voicerecorderwebsockettransmitter.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.MainThread
import com.arthurabreu.voicerecorderwebsockettransmitter.speech.domain.SpeechToTextService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Clean, minimal manager around Android SpeechRecognizer API.
 * - Handles setup, start/stop, and lifecycle.
 * - Exposes state via StateFlows.
 */
class SpeechToTextManager(private val context: Context) : SpeechToTextService {

    private var speechRecognizer: SpeechRecognizer? = null

    // Public immutable state
    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText

    private val _finalText = MutableStateFlow("")
    override val finalText: StateFlow<String> = _finalText

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { _error.value = null }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _partialText.value = matches.first()
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _finalText.value = matches.first()
                            _partialText.value = ""
                        }
                        _isListening.value = false
                    }
                    override fun onError(error: Int) {
                        _isListening.value = false
                        _error.value = mapError(error)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else {
            _error.value = "Speech recognition is not available on this device."
        }
    }

    @MainThread
    override fun startListening(languageTag: String) {
        if (_isListening.value) return
        val recognizer = speechRecognizer ?: run {
            _error.value = "Speech recognizer not initialized."
            return
        }
        _partialText.value = ""
        _error.value = null
        _isListening.value = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
        }
        recognizer.startListening(intent)
    }

    @MainThread
    override fun stopListening() {
        if (!_isListening.value) return
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    override fun release() {
        _isListening.value = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun mapError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
        else -> "Unknown error ($code)"
    }
}
