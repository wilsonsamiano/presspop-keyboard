package com.wilson.presspopkeyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build

/**
 * Every color the keyboard draws with, by role, so a theme is one object
 * instead of `if (darkMode) ... else ...` at each spot.
 *
 * - LIGHT and DARK are the keyboard's own colors exactly as they were
 *   (the Manna & Milk 3D keys, Tailwind greys and blue accent).
 * - dynamic() is Material You: Android 12+ colors from the wallpaper.
 * - omarchy() is one of the stock Omarchy themes, mapped to keys the same
 *   way KeyboardPOP (the Omarchy port) maps a theme's colors.toml, so the
 *   phone and the laptop keyboards match.
 */
data class KeyboardPalette(
    val isDark: Boolean,
    /** Behind the keys. */
    val keysBackground: Int,
    /** The suggestion strip. */
    val strip: Int,
    /** Clipboard / GIF panel background, and the cards in it. */
    val panel: Int,
    val surface: Int,
    /** The enlarged key popup and inline autofill chips. */
    val popup: Int,
    val text: Int,
    /** The small corner symbols on letter keys. */
    val hint: Int,
    /** The solid strip under every key (the 3D edge). */
    val edge: Int,
    val keyTop: Int,
    val keyBottom: Int,
    val actionTop: Int,
    val actionBottom: Int,
    val pressedTop: Int,
    val pressedBottom: Int,
    /** Swipe trail, the middle suggestion, the cursor hint. */
    val accent: Int,
    /** Shift-on key face (top), and the selected accent variant. */
    val accentLight: Int,
    /** Shift-on key face while pressed (bottom). */
    val accentDark: Int,
    /** Text on an accent-colored key. */
    val onAccent: Int,
) {
    companion object {
        val LIGHT = KeyboardPalette(
            isDark = false,
            keysBackground = c("#D1D5DB"), strip = Color.WHITE,
            panel = c("#F3F4F6"), surface = Color.WHITE, popup = Color.WHITE,
            text = Color.BLACK, hint = c("#6B7280"), edge = c("#243044"),
            keyTop = c("#FFFFFF"), keyBottom = c("#E7E9EC"),
            actionTop = c("#C2C7CE"), actionBottom = c("#AEB3BA"),
            pressedTop = c("#E5E7EB"), pressedBottom = c("#D1D5DB"),
            accent = c("#3B82F6"), accentLight = c("#60A5FA"), accentDark = c("#1D4ED8"),
            onAccent = Color.WHITE,
        )

        val DARK = KeyboardPalette(
            isDark = true,
            keysBackground = c("#1F2937"), strip = c("#111827"),
            panel = c("#1F2937"), surface = c("#1F2937"), popup = c("#374151"),
            text = Color.WHITE, hint = c("#9CA3AF"), edge = c("#0C121A"),
            keyTop = c("#2A3546"), keyBottom = c("#1F2937"),
            actionTop = c("#4B5563"), actionBottom = c("#374151"),
            pressedTop = c("#232E3D"), pressedBottom = c("#141B26"),
            accent = c("#3B82F6"), accentLight = c("#60A5FA"), accentDark = c("#1D4ED8"),
            onAccent = Color.WHITE,
        )

        /** The palette the settings ask for, resolved for this moment. */
        fun forSettings(context: Context, settings: SharedPreferences): KeyboardPalette {
            val dark = ThemeMode.isDark(context, settings)
            return when (ThemeMode.current(settings)) {
                "dynamic" -> dynamic(context, dark) ?: if (dark) DARK else LIGHT
                "omarchy" -> omarchy(OmarchyThemes.byId(settings.getString("omarchy_theme", null)))
                else -> if (dark) DARK else LIGHT
            }
        }

        /**
         * Material You. Android 12+ derives tonal palettes from the
         * wallpaper; older versions have none, so null (the caller falls
         * back to Light/Dark).
         */
        fun dynamic(context: Context, dark: Boolean): KeyboardPalette? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            fun r(id: Int) = context.getColor(id)
            val n1 = mapOf(
                10 to r(android.R.color.system_neutral1_10), 50 to r(android.R.color.system_neutral1_50),
                800 to r(android.R.color.system_neutral1_800), 900 to r(android.R.color.system_neutral1_900),
                1000 to r(android.R.color.system_neutral1_1000),
            )
            val n2 = mapOf(
                100 to r(android.R.color.system_neutral2_100), 200 to r(android.R.color.system_neutral2_200),
                300 to r(android.R.color.system_neutral2_300), 500 to r(android.R.color.system_neutral2_500),
                600 to r(android.R.color.system_neutral2_600), 700 to r(android.R.color.system_neutral2_700),
                800 to r(android.R.color.system_neutral2_800),
            )
            val a1 = mapOf(
                100 to r(android.R.color.system_accent1_100), 200 to r(android.R.color.system_accent1_200),
                300 to r(android.R.color.system_accent1_300), 400 to r(android.R.color.system_accent1_400),
                500 to r(android.R.color.system_accent1_500), 600 to r(android.R.color.system_accent1_600),
                700 to r(android.R.color.system_accent1_700), 900 to r(android.R.color.system_accent1_900),
            )
            return if (dark) KeyboardPalette(
                isDark = true,
                keysBackground = n1.getValue(900), strip = n1.getValue(900),
                panel = n1.getValue(800), surface = n1.getValue(800), popup = n2.getValue(700),
                text = n1.getValue(50), hint = n2.getValue(300), edge = n1.getValue(1000),
                keyTop = n2.getValue(800), keyBottom = mix(n2.getValue(800), n1.getValue(900), 0.35f),
                actionTop = n2.getValue(700), actionBottom = mix(n2.getValue(700), n1.getValue(900), 0.35f),
                pressedTop = n2.getValue(600), pressedBottom = n2.getValue(700),
                accent = a1.getValue(300), accentLight = a1.getValue(200), accentDark = a1.getValue(400),
                onAccent = a1.getValue(900),
            ) else KeyboardPalette(
                isDark = false,
                keysBackground = n2.getValue(100), strip = n1.getValue(10),
                panel = n1.getValue(50), surface = n1.getValue(10), popup = n1.getValue(10),
                text = n1.getValue(900), hint = n2.getValue(600), edge = n2.getValue(500),
                keyTop = n1.getValue(10), keyBottom = n1.getValue(50),
                actionTop = n2.getValue(200), actionBottom = n2.getValue(300),
                pressedTop = n2.getValue(200), pressedBottom = n2.getValue(300),
                accent = a1.getValue(600), accentLight = a1.getValue(400), accentDark = a1.getValue(700),
                onAccent = Color.WHITE,
            )
        }

        /**
         * An Omarchy theme, mapped like KeyboardPOP's Theme::paletteFor:
         * the keyboard's base a step darker than the theme's background,
         * keys raised a step lighter on a dark theme or near-white cards on
         * a light one, labels in the bright foreground.
         */
        fun omarchy(theme: OmarchyTheme): KeyboardPalette {
            val colors = theme.colors
            fun pick(name: String, fallback: Int) = colors[name]?.let { c(it) } ?: fallback
            val bg = c(colors.getValue("background"))
            val fg = c(colors.getValue("foreground"))
            val light = lightness(bg) > 0.5f
            val lighter = pick("lighter_background", mix(bg, fg, 0.12f))
            val dark = pick("dark_background", if (light) mix(bg, fg, 0.1f) else mix(bg, Color.BLACK, 0.2f))
            val darker = pick("darker_background", if (light) mix(bg, fg, 0.2f) else mix(bg, Color.BLACK, 0.4f))
            val label = pick("bright_foreground", fg)
            val hint = pick("dark_foreground", mix(fg, bg, 0.45f))
            val keyFace = if (light) mix(bg, Color.WHITE, 0.7f) else lighter
            val base = dark
            val accent = pick("accent", c("#3B82F6"))
            return KeyboardPalette(
                isDark = !light,
                keysBackground = base, strip = bg,
                panel = base, surface = bg, popup = mix(keyFace, fg, 0.08f),
                text = label, hint = hint,
                edge = if (light) mix(darker, fg, 0.15f) else mix(darker, Color.BLACK, 0.3f),
                keyTop = keyFace, keyBottom = mix(keyFace, base, 0.25f),
                actionTop = mix(keyFace, base, 0.55f), actionBottom = mix(keyFace, base, 0.7f),
                pressedTop = mix(keyFace, fg, 0.28f), pressedBottom = mix(keyFace, fg, 0.18f),
                accent = accent,
                accentLight = mix(accent, Color.WHITE, 0.2f),
                accentDark = mix(accent, Color.BLACK, 0.2f),
                onAccent = if (luminance(accent) > 0.179f) Color.BLACK else Color.WHITE,
            )
        }

        private fun c(hex: String) = Color.parseColor(hex)

        fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
        )

        /** HSL lightness, 0-1. */
        private fun lightness(color: Int): Float {
            val r = Color.red(color) / 255f
            val g = Color.green(color) / 255f
            val b = Color.blue(color) / 255f
            return (maxOf(r, g, b) + minOf(r, g, b)) / 2f
        }

        /** WCAG relative luminance, 0-1. */
        private fun luminance(color: Int): Float {
            fun linear(v: Float) = if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
            return 0.2126f * linear(Color.red(color) / 255f) +
                0.7152f * linear(Color.green(color) / 255f) +
                0.0722f * linear(Color.blue(color) / 255f)
        }
    }
}
