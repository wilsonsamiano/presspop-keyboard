package com.wilson.presspopkeyboard

import android.content.Context

/**
 * The "personal keyboard": words the user actually types.
 *
 * - Manual words: added in the app's My Words screen, active at once.
 * - Learned words: an unknown word typed twice in real use is promoted
 *   to a personal word, so it stops being autocorrected away. A word
 *   typed only once is NOT learned, so a one-off typo does not stick.
 *
 * Stored only on the device, in SharedPreferences. Nothing is sent
 * anywhere. A personal word is never autocorrected, and it is offered
 * first in suggestions and swipe matches.
 */
class PersonalDictionary(context: Context) {
    private val prefs = context.getSharedPreferences("presspop_personal", Context.MODE_PRIVATE)

    /** Manually added words. */
    fun manualWords(): Set<String> =
        prefs.getStringSet("manual", emptySet())?.map { it.lowercase() }?.toSet() ?: emptySet()

    fun addManual(word: String) {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return
        val set = manualWords().toMutableSet()
        set.add(w)
        prefs.edit().putStringSet("manual", set).apply()
    }

    fun removeManual(word: String) {
        val set = manualWords().toMutableSet()
        set.remove(word.lowercase())
        prefs.edit().putStringSet("manual", set).apply()
    }

    /** Words learned from typing (count >= 2). */
    fun learnedWords(): Set<String> {
        val counts = prefs.getStringSet("counts", emptySet()) ?: emptySet()
        return counts.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2 && (parts[1].toIntOrNull() ?: 0) >= 2) parts[0] else null
        }.toSet()
    }

    fun allPersonalWords(): Set<String> = manualWords() + learnedWords()

    /** Record that [word] was committed. Returns true if it just got learned. */
    fun noteTyped(word: String): Boolean {
        val w = word.lowercase()
        if (w.length < 3 || w in manualWords() || !w.all { it.isLetter() }) return false
        val counts = (prefs.getStringSet("counts", emptySet()) ?: emptySet()).toMutableSet()
        val old = counts.firstOrNull { it.startsWith("$w:") }
        val oldCount = old?.substringAfter(":")?.toIntOrNull() ?: 0
        if (old != null) counts.remove(old)
        counts.add("$w:${oldCount + 1}")
        prefs.edit().putStringSet("counts", counts).apply()
        return oldCount + 1 == 2
    }

    fun isPersonal(word: String): Boolean = word.lowercase() in allPersonalWords()
}
