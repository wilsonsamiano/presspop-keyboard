package com.wilson.presspopkeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Voice typing via Android's SpeechRecognizer (the device's own
 * speech service, usually Google's). No API key, no extra service.
 *
 * Limits, stated plainly: it needs the microphone permission and a
 * speech service on the device, and on most devices it sends audio
 * to that service for recognition — it is NOT fully on-device like
 * the rest of PressPop. If no recognizer is available, start() reports
 * that instead of crashing. Tap the mic again to stop early.
 */
class VoiceInput(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    var listening: Boolean = false
        private set

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!isAvailable()) {
            onError("Voice typing is not available on this device.")
            return
        }
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    listening = false
                    onError("Voice typing stopped (error $error). Try again.")
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotEmpty()) onResult(text) else onError("Did not catch that.")
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            listening = true
            startListening(intent)
        }
    }

    fun stop() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }
}
