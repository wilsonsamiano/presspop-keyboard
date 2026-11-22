package com.wilson.presspopkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Typing-quality tests. Plain JVM tests: Dictionary has no Android
 * dependencies, so these run with `./gradlew testDebugUnitTest`
 * (needs `testImplementation("junit:junit:4.13.2")` in app/build.gradle).
 *
 * Put this file at:
 *   app/src/test/java/com/wilson/presspopkeyboard/TypingQualityTest.kt
 *
 * The typo list is a way to MEASURE, not a promise that every pair must
 * pass: run it, read the printed MISS lines, and raise FLOOR_PERCENT as
 * the keyboard improves, so a later change that makes autocorrect worse
 * fails the build instead of going unnoticed.
 */
class TypingQualityTest {

    @Before
    fun useEnglish() {
        Dictionary.languageCode = "en"
    }

    // typo to intended word. Includes adjacent-key slips, transpositions,
    // doubled/missing letters and the classic hard spellings.
    private val typos = listOf(
        "helo" to "hello", "teh" to "the", "adn" to "and", "taht" to "that",
        "jsut" to "just", "waht" to "what", "thsi" to "this", "ahve" to "have",
        "abuot" to "about", "hte" to "the", "wiht" to "with", "yuo" to "you",
        "recieve" to "receive", "wierd" to "weird", "beleive" to "believe",
        "freind" to "friend", "thier" to "their", "becuase" to "because",
        "wich" to "which", "untill" to "until", "tommorow" to "tomorrow",
        "tomorow" to "tomorrow", "seperate" to "separate", "definately" to "definitely",
        "occured" to "occurred", "adress" to "address", "accomodate" to "accommodate",
        "begining" to "beginning", "calender" to "calendar", "comming" to "coming",
        "existance" to "existence", "goverment" to "government", "grammer" to "grammar",
        "happend" to "happened", "immediatly" to "immediately", "knowlege" to "knowledge",
        "neccessary" to "necessary", "noticable" to "noticeable", "posession" to "possession",
        "prefered" to "preferred", "publically" to "publicly", "realy" to "really",
        "recomend" to "recommend", "relevent" to "relevant", "resturant" to "restaurant",
        "succesful" to "successful", "suprise" to "surprise", "truely" to "truly",
        "wensday" to "wednesday", "peice" to "piece",
        "heigth" to "height", "wether" to "whether", "writting" to "writing",
        "diffrent" to "different", "interesing" to "interesting", "importnat" to "important",
        "pepole" to "people", "probaly" to "probably", "reccomend" to "recommend",
        "sentance" to "sentence", "shedule" to "schedule", "similer" to "similar",
        "somthing" to "something", "stoped" to "stopped", "strenght" to "strength",
        "thourough" to "thorough", "togehter" to "together", "unfortunatly" to "unfortunately",
        "usualy" to "usually", "vacume" to "vacuum", "wiating" to "waiting",
        "yestarday" to "yesterday", "accross" to "across", "agressive" to "aggressive",
        "apparant" to "apparent", "basicly" to "basically", "buisness" to "business",
        "catagory" to "category", "cemetary" to "cemetery", "colum" to "column",
        "concious" to "conscious", "dissapear" to "disappear", "embarass" to "embarrass",
        "enviroment" to "environment", "excersize" to "exercise", "familar" to "familiar",
        "finaly" to "finally", "foriegn" to "foreign", "gaurd" to "guard",
        "harrass" to "harass", "hieght" to "height", "independant" to "independent",
        "judgement" to "judgment"
    )

    // Raise this as autocorrect improves. Measured 89% on the real word
    // list (Nov 14, 2026, code 47); the floor sits one point below it so
    // a later change that makes autocorrect worse fails the build instead
    // of going unnoticed, without failing on float/order drift.
    private val FLOOR_PERCENT = 88

    @Test
    fun typoCorrectionAccuracy() {
        var hit = 0
        val misses = ArrayList<String>()
        for ((typo, want) in typos) {
            // Words the dictionary already contains are not "typos" for this test.
            if (Dictionary.isWord(typo)) continue
            val got = Dictionary.bestCorrection(typo)
            if (got == want) hit++ else misses.add("$typo -> $got (wanted $want)")
        }
        val tested = typos.count { !Dictionary.isWord(it.first) }
        val pct = if (tested == 0) 100 else hit * 100 / tested
        println("Typo accuracy: $hit / $tested ($pct%)")
        misses.forEach { println("  MISS: $it") }
        assertTrue("typo accuracy $pct% is below the $FLOOR_PERCENT% floor", pct >= FLOOR_PERCENT)
    }

    @Test
    fun validWordsAreNeverCorrectedWithoutContext() {
        for (w in listOf("the", "from", "form", "their", "there", "hello")) {
            assertNull("'$w' is a word and must not be auto-corrected", Dictionary.bestCorrection(w))
        }
    }

    @Test
    fun realWordNeedsContextToGetAHint() {
        // No predictions: never a hint.
        assertNull(Dictionary.contextualAlternative("form", emptyList()))
        // Typed word is itself the prediction: no hint.
        assertNull(Dictionary.contextualAlternative("from", listOf("from")))
        // Transposition of a more common predicted word.
        assertEquals("from", Dictionary.contextualAlternative("form", listOf("from", "the")))
        // Known mix-up pair, predicted by context.
        assertEquals("there", Dictionary.contextualAlternative("their", listOf("there")))
        assertEquals("than", Dictionary.contextualAlternative("then", listOf("than")))
        // Very common words are protected from near-miss hints.
        assertNull(Dictionary.contextualAlternative("on", listOf("in")))
    }

    @Test
    fun completionsAreBoostedByContext() {
        val plain = Dictionary.completions("be", emptyList())
        val boosted = Dictionary.completions("be", emptyList(), listOf("beach"))
        assertEquals("beach", boosted.first())
        assertTrue("beach should not already be first without context", plain.first() != "beach")
    }

    @Test
    fun correctionPrefersTheContextWord() {
        // "beavh" is one adjacent-key slip (v/c) from "beach".
        val withContext = Dictionary.bestCorrection("beavh", emptyList(), listOf("beach"))
        assertNotNull(withContext)
        assertEquals("beach", withContext)
    }

    // ---------- Code 50 ----------

    @Test
    fun spaceSlipsSplitIntoTwoWords() {
        assertEquals("the cat", Dictionary.spaceSlipSplit("thebcat"))
        assertEquals("if you", Dictionary.spaceSlipSplit("ifnyou"))
        assertEquals("in the", Dictionary.spaceSlipSplit("inthe"))
        assertEquals("and the", Dictionary.spaceSlipSplit("andvthe"))
        // Real words and names are never split.
        assertNull(Dictionary.spaceSlipSplit("cabin"))
        assertNull(Dictionary.spaceSlipSplit("guinard"))
    }

    @Test
    fun nextKeyPriorFavoursSpaceAfterAWholeWord() {
        val afterThe = Dictionary.nextKeyPrior("the")!!
        val space = afterThe[' '] ?: 0f
        assertTrue("space should beat b after 'the'", space > (afterThe['b'] ?: 0f))
        assertTrue("space should beat v after 'the'", space > (afterThe['v'] ?: 0f))
        val afterA = Dictionary.nextKeyPrior("a")!!
        assertTrue("n should beat j after 'a'", (afterA['n'] ?: 0f) > (afterA['j'] ?: 0f))
        assertNull("off-dictionary prefix gives no prior", Dictionary.nextKeyPrior("xqz"))
    }

    @Test
    fun commonTypoTgeBecomesThe() {
        assertEquals("the", Dictionary.bestCorrection("tge"))
    }
}
