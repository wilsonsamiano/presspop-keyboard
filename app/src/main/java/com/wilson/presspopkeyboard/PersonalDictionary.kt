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

    // Code 34 (Wilson, Oct 30, 2026): "thr" is
    // NOT in the built-in dictionary, yet it was
    // treated as a word — because a word typed
    // twice is learned as a personal word, and
    // the old Remove button only removed MANUAL
    // words, so a learned typo could never be
    // unlearned from the app. This removes both:
    // the manual entry and the learned count
    // entry, so the word stops being personal,
    // stops being suggested / swiped, and can be
    // autocorrected again. Callers should use
    // this, not removeManual, for the Remove button.
    fun removeWord(word: String) {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return
        removeManual(w)
        val counts = (prefs.getStringSet("counts", emptySet()) ?: emptySet()).toMutableSet()
        val old = counts.firstOrNull { it.startsWith("$w:") }
        if (old != null) {
            counts.remove(old)
            prefs.edit().putStringSet("counts", counts).apply()
        }
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

    // Next-word pairs the user actually types, code 20,
    // uncapped in code 22.
    // Stored on-device only, as "prev:next:count". A pair is
    // learned from the first time it is committed (count 1),
    // and ordered by count, so the user's own sentence habits
    // win over the generic BIGRAMS table in Dictionary.
    fun notePair(prev: String, next: String) {
        val a = prev.lowercase()
        val b = next.lowercase()
        if (a.isEmpty() || b.isEmpty()) return
        if (!a.all { it.isLetter() } || !b.all { it.isLetter() }) return
        val entries = (prefs.getStringSet("bigrams", emptySet()) ?: emptySet()).toMutableSet()
        val old = entries.firstOrNull { it.startsWith("$a:$b:") }
        val oldCount = old?.substringAfterLast(":")?.toIntOrNull() ?: 0
        if (old != null) entries.remove(old)
        entries.add("$a:$b:${oldCount + 1}")
        // No cap, code 22 (Wilson: "Why cap it? Please
        // undo that."): the old code dropped the least-used
        // pairs once 500 were stored, which threw away his
        // own learned sentence habits for no device reason.
        // Each pair is only about 20 bytes on-device, so
        // even 10,000 learned pairs is a few hundred KB —
        // nothing on a tablet with gigabytes free. The only
        // limit now is device storage itself, not PressPop.
        prefs.edit().putStringSet("bigrams", entries).apply()
    }

    /** Most-used next words after [prev], most-used first. */
    fun learnedNextWords(prev: String): List<String> {
        val a = prev.lowercase()
        val entries = prefs.getStringSet("bigrams", emptySet()) ?: emptySet()
        return entries.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 3 && parts[0] == a) {
                parts[1] to (parts[2].toIntOrNull() ?: 0)
            } else null
        }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .map { it.first }
    }
}
