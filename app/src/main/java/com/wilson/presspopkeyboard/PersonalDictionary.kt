package com.wilson.presspopkeyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

private const val LEARN_AT = 77
// Dismissing a suggested correction ("keep my word") counts as this many types.
private const val KEPT_WEIGHT = 10
private const val FLUSH_DELAY_MS = 3000L
private val LOCK = Any()

/**
 * The "personal keyboard": words the user actually types.
 *
 * - Manual words: added in the app's My Words screen, active at once.
 * - Learned words: an unknown word typed at least 77 times in real
 *   use is promoted to a personal word, so it stops being
 *   autocorrected away. A word typed fewer than 77 times is NOT
 *   learned, so a one-off typo does not stick. (Code 36, Wilson,
 *   Oct 30, 2026: not just twice — at least seven times. Code 44,
 *   Wilson, Nov 9, 2026: seven was still too little — raised to
 *   77 times.)
 *
 * Stored only on the device, in SharedPreferences. Nothing is sent
 * anywhere. A personal word is never autocorrected, and it is offered
 * first in suggestions and swipe matches.
 *
 * Typing-quality patch (performance only, no behaviour change):
 * the keyboard asks for the personal word list on every keystroke,
 * and the old code re-read and re-split every stored string each
 * time — including every learned next-word pair, which Wilson
 * deliberately left uncapped — and rewrote the whole pair set to
 * disk on every Space. Now the data is parsed ONCE into memory,
 * shared by every PersonalDictionary instance in the process (so
 * the My Words screen and the keyboard always see the same data),
 * and written back to SharedPreferences in the SAME string-set
 * format a few seconds after the last change. Backups, import and
 * export are unchanged. Destructive or explicit actions (add,
 * remove, clear, import) are written immediately; the keyboard
 * calls flush() when a field closes.
 */
class PersonalDictionary(private val context: Context) {
    private val prefs = context.getSharedPreferences("presspop_personal", Context.MODE_PRIVATE)

    private fun state(): State = State.get(prefs)

    /** Write any pending changes to disk now. Safe to call often. */
    fun flush() {
        synchronized(LOCK) { state().flushNow() }
    }

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
    fun manualWords(): Set<String> = synchronized(LOCK) { state().manualSet() }

    fun addManual(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        synchronized(LOCK) {
            val s = state()
            // One entry per word, regardless of case:
            // adding "Angel" replaces a stored "angel"
            // instead of leaving both in the list.
            s.manual.removeAll { it.lowercase() == w.lowercase() }
            s.manual.add(w)
            s.dirtyManual = true
            s.invalidate()
            s.flushNow()
        }
    }

    fun removeManual(word: String) {
        synchronized(LOCK) {
            val s = state()
            s.manual.removeAll { it.lowercase() == word.lowercase() }
            s.dirtyManual = true
            s.invalidate()
            s.flushNow()
        }
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
        synchronized(LOCK) {
            val s = state()
            s.manual.removeAll { it.lowercase() == w }
            s.counts.remove(w)
            s.learned.remove(w)
            s.caps.remove(w)
            s.dirtyManual = true
            s.dirtyCounts = true
            s.dirtyCaps = true
            s.invalidate()
            s.flushNow()
        }
    }

    /** Words learned from typing (count >= 77, code 36; raised to 77 in code 44). */
    fun learnedWords(): Set<String> = synchronized(LOCK) { state().learned.toSet() }

    fun allPersonalWords(): Set<String> = synchronized(LOCK) { state().allSet() }

    // Code 39 (Wilson, Nov 3, 2026): bulk delete for My Words.
    // Removes every manual word AND every learned count, so a
    // learned word is gone just like a manual one — and so is
    // progress toward learning (a word typed fewer than 77
    // times), otherwise a word at 76 types would quietly
    // become personal again on its next type after a bulk
    // delete. The 77-type rule itself is unchanged: after a
    // bulk delete, a word needs 77 fresh types to be
    // learned again. Wilson then said yes for all sections,
    // so learned next-word pairs ("bigrams") are cleared
    // here too — otherwise a deleted word could still
    // appear as a next-word suggestion. Built-in
    // dictionary, clipboard, and settings are untouched.
    // Callers must confirm first: this cannot be undone
    // except by importing a backup.
    fun clearAllPersonal() {
        synchronized(LOCK) {
            val s = state()
            s.manual.clear()
            s.counts.clear()
            s.learned.clear()
            s.caps.clear()
            s.bigrams.clear()
            s.dirtyManual = true
            s.dirtyCounts = true
            s.dirtyBigrams = true
            s.dirtyCaps = true
            s.invalidate()
            s.flushNow()
        }
    }

    /**
     * Record that [word] was committed. Returns true if it just got learned.
     * [capitalizedMidSentence] is true when the word was typed with a
     * capital first letter somewhere other than a sentence start, which is
     * evidence it is a name and should be shown capitalized once learned.
     */
    fun noteTyped(word: String, capitalizedMidSentence: Boolean = false): Boolean {
        if (isIncognito()) return false
        val w = word.lowercase()
        if (w.length < 3 || !w.all { it.isLetter() }) return false
        // Code 45 (Wilson, Nov 9, 2026): only learn words
        // the keyboard does NOT already know. The old code
        // counted every word typed, including ordinary
        // dictionary words, so "the", "and", "you" all
        // became "learned" words after 7 types and filled
        // the My Words screen with words that were never
        // unknown. A dictionary word needs no learning —
        // it is already suggested, swiped, and never
        // autocorrected — so it is never counted here.
        if (Dictionary.isWord(w)) return false
        synchronized(LOCK) {
            val s = state()
            if (s.manual.any { it.lowercase() == w }) return false
            val n = (s.counts[w] ?: 0) + 1
            s.counts[w] = n
            s.dirtyCounts = true
            if (capitalizedMidSentence) {
                s.caps[w] = (s.caps[w] ?: 0) + 1
                s.dirtyCaps = true
                if (w in s.learned) s.invalidate()   // its display casing may change
            }
            if (n >= LEARN_AT && s.learned.add(w)) s.invalidate()
            s.scheduleFlush()
            return n == LEARN_AT
        }
    }

    /**
     * The user tapped the word as typed to dismiss a suggested
     * correction: an explicit "this is my word" signal, counted as
     * KEPT_WEIGHT types on top of the normal count from when it was
     * committed. Same eligibility as [noteTyped] (not incognito, not a
     * dictionary or manual word, letters only). Returns true if this
     * just pushed the word over the learning threshold. The 77-type
     * rule itself is unchanged: this only makes a deliberate "keep"
     * worth more than a single type.
     */
    fun noteKept(word: String, capitalizedMidSentence: Boolean = false): Boolean {
        if (isIncognito()) return false
        val w = word.lowercase()
        if (w.length < 3 || !w.all { it.isLetter() }) return false
        if (Dictionary.isWord(w)) return false
        synchronized(LOCK) {
            val s = state()
            if (s.manual.any { it.lowercase() == w }) return false
            val before = s.counts[w] ?: 0
            val n = before + KEPT_WEIGHT
            s.counts[w] = n
            s.dirtyCounts = true
            // MAVERICK FIX: a kept word counts KEPT_WEIGHT toward
            // learning, so its capital evidence counts the same
            // weight, or the caps/total ratio could never reach a
            // name learned mostly through keeps.
            if (capitalizedMidSentence) {
                s.caps[w] = (s.caps[w] ?: 0) + KEPT_WEIGHT
                s.dirtyCaps = true
                if (w in s.learned) s.invalidate()   // its display casing may change
            }
            if (n >= LEARN_AT && s.learned.add(w)) s.invalidate()
            s.scheduleFlush()
            return before < LEARN_AT && n >= LEARN_AT
        }
    }

    fun isPersonal(word: String): Boolean =
        synchronized(LOCK) { word.lowercase() in state().allLower() }

    // Code 45 (Wilson, Nov 9, 2026): one-time cleanup
    // for the words noteTyped wrongly learned before
    // the dictionary check above — "the", "and", "you",
    // "that", and the rest of the common words that
    // filled the My Words screen. Removes the stored
    // counts for words already in the built-in
    // dictionary, once (guarded by a flag, so it never
    // re-runs). Manual words are untouched, and real
    // learned words the keyboard does not know ("thr",
    // names) stay learned. Returns how many entries
    // were removed.
    fun purgeDictionaryWordsOnce(): Int {
        synchronized(LOCK) {
            if (prefs.getBoolean("dict_purge_done", false)) return 0
            val s = state()
            val before = s.counts.size
            val iter = s.counts.keys.iterator()
            while (iter.hasNext()) {
                val k = iter.next()
                if (Dictionary.isWord(k)) {
                    iter.remove()
                    s.learned.remove(k)
                }
            }
            s.dirtyCounts = true
            s.invalidate()
            s.flushNow()
            prefs.edit().putBoolean("dict_purge_done", true).apply()
            return before - s.counts.size
        }
    }

    // Next-word pairs the user actually types, code 20,
    // uncapped in code 22.
    // Stored on-device only, as "prev:next:count". A pair is
    // learned from the first time it is committed (count 1),
    // and ordered by count, so the user's own sentence habits
    // win over the generic BIGRAMS table in Dictionary.
    // No cap, code 22 (Wilson: "Why cap it? Please
    // undo that."): each pair is tiny, so the only limit
    // is device storage itself, not PressPop.
    fun notePair(prev: String, next: String) {
        if (isIncognito()) return
        val a = prev.lowercase()
        val b = next.lowercase()
        if (a.isEmpty() || b.isEmpty()) return
        if (!a.all { it.isLetter() } || !b.all { it.isLetter() }) return
        synchronized(LOCK) {
            val s = state()
            val m = s.bigrams.getOrPut(a) { HashMap() }
            m[b] = (m[b] ?: 0) + 1
            s.dirtyBigrams = true
            s.scheduleFlush()
        }
    }

    // Code 37 (Wilson, Oct 30, 2026): back up and
    // export. A backup is everything the personal
    // keyboard has learned, in one JSON file the
    // user can keep anywhere (Drive, Files, email):
    // manual My Words (casing as added), learned
    // counts (so progress toward the 77-type
    // threshold survives a reinstall), and learned
    // word pairs. Nothing else is in the file — no
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
        synchronized(LOCK) {
            val s = state()
            val manual = org.json.JSONArray()
            s.manual.sorted().forEach { manual.put(it) }
            json.put("manual", manual)
            val counts = org.json.JSONArray()
            s.counts.map { "${it.key}:${it.value}" }.sorted().forEach { counts.put(it) }
            json.put("counts", counts)
            val capsArr = org.json.JSONArray()
            s.caps.map { "${it.key}:${it.value}" }.sorted().forEach { capsArr.put(it) }
            json.put("caps", capsArr)
            val bigrams = org.json.JSONArray()
            val pairs = ArrayList<String>()
            for ((a, m) in s.bigrams) for ((b, c) in m) pairs.add("$a:$b:$c")
            pairs.sorted().forEach { bigrams.put(it) }
            json.put("bigrams", bigrams)
        }
        return json.toString(2)
    }

    /** Merge a backup produced by [exportJson]. Returns changed entries, or throws on bad input. */
    fun importJson(text: String): Int {
        val json = org.json.JSONObject(text)
        if (json.optString("format") != "safepop-backup" || json.optInt("version", -1) != 1) {
            throw IllegalArgumentException("Not a SafePop backup file")
        }
        val countRe = Regex("[a-z]+:[0-9]+")
        val pairRe = Regex("[a-z]+:[a-z]+:[0-9]+")
        var changed = 0
        synchronized(LOCK) {
            val s = state()
            val manualArray = json.optJSONArray("manual")
            if (manualArray != null) {
                for (i in 0 until manualArray.length()) {
                    val w = manualArray.optString(i).trim()
                    if (w.isEmpty()) continue
                    if (!s.manual.any { it.lowercase() == w.lowercase() }) changed++
                    s.manual.removeAll { it.lowercase() == w.lowercase() }
                    s.manual.add(w)
                }
            }
            val countsArray = json.optJSONArray("counts")
            if (countsArray != null) {
                for (i in 0 until countsArray.length()) {
                    // Code 45: an old backup can carry the
                    // dictionary-word counts noteTyped used to
                    // store ("the:50" and the like) — those are
                    // junk from the old bug, not learned words,
                    // so they are skipped on import instead of
                    // being merged back in and re-learned at 77
                    // types. Bigrams below are NOT filtered:
                    // pairs of dictionary words are legitimate
                    // sentence habits.
                    val entry = countsArray.optString(i)
                    val w = entry.substringBefore(":")
                    if (Dictionary.isWord(w)) continue
                    if (!countRe.matches(entry)) continue
                    val c = entry.substringAfter(":").toIntOrNull() ?: continue
                    if (c <= (s.counts[w] ?: 0)) continue
                    s.counts[w] = c
                    if (c >= LEARN_AT) s.learned.add(w)
                    changed++
                }
            }
            val capsArray = json.optJSONArray("caps")
            if (capsArray != null) {
                for (i in 0 until capsArray.length()) {
                    val entry = capsArray.optString(i)
                    if (!countRe.matches(entry)) continue
                    val w = entry.substringBefore(":")
                    val c = entry.substringAfter(":").toIntOrNull() ?: continue
                    if (c <= (s.caps[w] ?: 0)) continue
                    s.caps[w] = c
                    changed++
                }
            }
            val bigramsArray = json.optJSONArray("bigrams")
            if (bigramsArray != null) {
                for (i in 0 until bigramsArray.length()) {
                    val entry = bigramsArray.optString(i)
                    if (!pairRe.matches(entry)) continue
                    val parts = entry.split(":")
                    val c = parts[2].toIntOrNull() ?: continue
                    val m = s.bigrams.getOrPut(parts[0]) { HashMap() }
                    if (c <= (m[parts[1]] ?: 0)) continue
                    m[parts[1]] = c
                    changed++
                }
            }
            s.dirtyManual = true
            s.dirtyCounts = true
            s.dirtyBigrams = true
            s.dirtyCaps = true
            s.invalidate()
            s.flushNow()
        }
        return changed
    }

    /** Most-used next words after [prev], most-used first. */
    fun learnedNextWords(prev: String): List<String> {
        val a = prev.lowercase()
        synchronized(LOCK) {
            val m = state().bigrams[a] ?: return emptyList()
            return m.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenBy { it.key }
                )
                .map { it.key }
        }
    }

    /**
     * Parsed, in-memory copy of everything stored in SharedPreferences,
     * shared by every PersonalDictionary in the process. All access is
     * under LOCK. Loaded once, from the same string-set format as before.
     */
    private class State(private val prefs: SharedPreferences) {
        val manual = LinkedHashSet<String>()
        val counts = HashMap<String, Int>()                      // lowercase word -> times typed
        val bigrams = HashMap<String, HashMap<String, Int>>()    // prev -> (next -> count)
        val learned = HashSet<String>()                          // counts >= LEARN_AT
        val caps = HashMap<String, Int>()                        // times typed capitalized MID-sentence

        var dirtyManual = false
        var dirtyCounts = false
        var dirtyBigrams = false
        var dirtyCaps = false

        private var manualCache: Set<String>? = null
        private var allCache: Set<String>? = null
        private var allLowerCache: Set<String>? = null

        private val handler = Handler(Looper.getMainLooper())
        private val flushTask = Runnable { synchronized(LOCK) { flushNow() } }

        init {
            prefs.getStringSet("manual", null)?.let { manual.addAll(it) }
            prefs.getStringSet("counts", null)?.forEach { e ->
                val parts = e.split(":")
                if (parts.size != 2) return@forEach
                val c = parts[1].toIntOrNull() ?: return@forEach
                if (c > (counts[parts[0]] ?: 0)) counts[parts[0]] = c
            }
            for ((w, c) in counts) if (c >= LEARN_AT) learned.add(w)
            prefs.getStringSet("caps", null)?.forEach { e ->
                val parts = e.split(":")
                if (parts.size != 2) return@forEach
                val c = parts[1].toIntOrNull() ?: return@forEach
                if (c > (caps[parts[0]] ?: 0)) caps[parts[0]] = c
            }
            prefs.getStringSet("bigrams", null)?.forEach { e ->
                val parts = e.split(":")
                if (parts.size != 3) return@forEach
                val c = parts[2].toIntOrNull() ?: return@forEach
                val m = bigrams.getOrPut(parts[0]) { HashMap() }
                if (c > (m[parts[1]] ?: 0)) m[parts[1]] = c
            }
        }

        // Each cache is rebuilt as a NEW set after a change, so a
        // reference handed out earlier (the keyboard view keeps one)
        // never changes underneath its holder.
        fun manualSet(): Set<String> =
            manualCache ?: LinkedHashSet(manual).also { manualCache = it }

        /**
         * A learned word is stored lowercase; show it capitalized when
         * the user has mostly typed it with a capital in the MIDDLE of a
         * sentence (a name like "Guinard"), at least 3 times. A capital
         * at a sentence start is just auto-shift and never counts.
         */
        fun displayCase(w: String): String {
            val c = caps[w] ?: 0
            val total = counts[w] ?: 0
            return if (c >= 3 && c * 2 >= total) w.replaceFirstChar { it.uppercase() } else w
        }

        fun allSet(): Set<String> =
            allCache ?: LinkedHashSet<String>(manual).apply { for (w in learned) add(displayCase(w)) }
                .also { allCache = it }

        fun allLower(): Set<String> =
            allLowerCache ?: allSet().mapTo(HashSet()) { it.lowercase() }
                .also { allLowerCache = it }

        fun invalidate() {
            manualCache = null
            allCache = null
            allLowerCache = null
        }

        fun scheduleFlush() {
            handler.removeCallbacks(flushTask)
            handler.postDelayed(flushTask, FLUSH_DELAY_MS)
        }

        fun flushNow() {
            handler.removeCallbacks(flushTask)
            if (!(dirtyManual || dirtyCounts || dirtyBigrams || dirtyCaps)) return
            val e = prefs.edit()
            if (dirtyManual) {
                if (manual.isEmpty()) e.remove("manual") else e.putStringSet("manual", HashSet(manual))
                dirtyManual = false
            }
            if (dirtyCounts) {
                if (counts.isEmpty()) e.remove("counts")
                else e.putStringSet("counts", counts.mapTo(HashSet()) { "${it.key}:${it.value}" })
                dirtyCounts = false
            }
            if (dirtyCaps) {
                if (caps.isEmpty()) e.remove("caps")
                else e.putStringSet("caps", caps.mapTo(HashSet()) { "${it.key}:${it.value}" })
                dirtyCaps = false
            }
            if (dirtyBigrams) {
                if (bigrams.isEmpty()) {
                    e.remove("bigrams")
                } else {
                    val out = HashSet<String>()
                    for ((a, m) in bigrams) for ((b, c) in m) out.add("$a:$b:$c")
                    e.putStringSet("bigrams", out)
                }
                dirtyBigrams = false
            }
            e.apply()
        }

        companion object {
            private var cached: State? = null

            /** Call only while holding LOCK. */
            fun get(prefs: SharedPreferences): State =
                cached ?: State(prefs).also { cached = it }
        }
    }
}
