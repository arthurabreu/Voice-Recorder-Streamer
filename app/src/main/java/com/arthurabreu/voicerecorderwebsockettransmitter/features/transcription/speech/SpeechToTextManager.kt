package com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.MainThread
import com.arthurabreu.voicerecorderwebsockettransmitter.features.transcription.speech.domain.SpeechToTextService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Clean, minimal manager around Android SpeechRecognizer API.
 *
 * What this class does (high level):
 * - Owns a [SpeechRecognizer] instance and its [RecognitionListener].
 * - Translates the Android callback-style API into cold, observable [StateFlow]s
 *   so the UI can simply collect state.
 * - Provides start/stop/release functions and a simple strategy for
 *   continuous dictation (auto-restart) until the user stops.
 *
 * Key observable state:
 * - [partialText]: the in-progress hypothesis as the user is speaking.
 * - [finalText]: accumulated confirmed results across turns (we append).
 * - [isListening]: whether the recognizer session is currently active from the UI point of view.
 * - [error]: a human-readable message when an error occurs.
 *
 * Continuous listening strategy:
 * - While [shouldContinue] is true, after each final result or benign errors like
 *   NO_MATCH/SPEECH_TIMEOUT, we invoke [restartListening] to immediately start a new session.
 * - This gives a "continuous dictation" experience, even though the platform API
 *   itself is session-based.
 *
 * Threading note:
 * - We use [MutableStateFlow] which is thread-safe for simple value writes. The recognizer
 *   callbacks are delivered on the main thread by Android; we also annotate entry points
 *   with [@MainThread] for clarity.
 */
class SpeechToTextManager(private val context: Context) : SpeechToTextService {

    private var speechRecognizer: SpeechRecognizer? = null

    // Control flags
    private var shouldContinue = false // when true, we auto-restart sessions to simulate continuous dictation
    private var lastLanguageTag: String = Locale.getDefault().toLanguageTag()

    // Public immutable state (exposed via the SpeechToTextService interface)
    // We keep MutableStateFlow internally and expose read-only StateFlow to callers.
    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText // in-progress hypothesis while user is speaking

    private val _finalText = MutableStateFlow("")
    override val finalText: StateFlow<String> = _finalText // accumulated confirmed phrases

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening // UI uses this to toggle the Start/Stop button

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error // non-null when an error happens

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
                        if (!shouldContinue) return
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _partialText.value = matches.first()
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        if (!shouldContinue) {
                            _isListening.value = false
                            return
                        }
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches.first()
                            // Append to accumulated final text
                            _finalText.value = listOf(_finalText.value, text)
                                .filter { it.isNotBlank() }
                                .joinToString(separator = "\n")
                            _partialText.value = ""
                        }
                        // Keep listening continuously
                        restartListening()
                    }
                    override fun onError(error: Int) {
                        if (!shouldContinue) {
                            _isListening.value = false
                            _error.value = mapError(error)
                            return
                        }
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // Non-fatal for continuous mode: just restart
                                restartListening()
                            }
                            else -> {
                                // Fatal: stop and expose error
                                _isListening.value = false
                                _error.value = mapError(error)
                                shouldContinue = false
                            }
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else {
            _error.value = "Speech recognition is not available on this device."
        }
    }

    // Build the standard recognizer intent used for each session
    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lastLanguageTag) // last language chosen by ViewModel/UI
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // we want partial hypotheses as the user speaks
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3) // allow more candidates
        // Encourage faster endpointing
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 500)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // If sdk < 24
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false) // prefer online for better accuracy
        }
    }

    private fun restartListening() {
        val recognizer = speechRecognizer ?: return
        // Keep the flag and isListening as true and restart the session
        _isListening.value = true
        recognizer.startListening(buildIntent())
    }

    @MainThread
    override fun startListening(languageTag: String) {
        if (_isListening.value) return
        val recognizer = speechRecognizer ?: run {
            _error.value = "Speech recognizer not initialized."
            return
        }
        lastLanguageTag = languageTag
        shouldContinue = true
        _partialText.value = ""
        _error.value = null
        _isListening.value = true
        recognizer.startListening(buildIntent())
    }

    @MainThread
    override fun stopListening() {
        if (!_isListening.value && !shouldContinue) return
        shouldContinue = false
        speechRecognizer?.stopListening()
        _isListening.value = false
        // Do not clear partial/final text; user asked to stop adding further text only
    }

    override fun release() {
        shouldContinue = false
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

    // Simple partial stabilizer: preserve common confirmed prefix and prefer longer hypothesis
    private fun stabilizePartial(prev: String, new: String): String {
        if (prev.isBlank()) return new
        if (new.startsWith(prev)) return new
        if (prev.startsWith(new)) return prev
        val prevTokens = prev.trim().split(" ").filter { it.isNotBlank() }
        val newTokens = new.trim().split(" ").filter { it.isNotBlank() }
        val minSize = minOf(prevTokens.size, newTokens.size)
        var i = 0
        while (i < minSize && prevTokens[i].equals(newTokens[i], ignoreCase = true)) i++
        if (i == 0) return if (new.length >= prev.length) new else prev
        val commonPrefix = prevTokens.take(i).joinToString(" ")
        val tail = newTokens.drop(i).joinToString(" ")
        return (listOf(commonPrefix, tail).filter { it.isNotBlank() }.joinToString(" ")).trim()
    }
}
