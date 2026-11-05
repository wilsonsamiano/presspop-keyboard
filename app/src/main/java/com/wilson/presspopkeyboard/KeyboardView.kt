package com.wilson.presspopkeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.PointF
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * PressPop keyboard view, v2.
 *
 * Features in this one view:
 * - iPhone-style key popups on EVERY key (letters, space, shift,
 *   delete, 123, enter, emoji). Press, slide, lift to commit.
 * - Plain nearest-key taps, Nov 2, 2026: the old predictive
 *   hit widening was removed (it stole near-miss taps from
 *   the key Wilson actually aimed at). A tap inside a key
 *   wins; a tap in a gap goes to the nearest key, with no
 *   boost for predicted letters or space.
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
    var onDeleteWord: (() -> Unit)? = null
    // Code 36, swipe research: previous word before the
    // cursor, set by PressPopIME, for bigram rescoring.
    var previousWord: String = ""
    private var downAction: KeyAction? = null
    private var downTimeMs: Long = 0L
    // Code 39 (Wilson, Nov 3, 2026): two-thumb ghost swipe.
    // The view never tracked fingers separately, so when a
    // second thumb landed while the first was still down,
    // the second thumb's movement was appended to the
    // first thumb's trace — the keyboard read the jump
    // from one thumb to the other as one long swipe and
    // committed a ghost word. activePointerId is the one
    // finger a gesture belongs to; multiTouchBlocked is
    // set the moment a second finger lands and stays set
    // until that gesture ends, so an overlapped gesture
    // can never become a swipe, whatever its path is.
    private var activePointerId: Int = -1
    private var multiTouchBlocked: Boolean = false
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

    // Key tap pop sound, Oct 29, 2026 (Wilson: no sound at all before
    // this — haptics only). A short synthesized pop in res/raw, played
    // through SoundPool on every tap commit, so it sounds like a pop
    // rather than the system click. On by default; the app's setting
    // ("key_sounds") can turn it off, and PressPopIME refreshes this
    // flag whenever the keyboard opens.
    var keySoundsOn: Boolean = true

    // In-keyboard debug banner, Oct 29, 2026: Toast popups never
    // showed on Wilson's onn tablet while typing — first hidden
    // behind the keyboard (fixed by moving them to the top), then
    // still nothing at all on code 10, even with debug on and the
    // file-based flag in place. A Toast is a separate system
    // window, so the keyboard cannot guarantee it appears. This
    // banner is drawn inside the keyboard view itself, so if the
    // keyboard is on screen, the debug line is on screen. It
    // shows for 5 seconds, then clears itself.
    private var debugLine: String? = null
    private val clearDebug = Runnable {
        debugLine = null
        layoutKeys()
        invalidate()
    }

    /** Code 26: set by PressPopIME on every new field.
     * When true, no debug line is recorded at all, so a
     * swipe trace in a password field can never name
     * the password in the Copy-debug readout. */
    var suppressDebug: Boolean = false

    fun showDebug(message: String) {
        // Popup REMOVED Oct 29, 2026 (Wilson): the banner was in
        // the way while typing, and the "Copy last debug line"
        // button in the app already captures the line, so no
        // on-screen popup is needed at all. This just records the
        // line for that button, and shows nothing. debugLine stays
        // null, so layoutKeys() never shifts the keys for a banner.
        if (suppressDebug) return
        if (!DebugMode.isOn(context)) return
        DebugMode.record(message)
    }

    private var soundPool: SoundPool? = null
    private var popSoundId: Int = 0
    private var popLoaded: Boolean = false

    private fun initPopSound() {
        if (soundPool != null) return
        try {
            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    popSoundId = sampleId
                    popLoaded = true
                }
            }
            soundPool?.load(context, R.raw.key_pop, 1)
        } catch (e: Exception) {
            // No sound is better than a crash: haptics still work.
            soundPool = null
        }
    }

    private fun playKeySound() {
        if (!keySoundsOn) return
        try {
            if (soundPool == null) initPopSound()
            if (popLoaded && popSoundId != 0) {
                // Half volume, Oct 29, 2026: the first pop was a
                // loud clipped tone and the second a spacey sine.
                // Wilson wants a TAP, one register lower: the wav
                // is now a short 325 Hz tap with a low thump and a
                // brief contact click, played at 0.5, not 1.0.
                soundPool?.play(popSoundId, 0.5f, 0.5f, 0, 0, 1f)
            }
        } catch (e: Exception) {
            // Silent fallback: a sound failure must never break typing.
        }
    }

    override fun onDetachedFromWindow() {
        // Code 31: also dismiss any key popup,
        // so no bubble is left behind on detach.
        dismissKeyPopup()
        try { soundPool?.release() } catch (e: Exception) { }
        soundPool = null
        popLoaded = false
        super.onDetachedFromWindow()
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

    /** True if any letter of the current word was typed uppercase.
     * Code 24: a capitalized unknown word is likely a proper
     * name (Guinard) or an acronym (SFC), not a typo — see
     * PressPopIME.handleSpace, which skips autocorrect
     * for such words. Cleared whenever the word clears. */
    var typedWordHadUpper: Boolean = false
        private set

    fun setTypedWord(word: String) {
        typedWord = word.lowercase()
        if (word.isEmpty()) typedWordHadUpper = false
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

    // Flick state, Nov 2, 2026: the key the finger
    // started on, and the secondary symbol it flicked
    // to, if any. A flick is a short, mostly-vertical
    // pull down on one letter key (see ACTION_MOVE):
    // it must NOT turn into a glide word, and it must
    // NOT trigger the long-press accent picker.
    private var downKey: Key? = null
    private var flickSecondary: String? = null
    // How far a downward pull has revealed the secondary
    // symbol, 0..1. iPhone's keyboard (the iPad QuickType
    // pull) slides the keycap down: the letter leaves and
    // the small grey symbol grows into the primary glyph.
    private var flickPull = 0f

    // Code 31 (Wilson, Oct 29, 2026): key popups are
    // now their own PopupWindow, not canvas drawing.
    // The canvas version had to clamp a top-row
    // popup to the top edge of this view, so a top
    // number-row key's popup landed ON the key
    // itself, under the finger, which defeats the
    // purpose. A PopupWindow sits above the key,
    // over the strip / app area, so every row —
    // including the top number row — pops visibly
    // above the pressed key. It also removes itself
    // cleanly on dismiss, so no ghost bubble is
    // left over the strip.
    // Code 32 (Wilson, Oct 30, 2026): the code 31
    // popup used showAtLocation() with SCREEN
    // coordinates, but a PopupWindow from an IME
    // window treats those as relative to the
    // keyboard window, so every popup was clipped
    // to the BOTTOM of the keyboard — exactly what
    // Wilson reported on v31 ("all the popping is
    // happening on the bottom of the keyboard").
    // showAsDropDown() is anchor-relative, so
    // Android positions it from the key itself,
    // and that coordinate confusion cannot happen.
    private var keyPopup: PopupWindow? = null

    private fun dismissKeyPopup() {
        try { keyPopup?.dismiss() } catch (e: Exception) { }
        keyPopup = null
    }

    private fun showKeyPopup() {
        val key = pressedKey
        val rect = pressedRect
        if (key == null || rect == null || cursorMode || variantMode) {
            dismissKeyPopup()
            return
        }
        try {
            dismissKeyPopup()
            val density = resources.displayMetrics.density
            val bubble = TextView(context).apply {
                // While flicking, the popup shows the
                // secondary symbol, not the letter —
                // otherwise the popup would say "q"
                // while a "1" is about to be typed.
                text = flickSecondary ?: key.popupLabel
                textSize =
                    if (mode == KeyboardMode.EMOJI && key.action == KeyAction.CHAR) 44f else 32f
                gravity = Gravity.CENTER
                setSingleLine(true)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    // Same shape as the main keys, Nov 2, 2026:
                    // 8dp corners (was 10dp), so the popup
                    // above a pressed key reads as a larger
                    // version of that key, LeanType-style.
                    cornerRadius = 8 * density
                    setColor(
                        if (darkMode) Color.parseColor("#374151")
                        else Color.WHITE
                    )
                }
                setTextColor(if (darkMode) Color.WHITE else Color.BLACK)
            }
            val popupW = maxOf(rect.width() * 1.25f, dp(56f))
            val popupH = rect.height() * 1.45f + dp(12f)
            // Anchor-relative, code 32: showAsDropDown
            // positions the popup from this view's
            // bottom-left corner, so xOff is the
            // popup's left edge in view coordinates,
            // and yOff is its top edge minus this
            // view's height. Above the key by
            // default; below it only if above would
            // run off the top of the screen.
            val left = (rect.centerX() - popupW / 2)
                .coerceIn(0f, (width - popupW).coerceAtLeast(0f))
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val screenTop = loc[1] + rect.top - popupH - dp(4f)
            val yOff = if (screenTop >= 0) {
                (rect.top - popupH - dp(4f) - height).toInt()
            } else {
                (rect.bottom + dp(4f) - height).toInt()
            }
            val popup = PopupWindow(
                bubble, popupW.toInt(), popupH.toInt(), false
            )
            popup.isClippingEnabled = false
            popup.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            keyPopup = popup
            popup.showAsDropDown(this, left.toInt(), yOff)
            // Secondary reveal: the symbol slides up into
            // the bubble and settles with a small overshoot,
            // the same pop iOS uses when the alternate
            // character takes over the preview.
            if (flickSecondary != null) {
                bubble.pivotX = popupW / 2f
                bubble.pivotY = popupH / 2f
                bubble.translationY = dp(14f)
                bubble.scaleX = 0.82f
                bubble.scaleY = 0.82f
                bubble.alpha = 0.55f
                bubble.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(170)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                    .start()
            }
        } catch (e: Exception) {
            keyPopup = null
        }
    }


    // Swipe state
    private val swipePoints = mutableListOf<PointF>()
    private val swipeTimes = mutableListOf<Long>()
    private val visitedLetters = mutableListOf<String>()
    // Last shape-match score, in dp, for the debug line. Set by
    // shapeMatch() so the next debug line shows not just which
    // word won, but by how much — otherwise a wrong winner like
    // hell-over-hello cannot be told apart from a close call.
    var lastShapeScoreDp: Int? = null
        private set
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
                dismissKeyPopup()
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
        // Flick secondary symbols, Nov 2, 2026 (Wilson):
        // the little symbol in the corner of each letter
        // key, typed by pulling DOWN on that key, without
        // switching to the 123 page. This is the
        // Gboard / iPad flick gesture, and the same
        // hint idea HeliBoard / LeanType use: a small
        // symbol shown on the key itself. Top row is
        // the digits, lower rows are the common
        // punctuation for that letter. The map is
        // drawn on the keys (see onDraw), so it does
        // not have to be memorised. Only Letters
        // mode has flick secondaries; 123 / #+= keys
        // already are the symbol, so they need none.
        val SECONDARY = mapOf(
            'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
            'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0",
            'a' to "@", 's' to "$", 'd' to "#", 'f' to "%", 'g' to "&",
            'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")",
            'z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":",
            'b' to ";", 'n' to "!", 'm' to "?"
        )

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
            dismissKeyPopup()
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

    private fun mixColor(from: Int, to: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        return Color.argb(
            255,
            (Color.red(from) + (Color.red(to) - Color.red(from)) * u).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * u).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * u).toInt()
        )
    }

    private fun charKey(c: Char): Key {
        val shown = if (shiftOn) c.uppercaseChar().toString() else c.toString()
        return Key(shown, shown, KeyAction.CHAR, shown)
    }

    // Code 39 (Wilson, Nov 3, 2026): the emoji key was too
    // "out there" on the bottom row — first the grinning face
    // was swapped for a calmer smile, then Wilson asked for
    // smaller and monochrome. So the key now shows U+263A
    // WHITE SMILING FACE with U+FE0E (text presentation,
    // forcing a monochrome outline instead of a colour
    // emoji), drawn smaller than the letter keys (see
    // onDraw). The emoji page itself is unchanged — the
    // colour emoji are still all there once the page opens.
    private fun emojiKey(): Key = Key("☺︎", "☺︎", KeyAction.MODE, mode = KeyboardMode.EMOJI, weight = 0.9f)

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
                    // Oct 29, 2026 (Wilson): comma and emoji swapped,
                    // and the mic moved off the bottom row to the top
                    // strip — so the bottom row is now 123, comma,
                    // emoji, space, period, enter. The mic's 0.8
                    // weight went back to the spacebar, so the period
                    // and enter keys end up exactly where they were.
                    Key(",", ",", KeyAction.CHAR, ",", weight = 1.0f),
                    emojiKey(),
                    Key("space", "space", KeyAction.SPACE, weight = 4.0f),
                    Key(".", ".", KeyAction.CHAR, ".", weight = 1.0f),
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
                    Key("🔢", "🔢", KeyAction.MODE, mode = KeyboardMode.NUMPAD, weight = 1.0f),
                    Key("space", "space", KeyAction.SPACE, weight = 3.2f),
                    Key(".", ".", KeyAction.CHAR, ".", weight = 1.0f),
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
                    Key("space", "space", KeyAction.SPACE, weight = 4.0f),
                    Key(".", ".", KeyAction.CHAR, ".", weight = 1.0f),
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
                    Key("space", "space", KeyAction.SPACE, weight = 2.0f),
                    Key("⌫", "⌫", KeyAction.DELETE, weight = 1.5f),
                    Key(".", ".", KeyAction.CHAR, ".", weight = 1.0f),
                    Key("⏎", "⏎", KeyAction.ENTER, weight = 1.5f)
                )
            )
            KeyboardMode.EMOJI -> listOf(
                EMOJIS.subList(0, 10).map { Key(it, it, KeyAction.CHAR, it) },
                EMOJIS.subList(10, 20).map { Key(it, it, KeyAction.CHAR, it) },
                EMOJIS.subList(20, 30).map { Key(it, it, KeyAction.CHAR, it) },
                listOf(
                    Key("ABC", "ABC", KeyAction.MODE, mode = KeyboardMode.LETTERS, weight = 1.5f),
                    Key("space", "space", KeyAction.SPACE, weight = 3.8f),
                    Key("⌫", "⌫", KeyAction.DELETE, weight = 1.5f),
                    Key(".", ".", KeyAction.CHAR, ".", weight = 1.0f),
                    Key("⏎", "⏎", KeyAction.ENTER, weight = 1.5f)
                )
            )
        }
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width == 0) return
        // LeanType-style geometry, Nov 2, 2026 (Wilson):
        // tighter 4dp gaps between keys (was 6dp), so the
        // keys themselves fill more of the keyboard and
        // the layout reads like LeanType/AOSP, not like
        // widely spaced iPhone-style keys.
        gap = dp(4f)
        // Fixed-height keyboard, Oct 29, 2026: the view used to
        // measure to its current row count, so switching Letters
        // (5 rows with the number row on) to 123 (4 rows) shrank the
        // whole keyboard by one row, the app above it jumped, and
        // the screen felt like it was flickering. Now the view is
        // always 5 rows tall (see onMeasure) and the rows stretch to
        // fill it, so a mode switch never resizes the window — keys
        // just get a little taller in 4-row modes, and the bottom
        // row never moves.
        // Debug banner offset, Oct 29, 2026 (Wilson): the banner
        // used to draw over the top of the keyboard, covering the
        // top row and hiding the letter popups that rise above it.
        // Now, while a debug line is showing, the keys shift down
        // below the banner and shrink slightly to fit, so the banner
        // never sits on top of a key. Letter popups are drawn after
        // the banner in onDraw, so they draw on top of it. When the
        // banner clears, layoutKeys() runs again and the keys move
        // back to their normal positions.
        val debugOffset = if (debugLine != null) dp(30f) else 0f
        rowHeight = if (height > 0) {
            (height - debugOffset - gap * (rows.size + 1)) / rows.size
        } else {
            dp(52f)
        }
        val rects = mutableListOf<KeyRect>()
        // Bottom-anchor the rows: if this view is still at its old
        // (taller) height when a mode with fewer rows is drawn, the
        // bottom row — and the spacebar in it — stays at the bottom
        // instead of floating one row up. When the height matches the
        // rows exactly, this is the same as starting at `gap`.
        val rowsHeight = rowHeight * rows.size + gap * (rows.size + 1) + debugOffset
        var top = if (height > rowsHeight) height - rowsHeight + gap + debugOffset else gap + debugOffset
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
        val h = (dp(52f) * 5 + dp(4f) * 6).roundToInt()
        setMeasuredDimension(w, h)
    }

    /**
     * Plain nearest-key hit test, reverted Nov 2, 2026.
     *
     * History: Oct 28-29, 2026 added predictive widening —
     * predicted next letters got a 1.7x distance advantage
     * and space got an unconditional 2.0x advantage, to
     * catch taps in the gap between "v"/"b" and space.
     * Wilson reported Nov 2, 2026 that the widening made
     * typing WORSE: a precise tap could be stolen by the
     * predicted key or by space. Lesson, generalized:
     * never let a prediction override the key the user
     * actually aimed at on a near-miss.
     *
     * So this is now what LeanType/AOSP does: the key
     * whose visible rect is nearest the touch wins, with
     * no boost for any key. A tap inside a key still has
     * distance 0 and wins outright; a tap in a gap goes
     * to the nearest key, not the predicted one.
     * Do NOT re-add a boost here without Wilson's word.
     */
    private fun keyAt(x: Float, y: Float): KeyRect? {
        if (keyRects.isEmpty()) return null
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
            if (dist < bestScore) {
                bestScore = dist
                best = kr
            }
        }
        // Accept only if reasonably near a key.
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

    // Whole-path shape matching, Oct 29, 2026 (Wilson: letter-by-
    // letter fixing would take years). The old matcher turned the
    // finger path into a letter sequence and then looked that
    // sequence up — so any key the finger crossed became a letter,
    // and any intended key it missed broke the word. That is why
    // "that" became "they" (the trace ended on y), "hello" became
    // "hgfdghjklo" (e was never recorded), and "trying" became
    // "trtyujnbhg" (i was never recorded). A working swipe keyboard
    // does not do that: it compares the SHAPE of the whole path to
    // the shape of each candidate word's key-center path. So this
    // resamples both the actual path and each candidate's key-center
    // path to 32 points and scores by mean distance between
    // corresponding points. A wandering finger, a crossed key, or a
    // missed key raises the distance a little, but does not destroy
    // the match the way a wrong letter sequence does. Threshold
    // 45dp: about two-thirds of a key width on this tablet.
    private fun resamplePath(points: List<PointF>, n: Int): List<PointF> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return List(n) { points[0] }
        var total = 0f
        for (i in 1 until points.size) {
            total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        if (total == 0f) return List(n) { points[0] }
        val step = total / (n - 1)
        val out = mutableListOf<PointF>()
        out.add(points[0])
        var dist = 0f
        var idx = 1
        while (out.size < n - 1 && idx < points.size) {
            val seg = hypot(points[idx].x - points[idx - 1].x, points[idx].y - points[idx - 1].y)
            if (dist + seg >= step * out.size) {
                val need = step * out.size - dist
                val ratio = if (seg == 0f) 0f else need / seg
                out.add(PointF(
                    points[idx - 1].x + (points[idx].x - points[idx - 1].x) * ratio,
                    points[idx - 1].y + (points[idx].y - points[idx - 1].y) * ratio
                ))
            } else {
                dist += seg
                idx++
            }
        }
        while (out.size < n) out.add(points.last())
        return out
    }

    // Point-to-segment distance, for Chamfer shape scoring.
    private fun pointToSegmentDistance(
        px: Float, py: Float,
        ax: Float, ay: Float, bx: Float, by: Float
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq == 0f) 0f else {
            ((px - ax) * dx + (py - ay) * dy) / lenSq
        }.coerceIn(0f, 1f)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return hypot(px - cx, py - cy)
    }

    // Symmetric Chamfer distance between a swipe path and a
    // candidate word's key-center path: mean distance from each
    // actual point to the nearest candidate segment, averaged
    // with mean distance from each candidate center to the
    // nearest actual segment. Unlike point-by-point scoring,
    // a wandering loop or an extra curve does not shift every
    // later point out of alignment — it only adds distance
    // where the loop actually goes. That is what makes a fast,
    // sloppy swipe still land, instead of requiring a slow,
    // precise trace (Wilson, Oct 29, 2026: slow swiping
    // defeats the purpose of swipe).
    private fun chamferScore(
        actual: List<PointF>, candPath: List<PointF>
    ): Float {
        if (actual.isEmpty() || candPath.isEmpty()) return Float.MAX_VALUE
        var sumActual = 0f
        for (pt in actual) {
            var best = Float.MAX_VALUE
            for (i in 1 until candPath.size) {
                val d = pointToSegmentDistance(
                    pt.x, pt.y,
                    candPath[i - 1].x, candPath[i - 1].y,
                    candPath[i].x, candPath[i].y
                )
                if (d < best) best = d
            }
            if (candPath.size == 1) best = hypot(pt.x - candPath[0].x, pt.y - candPath[0].y)
            sumActual += best
        }
        var sumCand = 0f
        for (pt in candPath) {
            var best = Float.MAX_VALUE
            for (i in 1 until actual.size) {
                val d = pointToSegmentDistance(
                    pt.x, pt.y,
                    actual[i - 1].x, actual[i - 1].y,
                    actual[i].x, actual[i].y
                )
                if (d < best) best = d
            }
            sumCand += best
        }
        return (sumActual / actual.size + sumCand / candPath.size) / 2f
    }

    // Code 36, swipe research (Wilson, Oct 30, 2026:
    // "implement all these findings"):
    // SHARK2 pipeline, in general terms, built on
    // the existing Chamfer + ordered scorer, not
    // a new model and no proprietary code:
    // - Key-width units, not dp: thresholds scale
    //   with the actual key size on this device,
    //   so the same swipe works on a tablet and
    //   on a small phone. Key width is the median
    //   CHAR key width from the live layout, so
    //   the scorer is layout-agnostic (FUTO's
    //   general idea, without any neural model).
    // - Path-length gate: a candidate whose ideal
    //   path is far longer or far shorter than
    //   the actual path is pruned before scoring.
    //   This stops long rare words beating short
    //   common ones on shape alone.
    // - Speed weighting (SHARK2 dynamic weighting):
    //   a fast stroke is spatially sloppy, so the
    //   ordered/location term is down-weighted
    //   when the swipe is fast, and shape (Chamfer)
    //   carries more weight.
    // - N-best rescoring: the top 8 geometry
    //   candidates are rescored with frequency,
    //   personal-word, bigram, and double-letter
    //   boosts, instead of a single 10dp frequency
    //   tie-break. Geometry still gates: a word
    //   with bad geometry cannot win on boosts.
    // - Double-letter dwell: a pause of 180ms or
    //   more on a middle key (not the first or
    //   last key, where a pause is just starting
    //   or lifting) is weak evidence that key is
    //   doubled, and candidates with that double
    //   get a boost. This is SwiftKey's dwell
    //   idea in general terms; no loop gesture
    //   is required, and the dictionary layer
    //   (collapsed doubles in the template) still
    //   works without a dwell.
    // - Soft key proximity is already the Chamfer
    //   term: distance to the nearest segment, not
    //   a hard key sequence, so a missed or crossed
    //   key only adds distance, it does not break
    //   the word.
    // NOT in this build, deliberately, per the
    // research's own ranking: a neural decoder
    // (needs data and a trained model first) and
    // true space-through multi-word swiping
    // (needs trace segmentation, only SwiftKey
    // documents a shipped version, no public
    // algorithm found). Auto-space on lift is
    // already in PressPopIME, and is the v1
    // behaviour for multi-word input.
    private fun shapeMatch(): String? {
        if (swipePoints.size < 2) return null
        val centers = mutableMapOf<String, PointF>()
        for ((key, rect) in keyRects) {
            if (key.action == KeyAction.CHAR && key.output.length == 1) {
                centers.putIfAbsent(key.output.lowercase(), PointF(rect.centerX(), rect.centerY()))
            }
        }
        if (centers.isEmpty()) return null
        // Key width: median CHAR key width, px.
        // keyRects is a List<KeyRect>, not a Map: no .entries,
        // and a KeyRect's rect is .rect, not .value.
        val keyWidths = keyRects
            .filter { it.key.action == KeyAction.CHAR && it.key.output.length == 1 }
            .map { it.rect.width().toFloat() }.sorted()
        val keyW = if (keyWidths.isNotEmpty()) keyWidths[keyWidths.size / 2] else dp(60f)
        if (keyW <= 0f) return null
        val first = swipePoints[0]
        var startLetter: String? = null
        var startDist = Float.MAX_VALUE
        for ((letter, center) in centers) {
            val d = hypot(first.x - center.x, first.y - center.y)
            if (d < startDist) { startDist = d; startLetter = letter }
        }
        if (startDist > keyW * 0.9f) startLetter = null
        val lift = swipePoints.last()
        var liftLetter: String? = null
        var liftDist = Float.MAX_VALUE
        for ((letter, center) in centers) {
            val d = hypot(lift.x - center.x, lift.y - center.y)
            if (d < liftDist) { liftDist = d; liftLetter = letter }
        }
        if (liftDist > keyW * 0.9f) liftLetter = null
        fun smoothOnce(pts: List<PointF>): List<PointF> {
            if (pts.size < 3) return pts
            val out = mutableListOf<PointF>()
            out.add(pts[0])
            for (i in 1 until pts.size - 1) {
                out.add(PointF(
                    (pts[i - 1].x + pts[i].x * 2f + pts[i + 1].x) / 4f,
                    (pts[i - 1].y + pts[i].y * 2f + pts[i + 1].y) / 4f
                ))
            }
            out.add(pts.last())
            return out
        }
        val smoothed = smoothOnce(smoothOnce(swipePoints))
        val actual = resamplePath(smoothed, 32)
        var actualLen = 0f
        for (i in 1 until smoothed.size) {
            actualLen += hypot(smoothed[i].x - smoothed[i - 1].x, smoothed[i].y - smoothed[i - 1].y)
        }
        if (actualLen <= 0f) return null
        // Speed: path length in dp over duration in ms.
        val durationMs = if (swipeTimes.size >= 2) {
            (swipeTimes.last() - swipeTimes.first()).coerceAtLeast(1L)
        } else 0L
        val speedDpPerMs = if (durationMs > 0L) {
            (actualLen / resources.displayMetrics.density) / durationMs.toFloat()
        } else 0f
        val fast = speedDpPerMs > 1.2f
        val orderedWeight = if (fast) 0.15f else 0.30f
        val chamferWeight = 1f - orderedWeight
        // Double-letter dwell: consecutive points near
        // the same middle key, spanning 180ms or more.
        val dwelled = mutableSetOf<String>()
        if (swipeTimes.size == swipePoints.size && swipeTimes.size >= 3) {
            var runLetter: String? = null
            var runStart = 0L
            var runLast = 0L
            fun flushRun() {
                if (runLetter != null && runLast - runStart >= 180L) {
                    dwelled.add(runLetter!!)
                }
            }
            for (i in swipePoints.indices) {
                var near: String? = null
                var nearDist = Float.MAX_VALUE
                for ((letter, center) in centers) {
                    val d = hypot(swipePoints[i].x - center.x, swipePoints[i].y - center.y)
                    if (d < nearDist) { nearDist = d; near = letter }
                }
                if (nearDist > keyW * 0.5f) near = null
                // First and last points are start/lift
                // pauses, not doubles: ignore runs that
                // touch either end of the stroke.
                if (i == 0 || i == swipePoints.size - 1) near = null
                if (near == runLetter) {
                    runLast = swipeTimes[i]
                } else {
                    flushRun()
                    runLetter = near
                    runStart = swipeTimes[i]
                    runLast = swipeTimes[i]
                }
            }
            flushRun()
        }
        // Frequency rank map, built once per swipe.
        val activeList = Dictionary.activeWords
        val freqRank = HashMap<String, Int>(activeList.size * 2)
        for (i in activeList.indices) {
            if (!freqRank.containsKey(activeList[i])) freqRank[activeList[i]] = i
        }
        // Code 37: personal words keep their casing
        // ("Angel"), so compare lowercased here;
        // the scored word itself keeps its form.
        val personalSet = personalWords.map { it.lowercase() }.toSet()
        val prev = previousWord.lowercase()
        val bigramNext = if (prev.isNotEmpty()) Dictionary.BIGRAMS[prev] ?: emptyList() else emptyList()
        data class Scored(val word: String, val geometry: Float, val rescore: Float)
        fun scoreCandidates(allowedStarts: Set<String>?): List<Scored> {
            val out = mutableListOf<Scored>()
            // Personal first, deduped by lowercase, so
            // "Angel" wins over a duplicate "angel".
            val candidates = (personalWords + Dictionary.activeWords).distinctBy { it.lowercase() }
                .filter { allowedStarts == null || it.isNotEmpty() && it.lowercase()[0].toString() in allowedStarts }
            for (cand in candidates) {
                if (cand.length < 2) continue
                val path = mutableListOf<PointF>()
                var ok = true
                // Code 37: apostrophes have no key on
                // the letters layout, so a candidate
                // like "we'll" must not fail here.
                // Skip non-letters in the template:
                // "we'll" then templates as w-e-l-l,
                // exactly the trace a user swipes,
                // and can compete with "well" on
                // geometry, frequency, and bigram.
                for (c in cand.lowercase()) {
                    if (!c.isLetter()) continue
                    val center = centers[c.toString()]
                    if (center == null) { ok = false; break }
                    if (path.isEmpty() || hypot(center.x - path.last().x, center.y - path.last().y) > 1f) {
                        path.add(center)
                    }
                }
                if (!ok || path.size < 2) continue
                var candLen = 0f
                for (i in 1 until path.size) {
                    candLen += hypot(path[i].x - path[i - 1].x, path[i].y - path[i - 1].y)
                }
                // Path-length gate, loose on the long
                // side (a wandering finger lengthens
                // the actual path) and tighter on the
                // short side (a short actual path
                // cannot be a long word).
                if (candLen > actualLen * 1.6f || candLen < actualLen * 0.25f) continue
                val orderedPath = resamplePath(path, 32)
                var orderedSum = 0f
                for (i in actual.indices) {
                    orderedSum += hypot(actual[i].x - orderedPath[i].x, actual[i].y - orderedPath[i].y)
                }
                val orderedScore = orderedSum / actual.size
                val geometry = chamferScore(actual, path) * chamferWeight + orderedScore * orderedWeight
                var rescore = geometry
                if (cand.lowercase() in personalSet) rescore -= keyW * 0.30f
                val rank = freqRank[cand.lowercase()]
                if (rank != null && rank < 500) rescore -= keyW * 0.25f
                else if (rank != null && rank < 4000) rescore -= keyW * 0.12f
                if (cand.lowercase() in bigramNext) rescore -= keyW * 0.30f
                for (dl in dwelled) {
                    if (cand.lowercase().contains(dl + dl)) { rescore -= keyW * 0.25f; break }
                }
                out.add(Scored(cand, geometry, rescore))
            }
            return out.sortedBy { it.geometry }.take(8)
        }
        fun pickFrom(scored: List<Scored>): Scored? {
            if (scored.isEmpty()) return null
            val thresholdNormal = keyW * 0.90f
            val thresholdLift = keyW * 1.05f
            // Geometry gates first: boosts never rescue
            // a word whose shape is too far off.
            val passing = scored.filter { it.geometry <= thresholdLift }
            if (passing.isEmpty()) return null
            var best = passing.minByOrNull { it.rescore } ?: return null
            if (best.geometry > thresholdNormal) {
                // Only a lift-letter word may use the
                // looser lift threshold.
                if (liftLetter == null || !best.word.endsWith(liftLetter!!)) return null
            }
            // Lift preference, graded: a lift-letter
            // candidate within 0.45 key widths of the
            // best rescore wins, as before, but now
            // on the rescored list, not geometry alone.
            if (liftLetter != null) {
                val liftCand = passing.filter { it.word.endsWith(liftLetter!!) }
                    .minByOrNull { it.rescore }
                if (liftCand != null && liftCand != best &&
                    liftCand.rescore <= best.rescore + keyW * 0.45f &&
                    liftCand.geometry <= thresholdLift
                ) {
                    best = liftCand
                }
            }
            return best
        }
        val firstStarts = if (startLetter == null) null else setOf(startLetter)
        var picked = pickFrom(scoreCandidates(firstStarts))
        if (picked == null && startLetter != null) {
            val neighbors = centers.entries
                .filter { hypot(it.value.x - first.x, it.value.y - first.y) <= keyW * 1.2f }
                .sortedBy { hypot(it.value.x - first.x, it.value.y - first.y) }
                .map { it.key }.filter { it != startLetter }.take(3).toSet()
            if (neighbors.isNotEmpty()) {
                val picked2 = pickFrom(scoreCandidates(neighbors))
                // We are inside `picked == null`, so picked2
                // always wins here; no rescore comparison
                // against a null picked is needed.
                if (picked2 != null) {
                    picked = picked2
                }
            }
        }
        lastShapeScoreDp = if (picked != null) {
            (picked.geometry / resources.displayMetrics.density).roundToInt()
        } else null
        if (picked == null) return null
        val thresholdNormal = keyW * 0.90f
        val thresholdLift = keyW * 1.05f
        val okThreshold = picked.geometry <= thresholdNormal ||
            (liftLetter != null && picked.word.endsWith(liftLetter!!) && picked.geometry <= thresholdLift)
        return if (okThreshold) picked.word else null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                multiTouchBlocked = false
                downPoint.set(event.x, event.y)
                swipePoints.clear()
                swipeTimes.clear()
                swipePoints.add(PointF(event.x, event.y))
                swipeTimes.add(event.eventTime)
                downTimeMs = event.eventTime
                visitedLetters.clear()
                cursorMode = false
                spaceHeld = false
                val hit = keyAt(event.x, event.y)
                pressedKey = hit?.key
                pressedRect = hit?.rect
                downAction = hit?.key?.action
                // Flick tracking starts here, and only
                // here: downKey is the one key a flick
                // can come from, whatever key the
                // finger later slides over.
                downKey = if (mode == KeyboardMode.LETTERS &&
                    hit?.key?.action == KeyAction.CHAR
                ) hit.key else null
                flickSecondary = null
                flickPull = 0f
                showKeyPopup()
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
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second thumb lands while the first is still
                // down: this gesture is now two thumbs, not a
                // swipe. Freeze it — later moves are ignored
                // (see ACTION_MOVE), and the wasSwipe check
                // in ACTION_UP is forced false while this
                // flag is set. The second thumb's own tap
                // is not started here; see ACTION_POINTER_UP
                // for the trade-off that follows from that.
                multiTouchBlocked = true
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedId = event.getPointerId(event.actionIndex)
                if (liftedId != activePointerId) {
                    // The other thumb lifted; the first
                    // gesture stays blocked until it ends.
                    return true
                }
                // The tracked thumb lifted while another
                // thumb is still down. Its tap cannot be
                // committed through the normal ACTION_UP
                // path (that event only fires when the
                // last finger lifts), so this gesture ends
                // with nothing committed rather than a
                // ghost word — one lost tap in an overlap
                // is far less damage than a wrong word
                // plus a trailing space. The remaining
                // thumb's lift will also commit nothing,
                // because its DOWN was never recorded;
                // it starts clean on its next DOWN.
                activePointerId = -1
                multiTouchBlocked = false
                dismissKeyPopup()
                pressedKey = null
                pressedRect = null
                flickSecondary = null
                flickPull = 0f
                downKey = null
                swipePoints.clear()
                swipeTimes.clear()
                visitedLetters.clear()
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(spaceCursorStart)
                deleteRepeating = false
                handler.removeCallbacks(deleteRepeat)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Frozen while a second thumb is down:
                // no tracing, no flick, no cursor move.
                // Without this, the second thumb's
                // position would extend the first
                // thumb's trace into a ghost swipe.
                if (multiTouchBlocked) return true
                // Flick to a secondary symbol, Nov 2, 2026:
                // pull DOWN on a letter key to type the
                // small symbol in its corner. Gated hard
                // so a glide word cannot become a flick
                // by accident: the pull must be short
                // (under 350 ms), mostly vertical (down
                // more than 2.5x the sideways drift, and
                // at least 30dp down), and it must start
                // on a letter key with no second letter
                // traced yet. A glide to the letter in
                // the row below is diagonal (its row is
                // inset by about half a key), so it fails
                // the vertical test and traces normally.
                // Code 39 (Wilson, Nov 3, 2026): the
                // pull was 18dp, and a two-thumb tap
                // roll on a phone reached it by
                // accident, turning plain taps into
                // symbols. 30dp is a deliberate pull,
                // past a tap roll, but still well
                // inside one key's height.
                // Once a flick is set, later moves are
                // ignored: the finger stays on this
                // gesture until it lifts, it does not
                // start tracing a word or switch keys.
                if (flickSecondary != null) return true
                if (!variantMode && !cursorMode && downKey != null &&
                    visitedLetters.size <= 1
                ) {
                    val dx = event.x - downPoint.x
                    val dy = event.y - downPoint.y
                    val secondary = downKey!!.output.lowercase()
                        .firstOrNull()?.let { SECONDARY[it] }
                    // Follow the finger while it is still a
                    // downward pull, so the keycap animation
                    // plays before the flick actually commits.
                    // A sideways move is a swipe, not a pull,
                    // and the key snaps back.
                    val pulling = secondary != null &&
                        dy > 0f && dy > kotlin.math.abs(dx) * 1.5f
                    val nextPull = if (pulling) (dy / dp(30f)).coerceIn(0f, 1f) else 0f
                    if (nextPull != flickPull) {
                        flickPull = nextPull
                        invalidate()
                    }
                    if (secondary != null &&
                        dy > dp(30f) && dy > kotlin.math.abs(dx) * 2.5f &&
                        event.eventTime - downTimeMs < 350L
                    ) {
                        flickSecondary = secondary
                        flickPull = 1f
                        handler.removeCallbacks(longPressRunnable)
                        showKeyPopup()
                        invalidate()
                        return true
                    }
                }
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
                    showKeyPopup()
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
                swipeTimes.add(event.eventTime)
                if (hypot(event.x - downPoint.x, event.y - downPoint.y) > dp(20f)) {
                    spaceHeld = false
                    handler.removeCallbacks(spaceCursorStart)
                    handler.removeCallbacks(longPressRunnable)
                }
                val hit = keyAt(event.x, event.y)
                // Swipe tracing, Oct 29, 2026 (systematic fix, not
                // per-word): only record a letter when the finger is
                // actually inside that key's rectangle, because
                // keyAt() returns the NEAREST key, so recording on
                // nearest alone recorded every key passed NEAR and
                // made garbage traces. But move events alone are too
                // sparse: a fast swipe jumps 20-40dp between events
                // and can skip a whole key's rectangle, so its letter
                // never appears in the trace and the word fails to
                // match. Fixing that word by word would mean going
                // through the whole dictionary. So the segment from
                // the previous point to this one is sampled every
                // ~8dp, and every key rectangle that segment crosses
                // records its letter, in order (deduped against the
                // last one). That fixes skipped letters for every
                // word at once, instead of one word at a time.
                if (mode == KeyboardMode.LETTERS && swipePoints.size >= 2) {
                    val prev = swipePoints[swipePoints.size - 2]
                    val curr = swipePoints[swipePoints.size - 1]
                    val dist = hypot(curr.x - prev.x, curr.y - prev.y)
                    val steps = maxOf(1, (dist / dp(8f)).toInt())
                    for (i in 1..steps) {
                        val fx = prev.x + (curr.x - prev.x) * i / steps
                        val fy = prev.y + (curr.y - prev.y) * i / steps
                        val sample = keyAt(fx, fy)
                        if (sample != null &&
                            sample.key.action == KeyAction.CHAR &&
                            fx in sample.rect.left..sample.rect.right &&
                            fy in sample.rect.top..sample.rect.bottom
                        ) {
                            val letter = sample.key.output.lowercase()
                            if (visitedLetters.isEmpty() ||
                                visitedLetters.last() != letter
                            ) {
                                visitedLetters.add(letter)
                            }
                        }
                    }
                } else if (hit != null && hit.key.action == KeyAction.CHAR &&
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
                    showKeyPopup()
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
                dismissKeyPopup()
                pressedKey = null
                pressedRect = null
                // Code 39 (Wilson, Nov 3, 2026): two-thumb tap
                // typing kept turning into ghost words — a tap
                // that rolls a little into the next key cleared
                // the old bar (2 distinct letters, >50dp) and
                // shapeMatch then committed a whole word,
                // usually several in a row while typing fast.
                // A short, fast, 2-letter trace is now a tap,
                // not a swipe: it only counts as a swipe if
                // it is long (>90dp, about two and a half
                // phone keys) or slow (>=300ms, a deliberate
                // drag rather than a tap roll). That keeps
                // every 3+ letter swipe and every long
                // 2-letter swipe (to, in, me, go, and the
                // rest) unchanged; only adjacent-key pairs
                // like "we" / "as" lose fast-swipe, and
                // those are two taps for a two-thumb
                // typist anyway. A suppressed roll falls
                // through to the normal tap commit below,
                // so at worst one letter is wrong, never
                // a whole ghost word.
                val gestureDurationMs = event.eventTime - downTimeMs
                val distinctLetters = visitedLetters.distinct().size
                val tracePath = pathLength()
                val shortFastTwoLetter = distinctLetters == 2 &&
                    tracePath <= dp(90f) && gestureDurationMs < 300L
                val wasSwipe = mode == KeyboardMode.LETTERS &&
                    distinctLetters >= 2 &&
                    tracePath > dp(50f) && !shortFastTwoLetter &&
                    !multiTouchBlocked
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
                val debugShape = if (wasSwipe) shapeMatch() else null
                // Show this even when the trace is empty — that is
                // the whole point of the debug tool. The old version
                // only showed a popup when a trace existed, so when
                // tracing failed completely (the exact bug being
                // chased), the popup never happened at all and the
                // user saw nothing. An empty trace with a long path
                // is itself the diagnosis: the finger moved, but no
                // letters were recorded.
                if (debugPathDp > 20) {
                    showDebug(
                        "Swipe: trace=${debugTrace.ifEmpty { "(empty)" }} " +
                            "match=${debugMatch ?: "none"} shape=${debugShape ?: "none"} " +
                            "shapeScore=${lastShapeScoreDp?.let { "${it}dp" } ?: "none"} " +
                            "swipe=$wasSwipe distinct=${tracedLetters.distinct().size} " +
                            "path=${debugPathDp}dp"
                    )
                }
                swipePoints.clear()
                swipeTimes.clear()
                visitedLetters.clear()
                activePointerId = -1
                multiTouchBlocked = false
                invalidate()
                // Flick commit: the secondary symbol, not
                // the letter. It is punctuation or a
                // digit, so it ends the current word
                // exactly like a punctuation tap does,
                // and it never goes through autocorrect
                // as if it were a letter. A cancelled
                // gesture types nothing.
                val flick = flickSecondary
                flickSecondary = null
                flickPull = 0f
                downKey = null
                if (event.actionMasked == MotionEvent.ACTION_CANCEL || wasCursor) return true
                if (flick != null) {
                    typedWord = ""
                    typedWordHadUpper = false
                    noteTypedWordChange()
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    playKeySound()
                    onKey?.invoke(Key(flick, flick, KeyAction.CHAR, flick))
                    return true
                }
                if (wasVariant && pickedVariant != null) {
                    val variantKey = Key(pickedVariant, pickedVariant, KeyAction.CHAR, pickedVariant)
                    if (mode == KeyboardMode.LETTERS) {
                        typedWord += pickedVariant.lowercase()
                        noteTypedWordChange()
                    }
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    playKeySound()
                    onKey?.invoke(variantKey)
                    return true
                }
                // Code 36, editing gesture (Fleksy / ASK
                // idea, in general terms): a quick slide
                // left starting on Delete deletes a whole
                // word, instead of one character. Gated
                // hard so it cannot fire by accident: the
                // finger must have started on Delete, slid
                // left at least one key width, and lifted
                // in under 450ms (before delete-repeat
                // starts deleting characters on its own).
                // A slow hold on Delete keeps its old
                // repeat behaviour, unchanged.
                if (downAction == KeyAction.DELETE &&
                    event.eventTime - downTimeMs < 450L &&
                    downPoint.x - event.x > dp(60f)
                ) {
                    onDeleteWord?.invoke()
                    return true
                }
                if (wasSwipe) {
                    // Shape first, letter-sequence second, garbage
                    // never: the old short-trace fallback committed
                    // the raw trace when nothing matched, which is
                    // exactly how "hello" became "hgfdghjklo" and
                    // "trying" became "trtyujnbhg" in the text field.
                    // Now an unmatched swipe commits nothing at all —
                    // garbage in the message is worse than nothing,
                    // and the user can tap-type that word instead.
                    // Swipe is one word at a time, and onSwipeWord
                    // now adds the trailing space automatically.
                    val traced = tracedLetters.joinToString("")
                    val match = debugShape
                        ?: if (traced.isNotEmpty()) {
                            Dictionary.bestSwipeMatch(traced, personalWords)
                        } else null
                    if (match != null) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        playKeySound()
                        onSwipeWord?.invoke(match)
                    }
                    return true
                }
                if (key == null) return true
                // One pop per tap commit, for every key type — the
                // sound sits next to the haptics, not in each branch.
                playKeySound()
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
                                if (key.output[0].isUpperCase()) typedWordHadUpper = true
                                typedWord += key.output.lowercase()
                            } else {
                                typedWord = ""
                                typedWordHadUpper = false
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
                        typedWordHadUpper = false
                        noteTypedWordChange()
                    }
                    KeyAction.DELETE -> {
                        typedWord = typedWord.dropLast(1)
                        if (typedWord.isEmpty()) typedWordHadUpper = false
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
                        typedWordHadUpper = false
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
            // Manna & Milk 3D keys, Nov 2, 2026 (Wilson):
            // Manna's buttons are a face over a solid
            // dark edge — CSS `box-shadow: 0 4px 0
            // var(--edge)`, with a dark border — and on
            // press the face sinks 2px and the edge
            // shrinks to 2px. Translated to canvas:
            // the edge is a rounded rect 3dp below the
            // face, so a 3dp dark strip shows under
            // every key (the 4dp LeanType gap leaves
            // 1dp of background under it, so rows do
            // not merge). A pressed face moves down
            // 2dp while its edge stays put, so the
            // visible strip shrinks to 1dp — the same
            // sinking press. The face itself is a
            // light-top to darker-bottom gradient, so
            // the top catches the light like a raised
            // button. Dark mode uses Manna's dark
            // recipe: slate face (#2A3546) over a
            // near-black edge (#0C121A), not just a
            // darker flat grey. Corners and gaps stay
            // LeanType: 8dp corners, 4dp gaps.
            val isAction = key.action in listOf(
                KeyAction.SHIFT, KeyAction.DELETE,
                KeyAction.MODE, KeyAction.ENTER
            )
            val isShiftOn = key.action == KeyAction.SHIFT && shiftOn
            val edgeColor = if (darkMode) "#0C121A" else "#243044"
            val (faceTop, faceBottom) = when {
                isShiftOn && isPressed -> "#3B82F6" to "#1D4ED8"
                isShiftOn -> "#60A5FA" to "#3B82F6"
                isPressed && darkMode -> "#232E3D" to "#141B26"
                isPressed -> "#E5E7EB" to "#D1D5DB"
                isAction && darkMode -> "#4B5563" to "#374151"
                isAction -> "#C2C7CE" to "#AEB3BA"
                darkMode -> "#2A3546" to "#1F2937"
                else -> "#FFFFFF" to "#E7E9EC"
            }
            val edgeRect = RectF(rect).apply { offset(0f, dp(3f)) }
            val faceRect = RectF(rect).apply {
                var drop = if (isPressed) dp(2f) else 0f
                // iPhone pull: the keycap itself travels
                // down a few dp with the finger. The dark
                // edge stays put, so the cap looks lifted
                // off its base.
                if (isPressed && flickPull > 0f) drop += dp(5f) * flickPull
                if (drop != 0f) offset(0f, drop)
            }
            keyPaint.shader = null
            keyPaint.color = Color.parseColor(edgeColor)
            canvas.drawRoundRect(edgeRect, dp(8f), dp(8f), keyPaint)
            keyPaint.shader = LinearGradient(
                faceRect.left, faceRect.top, faceRect.left, faceRect.bottom,
                Color.parseColor(faceTop), Color.parseColor(faceBottom),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(faceRect, dp(8f), dp(8f), keyPaint)
            keyPaint.shader = null
            textPaint.color = when {
                key.action == KeyAction.SHIFT && shiftOn -> Color.WHITE
                darkMode -> Color.WHITE
                else -> Color.BLACK
            }
            textPaint.textSize = when {
                // Code 39: the emoji mode key is smaller than
                // the letter keys (16dp vs 22dp), per Wilson's
                // "smaller monochrome" ask — it should sit
                // quietly on the bottom row, not shout.
                key.action == KeyAction.MODE && key.mode == KeyboardMode.EMOJI -> dp(16f)
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
            // Text rides the face, not the slot: a
            // pressed key's label sinks with it, 2dp.
            val y = faceRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            val pullHint = if (isPressed && flickPull > 0f &&
                mode == KeyboardMode.LETTERS &&
                key.action == KeyAction.CHAR &&
                key.output.length == 1 && key.output[0].isLetter()
            ) {
                SECONDARY[key.output.lowercase().first()]
            } else null
            if (pullHint != null) {
                // Pull the letter down and out, and bring
                // the corner symbol into the center at
                // full size and the primary color. Clipped
                // to the key so it does not spill onto the
                // row below.
                val p = flickPull
                val primary = textPaint.color
                val grey = if (darkMode) Color.parseColor("#9CA3AF") else Color.parseColor("#6B7280")
                canvas.save()
                canvas.clipRect(rect)
                textPaint.alpha = ((1f - p) * 255f).toInt().coerceIn(0, 255)
                canvas.drawText(
                    key.label,
                    faceRect.centerX(),
                    y + rect.height() * 0.65f * p,
                    textPaint
                )
                textPaint.textSize = dp(11f)
                textPaint.textAlign = Paint.Align.CENTER
                val hintW = textPaint.measureText(pullHint)
                val startX = faceRect.right - dp(5f) - hintW / 2f
                val startY = faceRect.top + dp(13f)
                textPaint.textSize = dp(11f) + (dp(22f) - dp(11f)) * p
                textPaint.color = mixColor(grey, primary, p)
                textPaint.alpha = (180 + 75f * p).toInt().coerceIn(0, 255)
                canvas.drawText(
                    pullHint,
                    startX + (faceRect.centerX() - startX) * p,
                    startY + (y - startY) * p,
                    textPaint
                )
                canvas.restore()
                textPaint.alpha = 255
                textPaint.color = primary
                textPaint.textSize = dp(22f)
            } else {
                canvas.drawText(key.label, faceRect.centerX(), y, textPaint)
            }
            // Flick secondary hint, Nov 2, 2026: the
            // small symbol in the top-right corner of
            // each letter key, same 8dp-corner key
            // shape, grey so it never competes with
            // the letter. This is what a pull down on
            // that key types. Letters mode only.
            // Hidden while the pull animation is
            // already moving that same symbol.
            if (pullHint == null && mode == KeyboardMode.LETTERS &&
                key.action == KeyAction.CHAR &&
                key.output.length == 1 && key.output[0].isLetter()
            ) {
                val hint = key.output.lowercase().firstOrNull()
                    ?.let { SECONDARY[it] }
                if (hint != null) {
                    textPaint.typeface = Typeface.DEFAULT
                    textPaint.textSize = dp(11f)
                    textPaint.color = if (darkMode) {
                        Color.parseColor("#9CA3AF")
                    } else {
                        Color.parseColor("#6B7280")
                    }
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(
                        hint, faceRect.right - dp(5f), faceRect.top + dp(14f), textPaint
                    )
                    textPaint.textAlign = Paint.Align.CENTER
                }
            }
        }

        // In-keyboard debug banner (see showDebug above).
        if (debugLine != null) {
            val bannerH = dp(30f)
            val banner = RectF(0f, 0f, width.toFloat(), bannerH)
            keyPaint.color = Color.parseColor("#111827")
            canvas.drawRect(banner, keyPaint)
            textPaint.color = Color.WHITE
            textPaint.typeface = Typeface.DEFAULT
            var size = dp(13f)
            textPaint.textSize = size
            while (size > dp(9f) &&
                textPaint.measureText(debugLine!!) > width - dp(16f)
            ) {
                size -= dp(1f)
                textPaint.textSize = size
            }
            val y = banner.centerY() -
                (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(debugLine!!, width / 2f, y, textPaint)
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
            // Code 28: clamp to the top of the keyboard
            // view, or a top-row key's picker is drawn
            // off-screen and never shows.
            val top = (rect.top - rect.height() - dp(10f)).coerceAtLeast(0f)
            for ((i, variant) in variantList.withIndex()) {
                val vRect = RectF(
                    startX + i * variantW, top,
                    startX + (i + 1) * variantW - gap, top + rect.height()
                )
                // Same Manna 3D as the main keys:
                // dark edge 3dp below, gradient face.
                val vEdge = RectF(vRect).apply { offset(0f, dp(3f)) }
                val (vTop, vBottom) = if (i == variantSelected) {
                    "#60A5FA" to "#3B82F6"
                } else if (darkMode) {
                    "#2A3546" to "#1F2937"
                } else {
                    "#FFFFFF" to "#E7E9EC"
                }
                keyPaint.shader = null
                keyPaint.color = Color.parseColor(
                    if (darkMode) "#0C121A" else "#243044"
                )
                canvas.drawRoundRect(vEdge, dp(8f), dp(8f), keyPaint)
                keyPaint.shader = LinearGradient(
                    vRect.left, vRect.top, vRect.left, vRect.bottom,
                    Color.parseColor(vTop), Color.parseColor(vBottom),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(vRect, dp(8f), dp(8f), keyPaint)
                keyPaint.shader = null
                textPaint.color = if (i == variantSelected) Color.WHITE
                    else if (darkMode) Color.WHITE else Color.BLACK
                textPaint.textSize = dp(22f)
                val y = vRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(variant, vRect.centerX(), y, textPaint)
            }
        }

        // Code 31: key popups are no longer drawn
        // here — see showKeyPopup(), a PopupWindow
        // above the key in screen space, so top-row
        // keys (including the number row) pop above
        // the key instead of on top of it.
        // Space-cursor indicator.
        if (cursorMode) {
            textPaint.textSize = dp(14f)
            textPaint.color = Color.parseColor("#3B82F6")
            canvas.drawText("◀ cursor ▶ ▲▼", width / 2f, dp(18f), textPaint)
        }
    }
}
