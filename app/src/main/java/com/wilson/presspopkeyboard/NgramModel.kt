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
    private var loadingStarted = false

    fun ensureLoaded(context: Context) {
        if (map != null || loadingStarted) return
        loadingStarted = true
        Thread {
            try {
                val loaded = HashMap<String, List<String>>()
                context.assets.open("ngram_model.txt").use { stream ->
                    BufferedReader(InputStreamReader(stream)).useLines { lines ->
                        for (line in lines) {
                            val bar = line.indexOf('|')
                            if (bar <= 0) continue
                            val key = line.substring(0, bar)
                            val next = line.substring(bar + 1)
                                .split(",")
                                .filter { it.isNotEmpty() }
                            if (next.isNotEmpty()) loaded[key] = next
                        }
                    }
                }
                map = loaded
            } catch (e: Exception) {
                // Leave map null: callers fall back to trigram / bigram.
                map = null
                loadingStarted = false
            }
        }.start()
    }

    /** Next words for the last words typed, 5-gram first, then 4-gram. */
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
}
