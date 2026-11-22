package com.wilson.presspopkeyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * Keyboard color themes, code 51 (Wilson, Nov 20, 2026: "Can you
 * add different color choices for my keyboard? ... at least 10
 * options. I don't like the current gray on gray. Kinda hard to
 * tell where the buttons ends and starts.").
 *
 * The old dark keyboard drew #2A3546 -> #1F2937 keys on a #1F2937
 * background: the bottom of every key was the SAME color as the
 * background, so key edges disappeared. Every palette here was
 * checked for contrast before it went in:
 * - key face vs background at least 1.7:1 (the old dark theme was
 *   1.0 to 1.19), so each key reads as its own shape;
 * - letter text vs key face at least 7:1;
 * - action-key icons (shift, delete, enter) at least 3:1, the
 *   large-glyph level;
 * - the corner flick hint at least 3:1.
 * The face is still a light-top to darker-bottom gradient over a
 * darker 3dp edge (the Manna 3D key from Nov 2).
 *
 * "Classic" is the original look, made higher-contrast, and it is
 * the only theme that follows the Light / Dark / System setting.
 * Every other theme is fixed, and is also what the suggestion strip,
 * key popups, accent picker, clipboard and emoji panels use.
 * Stored as "keyboard_theme" in presspop_settings; "classic" if unset.
 */
data class KeyboardPalette(
    val id: String,
    val label: String,
    val isDark: Boolean,
    val background: Int,
    val keyTop: Int,
    val keyBottom: Int,
    val actionTop: Int,
    val actionBottom: Int,
    val pressedTop: Int,
    val pressedBottom: Int,
    val edge: Int,
    val text: Int,
    val hint: Int,
    val accentTop: Int,
    val accentBottom: Int,
    val accentText: Int,
    val strip: Int,
    val panel: Int,
    val popup: Int,
    /** A 1dp outline around every key, or null for none (High Contrast only). */
    val outline: Int?
)

object KeyboardTheme {
    private fun c(hex: String): Int = Color.parseColor(hex)

    const val PREF = "keyboard_theme"
    const val CLASSIC = "classic"

    val ALL: List<KeyboardPalette> = listOf(
        KeyboardPalette(
            id = "classic_light", label = "Classic Light", isDark = false,
            background = c("#A3ACB9"),
            keyTop = c("#FFFFFF"), keyBottom = c("#EEF0F3"),
            actionTop = c("#7E8999"), actionBottom = c("#717C8D"),
            pressedTop = c("#DDE1E6"), pressedBottom = c("#CDD2D9"),
            edge = c("#4B5563"), text = c("#111111"), hint = c("#5B6472"),
            accentTop = c("#60A5FA"), accentBottom = c("#3B82F6"), accentText = c("#FFFFFF"),
            strip = c("#FFFFFF"), panel = c("#F3F4F6"), popup = c("#FFFFFF"),
            outline = null
        ),
        KeyboardPalette(
            id = "classic_dark", label = "Classic Dark", isDark = true,
            background = c("#0A0E14"),
            keyTop = c("#435268"), keyBottom = c("#3C4A5E"),
            actionTop = c("#566376"), actionBottom = c("#4A5668"),
            pressedTop = c("#263040"), pressedBottom = c("#1E2733"),
            edge = c("#030508"), text = c("#FFFFFF"), hint = c("#A3ADBB"),
            accentTop = c("#60A5FA"), accentBottom = c("#3B82F6"), accentText = c("#FFFFFF"),
            strip = c("#111827"), panel = c("#1F2937"), popup = c("#374151"),
            outline = null
        ),
        KeyboardPalette(
            id = "mocha", label = "Mocha", isDark = true,
            background = c("#1C140F"),
            keyTop = c("#5A4E46"), keyBottom = c("#524740"),
            actionTop = c("#6E4F39"), actionBottom = c("#614532"),
            pressedTop = c("#362D27"), pressedBottom = c("#2E2620"),
            edge = c("#0F0B08"), text = c("#F5EEE8"), hint = c("#BFAE9F"),
            accentTop = c("#E0A872"), accentBottom = c("#C98B55"), accentText = c("#241B15"),
            strip = c("#2C221B"), panel = c("#2C221B"), popup = c("#5C5048"),
            outline = null
        ),
        KeyboardPalette(
            id = "midnight", label = "Midnight", isDark = true,
            background = c("#060A16"),
            keyTop = c("#31446F"), keyBottom = c("#2C3E66"),
            actionTop = c("#3A5592"), actionBottom = c("#31487E"),
            pressedTop = c("#18223A"), pressedBottom = c("#131B2F"),
            edge = c("#02050C"), text = c("#EEF2FF"), hint = c("#9EADCF"),
            accentTop = c("#60A5FA"), accentBottom = c("#3B82F6"), accentText = c("#FFFFFF"),
            strip = c("#0F172A"), panel = c("#111C33"), popup = c("#2C3D63"),
            outline = null
        ),
        KeyboardPalette(
            id = "forest", label = "Forest", isDark = true,
            background = c("#07120B"),
            keyTop = c("#2F5A43"), keyBottom = c("#2A513C"),
            actionTop = c("#33744C"), actionBottom = c("#2B6441"),
            pressedTop = c("#183024"), pressedBottom = c("#13271D"),
            edge = c("#030805"), text = c("#ECFDF3"), hint = c("#9FCAB1"),
            accentTop = c("#4ADE80"), accentBottom = c("#22C55E"), accentText = c("#052E16"),
            strip = c("#0F2218"), panel = c("#0F2218"), popup = c("#2E553D"),
            outline = null
        ),
        KeyboardPalette(
            id = "ocean", label = "Ocean", isDark = true,
            background = c("#031D1B"),
            keyTop = c("#165650"), keyBottom = c("#124A45"),
            actionTop = c("#0F8378"), actionBottom = c("#0C7067"),
            pressedTop = c("#0D3A36"), pressedBottom = c("#0A2F2C"),
            edge = c("#010A09"), text = c("#ECFEFB"), hint = c("#97D6CE"),
            accentTop = c("#2DD4BF"), accentBottom = c("#14B8A6"), accentText = c("#042F2E"),
            strip = c("#052926"), panel = c("#052926"), popup = c("#1C6660"),
            outline = null
        ),
        KeyboardPalette(
            id = "plum", label = "Plum", isDark = true,
            background = c("#0E0617"),
            keyTop = c("#58357C"), keyBottom = c("#513173"),
            actionTop = c("#64308C"), actionBottom = c("#57297A"),
            pressedTop = c("#2A173A"), pressedBottom = c("#22122F"),
            edge = c("#06020A"), text = c("#FAF5FF"), hint = c("#CBAAE6"),
            accentTop = c("#C084FC"), accentBottom = c("#A855F7"), accentText = c("#FFFFFF"),
            strip = c("#1D0E2A"), panel = c("#1D0E2A"), popup = c("#4E2C6B"),
            outline = null
        ),
        KeyboardPalette(
            id = "crimson", label = "Crimson", isDark = true,
            background = c("#100506"),
            keyTop = c("#6A3243"), keyBottom = c("#622D3E"),
            actionTop = c("#82263A"), actionBottom = c("#722133"),
            pressedTop = c("#33171E"), pressedBottom = c("#2A1218"),
            edge = c("#070203"), text = c("#FFF1F2"), hint = c("#E6A9B2"),
            accentTop = c("#FB7185"), accentBottom = c("#F43F5E"), accentText = c("#FFFFFF"),
            strip = c("#200D11"), panel = c("#200D11"), popup = c("#5A2A35"),
            outline = null
        ),
        KeyboardPalette(
            id = "ember", label = "Ember", isDark = true,
            background = c("#09090B"),
            keyTop = c("#4A4A52"), keyBottom = c("#43434A"),
            actionTop = c("#C2410C"), actionBottom = c("#A9380A"),
            pressedTop = c("#28282C"), pressedBottom = c("#202024"),
            edge = c("#000000"), text = c("#FAFAFA"), hint = c("#A8A8B3"),
            accentTop = c("#FB923C"), accentBottom = c("#F97316"), accentText = c("#1C0A00"),
            strip = c("#18181B"), panel = c("#18181B"), popup = c("#4A4A52"),
            outline = null
        ),
        KeyboardPalette(
            id = "contrast", label = "High Contrast", isDark = true,
            background = c("#000000"),
            keyTop = c("#3D3D3D"), keyBottom = c("#383838"),
            actionTop = c("#474747"), actionBottom = c("#3C3C3C"),
            pressedTop = c("#141414"), pressedBottom = c("#0C0C0C"),
            edge = c("#000000"), text = c("#FFFFFF"), hint = c("#FDE047"),
            accentTop = c("#FACC15"), accentBottom = c("#EAB308"), accentText = c("#000000"),
            strip = c("#000000"), panel = c("#0A0A0A"), popup = c("#333333"),
            outline = c("#FFFFFF")
        ),
        KeyboardPalette(
            id = "sky", label = "Sky", isDark = false,
            background = c("#8FB5E2"),
            keyTop = c("#FFFFFF"), keyBottom = c("#F1F6FC"),
            actionTop = c("#5F8FCB"), actionBottom = c("#5283C2"),
            pressedTop = c("#DCE8F5"), pressedBottom = c("#CCDDEF"),
            edge = c("#56779F"), text = c("#0B1B33"), hint = c("#46617F"),
            accentTop = c("#3B82F6"), accentBottom = c("#2563EB"), accentText = c("#FFFFFF"),
            strip = c("#EAF2FB"), panel = c("#EAF2FB"), popup = c("#FFFFFF"),
            outline = null
        ),
        KeyboardPalette(
            id = "mint", label = "Mint", isDark = false,
            background = c("#8CCBAF"),
            keyTop = c("#FFFFFF"), keyBottom = c("#F0F9F4"),
            actionTop = c("#55AE86"), actionBottom = c("#4AA27A"),
            pressedTop = c("#DDF1E7"), pressedBottom = c("#CDE9DB"),
            edge = c("#4A8A6D"), text = c("#0D2B20"), hint = c("#3C6A56"),
            accentTop = c("#10B981"), accentBottom = c("#059669"), accentText = c("#FFFFFF"),
            strip = c("#EAF7F0"), panel = c("#EAF7F0"), popup = c("#FFFFFF"),
            outline = null
        ),
        KeyboardPalette(
            id = "sand", label = "Sand", isDark = false,
            background = c("#C2AB82"),
            keyTop = c("#FFFCF5"), keyBottom = c("#F7F0E2"),
            actionTop = c("#A68657"), actionBottom = c("#99794B"),
            pressedTop = c("#EFE5D1"), pressedBottom = c("#E5D8BF"),
            edge = c("#86704C"), text = c("#2E2416"), hint = c("#6B583C"),
            accentTop = c("#D97706"), accentBottom = c("#B45309"), accentText = c("#FFFFFF"),
            strip = c("#F6EFE2"), panel = c("#F6EFE2"), popup = c("#FFFCF5"),
            outline = null
        ),
        KeyboardPalette(
            id = "rose", label = "Rose", isDark = false,
            background = c("#DB9CAE"),
            keyTop = c("#FFFFFF"), keyBottom = c("#FCF1F4"),
            actionTop = c("#C26F87"), actionBottom = c("#B8637C"),
            pressedTop = c("#F6DDE4"), pressedBottom = c("#EFCCD6"),
            edge = c("#9E5A6E"), text = c("#3A1520"), hint = c("#874759"),
            accentTop = c("#F43F5E"), accentBottom = c("#E11D48"), accentText = c("#FFFFFF"),
            strip = c("#FBEFF2"), panel = c("#FBEFF2"), popup = c("#FFFFFF"),
            outline = null
        ),
        KeyboardPalette(
            id = "lavender", label = "Lavender", isDark = false,
            background = c("#B2A4E2"),
            keyTop = c("#FFFFFF"), keyBottom = c("#F5F2FD"),
            actionTop = c("#8B78D3"), actionBottom = c("#7E6ACA"),
            pressedTop = c("#E7E1F8"), pressedBottom = c("#DAD2F3"),
            edge = c("#6B5BAA"), text = c("#1E1538"), hint = c("#584A88"),
            accentTop = c("#8B5CF6"), accentBottom = c("#7C3AED"), accentText = c("#FFFFFF"),
            strip = c("#F3F0FC"), panel = c("#F3F0FC"), popup = c("#FFFFFF"),
            outline = null
        )
    )

    val CLASSIC_LIGHT: KeyboardPalette get() = ALL.first { it.id == "classic_light" }
    val CLASSIC_DARK: KeyboardPalette get() = ALL.first { it.id == "classic_dark" }

    /** What the picker lists: Classic (follows Light/Dark/System) plus every fixed theme. */
    val CHOICES: List<Pair<String, String>> =
        listOf(CLASSIC to "Classic (follows Light / Dark / System)") +
            ALL.filter { !it.id.startsWith("classic_") }.map { it.id to it.label }

    fun currentId(settings: SharedPreferences): String =
        settings.getString(PREF, CLASSIC) ?: CLASSIC

    fun label(id: String): String =
        if (id == CLASSIC) "Classic" else ALL.firstOrNull { it.id == id }?.label ?: "Classic"

    /** The palette the keyboard should draw with right now. */
    fun resolve(context: Context, settings: SharedPreferences): KeyboardPalette {
        val id = currentId(settings)
        ALL.firstOrNull { it.id == id && !id.startsWith("classic_") }?.let { return it }
        return if (ThemeMode.isDark(context, settings)) CLASSIC_DARK else CLASSIC_LIGHT
    }

    /** The palette for a picker preview (Classic previews its dark version). */
    fun previewPalette(id: String): KeyboardPalette =
        ALL.firstOrNull { it.id == id } ?: CLASSIC_DARK
}
