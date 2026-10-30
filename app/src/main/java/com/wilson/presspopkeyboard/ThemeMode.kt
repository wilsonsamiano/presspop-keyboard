package com.wilson.presspopkeyboard

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate

/**
 * Theme mode, code 30 2nd commit (Wilson, Oct 29, 2026:
 * "add system theme also"): three modes, not two.
 * - "light": always light.
 * - "dark": always dark.
 * - "system": follow the device's own dark mode.
 * Stored as a string, "theme_mode", in
 * "presspop_settings". The old boolean "dark_mode"
 * is still honoured as a fallback for anyone
 * upgrading from a version that only had Light/Dark:
 * dark_mode true means "dark", false means "light".
 * Once "theme_mode" exists, it wins, so an old
 * boolean can never override a System choice.
 * The keyboard and the app screen both resolve
 * through here, so they can never disagree:
 * - Keyboard: isDark() checks the device's current
 *   night mode when the mode is "system". The
 *   keyboard reads this fresh on every new field,
 *   so changing the device's theme and reopening
 *   the keyboard picks it up.
 * - App screen: nightMode() maps straight to
 *   AppCompatDelegate, and FOLLOW_SYSTEM lets
 *   AppCompat follow the device live.
 */
object ThemeMode {
    fun current(settings: SharedPreferences): String {
        val mode = settings.getString("theme_mode", null)
        if (mode == "light" || mode == "dark" || mode == "system") return mode
        return if (settings.getBoolean("dark_mode", false)) "dark" else "light"
    }

    fun next(mode: String): String = when (mode) {
        "light" -> "dark"
        "dark" -> "system"
        else -> "light"
    }

    fun label(mode: String): String = when (mode) {
        "dark" -> "Dark"
        "light" -> "Light"
        else -> "System"
    }

    fun isDark(context: Context, settings: SharedPreferences): Boolean {
        return when (current(settings)) {
            "dark" -> true
            "light" -> false
            else -> {
                // Code 34 (Wilson, Oct 30, 2026):
                // System mode was stuck dark on his
                // tablet even with the device on light
                // mode. The old check read the service
                // context's resources configuration,
                // which AppCompat's earlier Dark
                // delegate setting can leave stuck
                // in night mode for the whole app
                // process, even after the Theme button
                // is switched to System. Ask the
                // system directly instead: UiModeManager
                // reports the device's own night mode,
                // independent of AppCompat. AUTO falls
                // back to the resources check, since
                // AUTO means the system itself decides
                // by time / configuration.
                // Code 37 (Wilson, Oct 30, 2026):
                // System was still stuck dark on a
                // light device with automatic dark
                // at night. That device setting
                // makes UiModeManager report AUTO,
                // not YES or NO, so the code 35
                // fallback read the app's own
                // resources — which AppCompat's
                // earlier Dark setting had left
                // stuck at NIGHT_YES for the whole
                // process. Never fall back to the
                // app's own resources for System
                // mode. Resources.getSystem() is
                // the system's own resources,
                // untouched by AppCompat, so it
                // reports the true current state:
                // light in the daytime, dark at
                // night under Automatic.
                try {
                    val uiModeManager = context.getSystemService(
                        Context.UI_MODE_SERVICE
                    ) as? UiModeManager
                    when (uiModeManager?.nightMode) {
                        UiModeManager.MODE_NIGHT_YES -> true
                        UiModeManager.MODE_NIGHT_NO -> false
                        else -> (Resources.getSystem().configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK) ==
                            Configuration.UI_MODE_NIGHT_YES
                    }
                } catch (e: Exception) {
                    (Resources.getSystem().configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                }
            }
        }
    }

    fun nightMode(mode: String): Int = when (mode) {
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
