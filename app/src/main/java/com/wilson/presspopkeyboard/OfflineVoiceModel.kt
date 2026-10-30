package com.wilson.presspopkeyboard

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Code 37 (Wilson, Oct 30, 2026): offline voice model files.
 *
 * The model is NOT bundled in the app or the repo — it is about
 * 120-150 MB on the device once downloaded, far too big to ship
 * in the APK. It is downloaded once, on the user's tap, to the
 * app's private files directory, and after that voice typing
 * runs entirely on the device: no audio ever leaves the device.
 *
 * Model: sherpa-onnx Whisper tiny, MULTILINGUAL (not tiny.en —
 * tiny.en cannot do Tagalog or Spanish). Individual files are
 * downloaded from the official sherpa-onnx Whisper tiny
 * Hugging Face repo, so no tar.bz2 extraction is needed on
 * the device. If a file name changes upstream, the download
 * fails with that file's name in the error, and only that
 * URL needs updating — no other code changes.
 *
 * Files, in app filesDir/voice-model/:
 *   tiny-encoder.int8.onnx, tiny-decoder.int8.onnx, tiny-tokens.txt
 * Language for the recognizer comes from the keyboard's own
 * language setting (en / tl / es); anything else falls back
 * to English, because a multilingual Whisper model still
 * needs an explicit language to transcribe well.
 */
object OfflineVoiceModel {
    private const val MODEL_DIR_NAME = "voice-model"
    private const val BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"

    val fileNames = listOf(
        "tiny-encoder.int8.onnx",
        "tiny-decoder.int8.onnx",
        "tiny-tokens.txt"
    )

    fun modelDir(context: Context): File =
        File(context.filesDir, MODEL_DIR_NAME)

    fun isDownloaded(context: Context): Boolean {
        val dir = modelDir(context)
        return fileNames.all { name ->
            val f = File(dir, name)
            f.exists() && f.length() > 1024
        }
    }

    fun downloadedBytes(context: Context): Long {
        val dir = modelDir(context)
        return fileNames.sumOf { name -> File(dir, name).length() }
    }

    /** Whisper language code for the keyboard's current language. */
    fun languageCode(context: Context): String {
        val code = context.getSharedPreferences("presspop_settings", Context.MODE_PRIVATE)
            .getString("language_code", "en") ?: "en"
        return when (code) {
            "tl", "es" -> code
            else -> "en"
        }
    }

    /**
     * Download all three files on the calling thread — callers
     * MUST run this on a background thread. Returns null on
     * success, or an error message naming the file that failed.
     * A failed file is deleted, so a later retry re-downloads
     * it instead of keeping a partial file that looks complete.
     */
    fun download(context: Context, onProgress: (String) -> Unit): String? {
        val dir = modelDir(context)
        if (!dir.exists()) dir.mkdirs()
        for (name in fileNames) {
            onProgress("Downloading $name…")
            val out = File(dir, name)
            val tmp = File(dir, "$name.part")
            try {
                val url = URL("$BASE_URL/$name")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                conn.connect()
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    tmp.delete()
                    return "Download failed for $name (server said ${conn.responseCode})."
                }
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tmp.length() <= 1024) {
                    tmp.delete()
                    return "Download failed for $name (file too small — check the model URL)."
                }
                if (!tmp.renameTo(out)) {
                    tmp.delete()
                    return "Could not save $name on this device."
                }
            } catch (e: Exception) {
                tmp.delete()
                return "Download failed for $name: ${e.message ?: "unknown error"}."
            }
        }
        return null
    }
}
