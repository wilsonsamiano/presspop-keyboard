package com.wilson.presspopkeyboard

import android.content.Context
import android.view.Gravity
import android.widget.Toast

/**
 * General debug mode, Oct 29, 2026 (Wilson's request, for customer
 * support by email): one off-by-default switch in the app's settings
 * ("Debug mode"), so a customer can turn it on, reproduce the problem,
 * and send Wilson the one-line readout instead of guessing.
 *
 * When off (the default), nothing changes: no debug popups at all,
 * so daily use stays clean. When on, each feature that makes a
 * decision it is hard to see from the outside shows one short Toast
 * with what it actually saw and decided — swipe tracing, autocorrect,
 * suggestion taps (predictive text). Other features can add a line
 * here with one DebugMode.toast() call; they must never log typed
 * text beyond the single word the feature itself just acted on, and
 * must never log clipboard, passwords, or identifiers.
 */
object DebugMode {
    private const val PREFS = "presspop_settings"
    private const val KEY = "debug_mode"
    private const val FILE = "presspop_debug_mode"

    // File-based flag, Oct 29, 2026: the switch lives in the app
    // (MainActivity) but the popups live in the keyboard service
    // (PressPopIME), and Android caches SharedPreferences per
    // process — so a toggle in the app did NOT reach a keyboard
    // service that was already running, and debug "did nothing"
    // even though the app button said On. A plain file is read
    // fresh from disk on every check, so the keyboard sees the
    // toggle immediately, with no restart and no stale cache.
    // SharedPreferences is still written too, as a fallback for
    // anything that reads the old key.
    fun isOn(context: Context): Boolean {
        return try {
            val file = java.io.File(context.filesDir, FILE)
            if (file.exists()) file.readText().trim() == "1" else
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY, false)
        } catch (e: Exception) {
            try {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY, false)
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, on).apply()
        try {
            java.io.File(context.filesDir, FILE)
                .writeText(if (on) "1" else "0")
        } catch (e: Exception) {
            // SharedPreferences fallback above still carries the value.
        }
    }

    fun toast(context: Context, message: String) {
        if (isOn(context)) {
            // Top of screen, Oct 29, 2026: a default Toast sits at
            // the BOTTOM of the screen, and the keyboard window
            // covers that exact spot while typing — so the debug
            // readouts were firing but hidden behind the keyboard
            // (Wilson's video: the on/off confirmation Toast shows
            // fine in settings with no keyboard, but nothing shows
            // while typing in chat). The top of the screen is never
            // covered by the keyboard, so put debug readouts there.
            val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 200)
            toast.show()
        }
    }
}
