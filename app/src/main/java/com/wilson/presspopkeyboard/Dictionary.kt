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

    // Military rank casing, code 25 (Wilson: Army ranks
    // should be capitalized all letters, follow guidance
    // for different branches). Keys are lowercase typed
    // forms, values are the official abbreviation:
    // Army: ALL CAPS (AR 25-50) — PVT, SFC, 1SG, CPT, etc.
    // Marine Corps: mixed case — LCpl, GySgt, SgtMaj,
    // 1stSgt, MGySgt, 2ndLt, LtCol, BGen, MajGen, LtGen.
    // Air Force / Space Force: mixed case — Amn, A1C,
    // SrA, SSgt, TSgt, MSgt, SMSgt, CMSgt.
    // Navy / Coast Guard: ALL CAPS — ENS, LTJG, LCDR,
    // CPO, SCPO, MCPO, PO1, RADM, VADM, ADM.
    // Ambiguous forms default to Wilson's branch, Army
    // (SGT, MAJ, COL, GEN are Army all-caps here, even
    // though other branches write Sgt, Maj, Col, Gen),
    // and forms that are not an Army abbreviation at
    // all (capt, ssgt, msgt) are left to their own
    // branch's casing. Full rank titles (sergeant,
    // captain) are ordinary words, not abbreviations,
    // so they are NOT forced to all caps.
    val RANK_CASING: Map<String, String> = mapOf(
        // Army, all caps
        "pvt" to "PVT", "pv2" to "PV2", "pfc" to "PFC",
        "spc" to "SPC", "cpl" to "CPL", "sgt" to "SGT",
        "ssg" to "SSG", "sfc" to "SFC", "msg" to "MSG",
        "1sg" to "1SG", "sgm" to "SGM", "csm" to "CSM",
        "wo1" to "WO1", "cw2" to "CW2", "cw3" to "CW3",
        "cw4" to "CW4", "cw5" to "CW5",
        "2lt" to "2LT", "1lt" to "1LT", "cpt" to "CPT",
        "maj" to "MAJ", "ltc" to "LTC", "col" to "COL",
        "bg" to "BG", "mg" to "MG", "ltg" to "LTG",
        "gen" to "GEN",
        // Marine Corps, mixed case
        "lcpl" to "LCpl", "gysgt" to "GySgt",
        "sgtmaj" to "SgtMaj", "1stsgt" to "1stSgt",
        "mgysgt" to "MGySgt", "2ndlt" to "2ndLt",
        "1stlt" to "1stLt", "ltcol" to "LtCol",
        "bgen" to "BGen", "majgen" to "MajGen",
        "ltgen" to "LtGen",
        // Air Force / Space Force, mixed case
        "amn" to "Amn", "a1c" to "A1C", "sra" to "SrA",
        "ssgt" to "SSgt", "tsgt" to "TSgt",
        "msgt" to "MSgt", "smsgt" to "SMSgt",
        "cmsgt" to "CMSgt",
        // Navy / Coast Guard, all caps
        "ens" to "ENS", "ltjg" to "LTJG", "lcdr" to "LCDR",
        "cpo" to "CPO", "scpo" to "SCPO", "mcpo" to "MCPO",
        "po1" to "PO1", "po2" to "PO2", "po3" to "PO3",
        "radm" to "RADM", "vadm" to "VADM", "adm" to "ADM",
        "cwo2" to "CWO2", "cwo3" to "CWO3",
        "cwo4" to "CWO4", "cwo5" to "CWO5"
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
        "the" to listOf("first", "best", "only"),
        "am" to listOf("going", "not", "so"),
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

    /** Words starting with [prefix], personal words first.
     * Apostrophe forms are offered next, code 21 (Wilson:
     * words that should be with an apostrophe, like there's
     * and that's, should give me options as suggestions).
     * Without this, there's/that's sit at the end of the
     * 20,000-word list, past the take(5) cutoff, so typing
     * "ther" or "that" never offered them — even though
     * they are the word he wants. Now typing "theres"
     * offers "there's", typing "that" offers "that's",
     * and so on, as a tap choice, not only as a Space
     * auto-expansion. */
    fun completions(prefix: String, extra: Collection<String> = emptyList()): List<String> {
        if (prefix.length < 2) return emptyList()
        val p = prefix.lowercase()
        val personal = extra.filter { it.startsWith(p) }
        val apostrophe = activeWords.filter {
            it.contains('\'') && it.startsWith(p) && it !in personal
        }
        val common = activeWords.filter {
            it.startsWith(p) && it !in personal && it !in apostrophe
        }
        // Code 25: rank suggestions show in their
        // official casing in the strip (SFC, GySgt),
        // not in dictionary lowercase.
        return (personal + apostrophe + common).distinct().take(5)
            .map { RANK_CASING[it.lowercase()] ?: it }
    }

    /**
     * Grammar context, code 21 (Wilson: "please add grammar
     * context"). Two-word context -> next word, for common
     * phrases where the previous word alone is not enough:
     * "thank you" -> for, "how are" -> you, "going to" ->
     * the, "there is" -> a, and so on. Offered after
     * learned pairs and before the one-word BIGRAMS table,
     * so a specific phrase wins over a generic next word.
     * Honest limit, as with bigrams: this is a phrase
     * table, not a full grammar engine — it knows the
     * next word from the last two words, not from the
     * whole sentence, and it has no part-of-speech
     * tagging beyond the pairs listed here.
     */
    val TRIGRAMS: Map<String, List<String>> = mapOf(
        "thank you" to listOf("for", "so", "much"),
        "how are" to listOf("you", "things", "we"),
        "i have" to listOf("to", "been", "a"),
        "i am" to listOf("going", "not", "so"),
        "going to" to listOf("the", "be", "get"),
        "want to" to listOf("go", "be", "get"),
        "need to" to listOf("go", "be", "get"),
        "have to" to listOf("go", "be", "get"),
        "let me" to listOf("know", "see", "help"),
        "look at" to listOf("the", "this", "that"),
        "in the" to listOf("morning", "evening", "middle"),
        "on the" to listOf("other", "same", "top"),
        "at the" to listOf("moment", "time", "top"),
        "for the" to listOf("first", "last", "next"),
        "to the" to listOf("store", "house", "top"),
        "with the" to listOf("help", "time", "kids"),
        "from the" to listOf("store", "house", "top"),
        "one of" to listOf("the", "my", "those"),
        "a lot" to listOf("of", "more", "better"),
        "by the" to listOf("way", "time", "end"),
        "out of" to listOf("the", "my", "time"),
        "up to" to listOf("you", "me", "the"),
        "back to" to listOf("the", "work", "school"),
        "next to" to listOf("the", "me", "my"),
        "in front" to listOf("of", "the", "me"),
        "as soon" to listOf("as", "possible", "i"),
        "i will" to listOf("be", "go", "get"),
        "you will" to listOf("be", "see", "get"),
        "we will" to listOf("be", "go", "see"),
        "they will" to listOf("be", "go", "have"),
        "he will" to listOf("be", "go", "have"),
        "she will" to listOf("be", "go", "have"),
        "it will" to listOf("be", "take", "rain"),
        "i was" to listOf("going", "not", "born"),
        "you were" to listOf("going", "not", "right"),
        "we were" to listOf("going", "not", "talking"),
        "they were" to listOf("going", "not", "talking"),
        "this is" to listOf("a", "the", "my"),
        "that is" to listOf("a", "the", "not"),
        "there is" to listOf("a", "no", "the"),
        "there are" to listOf("many", "some", "no"),
        "here is" to listOf("the", "my", "a"),
        "what is" to listOf("your", "the", "this"),
        "what are" to listOf("you", "we", "they"),
        "when are" to listOf("you", "we", "they"),
        "where is" to listOf("the", "my", "your"),
        "why is" to listOf("it", "this", "that"),
        "how is" to listOf("your", "the", "it")
    )

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
