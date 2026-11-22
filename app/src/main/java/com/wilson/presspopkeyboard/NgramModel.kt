package com.wilson.presspopkeyboard

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Bigger on-device sentence model, code 23 (Wilson:
 * "Start with the bigger on-device model").
 *
 * This is a pruned 4-gram / 5-gram table, NOT a neural
 * model and NOT a cloud model. It was built from
 * WikiText-2 (general English) plus Samsum and
 * EmpatheticDialogues (everyday dialogues), with
 * conversational next words winning over WikiText
 * where both exist, so "going to the" gives beach /
 * gym / cinema, not a Wikipedia article word.
 * Final table, code 23: 131,304 contexts, 3,946,959
 * bytes as app/src/main/assets/ngram_model.txt,
 * one line per context: "w1 w2 w3|next1,next2,next3"
 * for 4-grams, and "w1 w2 w3 w4|..." for 5-grams.
 * Only dictionary words are in it, and only contexts
 * seen at least 4 times in dialogue (3 / 2 times in
 * WikiText) were kept, so rare article-specific
 * phrases were pruned out.
 *
 * Backoff, code 48 (Wilson, Nov 18, 2026: "it doesn't
 * seem to give me recommendations for the next word"):
 * the table only answers when the last 3 or 4 words
 * EXACTLY match one of its contexts, so after one or
 * two words — most of the time while typing — it said
 * nothing, and the strip fell back to a 138-entry
 * bigram table. Now, while loading, the same file is
 * also folded into 2-word and 1-word context tables:
 * every line votes for its next words under its last
 * two words and its last word (first-listed next word
 * 3 votes, second 2, the rest 1), and each context
 * keeps its top 5. That covers about 39,000 two-word
 * and 5,900 one-word contexts from the same data, with
 * no new asset and nothing sent anywhere.
 *
 * Loading runs on a background thread started from
 * the IME, so the first few next-word offers may
 * still come from the trigram / bigram tables until
 * this map is ready. If the asset is missing or
 * unreadable, this simply returns empty and the
 * older tables carry on — the keyboard never breaks
 * because this model failed.
 */
object NgramModel {
    @Volatile
    private var map: Map<String, List<String>>? = null

    @Volatile
    private var back2: Map<String, List<String>>? = null

    @Volatile
    private var back1: Map<String, List<String>>? = null

    @Volatile
    private var loadingStarted = false

    private const val BACKOFF_KEEP = 5

    fun ensureLoaded(context: Context) {
        if (map != null || loadingStarted) return
        loadingStarted = true
        val appContext = context.applicationContext ?: context
        Thread {
            try {
                val loaded = HashMap<String, List<String>>(140_000)
                val votes2 = HashMap<String, HashMap<String, Int>>(50_000)
                val votes1 = HashMap<String, HashMap<String, Int>>(8_000)
                appContext.assets.open("ngram_model.txt").use { stream ->
                    BufferedReader(InputStreamReader(stream)).useLines { lines ->
                        for (line in lines) {
                            val bar = line.indexOf('|')
                            if (bar <= 0) continue
                            val key = line.substring(0, bar)
                            val next = line.substring(bar + 1)
                                .split(",")
                                .filter { it.isNotEmpty() }
                            if (next.isEmpty()) continue
                            loaded[key] = next
                            val words = key.split(' ')
                            if (words.isEmpty()) continue
                            val k1 = words.last()
                            val k2 = if (words.size >= 2) {
                                words[words.size - 2] + " " + k1
                            } else null
                            for ((i, n) in next.withIndex()) {
                                val v = when (i) { 0 -> 3; 1 -> 2; else -> 1 }
                                if (k2 != null) {
                                    val m2 = votes2.getOrPut(k2) { HashMap(4) }
                                    m2[n] = (m2[n] ?: 0) + v
                                }
                                val m1 = votes1.getOrPut(k1) { HashMap(8) }
                                m1[n] = (m1[n] ?: 0) + v
                            }
                        }
                    }
                }
                back2 = topLists(votes2)
                back1 = topLists(votes1)
                map = loaded
            } catch (e: Exception) {
                // Leave the maps null: callers fall back to trigram / bigram.
                map = null
                back2 = null
                back1 = null
                loadingStarted = false
            }
        }.start()
    }

    private fun topLists(votes: HashMap<String, HashMap<String, Int>>): Map<String, List<String>> {
        val out = HashMap<String, List<String>>(votes.size * 2)
        for ((k, m) in votes) {
            out[k] = m.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenBy { it.key }
                )
                .take(BACKOFF_KEEP)
                .map { it.key }
        }
        return out
    }

    /** Next words for the last words typed, 5-gram first, then 4-gram. Exact contexts only. */
    fun nextWords(words: List<String>): List<String> {
        val m = map ?: return emptyList()
        if (words.size >= 4) {
            val key = words.takeLast(4).joinToString(" ")
            m[key]?.let { return it }
        }
        if (words.size >= 3) {
            val key = words.takeLast(3).joinToString(" ")
            m[key]?.let { return it }
        }
        return emptyList()
    }

    /** Backoff from the last TWO words (code 48). Empty if none or not loaded. */
    fun nextWordsFromTwo(words: List<String>): List<String> {
        val m = back2 ?: return emptyList()
        if (words.size < 2) return emptyList()
        return m[words.takeLast(2).joinToString(" ")] ?: emptyList()
    }

    /** Backoff from the last word only (code 48). Empty if none or not loaded. */
    fun nextWordsFromOne(words: List<String>): List<String> {
        val m = back1 ?: return emptyList()
        if (words.isEmpty()) return emptyList()
        return m[words.last()] ?: emptyList()
    }
}
