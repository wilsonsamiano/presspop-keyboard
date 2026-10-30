package com.wilson.presspopkeyboard

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Application class, code 30 (Wilson, Oct 29, 2026):
 * the settings screen was blinding at night because
 * the app theme was hardcoded Light in the manifest,
 * even when the PressPop theme setting was Dark.
 * The Dark setting already existed and already drove
 * the keyboard — it just never reached this app's
 * own screen. This class applies that same setting
 * to the app before any activity is created, so the
 * settings screen follows the Theme button, not the
 * hardcoded Light theme. The manifest theme is
 * DayNight, so AppCompat can actually switch: with
 * Light hardcoded, no delegate setting could ever
 * make the screen dark.
 */
class PressPopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = getSharedPreferences("presspop_settings", MODE_PRIVATE)
        // Code 30 2nd commit: three modes now, via
        // ThemeMode — Light, Dark, or System (follow
        // the device). Old dark_mode boolean is only a
        // fallback inside ThemeMode.current().
        AppCompatDelegate.setDefaultNightMode(
            ThemeMode.nightMode(ThemeMode.current(settings))
        )
    }
}
