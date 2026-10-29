package com.wilson.presspopkeyboard

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Anonymous error reporting for PressPop.
 *
 * What is collected, and ONLY this:
 * - Device: model, manufacturer, brand, Android version, SDK level.
 * - CPU: supported ABIs, plus the /proc/cpuinfo model line if readable.
 * - RAM: total device memory.
 * - App: version name + version code, keyboard language code.
 * - The crash / error itself: exception type, message, and stack trace
 *   (truncated). Stack traces from PressPop's own code contain no
 *   typed text, clipboard contents, or personal words.
 *
 * What is NEVER collected:
 * - Anything the user typed, pasted, or swiped.
 * - Personal words, clipboard history, contacts, accounts, or any
 *   identifier: no Android ID, serial number, email, or phone number.
 * - Location, microphone audio, or GIF search queries.
 *
 * Behaviour: reports are stored locally only, up to 10, newest first.
 * Nothing is sent automatically. Reporting is opt-in (toggle in the
 * app, off by default): when on, crashes are recorded locally; the
 * user taps "Send error reports" in the app to share them via the
 * system share sheet, to whoever they choose. Turning the toggle off
 * stops new reports (existing stored reports stay until cleared).
 */
object ErrorReporter {

    private const val PREFS = "presspop_errors"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_REPORTS = "reports"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun deviceInfo(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val ramMb = if (memInfo.totalMem > 0) memInfo.totalMem / (1024 * 1024) else 0
        val cpuModel = try {
            java.io.File("/proc/cpuinfo").readLines()
                .firstOrNull { it.startsWith("model name") || it.startsWith("Hardware") }
                ?.substringAfter(":")?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
        return buildString {
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Brand: ${Build.BRAND}, Product: ${Build.PRODUCT}\n")
            append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("CPU ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
            if (cpuModel.isNotEmpty()) append("CPU: $cpuModel\n")
            append("RAM: ${ramMb} MB total\n")
            try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                append("App: ${info.versionName} (code ${info.longVersionCode})\n")
            } catch (e: Exception) {
            }
            append("Language: ${Dictionary.languageCode}\n")
        }
    }

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (isEnabled(context)) record(context, throwable)
            } catch (e: Exception) {
                // Reporting must never make a crash worse.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun record(context: Context, throwable: Throwable) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val stack = writer.toString().take(4000)
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = "--- $date ---\n" +
            deviceInfo(context) +
            "Error: ${throwable.javaClass.simpleName}: ${throwable.message}\n" +
            stack
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_REPORTS, "") ?: ""
        val all = listOf(report) +
            (if (existing.isEmpty()) emptyList() else existing.split("\n--- ").filter { it.isNotEmpty() }
                .map { "--- $it" })
        prefs.edit().putString(KEY_REPORTS, all.take(10).joinToString("\n")).apply()
    }

    fun allReports(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REPORTS, "") ?: ""

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_REPORTS).apply()
    }

    fun reportText(context: Context): String {
        val reports = allReports(context)
        return if (reports.isEmpty()) {
            "No PressPop error reports stored.\n\n" + deviceInfo(context)
        } else {
            "PressPop error reports (anonymous — device info + stack traces only, no typed text or identifiers):\n\n" + reports
        }
    }
}
