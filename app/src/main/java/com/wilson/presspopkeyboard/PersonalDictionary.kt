package com.wilson.presspopkeyboard

import android.content.Context

/**
 * The "personal keyboard": words the user actually types.
 *
 * - Manual words: added in the app's My Words screen, active at once.
 * - Learned words: an unknown word typed at least seven times in real
 *   use is promoted to a personal word, so it stops being
 *   autocorrected away. A word typed fewer than seven times is NOT
 *   learned, so a one-off typo does not stick. (Code 36, Wilson,
 *   Oct 30, 2026: not just twice — at least seven times.)
 *
 * Stored only on the device, in SharedPreferences. Nothing is sent
 * anywhere. A personal word is never autocorrected, and it is offered
 * first in suggestions and swipe matches.
 */
class PersonalDictionary(private val context: Context) {
    private val prefs = context.getSharedPreferences("presspop_personal", Context.MODE_PRIVATE)

    // Code 37 (Wilson, Oct 30, 2026): Incognito /
    // "don't learn this" toggle. Read live from
    // presspop_settings on every learn, so a
    // toggle in the app takes effect the next
    // time the keyboard opens, with no restart
    // wiring to miss. While on, nothing typed
    // is learned — no learned words, no word
    // pairs. Manual My Words are untouched:
    // adding a word by hand is an explicit
    // choice, not learning from typing.
    fun isIncognito(): Boolean =
        context.getSharedPreferences("presspop_settings", Context.MODE_PRIVATE)
            .getBoolean("incognito", false)

    // Code 37 (Wilson, Oct 30, 2026): manual words
    // keep the casing they were added with, so
    // contact names show as "Angel", not "angel",
    // and are suggested / swiped in that form.
    // Matching stays case-insensitive everywhere
    // it matters (isPersonal, noteTyped, and the
    // callers that lowercase before comparing),
    // so a capitalized name still blocks
    // autocorrect of its lowercase typing.
    /** Manually added words, casing as added. */
    fun manualWords(): Set<String> =
        prefs.getStringSet("manual", emptySet())?.toSet() ?: emptySet()

    fun addManual(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        // One entry per word, regardless of case:
        // adding "Angel" replaces a stored "angel"
        // instead of leaving both in the list.
        val set = manualWords().toMutableSet()
        set.removeAll { it.lowercase() == w.lowercase() }
        set.add(w)
        prefs.edit().putStringSet("manual", set).apply()
    }

    fun removeManual(word: String) {
        val set = manualWords().toMutableSet()
        set.removeAll { it.lowercase() == word.lowercase() }
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

    /** Words learned from typing (count >= 7, code 36). */
    fun learnedWords(): Set<String> {
        val counts = prefs.getStringSet("counts", emptySet()) ?: emptySet()
        return counts.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2 && (parts[1].toIntOrNull() ?: 0) >= 7) parts[0] else null
        }.toSet()
    }

    fun allPersonalWords(): Set<String> = manualWords() + learnedWords()

    /** Record that [word] was committed. Returns true if it just got learned. */
    fun noteTyped(word: String): Boolean {
        if (isIncognito()) return false
        val w = word.lowercase()
        if (w.length < 3 || manualWords().any { it.lowercase() == w } || !w.all { it.isLetter() }) return false
        val counts = (prefs.getStringSet("counts", emptySet()) ?: emptySet()).toMutableSet()
        val old = counts.firstOrNull { it.startsWith("$w:") }
        val oldCount = old?.substringAfter(":")?.toIntOrNull() ?: 0
        if (old != null) counts.remove(old)
        counts.add("$w:${oldCount + 1}")
        prefs.edit().putStringSet("counts", counts).apply()
        return oldCount + 1 == 7
    }

    fun isPersonal(word: String): Boolean =
        allPersonalWords().any { it.lowercase() == word.lowercase() }

    // Next-word pairs the user actually types, code 20,
    // uncapped in code 22.
    // Stored on-device only, as "prev:next:count". A pair is
    // learned from the first time it is committed (count 1),
    // and ordered by count, so the user's own sentence habits
    // win over the generic BIGRAMS table in Dictionary.
    fun notePair(prev: String, next: String) {
        if (isIncognito()) return
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

    // Code 37 (Wilson, Oct 30, 2026): back up and
    // export. A backup is everything the personal
    // keyboard has learned, in one JSON file the
    // user can keep anywhere (Drive, Files, email):
    // manual My Words (casing as added), learned
    // counts (so progress toward the 7-type
    // threshold survives a reinstall, not just
    // words already at 7), and learned word
    // pairs. Nothing else is in the file — no
    // typed sentences, no clipboard, no settings,
    // no accounts. Import MERGES, it never wipes:
    // a word or pair already on the device keeps
    // the higher of the two counts, and a manual
    // word keeps the imported casing. That way
    // restoring an old backup over a newer phone
    // cannot delete words learned since the backup.
    // Format version 1, so a future format can be
    // told apart instead of silently misread.
    fun exportJson(): String {
        val json = org.json.JSONObject()
        json.put("format", "safepop-backup")
        json.put("version", 1)
        val manual = org.json.JSONArray()
        manualWords().sorted().forEach { manual.put(it) }
        json.put("manual", manual)
        val counts = org.json.JSONArray()
        (prefs.getStringSet("counts", emptySet()) ?: emptySet()).sorted().forEach { counts.put(it) }
        json.put("counts", counts)
        val bigrams = org.json.JSONArray()
        (prefs.getStringSet("bigrams", emptySet()) ?: emptySet()).sorted().forEach { bigrams.put(it) }
        json.put("bigrams", bigrams)
        return json.toString(2)
    }

    /** Merge a backup produced by [exportJson]. Returns changed entries, or throws on bad input. */
    fun importJson(text: String): Int {
        val json = org.json.JSONObject(text)
        if (json.optString("format") != "safepop-backup" || json.optInt("version", -1) != 1) {
            throw IllegalArgumentException("Not a SafePop backup file")
        }
        var changed = 0
        val manualArray = json.optJSONArray("manual")
        if (manualArray != null) {
            for (i in 0 until manualArray.length()) {
                val w = manualArray.optString(i).trim()
                if (w.isEmpty()) continue
                if (!manualWords().any { it.lowercase() == w.lowercase() }) changed++
                addManual(w)
            }
        }
        fun mergeCounted(key: String, entry: String, pattern: Regex): Boolean {
            if (!pattern.matches(entry)) return false
            val parts = entry.split(":")
            val count = parts.last().toIntOrNull() ?: return false
            val id = parts.dropLast(1).joinToString(":")
            val existing = (prefs.getStringSet(key, emptySet()) ?: emptySet())
            val old = existing.firstOrNull { it.split(":").dropLast(1).joinToString(":") == id }
            val oldCount = old?.substringAfterLast(":")?.toIntOrNull() ?: 0
            if (count <= oldCount) return false
            val updated = existing.toMutableSet()
            if (old != null) updated.remove(old)
            updated.add(entry)
            prefs.edit().putStringSet(key, updated).apply()
            return true
        }
        val countsArray = json.optJSONArray("counts")
        if (countsArray != null) {
            for (i in 0 until countsArray.length()) {
                if (mergeCounted("counts", countsArray.optString(i), Regex("[a-z]+:[0-9]+"))) changed++
            }
        }
        val bigramsArray = json.optJSONArray("bigrams")
        if (bigramsArray != null) {
            for (i in 0 until bigramsArray.length()) {
                if (mergeCounted("bigrams", bigramsArray.optString(i), Regex("[a-z]+:[a-z]+:[0-9]+"))) changed++
            }
        }
        return changed
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
