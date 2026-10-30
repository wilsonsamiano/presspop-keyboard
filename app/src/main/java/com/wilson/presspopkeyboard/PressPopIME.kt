package com.wilson.presspopkeyboard

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import android.content.ClipDescription
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * PressPop input method service, v2.
 *
 * Suggestion strip: above the keyboard, three slots. While typing a
 * word, the middle slot is the best completion / correction and the
 * right slot is the next one. Tap a suggestion to replace the word
 * being typed with it. The left slot shows the word as typed, so an
 * autocorrection can be undone with one tap.
 *
 * Autocorrect: when Space is tapped, a word that is not in the
 * dictionary and not a personal word is replaced by its closest
 * dictionary word (edit distance 1 for short words, 2 for longer),
 * if one exists. A personal word is NEVER autocorrected. Autocorrect
 * only fires on Space, never mid-word, and the original word stays
 * in the left suggestion slot so it can be tapped back.
 *
 * Double-tap Space: the second tap replaces the space just typed
 * with ". " (period + space), iPhone-style.
 *
 * Personal keyboard: PersonalDictionary is loaded here and passed
 * to the view for predictions and swipe matches. Every committed
 * word is offered to it for learning (twice-typed unknowns become
 * personal words).
 */
class PressPopIME : InputMethodService() {

    private lateinit var keyboardView: KeyboardView
    private lateinit var personal: PersonalDictionary
    private lateinit var clipboard: ClipboardHistory
    private lateinit var voice: VoiceInput
    private lateinit var suggestionViews: List<TextView>
    private lateinit var strip: LinearLayout
    private lateinit var clipboardPanel: LinearLayout
    private lateinit var gifPanel: LinearLayout
    private lateinit var gifQueryText: TextView
    private lateinit var gifGrid: GridLayout
    private lateinit var gifInfoText: TextView
    private lateinit var languageButton: TextView

    private var gifMode = false
    private var gifQuery = ""
    private var gifStickerMode = false

    private var lastSpaceTap = 0L
    private var lastCorrected: Pair<String, String>? = null // (typed, correction)

    // Last word committed in this session, for next-word
    // prediction (code 20). Updated whenever a word is
    // committed by tapping, swiping, Space, or a suggestion.
    private var lastCommittedWord: String = ""

    // Record a committed word and learn the pair it forms
    // with the previous committed word, on-device only.
    private fun recordCommittedWord(word: String) {
        val w = word.lowercase().trim()
        if (w.isEmpty()) return
        if (lastCommittedWord.isNotEmpty()) {
            personal.notePair(lastCommittedWord, w)
        }
        lastCommittedWord = w
        committedHistory.add(w)
        if (committedHistory.size > 2) committedHistory.removeAt(0)
    }

    // Previous word before the cursor, for next-word
    // prediction. Reads the field itself first, so it works
    // even if the cursor moved; falls back to the last word
    // this keyboard committed in this session.
    // Last two committed words, for trigram grammar context.
    private val committedHistory = mutableListOf<String>()

    private fun lastWordBeforeCursor(): String {
        val (prevPrev, prev) = lastTwoWordsBeforeCursor()
        return prev
    }

    // Last two words before the cursor, for trigram context.
    // Returns ("", "") at a sentence start (newline, or after
    // ". ", "? ", "! "), because a bigram from the previous
    // sentence must not predict the first word of the next.
    private fun lastTwoWordsBeforeCursor(): Pair<String, String> {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: ""
                val trimmed = before.trimEnd()
                if (trimmed.isEmpty() || trimmed.endsWith("\n") ||
                    trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!")
                ) {
                    return "" to ""
                }
                val words = Regex("[A-Za-z']+").findAll(before).map { it.value.lowercase() }.toList()
                if (words.isNotEmpty()) {
                    val prev = words.last()
                    val prevPrev = if (words.size >= 2) words[words.size - 2] else ""
                    return prevPrev to prev
                }
            } catch (e: Exception) {
                // Fall through to committed history.
            }
        }
        val prev = committedHistory.lastOrNull() ?: ""
        val prevPrev = if (committedHistory.size >= 2) committedHistory[committedHistory.size - 2] else ""
        return prevPrev to prev
    }
    private var darkMode = false
    // Auto-capitalization for this field: on for plain text, off for
    // passwords, email addresses, URLs, numbers and phone fields.
    private var autoCapitalize = false // Wilson, Oct 29, 2026: dont put caps — no sentence-start auto-capitalization. Manual Shift still works for a capital when he wants one.

    // Dark mode is read fresh every time the input view is created
    // or a new field starts — NOT just in onCreate(), where the
    // service may have started long before the user toggled the
    // theme in the app. Reading it only in onCreate() was why
    // toggling Dark in the app appeared to do nothing.
    private fun refreshDarkMode() {
        val settings = getSharedPreferences("presspop_settings", MODE_PRIVATE)
        darkMode = settings.getBoolean("dark_mode", false)
        if (::keyboardView.isInitialized) {
            keyboardView.darkMode = darkMode
            // Key pop sounds, on by default ("key_sounds", app toggle).
            keyboardView.keySoundsOn = settings.getBoolean("key_sounds", true)
        }
        if (::strip.isInitialized) {
            strip.setBackgroundColor(
                if (darkMode) Color.parseColor("#111827") else Color.WHITE
            )
        }
        if (::languageButton.isInitialized) {
            languageButton.setTextColor(
                if (darkMode) Color.WHITE else Color.BLACK
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        personal = PersonalDictionary(this)
        clipboard = ClipboardHistory(this)
        val settings = getSharedPreferences("presspop_settings", MODE_PRIVATE)
        darkMode = settings.getBoolean("dark_mode", false)
        ErrorReporter.install(this)
        // Restore a custom language pack, if one was installed.
        run {
            val customPrefs = getSharedPreferences("presspop_languages", MODE_PRIVATE)
            val wordsRaw = customPrefs.getString("custom_words", "") ?: ""
            if (wordsRaw.isNotEmpty()) {
                Dictionary.customWords = wordsRaw.split("\n").filter { it.isNotBlank() }
                Dictionary.customLanguageName =
                    customPrefs.getString("custom_name", "") ?: ""
            }
        }
        voice = VoiceInput(
            this,
            onResult = { text ->
                val ic = currentInputConnection
                ic?.commitText("$text ", 1)
                if (::keyboardView.isInitialized) {
                    keyboardView.setTypedWord("")
                    refreshSuggestions("")
                }
                updateAutoShift()
            },
            onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        )
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
                if (text.isNotEmpty()) clipboard.add(text)
            }
        }
    }

    override fun onCreateInputView(): View {
        val settings = getSharedPreferences("presspop_settings", MODE_PRIVATE)
        keyboardView = KeyboardView(this)
        refreshDarkMode()
        keyboardView.showNumberRow = settings.getBoolean("number_row", false)
        keyboardView.personalWords = personal.allPersonalWords()

        strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (darkMode) Color.parseColor("#111827") else Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (56 * resources.displayMetrics.density).toInt()
            )
        }
        // Clipboard button at the start of the strip.
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (48 * resources.displayMetrics.density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            text = "📋"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 20f
            setTextColor(if (darkMode) Color.WHITE else Color.BLACK)
            setOnClickListener { toggleClipboardPanel() }
            strip.addView(this)
        }
        // Language toggle: cycles EN -> TL -> ES -> custom (if installed) -> EN.
        // The stored code may be "en", "tl", "es", or "custom" (new in v5);
        // the old boolean "tagalog" setting is still honoured for upgrades.
        val storedCode = settings.getString("language_code", null)
            ?: if (settings.getBoolean("tagalog", false)) "tl" else "en"
        Dictionary.languageCode = storedCode
        languageButton = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (48 * resources.displayMetrics.density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            text = languageLabel()
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 16f
            setTextColor(if (darkMode) Color.WHITE else Color.BLACK)
            setOnClickListener { toggleLanguage() }
            strip.addView(this)
        }
        // GIF / sticker search button.
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (56 * resources.displayMetrics.density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            text = "GIF"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 16f
            setTextColor(if (darkMode) Color.WHITE else Color.BLACK)
            setOnClickListener { toggleGifPanel() }
            strip.addView(this)
        }
        suggestionViews = (0..2).map { index ->
            TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                textSize = 17f
                setTextColor(if (darkMode) Color.WHITE else Color.BLACK)
                setOnClickListener { onSuggestionTapped(index) }
                strip.addView(this)
            }
        }
        // Microphone at the right end of the strip (top right), Oct
        // 29, 2026 (Wilson): the bottom-row mic key is gone, and
        // this replaces it — same voice input, just moved up here.
        TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (48 * resources.displayMetrics.density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            text = "🎤"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 20f
            setTextColor(if (darkMode) Color.WHITE else Color.BLACK)
            setOnClickListener { handleVoice() }
            strip.addView(this)
        }

        keyboardView.onWordChanged = { word -> refreshSuggestions(word) }

        clipboardPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (darkMode) Color.parseColor("#1F2937") else Color.parseColor("#F3F4F6"))
            visibility = View.GONE
        }

        // GIF / sticker panel, hidden until the GIF button is tapped.
        // While it is open, keyboard keys type into the search query
        // instead of the app (see handleGifKey), so no separate
        // search box focus trick is needed.
        gifPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (darkMode) Color.parseColor("#1F2937") else Color.WHITE)
            visibility = View.GONE
        }
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gifQueryText = TextView(this@PressPopIME).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = "Search GIFs…"
                textSize = 16f
                setTextColor(if (darkMode) Color.WHITE else Color.BLACK)
                setPadding(24, 16, 24, 16)
            }
            addView(gifQueryText)
            Button(this@PressPopIME).apply {
                text = "Search"
                setOnClickListener { searchGifs() }
                addView(this)
            }
            Button(this@PressPopIME).apply {
                text = "GIF/Sticker"
                setOnClickListener {
                    gifStickerMode = !gifStickerMode
                    Toast.makeText(
                        this@PressPopIME,
                        if (gifStickerMode) "Sticker search" else "GIF search",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (gifQuery.isNotEmpty()) searchGifs()
                }
                addView(this)
            }
            Button(this@PressPopIME).apply {
                text = "Close"
                setOnClickListener { closeGifPanel() }
                addView(this)
            }
            gifPanel.addView(this)
        }
        gifInfoText = TextView(this).apply {
            text = "Type a search with the keyboard, then tap Search. Tap a GIF to insert it."
            textSize = 14f
            setTextColor(if (darkMode) Color.WHITE else Color.BLACK)
            setPadding(24, 8, 24, 8)
            gifPanel.addView(this)
        }
        gifGrid = GridLayout(this).apply {
            columnCount = 4
            gifPanel.addView(this)
        }

        keyboardView.onKey = { key ->
            if (gifMode) {
                handleGifKey(key)
            } else {
                val ic = currentInputConnection
                if (ic != null) {
                    when (key.action) {
                        KeyAction.VOICE -> handleVoice()
                        KeyAction.CHAR -> {
                            ic.commitText(key.output, 1)
                            // One-shot shift: a single (non-locked) shift,
                            // manual or automatic, applies to one letter only.
                            if (keyboardView.shiftOn && !keyboardView.shiftLock &&
                                key.output.length == 1 && key.output[0].isLetter()
                            ) {
                                keyboardView.shiftOn = false
                            }
                        }
                        KeyAction.SPACE -> handleSpace()
                        KeyAction.DELETE -> handleDelete()
                        KeyAction.ENTER -> {
                            // Autocorrect on Enter, code 21: the
                            // last word before Enter used to be
                            // left uncorrected, because
                            // autocorrect only ran on Space —
                            // so a typo like "somtimes" at the
                            // end of a line stayed wrong. Now
                            // Enter runs the same contraction /
                            // correction as Space first, then
                            // sends Enter. Password fields are
                            // exempt, as with auto-capitalization.
                            val enterWord = keyboardView.typedWord
                            val enterType = currentInputEditorInfo?.inputType ?: InputType.TYPE_CLASS_TEXT
                            val enterVariation = enterType and InputType.TYPE_MASK_VARIATION
                            val isPasswordField = enterVariation ==
                                InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                                enterVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            if (enterWord.isNotEmpty() && !isPasswordField) {
                                val enterContraction = Dictionary.CONTRACTIONS[enterWord.lowercase()]
                                if (enterContraction != null) {
                                    ic.deleteSurroundingText(enterWord.length, 0)
                                    ic.commitText(enterContraction, 1)
                                    recordCommittedWord(enterContraction)
                                    lastCorrected = enterWord to enterContraction
                                } else if (!Dictionary.isWord(enterWord) &&
                                    !personal.isPersonal(enterWord) &&
                                    enterWord.all { it.code < 128 }
                                ) {
                                    val enterCorrection = Dictionary.bestCorrection(
                                        enterWord, personal.allPersonalWords()
                                    )
                                    if (enterCorrection != null) {
                                        ic.deleteSurroundingText(enterWord.length, 0)
                                        ic.commitText(enterCorrection, 1)
                                        recordCommittedWord(enterCorrection)
                                        lastCorrected = enterWord to enterCorrection
                                    } else {
                                        personal.noteTyped(enterWord)
                                        recordCommittedWord(enterWord)
                                    }
                                } else {
                                    personal.noteTyped(enterWord)
                                    recordCommittedWord(enterWord)
                                }
                                keyboardView.setTypedWord("")
                                refreshSuggestions("")
                            } else {
                                commitCurrentWordForLearning()
                            }
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                            updateAutoShift()
                        }
                        else -> Unit
                    }
                }
            }
        }

        keyboardView.onSwipeWord = { word ->
            val ic = currentInputConnection
            // Auto-space after a swiped word, Oct 29, 2026 (Wilson):
            // a landed swipe used to leave the cursor right after
            // the word, so the user still had to press Space before
            // the next word — and tap-typing or swiping the next
            // word without it joined it onto the swiped word. Now
            // the swipe commits "word " itself, so the next attempt
            // is automatically a fresh, separate word. Password
            // fields are the one exception: a trailing space there
            // would corrupt the password, so they get the word only.
            val inputType = currentInputEditorInfo?.inputType
                ?: InputType.TYPE_CLASS_TEXT
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val isPassword = variation ==
                InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation ==
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            // Contraction expansion, code 21: a swiped
            // "dont" becomes "don't", always lowercase.
            val swipedWord = Dictionary.CONTRACTIONS[word.lowercase()] ?: word
            val text = capitalizeForSentenceStart(swipedWord, 0) +
                if (isPassword) "" else " "
            ic?.commitText(text, 1)
            if (keyboardView.shiftOn && !keyboardView.shiftLock) {
                keyboardView.shiftOn = false
            }
            recordCommittedWord(swipedWord)
            personal.noteTyped(swipedWord)
            keyboardView.personalWords = personal.allPersonalWords()
            keyboardView.setTypedWord("")
            refreshSuggestions("")
            updateAutoShift()
        }

        keyboardView.onCursorMove = { delta ->
            val ic = currentInputConnection
            if (ic != null) {
                val extracted = ic.getExtractedText(
                    android.view.inputmethod.ExtractedTextRequest(), 0
                )
                if (extracted != null) {
                    val pos = (extracted.selectionStart + delta)
                        .coerceIn(0, extracted.text.length)
                    ic.setSelection(pos, pos)
                }
            }
        }

        // Vertical space-cursor: send the system's line-up / line-down
        // key, so the app moves the cursor by its own (wrapped) lines
        // instead of the keyboard guessing at the app's layout.
        keyboardView.onCursorLineMove = { lines ->
            val ic = currentInputConnection
            if (ic != null) {
                val keyCode = if (lines > 0) KeyEvent.KEYCODE_DPAD_DOWN
                    else KeyEvent.KEYCODE_DPAD_UP
                repeat(kotlin.math.abs(lines)) {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(strip)
            addView(clipboardPanel)
            addView(gifPanel)
            // One-handed mode: shrink the keyboard to 85% width,
            // pinned left or right. Full width is the default.
            val oneHanded = settings.getString("one_handed", "full") ?: "full"
            if (oneHanded != "full") {
                val screenW = resources.displayMetrics.widthPixels
                keyboardView.layoutParams = LinearLayout.LayoutParams(
                    (screenW * 0.85).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = if (oneHanded == "left") Gravity.START else Gravity.END
                }
            }
            addView(keyboardView)
        }
    }

    private fun languageLabel(): String = when (Dictionary.languageCode) {
        "tl" -> "TL"
        "es" -> "ES"
        "custom" -> Dictionary.customLanguageName.take(2).uppercase().ifEmpty { "CU" }
        else -> "EN"
    }

    private fun languageName(): String = when (Dictionary.languageCode) {
        "tl" -> "Tagalog"
        "es" -> "Spanish"
        "custom" -> Dictionary.customLanguageName.ifEmpty { "Custom" }
        else -> "English"
    }

    private fun toggleLanguage() {
        val options = mutableListOf("en", "tl", "es")
        if (Dictionary.customWords.isNotEmpty()) options.add("custom")
        val current = Dictionary.languageCode
        val currentIndex = options.indexOf(current)
        val nextIndex = if (currentIndex < 0 || currentIndex + 1 >= options.size) 0 else currentIndex + 1
        val next = options[nextIndex]
        Dictionary.languageCode = next
        getSharedPreferences("presspop_settings", MODE_PRIVATE)
            .edit().putString("language_code", next).apply()
        if (::languageButton.isInitialized) {
            languageButton.text = languageLabel()
        }
        Toast.makeText(this, "Language: ${languageName()}", Toast.LENGTH_SHORT).show()
        if (::keyboardView.isInitialized) {
            keyboardView.setTypedWord("")
            refreshSuggestions("")
        }
    }

    private fun toggleGifPanel() {
        if (!::gifPanel.isInitialized) return
        if (gifPanel.visibility == View.VISIBLE) {
            closeGifPanel()
            return
        }
        gifMode = true
        gifQuery = ""
        gifQueryText.text = "Search GIFs…"
        gifInfoText.text = "Type a search with the keyboard, then tap Search. Tap a GIF to insert it."
        gifGrid.removeAllViews()
        gifPanel.visibility = View.VISIBLE
        if (::keyboardView.isInitialized) keyboardView.mode = KeyboardMode.LETTERS
    }

    private fun closeGifPanel() {
        gifMode = false
        gifQuery = ""
        if (::gifPanel.isInitialized) gifPanel.visibility = View.GONE
        if (::keyboardView.isInitialized) {
            keyboardView.mode = KeyboardMode.LETTERS
            // Letters typed into the GIF search also fed typedWord;
            // drop them so they do not leak into the next word.
            keyboardView.setTypedWord("")
        }
    }

    private fun handleGifKey(key: Key) {
        when (key.action) {
            KeyAction.CHAR -> {
                gifQuery += key.output
                gifQueryText.text = gifQuery
            }
            KeyAction.SPACE -> {
                gifQuery += " "
                gifQueryText.text = gifQuery
            }
            KeyAction.DELETE -> {
                gifQuery = gifQuery.dropLast(1)
                gifQueryText.text = gifQuery.ifEmpty { "Search GIFs…" }
            }
            KeyAction.ENTER -> searchGifs()
            else -> Unit
        }
    }

    private fun searchGifs() {
        val apiKey = getSharedPreferences("presspop_settings", MODE_PRIVATE)
            .getString("tenor_api_key", "") ?: ""
        if (apiKey.isBlank()) {
            gifInfoText.text = "Enter a free Tenor API key in the PressPop app first (Tenor API key setting), then search again."
            return
        }
        val query = gifQuery.trim().ifEmpty { "happy" } +
            if (gifStickerMode) " sticker" else ""
        gifInfoText.text = "Searching…"
        Thread {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL(
                    "https://tenor.googleapis.com/v2/search?q=$encoded" +
                        "&key=$apiKey&client_key=presspop&limit=8&media_filter=tinygif,gif"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val body = conn.inputStream.bufferedReader().readText()
                val results = JSONObject(body).getJSONArray("results")
                val items = mutableListOf<Pair<String, String>>()
                for (i in 0 until results.length()) {
                    val formats = results.getJSONObject(i).getJSONObject("media_formats")
                    val tiny = formats.optJSONObject("tinygif")?.optString("url") ?: ""
                    val full = formats.optJSONObject("gif")?.optString("url") ?: tiny
                    if (tiny.isNotEmpty() && full.isNotEmpty()) items.add(tiny to full)
                }
                gifGrid.post {
                    gifGrid.removeAllViews()
                    if (items.isEmpty()) {
                        gifInfoText.text = "No GIFs found for that search."
                        return@post
                    }
                    gifInfoText.text = "Tap a GIF to insert it."
                    val size = (80 * resources.displayMetrics.density).toInt()
                    for ((tiny, full) in items) {
                        val imageView = ImageView(this).apply {
                            layoutParams = GridLayout.LayoutParams().apply {
                                width = size
                                height = size
                                setMargins(8, 8, 8, 8)
                            }
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setOnClickListener { insertGif(full) }
                        }
                        gifGrid.addView(imageView)
                        Thread {
                            try {
                                val bitmap = BitmapFactory.decodeStream(URL(tiny).openStream())
                                imageView.post { imageView.setImageBitmap(bitmap) }
                            } catch (e: Exception) {
                                // Leave that thumbnail blank; the rest still work.
                            }
                        }.start()
                    }
                }
            } catch (e: Exception) {
                gifGrid.post {
                    gifInfoText.text = "GIF search failed. Check your connection and API key."
                }
            }
        }.start()
    }

    private fun insertGif(gifUrl: String) {
        val editorInfo = currentInputEditorInfo
        val mimeTypes = editorInfo?.contentMimeTypes ?: emptyArray()
        if (!mimeTypes.contains("image/gif")) {
            Toast.makeText(
                this,
                "This app doesn't accept GIFs. Try Messages or WhatsApp.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val ic = currentInputConnection ?: return
        Thread {
            try {
                val bytes = URL(gifUrl).openStream().readBytes()
                val file = File(cacheDir, "presspop_gif_${System.currentTimeMillis()}.gif")
                FileOutputStream(file).use { it.write(bytes) }
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val info = InputContentInfoCompat(
                    uri,
                    ClipDescription("GIF", arrayOf("image/gif")),
                    null
                )
                val ok = InputConnectionCompat.commitContent(ic, editorInfo, info, 0, null)
                gifGrid.post {
                    if (ok) {
                        closeGifPanel()
                    } else {
                        Toast.makeText(
                            this,
                            "That app refused the GIF. Try Messages or WhatsApp.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                gifGrid.post {
                    Toast.makeText(this, "Could not download that GIF.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun handleVoice() {
        if (voice.listening) {
            voice.stop()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Allow microphone in the PressPop app first, then tap the mic again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (::strip.isInitialized) suggestionViews.getOrNull(0)?.text = "Listening…"
        voice.start()
    }

    private fun toggleClipboardPanel() {
        if (!::clipboardPanel.isInitialized) return
        if (clipboardPanel.visibility == View.VISIBLE) {
            clipboardPanel.visibility = View.GONE
            return
        }
        rebuildClipboardPanel()
        clipboardPanel.visibility = View.VISIBLE
    }

    private fun rebuildClipboardPanel() {
        clipboardPanel.removeAllViews()
        fun addRow(text: String, onClick: (() -> Unit)? = null) {
            TextView(this).apply {
                this.text = text
                textSize = 16f
                setTextColor(if (darkMode) Color.WHITE else Color.BLACK)
                setPadding(24, 16, 24, 16)
                maxLines = 1
                if (onClick != null) setOnClickListener { onClick() }
                clipboardPanel.addView(this)
            }
        }
        addRow("Clipboard — tap an item to paste (stored on this device only)")
        val items = clipboard.all()
        if (items.isEmpty()) {
            addRow("Clipboard history is empty.")
        } else {
            for (item in items.take(5)) {
                addRow(item.take(60)) {
                    currentInputConnection?.commitText(item, 1)
                    clipboardPanel.visibility = View.GONE
                }
            }
        }
        addRow("Clear clipboard history") {
            clipboard.clear()
            rebuildClipboardPanel()
        }
    }

    // Auto-capitalization: the next letter is capitalized at the
    // start of a field, after ". ", "? ", "! ", or a newline.
    // updateAutoShift() only ever turns shift ON, so it never
    // overrides a manual shift choice; the one-shot rule in onKey
    // turns a non-locked shift back off after one letter.
    private fun isSentenceStart(text: CharSequence?): Boolean {
        if (text == null) return false
        val trimmed = text.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in ".?!\n"
    }

    private fun updateAutoShift() {
        if (!autoCapitalize || !::keyboardView.isInitialized ||
            keyboardView.shiftLock
        ) return
        val ic = currentInputConnection ?: return
        val before = try {
            ic.getTextBeforeCursor(64, 0)
        } catch (e: Exception) {
            null
        }
        if (isSentenceStart(before)) {
            keyboardView.shiftOn = true
        }
    }

    // Capitalize [word] when it starts a sentence. [charsBeforeWord]
    // is how many characters of already-committed text belong to the
    // word being replaced (0 for a swipe word, which is not typed yet).
    private fun capitalizeForSentenceStart(word: String, charsBeforeWord: Int): String {
        if (!autoCapitalize || word.isEmpty()) return word
        val ic = currentInputConnection ?: return word
        val before = try {
            ic.getTextBeforeCursor(charsBeforeWord + 64, 0)?.toString()
        } catch (e: Exception) {
            null
        } ?: return word
        val beforeWord = before.dropLast(charsBeforeWord.coerceAtMost(before.length))
        return if (isSentenceStart(beforeWord)) {
            word.replaceFirstChar { it.uppercaseChar() }
        } else {
            word
        }
    }

    // Backspace: if text is highlighted, one tap deletes the whole
    // selection — the old code only ever deleted one character before
    // the cursor (deleteSurroundingText ignores a selection), so a
    // highlighted word or sentence survived backspace.
    private fun handleDelete() {
        val ic = currentInputConnection ?: return
        val extracted = try {
            ic.getExtractedText(
                android.view.inputmethod.ExtractedTextRequest(), 0
            )
        } catch (e: Exception) {
            null
        }
        if (extracted != null && extracted.selectionStart >= 0 &&
            extracted.selectionEnd > extracted.selectionStart
        ) {
            ic.commitText("", 1)
            keyboardView.setTypedWord("")
            lastCorrected = null
            refreshSuggestions("")
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        updateAutoShift()
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return
        val word = keyboardView.typedWord
        val now = SystemClock.uptimeMillis()

        // Double-tap Space -> ". " (only if the last thing typed was
        // a word followed by a single space, i.e. a tap just before).
        if (word.isEmpty() && now - lastSpaceTap < 400) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            lastSpaceTap = 0L
            updateAutoShift()
            return
        }
        lastSpaceTap = now
        var committedWordThisSpace: String? = null

        if (word.isNotEmpty()) {
            // Contraction expansion, code 21, checked BEFORE
            // the isWord check: "dont" IS a dictionary word,
            // so without this it would be left alone and never
            // become "don't". Always lowercase, never
            // capitalized, per Wilson's "dont put caps".
            val contraction = Dictionary.CONTRACTIONS[word.lowercase()]
            if (contraction != null) {
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText(contraction, 1)
                lastCorrected = word to contraction
                committedWordThisSpace = contraction
            } else if (!Dictionary.isWord(word) && !personal.isPersonal(word) &&
                word.all { it.code < 128 }
            ) {
                val correction = Dictionary.bestCorrection(word, personal.allPersonalWords())
                if (correction != null) {
                    val finalCorrection =
                        capitalizeForSentenceStart(correction, word.length)
                    showDebug("Autocorrect: '$word' -> '$finalCorrection'")
                    ic.deleteSurroundingText(word.length, 0)
                    ic.commitText(finalCorrection, 1)
                    lastCorrected = word to finalCorrection
                    committedWordThisSpace = finalCorrection
                    // Do NOT learn the typo as a personal word: a
                    // corrected typo is not a word the user wants to
                    // keep, and learning it after two corrections
                    // would make autocorrect stop correcting it.
                } else {
                    showDebug("Autocorrect: '$word' -> no correction (left as typed)")
                    personal.noteTyped(word)
                }
            } else {
                showDebug("Autocorrect: '$word' left alone (known/personal word)")
                personal.noteTyped(word)
            }
            keyboardView.personalWords = personal.allPersonalWords()
        }
        // Record the committed word for next-word learning:
        // the correction if one was made in this Space press,
        // else the typed word. Using a local here, not
        // lastCorrected, so a stale correction from an earlier
        // word cannot be recorded for this word.
        if (word.isNotEmpty()) {
            recordCommittedWord(committedWordThisSpace ?: word)
        }
        ic.commitText(" ", 1)
        keyboardView.setTypedWord("")
        refreshSuggestions("")
        updateAutoShift()
    }

    private fun commitCurrentWordForLearning() {
        val word = keyboardView.typedWord
        if (word.isNotEmpty()) {
            personal.noteTyped(word)
            keyboardView.personalWords = personal.allPersonalWords()
        }
    }

    // Debug via the keyboard's own banner (KeyboardView.showDebug),
    // Oct 29, 2026: Toasts never showed on Wilson's onn tablet
    // while typing, even on code 10 with debug on, so autocorrect
    // and suggestion readouts go through the same in-keyboard
    // banner as swipe readouts. Falls back to a Toast only if the
    // keyboard view is not ready yet.
    private fun showDebug(message: String) {
        // No popup anywhere while typing, Oct 29, 2026 (Wilson):
        // the line is only recorded for the app's Copy button.
        if (::keyboardView.isInitialized) {
            keyboardView.showDebug(message)
        } else {
            DebugMode.record(message)
        }
    }

    private fun refreshSuggestions(word: String) {
        if (!::strip.isInitialized) return
        val personalWords = personal.allPersonalWords()
        // Next-word prediction, code 20: when no word is being
        // typed (right after a Space or a swiped word), the
        // middle/right slots offer the most likely NEXT word,
        // learned pairs first, then the generic BIGRAMS table.
        // Tapping one commits it plus a space, so the next
        // next-word offer appears at once.
        val nextWords = if (word.isEmpty()) {
            val (prevPrev, prev) = lastTwoWordsBeforeCursor()
            if (prev.isNotEmpty()) {
                val trigram = if (prevPrev.isNotEmpty()) {
                    Dictionary.TRIGRAMS["$prevPrev $prev"] ?: emptyList()
                } else emptyList()
                (personal.learnedNextWords(prev) + trigram + (Dictionary.BIGRAMS[prev] ?: emptyList()))
                    .distinct().take(2)
            } else emptyList()
        } else emptyList()
        val completions = if (word.length >= 2) {
            Dictionary.completions(word, personalWords)
        } else emptyList()
        val corrected = lastCorrected
        // Left slot: the word as typed, or the pre-autocorrect word
        // right after a correction (tap it to undo the correction).
        val left = when {
            corrected != null && word.isEmpty() -> corrected.first
            word.isNotEmpty() -> word
            else -> ""
        }
        val middle = if (word.isEmpty()) nextWords.getOrNull(0) ?: "" else completions.getOrNull(0) ?: ""
        val right = if (word.isEmpty()) nextWords.getOrNull(1) ?: "" else completions.getOrNull(1) ?: ""
        val texts = listOf(left, middle, right)
        suggestionViews.forEachIndexed { i, view ->
            view.text = texts[i]
            view.setTextColor(
                if (i == 1 && texts[i].isNotEmpty()) Color.parseColor("#3B82F6")
                else if (darkMode) Color.WHITE else Color.BLACK
            )
        }
    }

    private fun onSuggestionTapped(index: Int) {
        val ic = currentInputConnection ?: return
        val view = suggestionViews.getOrNull(index) ?: return
        val suggestion = view.text.toString()
        if (suggestion.isEmpty()) return
        showDebug(
            "Suggestion tapped: '$suggestion' (typed was '${keyboardView.typedWord}')"
        )
        val corrected = lastCorrected
        // Tapping the left slot right after an autocorrection undoes it.
        // Two old bugs made undo almost unusable, and both are fixed
        // here. (1) handleSpace() commits the correction AND a trailing
        // space, so the cursor sits after the space — deleting only
        // the correction's length deleted the space plus all but the
        // first letter of the correction, garbling the word, which is
        // why undo only worked after backspacing the space first.
        // Undo now deletes the correction plus its trailing space (if
        // present) and restores the original word plus that space.
        // (2) After undo, the next Space re-corrected the restored
        // word, because nothing remembered the undo. The restored word
        // is now learned as a personal word, so it is never
        // autocorrected again — undo is a choice, not a suggestion.
        if (index == 0 && corrected != null && keyboardView.typedWord.isEmpty() &&
            suggestion == corrected.first
        ) {
            val before = try {
                ic.getTextBeforeCursor(corrected.second.length + 2, 0)?.toString()
            } catch (e: Exception) {
                null
            }
            val hasTrailingSpace = before?.endsWith(" ") == true
            val charsToDrop = corrected.second.length + if (hasTrailingSpace) 1 else 0
            // Capitalize before deleting: the helper reads the text
            // before the cursor, which must still contain the word.
            val restored = capitalizeForSentenceStart(
                corrected.first, charsToDrop
            )
            ic.deleteSurroundingText(charsToDrop, 0)
            ic.commitText(restored + if (hasTrailingSpace) " " else "", 1)
            personal.addManual(corrected.first)
            keyboardView.personalWords = personal.allPersonalWords()
            lastCorrected = null
            keyboardView.setTypedWord("")
            refreshSuggestions("")
            return
        }
        // Next-word tap, code 20: no word is being typed, so
        // this is a predicted next word, not a completion.
        // Commit it plus a space, learn the pair, and offer the
        // next next-word at once — otherwise tapping a next
        // word would leave the cursor stuck right after it.
        if (keyboardView.typedWord.isEmpty() && index != 0 && corrected == null) {
            val nextWord = capitalizeForSentenceStart(suggestion, 0)
            ic.commitText(nextWord + " ", 1)
            recordCommittedWord(suggestion)
            lastCorrected = null
            keyboardView.setTypedWord("")
            refreshSuggestions("")
            updateAutoShift()
            return
        }
        val word = keyboardView.typedWord
        // Contraction expansion, code 21, for a tapped
        // suggestion that is a no-apostrophe form.
        val expandedSuggestion = Dictionary.CONTRACTIONS[suggestion.lowercase()] ?: suggestion
        // Capitalize before deleting, for the same reason as above.
        val finalSuggestion = capitalizeForSentenceStart(expandedSuggestion, word.length)
        if (word.isNotEmpty()) {
            ic.deleteSurroundingText(word.length, 0)
        }
        ic.commitText(finalSuggestion, 1)
        if (keyboardView.shiftOn && !keyboardView.shiftLock) {
            keyboardView.shiftOn = false
        }
        recordCommittedWord(expandedSuggestion)
        lastCorrected = null
        keyboardView.setTypedWord(expandedSuggestion)
        refreshSuggestions(suggestion)
    }

    override fun onStartInputView(
        info: android.view.inputmethod.EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInputView(info, restarting)
        if (gifMode) closeGifPanel()
        // Auto-capitalization only for plain text fields.
        val inputType = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        val fieldClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        // Auto-capitalization is OFF, Wilson Oct 29, 2026:
        // "Dont put caps." The field-based auto-cap that used
        // to run here is replaced by false, so words stay
        // lowercase unless he taps Shift himself.
        autoCapitalize = false
        if (::keyboardView.isInitialized) {
            // Auto numpad, Oct 29, 2026 (Wilson): a PIN / number /
            // phone field used to open the full letter keyboard
            // every time, because this always forced LETTERS here.
            // Gboard opens its numpad for these fields, so PressPop
            // now does the same: number and phone fields start on
            // the numpad, everything else starts on letters. The
            // numpad still has 123 and ABC keys, so a number field
            // that needs a symbol or a letter is one tap away, and
            // the numpad's period key covers decimal amounts.
            // (A PIN field declared as a text password, rather than
            // a number password, still opens letters — Android
            // gives the keyboard no way to tell that one is digits
            // only, so guessing numpad there would break real
            // alphanumeric passwords.)
            keyboardView.mode = when (fieldClass) {
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE -> KeyboardMode.NUMPAD
                else -> KeyboardMode.LETTERS
            }
            keyboardView.shiftOn = false
            keyboardView.shiftLock = false
            keyboardView.setTypedWord("")
            lastCorrected = null
            refreshDarkMode()
            refreshSuggestions("")
            // Capitalize the first letter of a fresh/empty field, or
            // when the existing text already ends at a sentence boundary.
            updateAutoShift()
        }
    }
}
