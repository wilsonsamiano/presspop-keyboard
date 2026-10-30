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
        "praying", "bless", "blessed", "amen", "lord", "christ", "faith",
        // Added Oct 29, 2026, from real autocorrect misses while
        // testing: "menu" was missing, so typing it was "corrected"
        // to "men" (distance 1) — a correct word changed to a wrong
        // one. Contractions with an apostrophe were missing entirely,
        // so "doesnt" could never become "doesn't".
        "menu", "keyboard", "swipe", "update", "settings", "scroll",
        "delete", "highlight", "sentence", "button", "backspace",
        "spacebar", "cursor", "theme", "dark", "light", "language",
        "english", "tagalog", "spanish", "custom", "word", "words",
        "personal", "learn", "learned", "app", "apps", "android",
        "google", "message", "messages", "email", "password",
        "account", "internet", "wifi", "bluetooth", "battery",
        "camera", "photo", "photos", "video", "videos",
        "don't", "doesn't", "can't", "won't", "isn't", "aren't",
        "wasn't", "weren't", "haven't", "hasn't", "didn't",
        "couldn't", "shouldn't", "wouldn't", "it's", "that's",
        "there's", "here's", "what's", "let's", "i'm", "you're",
        "we're", "they're", "i've", "you've", "we've", "they've",
        "i'll", "you'll", "he'll", "she'll", "we'll", "they'll",
        "i'd", "you'd", "he'd", "she'd", "we'd", "they'd",
        // Second expansion, Oct 29, 2026, from a scan for missing
        // everyday words: "is" and "he" were not in the list at all,
        // and neither were dozens of other ordinary words, which is
        // why autocorrect kept guessing at words it should have
        // known. A word in this list is never "corrected" away, and
        // it becomes available to suggestions and swipe.
        "is", "he", "above", "across", "against", "almost", "alone",
        "already", "although", "among", "amount", "anything",
        "baby", "bad", "ball", "become", "bed", "began", "behind",
        "being", "bring", "business", "care", "case", "cause",
        "center", "certain", "child", "children", "company",
        "course", "door", "early", "enough", "ever", "face",
        "fact", "far", "four", "girl", "government", "however",
        "important", "late", "later", "leave", "lot", "once",
        "question", "second", "side", "state", "sure", "though",
        "took", "toward", "upon", "war", "without", "years",
        // Expanded Oct 29, 2026 (Wilson): the old list was only
        // ~700 words, so real words like better, remove and trying
        // were not in it and autocorrect 'fixed' them into letter,
        // move and other wrong words. This batch adds common
        // English vocabulary in one go — no more fixing words one
        // by one. Predictive text uses the same list, so its
        // completions improve with it.
        "better", "best", "remove", "removed", "removing", "replacing", "replace", "replaced", "predictive", "typing", "trying", "everything", "actually", "probably", "maybe", "yesterday", "afternoon", "autocorrect", "become", "becomes", "became", "begin", "begins", "began", "bring", "brings", "brought", "build", "builds", "built", "buy", "buys", "bought", "choose", "chooses", "chose", "chosen", "come", "comes", "coming", "came", "do", "does", "doing", "done", "did", "draw", "draws", "drew", "drawn", "drink", "drinks", "drank", "drive", "drives", "drove", "driven", "eat", "eats", "eating", "ate", "eaten", "fall", "falls", "fell", "fallen", "feel", "feels", "felt", "find", "finds", "found", "fly", "flies", "flew", "flown", "forget", "forgets", "forgot", "forgotten", "give", "gives", "giving", "gave", "given", "go", "goes", "going", "gone", "went", "grow", "grows", "grew", "grown", "hear", "hears", "heard", "hold", "holds", "held", "keep", "keeps", "kept", "know", "knows", "knew", "known", "leave", "leaves", "left", "lend", "lends", "lent", "let", "lets", "lie", "lies", "lay", "lain", "lose", "loses", "lost", "make", "makes", "making", "made", "mean", "means", "meant", "meet", "meets", "met", "pay", "pays", "paid", "put", "puts", "putting", "read", "reads", "reading", "ride", "rides", "rode", "ridden", "ring", "rings", "rang", "rung", "rise", "rises", "rose", "risen", "run", "runs", "running", "ran", "say", "says", "saying", "said", "see", "sees", "seeing", "saw", "seen", "sell", "sells", "sold", "send", "sends", "sent", "set", "sets", "setting", "shake", "shakes", "shook", "shaken", "show", "shows", "showed", "shown", "sing", "sings", "sang", "sung", "sit", "sits", "sitting", "sat", "sleep", "sleeps", "slept", "speak", "speaks", "spoke", "spoken", "spend", "spends", "spent", "stand", "stands", "stood", "steal", "steals", "stole", "stolen", "swim", "swims", "swam", "swum", "take", "takes", "taking", "took", "taken", "teach", "teaches", "taught", "tear", "tears", "tore", "torn", "tell", "tells", "told", "think", "thinks", "thought", "throw", "throws", "threw", "thrown", "understand", "understands", "understood", "wake", "wakes", "woke", "woken", "wear", "wears", "wore", "worn", "win", "wins", "won", "write", "writes", "writing", "wrote", "written", "working", "walking", "talking", "looking", "listening", "watching", "waiting", "wanted", "needed", "liked", "loved", "hoped", "praying", "blessing", "blessed", "blessings", "faster", "slower", "stronger", "weaker", "bigger", "smaller", "longer", "shorter", "higher", "lower", "harder", "easier", "softer", "louder", "quieter", "brighter", "darker", "warmer", "colder", "cleaner", "dirtier", "happier", "sadder", "older", "younger", "newer", "better", "worse", "worst", "best", "least", "most", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "really", "very", "quite", "rather", "almost", "already", "always", "never", "sometimes", "often", "usually", "rarely", "seldom", "soon", "later", "earlier", "today", "tonight", "tomorrow", "yesterday", "morning", "afternoon", "evening", "night", "midnight", "noon", "daily", "weekly", "monthly", "yearly", "together", "alone", "away", "around", "about", "above", "below", "across", "through", "between", "behind", "beside", "inside", "outside", "under", "over", "again", "anymore", "anyway", "anyhow", "anywhere", "everywhere", "somewhere", "nowhere", "here", "there", "where", "when", "why", "how", "what", "which", "who", "whom", "whose", "that", "this", "these", "those", "then", "than", "though", "although", "because", "unless", "until", "while", "since", "during", "before", "after", "against", "among", "within", "without", "toward", "towards", "upon", "across", "people", "person", "woman", "women", "man", "men", "child", "children", "baby", "boy", "girl", "friend", "friends", "family", "parent", "parents", "mother", "father", "sister", "brother", "son", "daughter", "wife", "husband", "teacher", "student", "doctor", "soldier", "army", "church", "school", "house", "home", "room", "kitchen", "bathroom", "bedroom", "door", "window", "table", "chair", "car", "truck", "phone", "computer", "tablet", "keyboard", "screen", "battery", "charger", "music", "song", "video", "photo", "picture", "movie", "book", "story", "game", "sport", "tennis", "football", "basketball", "water", "food", "coffee", "breakfast", "lunch", "dinner", "morning", "afternoon", "evening", "week", "month", "year", "day", "hour", "minute", "second", "time", "money", "price", "cost", "free", "cheap", "expensive", "big", "small", "large", "little", "great", "good", "bad", "nice", "happy", "sad", "angry", "tired", "hungry", "thirsty", "sick", "healthy", "strong", "weak", "fast", "slow", "hot", "cold", "warm", "cool", "bright", "dark", "light", "heavy", "easy", "hard", "simple", "difficult", "important", "different", "same", "other", "another", "every", "each", "all", "some", "any", "no", "none", "many", "much", "few", "several", "both", "either", "neither", "own", "new", "old", "young", "right", "wrong", "true", "false", "sure", "certain", "possible", "impossible", "ready", "busy", "free", "open", "closed", "full", "empty", "clean", "dirty", "safe", "dangerous", "beautiful", "pretty", "ugly", "funny", "serious", "quiet", "loud", "soft", "sweet", "sour", "bitter", "salty", "work", "works", "worked", "job", "office", "meeting", "email", "message", "call", "text", "answer", "question", "problem", "solution", "idea", "plan", "project", "team", "leader", "boss", "soldier", "sergeant", "captain", "general", "command", "training", "exercise", "gym", "run", "walk", "drive", "travel", "trip", "vacation", "holiday", "birthday", "anniversary", "wedding", "church", "pastor", "prayer", "bible", "verse", "chapter", "faith", "hope", "love", "grace", "peace", "joy", "blessing", "worship", "song", "songs", "sing", "sang", "pray", "prays", "prayed", "praying", "god", "lord", "jesus", "christ", "amen", "able", "about", "above", "across", "act", "action", "actually", "add", "address", "admit", "adult", "affect", "afraid", "after", "afternoon", "again", "against", "age", "ago", "agree", "ahead", "air", "alarm", "album", "alert", "alike", "alive", "allow", "almost", "alone", "along", "already", "also", "alter", "although", "always", "amaze", "amount", "an", "and", "angry", "animal", "announce", "another", "answer", "ant", "anxiety", "any", "anybody", "anyone", "anything", "anyway", "apart", "apartment", "apologize", "appear", "apple", "apply", "appoint", "approach", "approve", "april", "arch", "area", "argue", "arm", "around", "arrange", "arrive", "article", "artist", "as", "ask", "asleep", "aspect", "assess", "asset", "assign", "assist", "assume", "assure", "at", "athlete", "atlas", "atmosphere", "attach", "attack", "attempt", "attend", "attention", "attitude", "attract", "audience", "august", "aunt", "author", "auto", "autumn", "available", "average", "avoid", "awake", "award", "aware", "awful", "baby", "back", "background", "backward", "bacteria", "bad", "bag", "bake", "balance", "ball", "ban", "band", "bank", "bar", "bare", "bargain", "barrel", "base", "basic", "basket", "bath", "battle", "bay", "beach", "beam", "bean", "bear", "beard", "beast", "beat", "beauty", "because", "become", "bed", "bee", "beef", "beer", "before", "beg", "begin", "behalf", "behave", "behind", "being", "belief", "bell", "belong", "below", "belt", "bench", "bend", "benefit", "berry", "beside", "best", "bet", "better", "between", "beyond", "bible", "bid", "big", "bill", "bird", "birth", "birthday", "bit", "bite", "bitter", "black", "blade", "blame", "blank", "blanket", "blast", "bleed", "bless", "blind", "block", "blood", "blow", "blue", "board", "boat", "body", "boil", "bold", "bolt", "bomb", "bond", "bone", "book", "boost", "boot", "border", "borrow", "boss", "both", "bother", "bottle", "bottom", "bounce", "bound", "bowl", "box", "boy", "brain", "branch", "brave", "bread", "break", "breath", "breeze", "brick", "bride", "bridge", "bright", "brilliant", "bring", "broad", "broken", "brother", "brown", "brush", "bubble", "bucket", "budget", "bug", "build", "bulb", "bulk", "bullet", "bump", "bunch", "bundle", "burden", "burn", "burst", "bury", "bus", "bush", "business", "busy", "but", "butter", "button", "buy", "buzz", "by", "cabin", "cable", "cake", "calculate", "calendar", "calm", "camera", "camp", "campaign", "can", "cancel", "cancer", "candidate", "candle", "candy", "canvas", "cap", "capable", "capital", "captain", "car", "carbon", "card", "care", "career", "careful", "carpet", "carry", "cart", "case", "cash", "cast", "castle", "cat", "catch", "category", "cattle", "cause", "ceiling", "celebrate", "cell", "cent", "center", "century", "cereal", "certain", "chain", "chair", "challenge", "chance", "change", "channel", "chaos", "chapter", "charge", "charity", "chart", "chase", "cheap", "check", "cheese", "chef", "chemical", "chest", "chicken", "chief", "child", "chill", "chip", "chocolate", "choice", "choose", "chop", "church", "cigarette", "circle", "citizen", "city", "civil", "claim", "clap", "clarify", "class", "clause", "clean", "clear", "clerk", "clever", "click", "client", "cliff", "climb", "clinic", "clip", "clock", "close", "cloth", "clothes", "cloud", "club", "clue", "coach", "coal", "coast", "coat", "code", "coffee", "coin", "cold", "collapse", "colleague", "collect", "college", "color", "column", "combine", "come", "comfort", "comic", "command", "comment", "common", "company", "compare", "compete", "complain", "complete", "complex", "component", "compose", "compound", "comprehend", "computer", "concentrate", "concept", "concern", "concert", "conclude", "concrete", "condition", "conduct", "conference", "confidence", "confirm", "conflict", "confuse", "connect", "consider", "consist", "constant", "construct", "consult", "contact", "contain", "content", "contest", "context", "continue", "contract", "contrast", "contribute", "control", "convenient", "conversation", "convert", "cook", "cool", "copy", "core", "corn", "corner", "correct", "cost", "cotton", "couch", "could", "count", "country", "couple", "courage", "course", "court", "cousin", "cover", "crack", "craft", "crash", "crazy", "cream", "create", "credit", "crew", "crime", "crisis", "criteria", "critic", "crop", "cross", "crowd", "crucial", "cry", "cultural", "culture", "cup", "curious", "current", "curtain", "curve", "cushion", "custom", "customer", "cut", "cycle", "dad", "daily", "damage", "dance", "danger", "dare", "dark", "data", "date", "daughter", "dawn", "day", "dead", "deal", "dear", "death", "debate", "debt", "decade", "decide", "decision", "declare", "decline", "decorate", "decrease", "deep", "deer", "defeat", "defend", "define", "degree", "delay", "deliver", "demand", "democracy", "demonstrate", "denial", "dense", "deny", "depart", "depend", "deposit", "depth", "deputy", "derive", "describe", "desert", "deserve", "design", "desk", "despite", "destroy", "detail", "detect", "develop", "device", "devote", "diagram", "dial", "dialogue", "diamond", "diary", "dice", "die", "diet", "differ", "dig", "digital", "dignity", "dilemma", "dinner", "dip", "direct", "dirt", "dirty", "disagree", "disappear", "disaster", "discipline", "discount", "discover", "discuss", "disease", "dish", "dismiss", "disorder", "display", "dispute", "distance", "distant", "distinct", "distinguish", "distribute", "district", "disturb", "ditch", "dive", "divide", "divorce", "doctor", "document", "dog", "doll", "dollar", "domain", "donate", "done", "door", "dose", "dot", "double", "doubt", "dough", "down", "dozen", "draft", "drag", "drama", "draw", "dream", "dress", "drift", "drill", "drink", "drive", "drop", "drum", "dry", "duck", "due", "dull", "dump", "dust", "duty", "dwarf", "dwell", "dynamic", "eager", "eagle", "early", "earn", "earth", "ease", "east", "easy", "eat", "echo", "economy", "edge", "edit", "educate", "effort", "egg", "ego", "eight", "either", "elbow", "elder", "elect", "elegant", "element", "elephant", "elite", "else", "embark", "embrace", "emerge", "emotion", "employ", "empty", "enable", "encounter", "end", "enemy", "energy", "engage", "engine", "enjoy", "enlarge", "enormous", "enough", "ensure", "enter", "entire", "entry", "envelope", "envy", "episode", "equal", "equip", "error", "escape", "especially", "essay", "essential", "establish", "estate", "estimate", "ethics", "even", "evening", "event", "ever", "every", "exact", "examine", "example", "exceed", "except", "exchange", "excite", "excuse", "exercise", "exhaust", "exhibit", "exist", "exit", "expand", "expect", "expense", "expert", "explain", "explode", "explore", "export", "expose", "express", "extend", "extra", "eye", "fabric", "face", "facility", "fact", "factor", "fail", "faint", "fair", "faith", "fall", "false", "fame", "family", "famous", "fan", "fancy", "far", "farm", "fashion", "fast", "fat", "fate", "father", "fault", "favor", "fear", "feather", "feature", "february", "fee", "feed", "feel", "fellow", "female", "fence", "fever", "few", "fiber", "fiction", "field", "fierce", "fight", "figure", "file", "fill", "film", "final", "finance", "find", "fine", "finger", "finish", "fire", "firm", "first", "fish", "fit", "fix", "flag", "flame", "flash", "flat", "flavor", "flee", "fleet", "flesh", "flex", "flight", "float", "flock", "flood", "floor", "flour", "flow", "flower", "flu", "fluid", "flush", "fly", "focus", "fog", "fold", "folk", "follow", "fond", "food", "fool", "foot", "for", "force", "foreign", "forest", "forget", "fork", "form", "formal", "former", "forth", "fortune", "forum", "forward", "fossil", "foster", "found", "four", "fox", "fraction", "fragile", "frame", "free", "freeze", "frequent", "fresh", "friend", "fright", "frog", "from", "front", "frost", "fruit", "fuel", "full", "fun", "function", "fund", "funny", "furniture", "further", "future", "gain", "galaxy", "gallery", "game", "gap", "garage", "garden", "gas", "gate", "gather", "gay", "gaze", "gear", "gem", "gender", "gene", "general", "genius", "genre", "gentle", "genuine", "gesture", "ghost", "giant", "gift", "giggle", "ginger", "girl", "give", "glad", "glance", "glass", "gleam", "glide", "glimpse", "global", "glory", "glove", "glow", "glue", "goal", "goat", "god", "gold", "golden", "golf", "good", "gorgeous", "gossip", "govern", "gown", "grab", "grace", "grade", "grain", "grand", "grant", "grape", "graph", "grasp", "grass", "grateful", "grave", "gray", "great", "greed", "green", "greet", "grid", "grief", "grill", "grip", "grocery", "ground", "group", "grow", "guarantee", "guard", "guess", "guest", "guide", "guilty", "guitar", "gun", "guy", "habit", "hair", "half", "hall", "halt", "hand", "handle", "hang", "happen", "happy", "harbor", "hard", "harm", "harvest", "hat", "hate", "have", "hawk", "hazard", "head", "health", "hear", "heart", "heat", "heaven", "heavy", "heel", "height", "helicopter", "hell", "hello", "help", "hen", "her", "herb", "here", "hero", "hers", "hesitate", "hey", "hidden", "hide", "high", "highlight", "highway", "hill", "hint", "hip", "hire", "his", "history", "hit", "hobby", "hockey", "hold", "hole", "holiday", "hollow", "holy", "home", "honest", "honey", "honor", "hood", "hope", "horizon", "horn", "horror", "horse", "hospital", "host", "hotel", "hour", "house", "hover", "how", "however", "huge", "human", "humble", "humor", "hundred", "hungry", "hunt", "hurry", "hurt", "husband", "hush", "hut", "hybrid", "ice", "icon", "idea", "ideal", "identify", "idle", "ignore", "ill", "illegal", "illness", "illustrate", "image", "imagine", "immediate", "immense", "immerse", "impact", "impatient", "imply", "import", "impose", "impress", "improve", "impulse", "in", "inch", "incident", "include", "income", "increase", "indeed", "index", "indicate", "indoor", "industry", "infant", "inflict", "influence", "inform", "ingredient", "initial", "injury", "inner", "input", "inquiry", "insect", "insert", "inside", "insight", "insist", "inspire", "install", "instance", "instead", "instrument", "insult", "insurance", "intact", "intake", "integer", "intend", "intense", "intent", "interact", "interest", "internal", "internet", "interpret", "interrupt", "interview", "into", "introduce", "invent", "invest", "invite", "involve", "iron", "island", "issue", "it", "item", "its", "itself", "jacket", "jail", "jam", "january", "jar", "jaw", "jazz", "jealous", "jeans", "jet", "jewel", "job", "join", "joint", "joke", "jolly", "journal", "journey", "joy", "judge", "juice", "july", "jump", "june", "jungle", "junior", "junk", "just", "justice", "justify", "keen", "keep", "kettle", "key", "kick", "kid", "kill", "kind", "king", "kiss", "kit", "kitchen", "knee", "kneel", "knife", "knight", "knock", "know", "known", "lab", "label", "labor", "ladder", "lady", "lake", "lamp", "land", "lane", "language", "lap", "large", "last", "late", "laugh", "launch", "laundry", "law", "lawn", "layer", "lazy", "lead", "leader", "leaf", "leak", "lean", "learn", "lease", "least", "leather", "leave", "lecture", "left", "leg", "legal", "lemon", "lend", "length", "lens", "less", "lesson", "let", "letter", "level", "liar", "liberty", "library", "license", "lid", "life", "lift", "light", "like", "likely", "limb", "limit", "line", "link", "lion", "lip", "liquid", "list", "listen", "literally", "literature", "little", "live", "load", "loan", "lobster", "local", "locate", "lock", "log", "logic", "lonely", "long", "look", "loom", "loop", "loose", "lose", "loss", "lot", "loud", "love", "low", "loyal", "luck", "luggage", "lunch", "lung", "luxury", "machine", "mad", "magazine", "magic", "mail", "main", "major", "make", "male", "mall", "man", "manage", "manner", "manual", "manufacture", "many", "map", "march", "margin", "marine", "mark", "market", "marriage", "marry", "mars", "mask", "mass", "master", "match", "mate", "material", "math", "matter", "mature", "maximum", "may", "maybe", "mayor", "meal", "mean", "meanwhile", "measure", "meat", "mechanism", "medal", "media", "medical", "medium", "meet", "melody", "melt", "member", "memory", "mental", "mention", "menu", "mercy", "mere", "merit", "mess", "message", "metal", "meter", "method", "metro", "middle", "might", "mild", "mile", "military", "milk", "mill", "mind", "mine", "mineral", "minimum", "minor", "minute", "miracle", "mirror", "miss", "mission", "mist", "mistake", "mix", "mode", "model", "modern", "modest", "modify", "mom", "moment", "monitor", "month", "mood", "moon", "moral", "more", "morning", "mosquito", "mother", "motion", "motor", "mountain", "mouse", "mouth", "move", "movie", "much", "muck", "mud", "multiple", "multiply", "muscle", "museum", "music", "must", "mutual", "my", "myself", "mysterious", "myth", "nail", "name", "narrow", "nasty", "nation", "native", "natural", "nature", "near", "nearby", "nearly", "neat", "necessarily", "neck", "need", "needle", "neglect", "neighbor", "neither", "nerve", "nervous", "net", "network", "never", "new", "news", "next", "nice", "night", "nine", "no", "noble", "nobody", "nod", "noise", "none", "noon", "nor", "normal", "north", "nose", "not", "note", "nothing", "notice", "notion", "novel", "november", "now", "nuclear", "number", "nurse", "nut", "oak", "obey", "object", "obligation", "observe", "obtain", "obvious", "occasion", "occupy", "occur", "ocean", "october", "odd", "odor", "off", "offend", "offer", "office", "officer", "official", "often", "oil", "okay", "old", "olive", "olympic", "omit", "once", "one", "onion", "online", "only", "onto", "open", "opera", "opinion", "opponent", "opportunity", "oppose", "option", "or", "orange", "orbit", "orchard", "order", "ordinary", "organ", "orient", "origin", "ornament", "orphan", "ostrich", "other", "otherwise", "ought", "ounce", "out", "outcome", "outdoor", "outer", "output", "outside", "oval", "oven", "over", "overall", "overcome", "owe", "own", "owner", "pace", "pack", "package", "page", "pain", "paint", "pair", "palace", "pale", "palm", "pan", "panel", "panic", "paper", "parade", "paradise", "paragraph", "parallel", "parcel", "pardon", "parent", "park", "parking", "part", "participant", "participate", "particular", "partner", "party", "pass", "passage", "passenger", "passion", "past", "patch", "path", "patient", "pattern", "pause", "pave", "pay", "peace", "peach", "peak", "peanut", "pear", "peasant", "peculiar", "pedal", "pen", "pencil", "people", "pepper", "perfect", "perform", "perfume", "perhaps", "period", "permanent", "permission", "permit", "person", "personal", "persuade", "pet", "phase", "phenomenon", "philosopher", "phone", "photo", "phrase", "physical", "piano", "pick", "picture", "piece", "pig", "pigeon", "pile", "pilot", "pin", "pine", "pink", "pint", "pioneer", "pipe", "pitch", "pity", "place", "plain", "plan", "plane", "planet", "plant", "plastic", "plate", "platform", "play", "pleasant", "please", "pleasure", "plenty", "plot", "plus", "pocket", "poem", "poet", "point", "poison", "pole", "police", "policy", "political", "poll", "pollution", "pond", "pool", "poor", "pop", "popular", "population", "port", "portion", "portrait", "pose", "position", "positive", "possess", "possible", "post", "pot", "potato", "potential", "pound", "pour", "poverty", "powder", "power", "practical", "praise", "pray", "precise", "predict", "prefer", "prepare", "present", "preserve", "president", "press", "pressure", "pretend", "pretty", "prevent", "previous", "price", "pride", "priest", "primary", "prime", "prince", "princess", "principal", "principle", "print", "prior", "prison", "private", "prize", "probably", "problem", "procedure", "proceed", "process", "produce", "product", "profession", "profile", "profit", "program", "progress", "project", "promise", "promote", "prompt", "pronounce", "proof", "proper", "property", "propose", "prospect", "protect", "proud", "prove", "provide", "public", "publish", "pull", "pulse", "pump", "punch", "punish", "pupil", "purchase", "pure", "purple", "purpose", "purse", "push", "put", "puzzle", "qualify", "quality", "quantity", "quarrel", "quarter", "queen", "question", "quick", "quiet", "quit", "quite", "quiz", "quote", "rabbit", "race", "rack", "radar", "radio", "rage", "raid", "rail", "rain", "raise", "rally", "ramble", "range", "rank", "rapid", "rare", "rate", "rather", "raw", "ray", "reach", "react", "read", "ready", "real", "realize", "really", "realm", "rear", "reason", "rebel", "recall", "receive", "recent", "recipe", "recognize", "recommend", "record", "recover", "recycle", "red", "reduce", "refer", "reflect", "reform", "refuse", "regard", "region", "register", "regret", "regular", "regulate", "rehearse", "reign", "reject", "relate", "relax", "release", "relevant", "relief", "rely", "remain", "remark", "remedy", "remember", "remind", "remote", "remove", "rent", "repair", "repeat", "replace", "reply", "report", "represent", "request", "require", "rescue", "research", "resemble", "reserve", "resign", "resist", "resort", "resource", "respect", "respond", "rest", "restore", "restrict", "result", "resume", "retail", "retain", "retire", "return", "reveal", "revenge", "review", "revise", "reward", "rhythm", "rice", "rich", "rid", "ride", "ridiculous", "rifle", "right", "rigid", "ring", "riot", "rip", "ripe", "rise", "risk", "rival", "river", "road", "roar", "roast", "rob", "rock", "rocket", "role", "roll", "romantic", "roof", "room", "root", "rope", "rose", "rough", "round", "route", "routine", "row", "royal", "rub", "rude", "rug", "rule", "rumor", "run", "rural", "rush", "rust", "sad", "safe", "sail", "salad", "sale", "salt", "salute", "same", "sample", "sand", "satisfy", "sauce", "save", "say", "scale", "scan", "scare", "scene", "scheme", "scholar", "school", "science", "scissors", "score", "scorn", "scout", "scrap", "scream", "screen", "screw", "script", "sea", "seal", "search", "season", "seat", "second", "secret", "section", "sector", "secure", "see", "seed", "seek", "seem", "segment", "select", "self", "sell", "send", "senior", "sense", "sensitive", "sentence", "separate", "september", "series", "serious", "serve", "session", "set", "settle", "seven", "several", "severe", "sew", "sex", "shade", "shadow", "shake", "shallow", "shame", "shape", "share", "sharp", "shave", "she", "sheep", "sheet", "shelf", "shell", "shelter", "shift", "shine", "ship", "shirt", "shock", "shoe", "shoot", "shop", "shore", "short", "shot", "should", "shoulder", "shout", "show", "shower", "shrink", "shut", "shy", "sick", "side", "sigh", "sight", "sign", "signal", "silence", "silk", "silly", "silver", "similar", "simple", "sin", "since", "sing", "single", "sink", "sip", "sir", "sister", "sit", "site", "situation", "six", "size", "ski", "skill", "skin", "skirt", "skull", "sky", "slack", "slam", "slave", "sleep", "slice", "slide", "slight", "slim", "slip", "slope", "slow", "small", "smart", "smell", "smile", "smoke", "smooth", "snake", "snap", "snatch", "sneak", "sneeze", "sniff", "snore", "snow", "so", "soap", "soccer", "social", "society", "sock", "soft", "soil", "solar", "soldier", "solid", "solve", "some", "son", "song", "soon", "sophisticated", "sorry", "sort", "soul", "sound", "soup", "source", "south", "space", "spare", "speak", "special", "species", "specific", "speech", "speed", "spell", "spend", "sphere", "spice", "spider", "spill", "spin", "spirit", "spite", "split", "spoil", "spoke", "spoon", "sport", "spot", "spray", "spread", "spring", "square", "squeeze", "stable", "stadium", "staff", "stage", "stain", "stairs", "stake", "stale", "stand", "standard", "stare", "start", "state", "station", "statue", "status", "stay", "steady", "steak", "steal", "steam", "steel", "steep", "steer", "stem", "step", "stick", "stiff", "still", "stimulate", "sting", "stir", "stock", "stomach", "stone", "stool", "stop", "store", "storm", "story", "stove", "straight", "strange", "straw", "stream", "street", "strength", "stress", "stretch", "strict", "strike", "string", "strip", "stripe", "stroke", "strong", "struggle", "student", "studio", "study", "stuff", "stupid", "style", "subject", "submit", "substance", "subtle", "suburb", "succeed", "such", "sudden", "suffer", "sufficient", "sugar", "suggest", "suit", "sum", "summary", "summer", "sun", "super", "supper", "supply", "support", "suppose", "sure", "surface", "surge", "surprise", "surround", "survey", "survive", "suspect", "sustain", "swallow", "swear", "sweat", "sweater", "sweep", "sweet", "swell", "swift", "swim", "swing", "switch", "sword", "symbol", "sympathy", "symptom", "system", "table", "tackle", "tail", "take", "tale", "talent", "talk", "tall", "tank", "tap", "tape", "target", "task", "taste", "tax", "taxi", "teach", "team", "tear", "tease", "technical", "technology", "teen", "tell", "temper", "temple", "temporary", "tempt", "ten", "tend", "tender", "tennis", "tense", "tent", "term", "terrible", "territory", "test", "text", "thank", "that", "the", "their", "them", "theme", "then", "theory", "there", "these", "they", "thick", "thin", "thing", "think", "third", "thirst", "thirty", "this", "thorough", "those", "though", "thought", "thousand", "threat", "three", "throat", "through", "throw", "thumb", "thursday", "thus", "ticket", "tide", "tie", "tight", "tile", "till", "time", "tin", "tiny", "tip", "tire", "tired", "tissue", "title", "to", "toast", "today", "toe", "together", "toilet", "token", "tomato", "tomorrow", "tone", "tongue", "tonight", "too", "tool", "tooth", "top", "topic", "torch", "tornado", "torrent", "toss", "total", "touch", "tough", "tour", "toward", "towel", "tower", "town", "toy", "trace", "track", "trade", "tradition", "traffic", "tragedy", "trail", "train", "transfer", "transform", "translate", "transmit", "transport", "trap", "travel", "tray", "treasure", "treat", "tree", "tremble", "trend", "trial", "triangle", "tribe", "trick", "trip", "triumph", "troop", "tropical", "trouble", "truck", "true", "trunk", "trust", "truth", "try", "tube", "tuesday", "tune", "tunnel", "turn", "tutor", "twelve", "twenty", "twice", "twin", "twist", "two", "type", "typical", "ugly", "ultimate", "unable", "uncle", "under", "undo", "unfold", "uniform", "union", "unit", "unite", "universe", "university", "unknown", "unless", "until", "unusual", "up", "upon", "upper", "upset", "urban", "urge", "us", "use", "used", "useful", "usual", "utility", "utter", "vacant", "vacation", "valley", "valuable", "value", "van", "vary", "vast", "vegetable", "vehicle", "venture", "verb", "verify", "version", "very", "vessel", "veteran", "via", "victim", "victory", "video", "view", "village", "violin", "virtual", "virtue", "virus", "visible", "vision", "visit", "visual", "vital", "vivid", "voice", "volume", "vote", "voyage", "wage", "wait", "wake", "walk", "wall", "wander", "want", "war", "warm", "warn", "wash", "waste", "watch", "water", "wave", "way", "weak", "wealth", "wear", "weather", "weave", "wedding", "week", "weep", "weigh", "weight", "welcome", "well", "west", "wet", "whale", "what", "whatever", "wheat", "wheel", "when", "whenever", "where", "whether", "which", "while", "whisper", "white", "who", "whole", "whom", "whose", "why", "wide", "widely", "wife", "wild", "will", "win", "wind", "window", "wine", "wing", "winner", "winter", "wipe", "wire", "wisdom", "wise", "wish", "with", "withdraw", "within", "without", "witness", "wolf", "woman", "wonder", "wood", "wooden", "wool", "word", "work", "worker", "world", "worry", "worth", "would", "wound", "wrap", "write", "writer", "wrong", "yard", "year", "yellow", "yes", "yesterday", "yet", "yield", "you", "young", "your", "youth", "zone"
        "young"
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
