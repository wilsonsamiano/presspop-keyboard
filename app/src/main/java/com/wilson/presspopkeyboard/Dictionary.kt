package com.wilson.presspopkeyboard

import kotlin.math.min

/**
 * Built-in English dictionary (20,084 words, code 17+). It powers:
 * 1. Predictive hit areas - which letters can follow the word so far.
 * 2. Swipe typing - which word a glide path most likely spells.
 * 3. Autocorrect + suggestions - completions and near-miss corrections.
 * 4. Next-word prediction (code 20) - BIGRAMS below, plus pairs
 *    the user actually types, learned on-device in PersonalDictionary.
 * Everything runs on-device, with no network and no data leaving
 * the phone. Personal words are merged in front of this list, so
 * the user's own words win.
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


    /**
     * Next-word prediction, code 20 (Wilson: predictive text must
     * learn proper sentence building, not just complete the word
     * being typed). Common English word pairs, previous word ->
     * most likely next words, in order. This is grammar in
     * practice, not a language model: after "thank" comes "you",
     * after "how" comes "are", after "going" comes "to", and so
     * on. Learned pairs from the user's own typing (see
     * PersonalDictionary.notePair) are offered BEFORE these, so
     * the keyboard learns his sentences, not just these generic
     * ones. Honest limit: this is a bigram table, not a full
     * grammar engine — it knows the next word from the previous
     * word only, not from the whole sentence. A word with no
     * entry here simply gets no next-word suggestion.
     */
    /**
     * Contraction expansion, code 21 (Wilson: add words with
     * apostrophes like dont, im, and such, and dont put caps).
     * Typed without an apostrophe, these are real shorthand
     * forms, but they are also dictionary words in the 20k
     * list (dont, cant, im, etc.), so Space used to leave
     * them alone and they never became don't, can't, i'm.
     * This map is checked BEFORE the isWord check in
     * handleSpace / swipe / suggestion taps: a mapped word
     * is always replaced with its apostrophe form, always
     * lowercase, never capitalized — even at a sentence
     * start, per Wilson's "dont put caps". Ambiguous forms
     * are deliberately NOT here: lets (verb vs let's),
     * its (possessive vs it's), ill (sick vs I'll),
     * id (identification vs I'd) would be wrong too often.
     */
    val CONTRACTIONS: Map<String, String> = mapOf(
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "isnt" to "isn't",
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "couldnt" to "couldn't",
        "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't",
        "havent" to "haven't",
        "hasnt" to "hasn't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "im" to "i'm",
        "ive" to "i've",
        "theyre" to "they're",
        "youre" to "you're",
        "weve" to "we've",
        "hes" to "he's",
        "shes" to "she's",
        "whats" to "what's",
        "thats" to "that's",
        "theres" to "there's",
        "heres" to "here's"
    )

    val BIGRAMS: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "have", "think"),
        "you" to listOf("are", "have", "can"),
        "he" to listOf("is", "was", "has"),
        "she" to listOf("is", "was", "has"),
        "we" to listOf("are", "have", "will"),
        "they" to listOf("are", "have", "will"),
        "it" to listOf("is", "was", "will"),
        "thank" to listOf("you", "god", "so"),
        "thanks" to listOf("for", "so", "a"),
        "how" to listOf("are", "do", "is"),
        "what" to listOf("is", "are", "do"),
        "when" to listOf("you", "i", "it"),
        "where" to listOf("is", "are", "you"),
        "why" to listOf("is", "are", "did"),
        "who" to listOf("is", "are", "was"),
        "good" to listOf("morning", "afternoon", "night"),
        "happy" to listOf("birthday", "new", "to"),
        "going" to listOf("to", "home", "back"),
        "want" to listOf("to", "a", "you"),
        "need" to listOf("to", "a", "help"),
        "can" to listOf("you", "i", "be"),
        "will" to listOf("be", "have", "go"),
        "would" to listOf("like", "be", "love"),
        "should" to listOf("be", "you", "we"),
        "let" to listOf("me", "us", "go"),
        "do" to listOf("you", "i", "not"),
        "did" to listOf("you", "he", "she"),
        "is" to listOf("a", "the", "not"),
        "are" to listOf("you", "we", "they"),
        "was" to listOf("a", "the", "not"),
        "were" to listOf("you", "we", "they"),
        "have" to listOf("a", "been", "to"),
        "has" to listOf("been", "a", "the"),
        "had" to listOf("a", "been", "the"),
        "this" to listOf("is", "morning", "one"),
        "that" to listOf("is", "was", "the"),
        "there" to listOf("is", "are", "was"),
        "here" to listOf("is", "are", "you"),
        "my" to listOf("name", "wife", "family"),
        "your" to listOf("name", "family", "time"),
        "his" to listOf("name", "family", "car"),
        "her" to listOf("name", "family", "car"),
        "their" to listOf("name", "family", "car"),
        "our" to listOf("family", "house", "time"),
        "very" to listOf("good", "much", "happy"),
        "really" to listOf("good", "want", "like"),
        "not" to listOf("only", "sure", "going"),
        "just" to listOf("want", "got", "a"),
        "get" to listOf("up", "back", "the"),
        "go" to listOf("to", "home", "back"),
        "come" to listOf("back", "home", "on"),
        "look" to listOf("at", "for", "like"),
        "take" to listOf("a", "care", "the"),
        "make" to listOf("sure", "a", "it"),
        "see" to listOf("you", "the", "a"),
        "know" to listOf("how", "what", "if"),
        "think" to listOf("about", "it", "so"),
        "feel" to listOf("like", "good", "better"),
        "love" to listOf("you", "it", "this"),
        "like" to listOf("to", "a", "this"),
        "one" to listOf("of", "more", "day"),
        "two" to listOf("of", "more", "days"),
        "first" to listOf("time", "of", "one"),
        "last" to listOf("night", "week", "year"),
        "next" to listOf("week", "time", "day"),
        "new" to listOf("year", "day", "one"),
        "today" to listOf("is", "i", "we"),
        "tomorrow" to listOf("morning", "i", "will"),
        "yesterday" to listOf("was", "morning", "i"),
        "morning" to listOf("good", "i", "at"),
        "night" to listOf("good", "last", "at"),
        "home" to listOf("from", "to", "at"),
        "work" to listOf("from", "at", "on"),
        "school" to listOf("at", "today", "after"),
        "church" to listOf("at", "today", "tomorrow"),
        "family" to listOf("is", "time", "today"),
        "time" to listOf("to", "for", "is"),
        "day" to listOf("is", "at", "today"),
        "people" to listOf("are", "who", "like"),
        "way" to listOf("to", "of", "back"),
        "back" to listOf("home", "to", "at"),
        "out" to listOf("of", "to", "the"),
        "up" to listOf("to", "the", "a"),
        "down" to listOf("to", "the", "a"),
        "over" to listOf("the", "to", "a"),
        "because" to listOf("i", "you", "it"),
        "if" to listOf("you", "i", "we"),
        "while" to listOf("i", "you", "we"),
        "after" to listOf("the", "a", "lunch"),
        "before" to listOf("the", "a", "bed"),
        "during" to listOf("the", "my", "this"),
        "through" to listOf("the", "a", "time"),
        "without" to listOf("a", "the", "my"),
        "with" to listOf("a", "the", "my"),
        "for" to listOf("the", "a", "my"),
        "from" to listOf("the", "a", "my"),
        "by" to listOf("the", "a", "now"),
        "at" to listOf("the", "home", "work"),
        "in" to listOf("the", "a", "my"),
        "on" to listOf("the", "a", "my"),
        "to" to listOf("be", "go", "the"),
        "of" to listOf("the", "course", "my"),
        "and" to listOf("then", "the", "i"),
        "but" to listOf("i", "it", "the"),
        "so" to listOf("i", "you", "much"),
        "please" to listOf("let", "call", "send"),
        "sorry" to listOf("about", "for", "that"),
        "a" to listOf("lot", "few", "good"),
        "an" to listOf("hour", "idea", "old"),
        "no" to listOf("problem", "worries", "idea"),
        "yes" to listOf("please", "sir", "maam"),
        // Plural / singular agreement, code 21 (Wilson: add
        // plural context to sentences). After a plural word,
        // the next word must agree in number: these/those/
        // they/we/you take are/have/were, not is/has/was;
        // after two/many/few/several, the next word is a
        // plural noun (days, years, things, people). After a
        // singular word (this/that/he/she/it, a/an/one/each/
        // every), the next word is singular: is/was/has, or
        // a singular noun. Learned pairs still win over
        // these generic entries, so his own sentences teach
        // the keyboard his plurals first.
        "these" to listOf("are", "were", "days"),
        "those" to listOf("are", "were", "days"),
        "two" to listOf("days", "years", "things"),
        "three" to listOf("days", "years", "things"),
        "many" to listOf("people", "things", "days"),
        "few" to listOf("days", "things", "people"),
        "several" to listOf("days", "years", "things"),
        "all" to listOf("the", "people", "things"),
        "some" to listOf("people", "things", "days"),
        "both" to listOf("of", "are", "the"),
        "other" to listOf("people", "things", "days"),
        "each" to listOf("day", "time", "one"),
        "every" to listOf("day", "time", "morning"),
        "another" to listOf("day", "time", "one"),
        "does" to listOf("it", "he", "she"),
        "goes" to listOf("to", "home", "back"),
        "wants" to listOf("to", "a", "me"),
        "needs" to listOf("to", "a", "help"),
        "likes" to listOf("to", "a", "this"),
        "kids" to listOf("are", "have", "love"),
        "friends" to listOf("are", "have", "and"),
        "days" to listOf("ago", "later", "are"),
        "years" to listOf("ago", "later", "old"),
        "things" to listOf("are", "like", "to"),
        "people" to listOf("are", "who", "say")
    )

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
