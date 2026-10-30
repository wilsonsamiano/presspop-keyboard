package com.wilson.presspopkeyboard

import kotlin.math.min

/**
 * Small built-in English dictionary. It powers three features:
 * 1. Predictive hit areas - which letters can follow the word so far.
 * 2. Swipe typing - which word a glide path most likely spells.
 * 3. Autocorrect + suggestions - completions and near-miss corrections.
 *
 * It is deliberately small (a few hundred common words) so the APK
 * stays tiny and everything runs on-device, with no network and no
 * data leaving the phone. Personal words (see PersonalDictionary)
 * are merged in front of this list, so the user's own words win.
 */
object Dictionary {

    /** Active language: "en" (default), "tl" (Tagalog), "es" (Spanish),
     *  or "custom" for an imported/downloaded word-list pack.
     *  Set by PressPopIME when the user taps the language button, and by
     *  MainActivity when a pack is installed. All prediction, swipe,
     *  suggestion and autocorrect functions use activeWords, so
     *  switching language switches everything at once. */
    var languageCode: String = "en"

    /** Words for an imported/downloaded custom pack, if any. */
    var customWords: List<String> = emptyList()
    var customLanguageName: String = ""

    val activeWords: List<String>
        get() = when (languageCode) {
            "tl" -> TAGALOG_WORDS
            "es" -> SPANISH_WORDS
            "custom" -> customWords
            else -> WORDS
        }

    fun setLanguage(tagalog: Boolean) {
        languageCode = if (tagalog) "tl" else "en"
    }

    // NOTE: there is deliberately NO setLanguageCode() function here.
    // `var languageCode` already generates a JVM setter named
    // setLanguageCode(String), so adding one clashes at compile time
    // ("Platform declaration clash"). Callers assign directly:
    // Dictionary.languageCode = code
    fun isTagalog(): Boolean = languageCode == "tl"

    /** Common Spanish words, for the built-in Spanish pack. Same
     *  starter-list idea as Tagalog: personal words fill the gaps. */
    val SPANISH_WORDS = listOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "en",
        "y", "a", "que", "es", "no", "si", "con", "por", "para", "como",
        "yo", "tu", "el", "ella", "nosotros", "vosotros", "ustedes",
        "ellos", "ellas", "esto", "eso", "aquello", "aqui", "ahi", "alli",
        "donde", "cuando", "porque", "como", "que", "quien", "cual",
        "gracias", "hola", "adios", "buenos", "dias", "noches", "tardes",
        "bien", "mal", "bueno", "buena", "grande", "pequeno", "nuevo",
        "viejo", "feliz", "triste", "cansado", "hambre", "sed", "caliente",
        "frio", "casa", "agua", "comida", "arroz", "pan", "dia", "noche",
        "manana", "ayer", "hoy", "ahora", "luego", "siempre", "nunca",
        "mucho", "poco", "mas", "menos", "muy", "tambien", "tampoco",
        "comer", "beber", "dormir", "despertar", "ir", "venir", "volver",
        "trabajar", "estudiar", "jugar", "correr", "caminar", "hablar",
        "escuchar", "mirar", "leer", "escribir", "comprar", "vender",
        "pagar", "ayuda", "dinero", "tiempo", "semana", "mes", "ano",
        "nino", "nina", "hombre", "mujer", "madre", "padre", "hermano",
        "hermana", "amigo", "familia", "maestro", "estudiante", "amor",
        "vida", "corazon", "dios", "iglesia", "fe", "paz", "alegria"
    ).distinct()

    /** Common Tagalog words (Wilson speaks Filipino). Single words
     *  only, for prediction / swipe / autocorrect in Tagalog mode.
     *  This is a starter list, not a full Tagalog dictionary — the
     *  personal dictionary fills the gaps with the user's own words. */
    val TAGALOG_WORDS = listOf(
        "ako", "ikaw", "ka", "siya", "kami", "tayo", "kayo", "sila",
        "ito", "iyan", "iyon", "dito", "diyan", "doon", "saan", "kailan",
        "bakit", "paano", "ano", "sino", "hindi", "oo", "opo", "po",
        "salamat", "paki", "mahal", "kaibigan", "pamilya", "bahay",
        "tubig", "pagkain", "kanin", "ulam", "araw", "gabi", "umaga",
        "hapon", "bukas", "kahapon", "ngayon", "mamaya", "mabuti",
        "maganda", "malaki", "maliit", "masaya", "malungkot", "pagod",
        "gutom", "uhaw", "mainit", "malamig", "kumain", "uminom",
        "matulog", "gumising", "pumunta", "umuwi", "magtrabaho",
        "mag-aral", "maglaro", "tumakbo", "lumakad", "umupo", "tumayo",
        "magsalita", "makinig", "tumingin", "magbasa", "magsulat",
        "bumili", "magbenta", "magbayad", "tulong", "pera", "oras",
        "linggo", "buwan", "taon", "bata", "matanda", "lalaki", "babae",
        "nanay", "tatay", "kuya", "ate", "bunso", "asawa", "anak",
        "kapatid", "guro", "estudyante", "sundalo", "aralin", "paaralan",
        "trabaho", "laro", "musika", "pelikula", "telepono", "kotse",
        "daan", "lungsod", "probinsya", "dagat", "bundok", "ilog",
        "langit", "lupa", "puso", "isip", "buhay", "pag-ibig", "diyos",
        "simbahan", "dasal", "biyaya", "kapayapaan", "kaligayahan"
    ).distinct()

    val WORDS = (WordChunk0.LIST + WordChunk1.LIST + WordChunk2.LIST + WordChunk3.LIST + WordChunk4.LIST + WordChunk5.LIST + WordChunk6.LIST + WordChunk7.LIST + WordChunk8.LIST + WordChunk9.LIST + WordChunk10.LIST).distinct()

    fun isWord(word: String): Boolean = activeWords.contains(word.lowercase())

    /** Letters that can follow [prefix] in some dictionary word. */
    fun nextLetters(prefix: String, extra: Collection<String> = emptyList()): Set<Char> {
        if (prefix.isEmpty()) return emptySet()
        val p = prefix.lowercase()
        val out = mutableSetOf<Char>()
        for (w in extra + activeWords) {
            if (w.length > p.length && w.startsWith(p)) out.add(w[p.length])
        }
        return out
    }

    /** Words starting with [prefix], personal words first. */
    fun completions(prefix: String, extra: Collection<String> = emptyList()): List<String> {
        if (prefix.length < 2) return emptyList()
        val p = prefix.lowercase()
        val personal = extra.filter { it.startsWith(p) }
        val common = activeWords.filter { it.startsWith(p) && it !in personal }
        return (personal + common).take(5)
    }

    // Edit distance, counting an adjacent transposition as ONE edit
    // (optimal string alignment / Damerau-Levenshtein). Plain
    // Levenshtein counts a transposition as two edits, which made
    // the classic typo "teh" -> "the" look too far away to correct
    // (distance 2, limit 1 for short words). With transposition = 1,
    // "teh" is distance 1 from "the" and corrects correctly.
    fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = min(
                min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                dp[i][j] = min(dp[i][j], dp[i - 2][j - 2] + 1)
            }
        }
        return dp[a.length][b.length]
    }

    /** Closest dictionary word to [word], or null if nothing is close. */
    fun bestCorrection(word: String, extra: Collection<String> = emptyList()): String? {
        val w = word.lowercase()
        if (w.length < 3) return null
        if (w.any { it.code > 127 }) return null // accented word: do not "correct" it away
        val maxDist = if (w.length <= 4) 1 else 2
        var best: String? = null
        var bestDist = Int.MAX_VALUE
        for (cand in extra + activeWords) {
            if (cand == w) return null // already a real word: do not "correct" it
            if (kotlin.math.abs(cand.length - w.length) > maxDist) continue
            val d = levenshtein(w, cand)
            if (d < bestDist) {
                bestDist = d
                best = cand
            }
        }
        return if (bestDist <= maxDist) best else null
    }

    /** Best swipe match for a traced letter sequence. */
    fun bestSwipeMatch(traced: String, extra: Collection<String> = emptyList()): String? {
        val t = traced.lowercase()
        if (t.length < 3) return null
        if (t in activeWords || t in extra) return t
        var best: String? = null
        var bestDist = Int.MAX_VALUE
        for (cand in extra + activeWords) {
            if (cand.first() != t.first() || cand.last() != t.last()) continue
            val d = levenshtein(t, cand)
            if (d < bestDist) {
                bestDist = d
                best = cand
            }
        }
        if (bestDist <= 2) return best
        // Subsequence fallback, Oct 29, 2026: a swipe trace almost
        // always crosses extra keys on the way (a straight line from
        // S to W passes near other keys), so the trace is longer than
        // the word and edit distance alone misses it. If a dictionary
        // word appears in order inside the trace, starting on the
        // first traced letter and ending on the last, that word is
        // almost certainly the intended one — e.g. "swipe" inside a
        // noisy "swertyuipe" trace. Longest such word wins.
        // Subsequence fallback, revised Oct 29, 2026: the old
        // version required the candidate to end on the LAST traced
        // letter, so a swipe for "that" that ended on "y" could only
        // match "they" — even though "that" was also in the trace.
        // Now the last-letter gate is gone, and candidates are scored
        // by how compactly they appear in the trace: the smallest
        // span (last matched index minus first), then the longest
        // word. For "tyhgfdsaserty", "that" spans 0..10 and "they"
        // spans 0..12, so "that" wins. The first-letter gate stays:
        // where a swipe starts is the most reliable part of it.
        var subBest: String? = null
        var subBestSpan = Int.MAX_VALUE
        for (cand in extra + activeWords) {
            if (cand.length < 3 || cand.first() != t.first()) continue
            var ti = 0
            var matched = true
            var firstIdx = -1
            var lastIdx = -1
            for (c in cand) {
                while (ti < t.length && t[ti] != c) ti++
                if (ti >= t.length) {
                    matched = false
                    break
                }
                if (firstIdx < 0) firstIdx = ti
                lastIdx = ti
                ti++
            }
            if (!matched) continue
            val span = lastIdx - firstIdx
            if (subBest == null || span < subBestSpan ||
                (span == subBestSpan && cand.length > subBest.length)
            ) {
                subBest = cand
                subBestSpan = span
            }
        }
        return subBest
    }
}
