package com.wilson.presspopkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * PressPop keyboard view, v2.
 *
 * Features in this one view:
 * - iPhone-style key popups on EVERY key (letters, space, shift,
 *   delete, 123, enter, emoji). Press, slide, lift to commit.
 * - Predictive hit areas (the original-iPhone trick): the visible
 *   keys never move or resize, but the invisible touch zone of the
 *   most likely next letter is widened, so a finger landing between
 *   two keys picks the letter the dictionary expects. A dead-centre
 *   tap on a neighbour still wins, because distance still matters.
 *   See Dictionary.nextLetters().
 * - Swipe / glide typing: glide across letters, lift, and the traced
 *   path is matched against the dictionary (personal words first).
 * - Emoji page, via the smiley key. Emoji is a mode like 123, not
 *   bolted onto the letter rows.
 * - Space-bar cursor: hold space, then slide left/right to move the
 *   text cursor, and up/down to move it a line at a time,
 *   iPhone-style. Horizontal travel is 8dp per character, vertical
 *   travel is 30dp per line.
 * - Double-tap Shift = caps lock. Double-tap Space = ". " is handled
 *   in PressPopIME, where the text before the cursor is known.
 * - Dark / light theme, from the app's setting.
 *
 * Deliberately NOT here: autocorrect and suggestions live in
 * PressPopIME (they need the input connection), and the personal
 * dictionary lives in PersonalDictionary (it needs storage).
 */

enum class KeyAction { CHAR, SPACE, SHIFT, DELETE, MODE, ENTER, VOICE }

enum class KeyboardMode { LETTERS, NUMBERS, SYMBOLS, EMOJI, NUMPAD }

data class Key(
    val label: String,
    val popupLabel: String,
    val action: KeyAction,
    val output: String = "",
    val mode: KeyboardMode? = null,
    val weight: Float = 1f
)

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onKey: ((Key) -> Unit)? = null
    var onSwipeWord: ((String) -> Unit)? = null
    var onCursorMove: ((Int) -> Unit)? = null
    var onCursorLineMove: ((Int) -> Unit)? = null
    var onWordChanged: ((String) -> Unit)? = null

    var personalWords: Set<String> = emptySet()

    var mode: KeyboardMode = KeyboardMode.LETTERS
        set(value) {
            field = value
            rebuildRows()
            // Row counts differ between modes (e.g. 5 rows in Letters
            // with the number row on, 4 in Numbers), so the measured
            // height must be recomputed — invalidate() alone keeps the
            // old height, which is what made the bottom row (and the
            // spacebar) appear to jump up a row on switching modes.
            requestLayout()
            invalidate()
        }

    var shiftOn: Boolean = false
        set(value) {
            field = value
            rebuildRows()
            invalidate()
        }

    var shiftLock: Boolean = false

    var darkMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Optional number row above the letter rows (off by default). */
    var showNumberRow: Boolean = false
        set(value) {
            field = value
            rebuildRows()
            requestLayout()
            invalidate()
        }

    /** The word currently being typed, lowercased, for prediction. */
    var typedWord: String = ""
        private set

    fun setTypedWord(word: String) {
        typedWord = word.lowercase()
        onWordChanged?.invoke(typedWord)
        invalidate()
    }

    private fun noteTypedWordChange() {
        onWordChanged?.invoke(typedWord)
    }

    private var rows: List<List<Key>> = emptyList()

    private data class KeyRect(val key: Key, val rect: RectF)
    private var keyRects: List<KeyRect> = emptyList()
    private var rowHeight = 0f
    private var gap = 0f

    private var pressedKey: Key? = null
    private var pressedRect: RectF? = null

    // Swipe state
    private val swipePoints = mutableListOf<PointF>()
    private val visitedLetters = mutableListOf<String>()
    private var downPoint = PointF()

    // Space-cursor state
    private var cursorMode = false
    private var lastCursorX = 0f
    private var cursorAccum = 0f
    private var lastCursorY = 0f
    private var cursorAccumY = 0f

    private var lastShiftTap = 0L

    // Long-press accent picker state
    private var variantMode = false
    private var variantBase: Key? = null
    private var variantList: List<String> = emptyList()
    private var variantSelected = -1
    private var longPressKey: Key? = null
    private val longPressRunnable = Runnable {
        val base = longPressKey
        if (base != null && base == pressedKey && base.action == KeyAction.CHAR) {
            val variants = ACCENTS[base.output.lowercase().firstOrNull()] ?: emptyList()
            if (variants.isNotEmpty()) {
                variantMode = true
                variantBase = base
                variantList = if (shiftOn) variants.map { it.uppercase() } else variants
                variantSelected = 0
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
        }
    }

    companion object {
        val ACCENTS = mapOf(
            'a' to listOf("à", "á", "â", "ä", "ã", "å", "æ"),
            'e' to listOf("è", "é", "ê", "ë"),
            'i' to listOf("ì", "í", "î", "ï"),
            'o' to listOf("ò", "ó", "ô", "ö", "õ", "ø"),
            'u' to listOf("ù", "ú", "û", "ü"),
            'n' to listOf("ñ"),
            'c' to listOf("ç"),
            's' to listOf("ß", "ś", "š"),
            'y' to listOf("ý", "ÿ"),
            'l' to listOf("ł"),
            'z' to listOf("ź", "ż", "ž"),
            'r' to listOf("ř"),
            't' to listOf("ť"),
            'd' to listOf("ď"),
            'g' to listOf("ğ")
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var deleteRepeating = false
    private val deleteRepeat = object : Runnable {
        override fun run() {
            if (deleteRepeating) {
                typedWord = typedWord.dropLast(1)
                noteTypedWordChange()
                onKey?.invoke(Key("⌫", "⌫", KeyAction.DELETE))
                handler.postDelayed(this, 70)
            }
        }
    }
    private var spaceHeld = false
    private val spaceCursorStart = Runnable {
        if (spaceHeld) {
            cursorMode = true
            lastCursorX = downPoint.x
            lastCursorY = downPoint.y
            cursorAccum = 0f
            cursorAccumY = 0f
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(12f, 0f, 4f, Color.argb(90, 0, 0, 0))
    }
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B82F6")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        rebuildRows()
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun charKey(c: Char): Key {
        val shown = if (shiftOn) c.uppercaseChar().toString() else c.toString()
        return Key(shown, shown, KeyAction.CHAR, shown)
    }

    private fun emojiKey(): Key = Key("😀", "😀", KeyAction.MODE, mode = KeyboardMode.EMOJI, weight = 0.9f)

    private val EMOJIS = listOf(
        "😀", "😂", "😍", "🥰", "😎", "🤔", "😢", "😡", "👍", "👏",
        "❤️", "🔥", "💯", "🎉", "🙏", "🙌", "⭐", "🌙", "☀️", "🎂",
        "🍕", "🚗", "🏠", "⚽", "🎵", "📱", "💡", "😊", "😅", "😴"
    )

    private fun rebuildRows() {
        val numberRow = listOf(
            "1234567890".map { Key(it.toString(), it.toString(), KeyAction.CHAR, it.toString()) }
        )
        rows = when (mode) {
            KeyboardMode.LETTERS -> (if (showNumberRow) numberRow else emptyList()) + listOf(
                "qwertyuiop".map { charKey(it) },
                "asdfghjkl".map { charKey(it) },
                listOf(Key("⇧", "⇧", KeyAction.SHIFT, weight = 1.5f)) +
                    "zxcvbnm".map { charKey(it) } +
                    listOf(Key("⌫", "⌫", KeyAction.DELETE, weight = 1.5f)),
                listOf(
                    Key("123", "123", KeyAction.MODE, mode = KeyboardMode.NUMBERS, weight = 1.3f),
                    emojiKey(),
                    Key("🎤", "🎤", KeyAction.VOICE, weight = 0.8f),
                    Key(",", ",", KeyAction.CHAR, ",", weight = 1.0f),
                    Key("space", "space", KeyAction.SPACE, weight = 4.2f),
                    Key("⏎", "⏎", KeyAction.ENTER, weight = 1.3f)
                )
            )
            KeyboardMode.NUMBERS -> listOf(
                "1234567890".map { Key(it.toString(), it.toString(), KeyAction.CHAR, it.toString()) },
                listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
                    .map { Key(it, it, KeyAction.CHAR, it) },
                listOf(Key("#+=", "#+=", KeyAction.MODE, mode = KeyboardMode.SYMBOLS, weight = 1.5f)) +
                    listOf(".", ",", "?", "!", "'").map { Key(it, it, KeyAction.CHAR, it) } +
                    listOf(Key("⌫", "⌫", KeyAction.DELETE, weight = 1.5f)),
                listOf(
                    Key("ABC", "ABC", KeyAction.MODE, mode = KeyboardMode.LETTERS, weight = 1.5f),
                    emojiKey(),
                    Key("🎤", "🎤", KeyAction.VOICE, weight = 0.8f),
                    Key("🔢", "🔢", KeyAction.MODE, mode = KeyboardMode.NUMPAD, weight = 1.0f),
                    Key("space", "space", KeyAction.SPACE, weight = 3.4f),
                    Key("⏎", "⏎", KeyAction.ENTER, weight = 1.5f)
                )
            )
            KeyboardMode.SYMBOLS -> listOf(
                listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
                    .map { Key(it, it, KeyAction.CHAR, it) },
                listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")
                    .map { Key(it, it, KeyAction.CHAR, it) },
                listOf(Key("123", "123", KeyAction.MODE, mode = KeyboardMode.NUMBERS, weight = 1.5f)) +
                    listOf(".", ",", "?", "!", "'").map { Key(it, it, KeyAction.CHAR, it) } +
                    listOf(Key("⌫", "⌫", KeyAction.DELETE, weight = 1.5f)),
                listOf(
                    Key("ABC", "ABC", KeyAction.MODE, mode = KeyboardMode.LETTERS, weight = 1.5f),
                    emojiKey(),
                    Key("🎤", "🎤", KeyAction.VOICE, weight = 0.8f),
                    Key("space", "space", KeyAction.SPACE, weight = 4.2f),
                    Key("⏎", "⏎", KeyAction.ENTER, weight = 1.5f)
                )
            )
            KeyboardMode.NUMPAD -> listOf(
                // Telephone-style number pad, for dialling, PINs,
                // amounts and any time a full keyboard is in the way.
                // Reach it from the 123 page (🔢 key), ABC goes back.
                listOf("1", "2", "3").map { Key(it, it, KeyAction.CHAR, it) },
                listOf("4", "5", "6").map { Key(it, it, KeyAction.CHAR, it) },
                listOf("7", "8", "9").map { Key(it, it, KeyAction.CHAR, it) },
                listOf("*", "0", "#").map { Key(it, it, KeyAction.CHAR, it) },
                listOf(
                    Key("123", "123", KeyAction.MODE, mode = KeyboardMode.NUMBERS, weight = 1.5f),
                    Key("ABC", "ABC", KeyAction.MODE, mode = KeyboardMode.LETTERS, weight = 1.5f),
                    Key("space", "space", KeyAction.SPACE, weight = 3.0f),
                    Key("⌫", "⌫", KeyAction.DELETE, weight = 1.5f),
                    Key("⏎", "⏎", KeyAction.ENTER, weight = 1.5f)
                )
            )
            KeyboardMode.EMOJI -> listOf(
                EMOJIS.subList(0, 10).map { Key(it, it, KeyAction.CHAR, it) },
                EMOJIS.subList(10, 20).map { Key(it, it, KeyAction.CHAR, it) },
                EMOJIS.subList(20, 30).map { Key(it, it, KeyAction.CHAR, it) },
                listOf(
                    Key("ABC", "ABC", KeyAction.MODE, mode = KeyboardMode.LETTERS, weight = 1.5f),
                    Key("space", "space", KeyAction.SPACE, weight = 4.0f),
                    Key("🎤", "🎤", KeyAction.VOICE, weight = 0.8f),
                    Key("⌫", "⌫", KeyAction.DELETE, weight = 1.5f),
                    Key("⏎", "⏎", KeyAction.ENTER, weight = 1.5f)
                )
            )
        }
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width == 0) return
        gap = dp(6f)
        // Fixed-height keyboard, Oct 29, 2026: the view used to
        // measure to its current row count, so switching Letters
        // (5 rows with the number row on) to 123 (4 rows) shrank the
        // whole keyboard by one row, the app above it jumped, and
        // the screen felt like it was flickering. Now the view is
        // always 5 rows tall (see onMeasure) and the rows stretch to
        // fill it, so a mode switch never resizes the window — keys
        // just get a little taller in 4-row modes, and the bottom
        // row never moves.
        rowHeight = if (height > 0) {
            (height - gap * (rows.size + 1)) / rows.size
        } else {
            dp(52f)
        }
        val rects = mutableListOf<KeyRect>()
        // Bottom-anchor the rows: if this view is still at its old
        // (taller) height when a mode with fewer rows is drawn, the
        // bottom row — and the spacebar in it — stays at the bottom
        // instead of floating one row up. When the height matches the
        // rows exactly, this is the same as starting at `gap`.
        val rowsHeight = rowHeight * rows.size + gap * (rows.size + 1)
        var top = if (height > rowsHeight) height - rowsHeight + gap else gap
        val insetRowIndex = if (showNumberRow && mode == KeyboardMode.LETTERS) 2 else 1
        for ((rowIndex, row) in rows.withIndex()) {
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            val isInsetRow = rowIndex == insetRowIndex && mode == KeyboardMode.LETTERS
            val inset = if (isInsetRow) (width - (width - gap * 2) * 0.9f) / 2f else 0f
            val rowWidth = if (isInsetRow) (width - gap * 2) * 0.9f else width - gap * 2
            var left = gap + inset
            for (key in row) {
                val w = rowWidth * key.weight / totalWeight - gap
                rects.add(KeyRect(key, RectF(left, top, left + w, top + rowHeight)))
                left += w + gap
            }
            top += rowHeight + gap
        }
        keyRects = rects
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        // Always 5 rows tall, whatever the current mode has — see
        // layoutKeys. 5 is the most rows any mode uses (Letters with
        // the number row, and the number pad), so nothing ever grows
        // or shrinks when switching modes.
        val h = (dp(52f) * 5 + dp(6f) * 6).roundToInt()
        setMeasuredDimension(w, h)
    }

    /**
     * Predictive hit test. Returns the key whose (possibly widened)
     * touch zone best matches the touch. Predicted next letters get
     * a 1.7x distance advantage, but never shrink any key below its
     * visible rect: a dead-centre tap still has distance 0 and wins.
     *
     * Gap fixed Oct 29, 2026: the prediction only covered letters.
     * When the word just typed is already a complete dictionary or
     * personal word, the most likely next key is SPACE, not another
     * letter — and "b" sits directly above the spacebar, so a low
     * thumb tap aimed at space kept landing on "b". Space now gets
     * a 2.0x advantage in exactly that case, so a tap between "b"
     * and space goes to space. A dead-centre tap on "b" still wins,
     * as it should — no prediction overrides a clear tap.
     *
     * Widened again Oct 29, 2026, from Wilson's report that he keeps
     * hitting "v" for spacebar: "v" also sits directly above the
     * spacebar, and the old boost only applied after a complete
     * word. Space now gets the 2.0x advantage unconditionally, in
     * every mode and at any point in a word, so a tap in the gap
     * between "v" (or "b", "c", "n", "m") and space goes to space.
     * A dead-centre tap on "v" itself still has distance 0 and
     * still wins — the boost only decides near-misses, which is
     * exactly the case that was going wrong.
     */
    private fun keyAt(x: Float, y: Float): KeyRect? {
        if (keyRects.isEmpty()) return null
        val predicted = if (mode == KeyboardMode.LETTERS) {
            Dictionary.nextLetters(typedWord, personalWords)
        } else emptySet()
        var best: KeyRect? = null
        var bestScore = Float.MAX_VALUE
        for (kr in keyRects) {
            val dx = when {
                x < kr.rect.left -> kr.rect.left - x
                x > kr.rect.right -> x - kr.rect.right
                else -> 0f
            }
            val dy = when {
                y < kr.rect.top -> kr.rect.top - y
                y > kr.rect.bottom -> y - kr.rect.bottom
                else -> 0f
            }
            val dist = hypot(dx, dy)
            val boost = when {
                kr.key.action == KeyAction.SPACE -> 2.0f
                kr.key.action == KeyAction.CHAR &&
                    kr.key.output.length == 1 &&
                    kr.key.output[0].lowercaseChar() in predicted -> 1.7f
                else -> 1f
            }
            val score = dist / boost
            if (score < bestScore) {
                bestScore = score
                best = kr
            }
        }
        // Accept only if reasonably near a key (expanded zone included).
        return if (bestScore <= dp(26f)) best else null
    }

    private fun pathLength(): Float {
        var total = 0f
        for (i in 1 until swipePoints.size) {
            total += hypot(
                swipePoints[i].x - swipePoints[i - 1].x,
                swipePoints[i].y - swipePoints[i - 1].y
            )
        }
        return total
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downPoint.set(event.x, event.y)
                swipePoints.clear()
                swipePoints.add(PointF(event.x, event.y))
                visitedLetters.clear()
                cursorMode = false
                spaceHeld = false
                val hit = keyAt(event.x, event.y)
                pressedKey = hit?.key
                pressedRect = hit?.rect
                if (hit?.key?.action == KeyAction.SPACE) {
                    spaceHeld = true
                    lastCursorX = event.x
                    handler.postDelayed(spaceCursorStart, 400)
                }
                if (hit?.key?.action == KeyAction.DELETE) {
                    deleteRepeating = true
                    handler.removeCallbacks(deleteRepeat)
                    handler.postDelayed(deleteRepeat, 450)
                }
                if (hit?.key?.action == KeyAction.CHAR && mode == KeyboardMode.LETTERS) {
                    visitedLetters.add(hit.key.output.lowercase())
                    longPressKey = hit.key
                    handler.removeCallbacks(longPressRunnable)
                    handler.postDelayed(longPressRunnable, 500)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // A slow swipe start could trigger the long-press
                // accent picker (500 ms on one key) and then stay
                // trapped in it, so the swipe never traced at all.
                // Sliding more than 20dp away cancels the picker and
                // falls through to normal swipe tracing below.
                if (variantMode &&
                    hypot(event.x - downPoint.x, event.y - downPoint.y) > dp(20f)
                ) {
                    variantMode = false
                    variantSelected = -1
                    invalidate()
                }
                if (variantMode) {
                    // Slide across the accent row to pick a variant.
                    val rect = pressedRect
                    if (rect != null && variantList.isNotEmpty()) {
                        val variantW = rect.width()
                        val totalW = variantW * variantList.size
                        val startX = (rect.centerX() - totalW / 2).coerceIn(0f, width - totalW)
                        val idx = ((event.x - startX) / variantW).toInt()
                            .coerceIn(0, variantList.size - 1)
                        if (idx != variantSelected) {
                            variantSelected = idx
                            invalidate()
                        }
                    }
                    return true
                }
                if (cursorMode) {
                    // Horizontal: 8dp per character (was 14dp), so a
                    // half-screen slide now moves about twice as many
                    // characters and a long jump no longer needs the
                    // whole screen width.
                    val dx = event.x - lastCursorX
                    cursorAccum += dx
                    lastCursorX = event.x
                    val step = dp(8f)
                    while (cursorAccum >= step) {
                        cursorAccum -= step
                        onCursorMove?.invoke(1)
                    }
                    while (cursorAccum <= -step) {
                        cursorAccum += step
                        onCursorMove?.invoke(-1)
                    }
                    // Vertical: 30dp per line, up or down. This sends
                    // the system's line-up / line-down command, so it
                    // follows the app's own line wrapping.
                    val dy = event.y - lastCursorY
                    cursorAccumY += dy
                    lastCursorY = event.y
                    val stepY = dp(30f)
                    while (cursorAccumY >= stepY) {
                        cursorAccumY -= stepY
                        onCursorLineMove?.invoke(1)
                    }
                    while (cursorAccumY <= -stepY) {
                        cursorAccumY += stepY
                        onCursorLineMove?.invoke(-1)
                    }
                    return true
                }
                swipePoints.add(PointF(event.x, event.y))
                if (hypot(event.x - downPoint.x, event.y - downPoint.y) > dp(20f)) {
                    spaceHeld = false
                    handler.removeCallbacks(spaceCursorStart)
                    handler.removeCallbacks(longPressRunnable)
                }
                val hit = keyAt(event.x, event.y)
                // Swipe tracing, Oct 29, 2026 (second fix): only record
                // a letter when the finger is actually inside that
                // key's rectangle — keyAt() returns the NEAREST key,
                // so recording on nearest alone recorded every key
                // passed NEAR and made garbage traces. But the first
                // version of this fix gated recording on the key
                // CHANGING, and that broke tracing the other way:
                // pressedKey already changes to a key while the finger
                // is still in the gap before it, so by the time the
                // finger actually enters that key, hit.key ==
                // pressedKey and the letter was never recorded at all.
                // Recording is therefore independent of the key-change
                // check: every move event inside a letter key records
                // that letter (deduped against the last one), whether
                // or not the pressed key just changed.
                if (hit != null && hit.key.action == KeyAction.CHAR &&
                    mode == KeyboardMode.LETTERS &&
                    event.x in hit.rect.left..hit.rect.right &&
                    event.y in hit.rect.top..hit.rect.bottom
                ) {
                    val letter = hit.key.output.lowercase()
                    if (visitedLetters.isEmpty() || visitedLetters.last() != letter) {
                        visitedLetters.add(letter)
                    }
                }
                if (hit != null && hit.key != pressedKey) {
                    pressedKey = hit.key
                    pressedRect = hit.rect
                    if (hit.key.action == KeyAction.DELETE) {
                        deleteRepeating = true
                        handler.removeCallbacks(deleteRepeat)
                        handler.postDelayed(deleteRepeat, 450)
                    } else {
                        deleteRepeating = false
                        handler.removeCallbacks(deleteRepeat)
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                spaceHeld = false
                handler.removeCallbacks(spaceCursorStart)
                handler.removeCallbacks(longPressRunnable)
                deleteRepeating = false
                handler.removeCallbacks(deleteRepeat)
                val wasCursor = cursorMode
                cursorMode = false
                val wasVariant = variantMode
                val pickedVariant = if (variantSelected in variantList.indices) {
                    variantList[variantSelected]
                } else null
                variantMode = false
                variantSelected = -1
                val key = pressedKey
                pressedKey = null
                pressedRect = null
                val wasSwipe = mode == KeyboardMode.LETTERS &&
                    visitedLetters.distinct().size >= 3 &&
                    pathLength() > dp(50f)
                // Capture the traced letters BEFORE clearing: visitedLetters
                // already dedupes consecutive repeats (h-e-l-o for hello),
                // and bestSwipeMatch tolerates that one missing letter.
                val tracedLetters = visitedLetters.toList()
                // Swipe debug (DebugMode, off by default): every
                // gesture (not every tap) shows what tracing saw —
                // trace, dictionary match, whether it counted as a
                // swipe, distinct letters, and path length. That
                // tells us whether tracing, the swipe threshold, or
                // matching is the failing step.
                val debugTrace = tracedLetters.joinToString("")
                val debugPathDp = (pathLength() / resources.displayMetrics.density).roundToInt()
                val debugMatch = if (debugTrace.isNotEmpty()) {
                    Dictionary.bestSwipeMatch(debugTrace, personalWords)
                } else null
                // Show this even when the trace is empty — that is
                // the whole point of the debug tool. The old version
                // only showed a popup when a trace existed, so when
                // tracing failed completely (the exact bug being
                // chased), the popup never happened at all and the
                // user saw nothing. An empty trace with a long path
                // is itself the diagnosis: the finger moved, but no
                // letters were recorded.
                if (debugPathDp > 20) {
                    DebugMode.toast(
                        context,
                        "Swipe: trace=${debugTrace.ifEmpty { "(empty)" }} " +
                            "match=${debugMatch ?: "none"} " +
                            "swipe=$wasSwipe distinct=${tracedLetters.distinct().size} " +
                            "path=${debugPathDp}dp"
                    )
                }
                swipePoints.clear()
                visitedLetters.clear()
                invalidate()
                if (event.action == MotionEvent.ACTION_CANCEL || wasCursor) return true
                if (wasVariant && pickedVariant != null) {
                    val variantKey = Key(pickedVariant, pickedVariant, KeyAction.CHAR, pickedVariant)
                    if (mode == KeyboardMode.LETTERS) {
                        typedWord += pickedVariant.lowercase()
                        noteTypedWordChange()
                    }
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onKey?.invoke(variantKey)
                    return true
                }
                if (wasSwipe) {
                    val traced = tracedLetters.joinToString("")
                    if (traced.isNotEmpty()) {
                        // Old fallback committed the raw trace when no
                        // dictionary word matched — that is exactly how
                        // a swipe produced a long garbage string instead
                        // of a word. Now: a real match commits, a short
                        // trace (a plausible unknown word, e.g. a name)
                        // commits as typed, and a long unmatched trace
                        // commits nothing at all — garbage is worse
                        // than nothing, and the user can tap-type it.
                        // Swipe is also one word at a time: swiping a
                        // whole phrase traces it as one long word, so
                        // lift between words and tap Space separately.
                        val match = Dictionary.bestSwipeMatch(traced, personalWords)
                        if (match != null) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSwipeWord?.invoke(match)
                        } else if (traced.length <= 10) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSwipeWord?.invoke(traced)
                        }
                    }
                    return true
                }
                if (key == null) return true
                when (key.action) {
                    KeyAction.SHIFT -> {
                        val now = SystemClock.uptimeMillis()
                        if (shiftLock) {
                            shiftLock = false
                            shiftOn = false
                        } else if (now - lastShiftTap < 350) {
                            shiftLock = true
                            shiftOn = true
                        } else {
                            shiftOn = !shiftOn
                        }
                        lastShiftTap = now
                    }
                    KeyAction.MODE -> key.mode?.let { mode = it }
                    KeyAction.VOICE -> {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onKey?.invoke(key)
                    }
                    KeyAction.CHAR -> {
                        if (mode == KeyboardMode.LETTERS) {
                            // Only letters extend the word being typed.
                            // Punctuation (e.g. the comma key on the main
                            // row) ends the word, or autocorrect would
                            // try to correct "word," as if it were a word.
                            if (key.output.length == 1 && key.output[0].isLetter()) {
                                typedWord += key.output.lowercase()
                            } else {
                                typedWord = ""
                            }
                            noteTypedWordChange()
                        } else if (mode == KeyboardMode.NUMBERS || mode == KeyboardMode.SYMBOLS ||
                            mode == KeyboardMode.NUMPAD
                        ) {
                            typedWord = ""
                            noteTypedWordChange()
                        }
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onKey?.invoke(key)
                        // Punctuation typed from the 123 / #+= pages
                        // returns to ABC automatically — typing an
                        // apostrophe in "don't" should not need a
                        // second tap on ABC. Digits stay put, so a
                        // multi-digit number is not interrupted, and
                        // other symbols ($, -, /, etc.) stay too, so
                        // sequences like $100 or 2026-10 are not broken.
                        if ((mode == KeyboardMode.NUMBERS || mode == KeyboardMode.SYMBOLS) &&
                            key.output in setOf("'", "\"", ".", ",", "?", "!", ";", ":")
                        ) {
                            mode = KeyboardMode.LETTERS
                        }
                    }
                    KeyAction.SPACE -> {
                        // Autocorrect bug fix: do NOT clear typedWord
                        // before invoking onKey. PressPopIME's
                        // handleSpace() reads typedWord to decide the
                        // correction — clearing it first meant the word
                        // was always empty there, so autocorrect never
                        // ran at all. Clear it after, as before.
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onKey?.invoke(key)
                        typedWord = ""
                        noteTypedWordChange()
                    }
                    KeyAction.DELETE -> {
                        typedWord = typedWord.dropLast(1)
                        noteTypedWordChange()
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onKey?.invoke(key)
                    }
                    KeyAction.ENTER -> {
                        // Same reason as Space: PressPopIME learns the
                        // word on Enter, so it must still be present
                        // when onKey runs. Clear it after.
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onKey?.invoke(key)
                        typedWord = ""
                        noteTypedWordChange()
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(if (darkMode) Color.parseColor("#1F2937") else Color.parseColor("#D1D5DB"))

        for ((key, rect) in keyRects) {
            val isPressed = key == pressedKey
            keyPaint.color = when {
                key.action == KeyAction.SHIFT && shiftOn -> Color.parseColor("#3B82F6")
                isPressed -> if (darkMode) Color.parseColor("#4B5563") else Color.parseColor("#E5E7EB")
                key.action in listOf(KeyAction.SHIFT, KeyAction.DELETE, KeyAction.MODE, KeyAction.ENTER) ->
                    if (darkMode) Color.parseColor("#4B5563") else Color.parseColor("#AEB3BA")
                else -> if (darkMode) Color.parseColor("#374151") else Color.WHITE
            }
            canvas.drawRoundRect(rect, dp(6f), dp(6f), keyPaint)
            textPaint.color = when {
                key.action == KeyAction.SHIFT && shiftOn -> Color.WHITE
                darkMode -> Color.WHITE
                else -> Color.BLACK
            }
            textPaint.textSize = when {
                mode == KeyboardMode.EMOJI && key.action == KeyAction.CHAR -> dp(24f)
                key.label.length > 2 -> dp(15f)
                else -> dp(22f)
            }
            // The return arrow (⏎) is a thin outline glyph, so in
            // regular weight it reads much lighter than the letters.
            // Action keys (enter, shift, delete, mode) are bold, so
            // their symbols match the letters in visual weight.
            textPaint.typeface = if (key.action in listOf(
                    KeyAction.ENTER, KeyAction.SHIFT,
                    KeyAction.DELETE, KeyAction.MODE
                )
            ) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(key.label, rect.centerX(), y, textPaint)
        }

        // Swipe trail.
        if (swipePoints.size > 1) {
            for (i in 1 until swipePoints.size) {
                canvas.drawLine(
                    swipePoints[i - 1].x, swipePoints[i - 1].y,
                    swipePoints[i].x, swipePoints[i].y, trailPaint
                )
            }
        }

        // Long-press accent picker row, above the pressed key.
        if (variantMode && pressedRect != null && variantList.isNotEmpty()) {
            val rect = pressedRect!!
            val variantW = rect.width()
            val totalW = variantW * variantList.size
            val startX = (rect.centerX() - totalW / 2).coerceIn(0f, width - totalW)
            val top = rect.top - rect.height() - dp(10f)
            for ((i, variant) in variantList.withIndex()) {
                val vRect = RectF(
                    startX + i * variantW, top,
                    startX + (i + 1) * variantW - gap, top + rect.height()
                )
                keyPaint.color = if (i == variantSelected) {
                    Color.parseColor("#3B82F6")
                } else if (darkMode) Color.parseColor("#374151") else Color.WHITE
                canvas.drawRoundRect(vRect, dp(6f), dp(6f), keyPaint)
                textPaint.color = if (i == variantSelected) Color.WHITE
                    else if (darkMode) Color.WHITE else Color.BLACK
                textPaint.textSize = dp(22f)
                val y = vRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(variant, vRect.centerX(), y, textPaint)
            }
        }

        // Popup bubble above the pressed key - for EVERY key.
        val key = pressedKey
        val rect = pressedRect
        if (key != null && rect != null && !cursorMode && !variantMode) {
            val popupW = maxOf(rect.width() * 1.25f, dp(56f))
            val popupH = rect.height() * 1.45f
            val left = (rect.centerX() - popupW / 2).coerceIn(0f, width - popupW)
            val popup = RectF(left, rect.top - popupH - dp(4f), left + popupW, rect.top + dp(8f))
            popupPaint.color = if (darkMode) Color.parseColor("#374151") else Color.WHITE
            canvas.drawRoundRect(popup, dp(10f), dp(10f), popupPaint)
            popupTextPaint.color = if (darkMode) Color.WHITE else Color.BLACK
            popupTextPaint.textSize =
                if (mode == KeyboardMode.EMOJI && key.action == KeyAction.CHAR) dp(44f) else dp(32f)
            val y = popup.top + popupH * 0.55f -
                (popupTextPaint.descent() + popupTextPaint.ascent()) / 2
            canvas.drawText(key.popupLabel, popup.centerX(), y, popupTextPaint)
        }
        // Space-cursor indicator.
        if (cursorMode) {
            textPaint.textSize = dp(14f)
            textPaint.color = Color.parseColor("#3B82F6")
            canvas.drawText("◀ cursor ▶ ▲▼", width / 2f, dp(18f), textPaint)
        }
    }
}
