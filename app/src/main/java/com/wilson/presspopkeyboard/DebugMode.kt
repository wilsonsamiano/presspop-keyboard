package com.wilson.presspopkeyboard

import android.content.Context
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

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, on).apply()
    }

    fun toast(context: Context, message: String) {
        if (isOn(context)) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
