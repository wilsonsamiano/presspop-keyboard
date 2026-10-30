package com.wilson.presspopkeyboard

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/**
 * Code 37 (Wilson, Oct 30, 2026): offline voice typing.
 *
 * Same start()/stop()/listening/onResult/onError shape as
 * VoiceInput.kt, so PressPopIME can swap between the system
 * recognizer and this one — but the audio path is completely
 * different: the microphone is recorded here, and the text is
 * produced on the device by sherpa-onnx Whisper tiny
 * (multilingual, so English / Tagalog / Spanish all work),
 * with capitalization and punctuation from Whisper itself.
 * No audio, and no transcript, ever leaves the device after
 * the one-time model download (see OfflineVoiceModel.kt).
 *
 * Whisper is batch, not streaming: there are no partial
 * results while speaking. Tap the mic to start recording,
 * tap it again to finish — that second tap is what triggers
 * decoding, it is not a cancel. Recording is capped at
 * 30 seconds per tap, because Whisper's encoder window is
 * 30 seconds; tap again for the next sentence.
 *
 * Everything blocking — model load and decode() — runs on
 * a background thread, never the UI thread. Results and
 * errors are posted back to the main thread, matching
 * VoiceInput's callback behaviour.
 *
 * BUILD NOTE (Grok): this file needs the sherpa-onnx AAR in
 * app/libs/ as sherpa-onnx.aar (see app/build.gradle).
 * Without it, the com.k2fsa.sherpa.onnx imports fail.
 */
class OfflineVoice(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val MAX_RECORD_MS = 30_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var listening: Boolean = false
        private set

    @Volatile
    private var stopRequested = false
    @Volatile
    private var recordError: String? = null
    private var recorder: AudioRecord? = null
    private var recordThread: Thread? = null

    fun isAvailable(): Boolean = OfflineVoiceModel.isDownloaded(context)

    fun start() {
        if (!isAvailable()) {
            onError("Offline voice model is not downloaded yet. Download it in the SafePop app first.")
            return
        }
        stopRecordingOnly()
        val samples = mutableListOf<Float>()
        stopRequested = false
        recordError = null
        listening = true
        recordThread = Thread {
            record(samples)
            // Loop exited: second tap, 30-second cap, or error.
            // listening stays true through decode, so a tap
            // during transcription cannot start a second
            // recording that would commit a second result.
            // record() records its own failure message instead
            // of posting, so there is exactly one error Toast.
            val failure = recordError
            if (failure != null) {
                listening = false
                postError(failure)
                return@Thread
            }
            if (samples.isEmpty()) {
                listening = false
                postError("Did not catch that.")
                return@Thread
            }
            decode(samples.toFloatArray())
            listening = false
        }.also { it.start() }
    }

    /**
     * For OfflineVoice, stop() means "finish and transcribe",
     * not "cancel": the recording thread decodes whatever was
     * recorded so far. This is what PressPopIME calls when the
     * mic is tapped a second time.
     */
    fun stop() {
        if (!listening) return
        stopRequested = true
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // Recorder may already be stopped; the record
            // thread still decodes what it captured.
        }
    }

    /** Stop recording without decoding (used when starting over). */
    private fun stopRecordingOnly() {
        stopRequested = true
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
        }
        recorder = null
        recordThread = null
    }

    private fun record(samples: MutableList<Float>) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            recordError = "Voice recording is not available on this device."
            return
        }
        val bufferSize = maxOf(minBuffer, 4096)
        val record: AudioRecord
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            recordError = "Could not start voice recording: ${e.message ?: "unknown error"}."
            return
        }
        recorder = record
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            recordError = "Voice recording is not available on this device."
            return
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            recordError = "Could not start voice recording: ${e.message ?: "unknown error"}."
            return
        }
        val chunk = ShortArray(2048)
        val startMs = SystemClock.elapsedRealtime()
        try {
            while (!stopRequested &&
                SystemClock.elapsedRealtime() - startMs < MAX_RECORD_MS
            ) {
                val read = record.read(chunk, 0, chunk.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        samples.add(chunk[i] / 32768f)
                    }
                } else if (read < 0) {
                    break
                }
            }
        } catch (e: Exception) {
            // Keep whatever was recorded; decode below will
            // report "did not catch that" if it is empty.
        } finally {
            try {
                record.stop()
            } catch (e: Exception) {
            }
            try {
                record.release()
            } catch (e: Exception) {
            }
            recorder = null
        }
    }

    private fun decode(samples: FloatArray) {
        try {
            val dir = OfflineVoiceModel.modelDir(context)
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(dir, "tiny-encoder.int8.onnx").absolutePath,
                        decoder = File(dir, "tiny-decoder.int8.onnx").absolutePath,
                        language = OfflineVoiceModel.languageCode(context),
                        task = "transcribe"
                    ),
                    tokens = File(dir, "tiny-tokens.txt").absolutePath,
                    numThreads = 2,
                    modelType = "whisper"
                )
            )
            val recognizer = OfflineRecognizer(config = config)
            try {
                val stream = recognizer.createStream()
                // GROK — VERIFY THIS CALL BEFORE SHIPPING:
                // The C++ signature is AcceptWaveform(sampling_rate,
                // waveform, n), and Go/Python are sampleRate-first,
                // so this call is written sampleRate-first.
                // The Kotlin wrapper order in OfflineStream.kt
                // inside the AAR was NOT verified from a Kotlin
                // source this session. If this line fails to
                // compile (type mismatch), swap it to
                // stream.acceptWaveform(samples, SAMPLE_RATE)
                // and nothing else — do not change the rest
                // of this file to work around it.
                stream.acceptWaveform(SAMPLE_RATE, samples)
                recognizer.decode(stream)
                val text = recognizer.getResult(stream).text.trim()
                if (text.isNotEmpty()) {
                    postResult(text)
                } else {
                    postError("Did not catch that.")
                }
            } finally {
                recognizer.release()
            }
        } catch (e: Exception) {
            postError("Offline voice failed: ${e.message ?: "unknown error"}.")
        }
    }

    private fun postResult(text: String) {
        mainHandler.post { onResult(text) }
    }

    private fun postError(message: String) {
        mainHandler.post { onError(message) }
    }
}
