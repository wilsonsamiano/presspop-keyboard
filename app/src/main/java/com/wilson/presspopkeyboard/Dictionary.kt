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

    val WORDS = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "it", "was", "for",
        "on", "are", "as", "with", "his", "they", "i", "at", "this", "have",
        "from", "or", "one", "had", "by", "but", "not", "what", "all", "were",
        "we", "when", "your", "can", "said", "there", "use", "an", "each",
        "which", "she", "do", "how", "their", "if", "will", "up", "other",
        "about", "out", "many", "then", "them", "these", "so", "some", "her",
        "would", "make", "like", "him", "into", "time", "has", "look", "two",
        "more", "write", "go", "see", "number", "no", "way", "could", "people",
        "my", "than", "first", "water", "been", "call", "who", "oil", "its",
        "now", "find", "long", "down", "day", "did", "get", "come", "made",
        "may", "part", "over", "new", "sound", "take", "only", "little",
        "work", "know", "place", "year", "live", "me", "back", "give", "most",
        "very", "after", "thing", "our", "just", "name", "good", "man", "think",
        "say", "great", "where", "help", "through", "much", "before", "line",
        "right", "too", "mean", "old", "any", "same", "tell", "boy", "follow",
        "came", "want", "show", "also", "around", "form", "three", "small",
        "set", "put", "end", "does", "another", "well", "large", "must", "big",
        "even", "such", "because", "turn", "here", "why", "ask", "went", "men",
        "read", "need", "land", "different", "home", "us", "move", "try",
        "kind", "hand", "picture", "again", "change", "off", "play", "spell",
        "air", "away", "animal", "house", "point", "page", "letter", "mother",
        "answer", "found", "study", "still", "learn", "should", "world",
        "high", "every", "near", "add", "food", "between", "own", "below",
        "country", "plant", "last", "school", "father", "keep", "tree", "never",
        "start", "city", "earth", "eye", "light", "thought", "head", "under",
        "story", "saw", "left", "few", "while", "along", "might", "close",
        "something", "seem", "next", "hard", "open", "example", "begin", "life",
        "always", "those", "both", "paper", "together", "got", "group", "often",
        "run", "dog", "horse", "cat", "bird", "fish", "love", "god", "jesus",
        "bible", "church", "family", "friend", "happy", "today", "tomorrow",
        "morning", "night", "thank", "thanks", "please", "yes", "yeah", "okay",
        "hello", "hi", "hey", "good", "morning", "evening", "week", "month",
        "work", "army", "soldier", "training", "gym", "run", "walk", "drive",
        "car", "phone", "tablet", "video", "music", "game", "tennis", "eala",
        "watch", "play", "win", "lose", "team", "match", "set", "point",
        "please", "sorry", "really", "very", "much", "many", "well", "just",
        "going", "come", "coming", "get", "got", "getting", "make", "made",
        "take", "taking", "give", "giving", "see", "seeing", "know", "knowing",
        "think", "thought", "feel", "feeling", "want", "wanted", "need",
        "needed", "like", "liked", "love", "loved", "hope", "hoping", "pray",
        "praying", "bless", "blessed", "amen", "lord", "christ", "faith"
    ).distinct()

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
        return if (bestDist <= 2) best else null
    }
}
