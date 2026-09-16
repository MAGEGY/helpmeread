package com.helpmeread.app.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Voice command recognizer for hands-free operation.
 * Listens for commands: "read", "stop", "next", "back", "save", "camera".
 * Designed for accessibility users who cannot see or tap buttons.
 */
class VoiceCommandManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldListen = false

    private val _command = MutableStateFlow<VoiceCommand?>(null)
    val command: StateFlow<VoiceCommand?> = _command

    private val _isAvailable = MutableStateFlow(SpeechRecognizer.isRecognitionAvailable(context))
    val isAvailable: StateFlow<Boolean> = _isAvailable

    fun startListening() {
        if (!_isAvailable.value || isListening) return

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        isListening = false
                        Log.w("VoiceCommand", "Recognition error: $error")
                        // Don't restart on permanent errors (missing permission, busy, client)
                        if (shouldListen &&
                            error != SpeechRecognizer.ERROR_CLIENT &&
                            error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY &&
                            error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        ) {
                            restartListening()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].lowercase().trim()
                            _command.value = parseCommand(text)
                        }
                        // Continue listening for next command
                        if (shouldListen) restartListening()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            shouldListen = true
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e("VoiceCommand", "Failed to start listening", e)
            isListening = false
        }
    }

    private fun restartListening() {
        if (isListening || !shouldListen) return
        speechRecognizer?.let {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                it.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e("VoiceCommand", "Failed to restart listening", e)
                isListening = false
            }
        }
    }

    fun stopListening() {
        shouldListen = false
        isListening = false
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        shouldListen = false
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun clearCommand() {
        _command.value = null
    }

    private fun parseCommand(text: String): VoiceCommand {
        return when {
            // English + Arabic + French + Spanish keywords
            text.contains("read") || text.contains("play") || text.contains("start") ||
                text.contains("اقرأ") || text.contains("lis") || text.contains("lire") ||
                text.contains("lee") || text.contains("leer") ->
                VoiceCommand.READ
            text.contains("stop") || text.contains("pause") || text.contains("halt") ||
                text.contains("توقف") || text.contains("قف") || text.contains("arrêt") ||
                text.contains("para") || text.contains("detente") ->
                VoiceCommand.STOP
            text.contains("next") || text.contains("skip") ||
                text.contains("التالي") || text.contains("suivant") || text.contains("siguiente") ->
                VoiceCommand.NEXT
            text.contains("back") || text.contains("previous") || text.contains("return") ||
                text.contains("رجوع") || text.contains("retour") || text.contains("atrás") ->
                VoiceCommand.BACK
            text.contains("save") || text.contains("keep") || text.contains("store") ||
                text.contains("احفظ") || text.contains("enregistre") || text.contains("guarda") ->
                VoiceCommand.SAVE
            text.contains("camera") || text.contains("retake") || text.contains("new") ||
                text.contains("كاميرا") || text.contains("caméra") || text.contains("cámara") ->
                VoiceCommand.CAMERA
            text.contains("history") || text.contains("saved") || text.contains("past") ||
                text.contains("سجل") || text.contains("historique") || text.contains("historial") ->
                VoiceCommand.HISTORY
            else -> VoiceCommand.UNKNOWN
        }
    }
}

enum class VoiceCommand {
    READ, STOP, NEXT, BACK, SAVE, CAMERA, HISTORY, UNKNOWN
}
