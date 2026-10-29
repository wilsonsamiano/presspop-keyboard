package com.wilson.presspopkeyboard

import android.content.Context

/**
 * Clipboard history, on-device only.
 *
 * Android does not give an app the full system clipboard history,
 * so PressPop builds its own: while the keyboard service is running,
 * every new text clip is remembered, newest first, up to 20 items
 * of up to 500 characters each. Nothing leaves the device.
 *
 * Honest limit: copied text can include sensitive things (a code,
 * a password pasted from a manager). History is therefore capped,
 * and the panel in the keyboard has a one-tap Clear. If you copy
 * sensitive text, clear it after pasting.
 */
class ClipboardHistory(context: Context) {
    private val prefs = context.getSharedPreferences("presspop_clipboard", Context.MODE_PRIVATE)

    fun all(): List<String> {
        val raw = prefs.getString("items", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split('\u0001').filter { it.isNotEmpty() }
    }

    fun add(text: String) {
        val clean = text.trim().take(500)
        if (clean.isEmpty()) return
        val items = all().toMutableList()
        if (items.firstOrNull() == clean) return
        items.remove(clean)
        items.add(0, clean)
        prefs.edit().putString("items", items.take(20).joinToString("\u0001")).apply()
    }

    fun clear() {
        prefs.edit().remove("items").apply()
    }
}
