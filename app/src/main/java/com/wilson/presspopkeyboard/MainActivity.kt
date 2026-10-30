package com.wilson.presspopkeyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PressPop setup screen + personal keyboard controls.
 * - Enable / switch the keyboard (Android requires this by hand).
 * - Dark / light theme for the keyboard and for this
 *   settings screen itself (code 30: the screen used
 *   to stay Light even when Dark was chosen).
 * - My Words: personal words, active at once. They are never
 *   autocorrected and are suggested first. Learned words (typed
 *   twice) appear here too, via the personal dictionary, but only
 *   manual words are listed for editing here.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var personal: PersonalDictionary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        personal = PersonalDictionary(this)
        ErrorReporter.install(this)
        UpdateChecker.checkAuto(this)

        val version = packageManager.getPackageInfo(packageName, 0)
        findViewById<TextView>(R.id.versionText).text =
            "PressPop Keyboard - ${version.versionName} (code ${version.longVersionCode})"

        findViewById<Button>(R.id.enableButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.switchButton).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
        findViewById<Button>(R.id.coffeeButton).setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://buymeacoffee.com/wilsonsamiano")
                )
            )
        }
        findViewById<Button>(R.id.updateButton).setOnClickListener {
            UpdateChecker.check(this)
        }

        val settings = getSharedPreferences("presspop_settings", MODE_PRIVATE)
        val themeButton = findViewById<Button>(R.id.themeButton)
        fun refreshThemeLabel() {
            val mode = ThemeMode.current(settings)
            themeButton.text =
                "Theme: ${ThemeMode.label(mode)} (tap for ${ThemeMode.label(ThemeMode.next(mode))})"
        }
        refreshThemeLabel()
        themeButton.setOnClickListener {
            // Code 30 2nd commit: three modes now, in
            // order Light -> Dark -> System -> Light.
            // System means follow the device's own dark
            // mode, for both this screen and the
            // keyboard. The old dark_mode boolean is
            // kept in step as a fallback for older
            // versions, but theme_mode is what the
            // app and keyboard actually read.
            val mode = ThemeMode.next(ThemeMode.current(settings))
            settings.edit()
                .putString("theme_mode", mode)
                .putBoolean("dark_mode", mode == "dark")
                .apply()
            // Code 30: the same setting already drove
            // the keyboard, but this screen stayed Light
            // because the manifest theme was hardcoded
            // Light. Now the app theme is DayNight and
            // this button also switches the app itself,
            // immediately, by recreating this screen —
            // no need to close and reopen the app.
            // The keyboard itself still picks the new
            // theme up next time it opens.
            AppCompatDelegate.setDefaultNightMode(ThemeMode.nightMode(mode))
            refreshThemeLabel()
            Toast.makeText(
                this,
                "Theme: ${ThemeMode.label(mode)}. The keyboard picks it up next time it opens.",
                Toast.LENGTH_SHORT
            ).show()
            recreate()
        }

        // Voice typing permission, number row, one-handed width.
        // These are read by the keyboard service when it next starts;
        // switch keyboards away and back to apply them.
        findViewById<Button>(R.id.micButton).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Microphone already allowed.", Toast.LENGTH_SHORT).show()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.RECORD_AUDIO), 1
                )
            }
        }
        val numberRowButton = findViewById<Button>(R.id.numberRowButton)
        fun refreshNumberRowLabel() {
            val on = settings.getBoolean("number_row", false)
            numberRowButton.text = if (on) "Number row: On (tap to turn off)"
                else "Number row: Off (tap to turn on)"
        }
        refreshNumberRowLabel()
        numberRowButton.setOnClickListener {
            val on = !settings.getBoolean("number_row", false)
            settings.edit().putBoolean("number_row", on).apply()
            refreshNumberRowLabel()
            Toast.makeText(this, "Switch keyboards away and back to apply it.", Toast.LENGTH_SHORT).show()
        }
        val oneHandedButton = findViewById<Button>(R.id.oneHandedButton)
        fun refreshOneHandedLabel() {
            val mode = settings.getString("one_handed", "full") ?: "full"
            oneHandedButton.text = when (mode) {
                "left" -> "Keyboard width: Left-handed (tap for Right)"
                "right" -> "Keyboard width: Right-handed (tap for Full)"
                else -> "Keyboard width: Full (tap for Left-handed)"
            }
        }
        refreshOneHandedLabel()
        oneHandedButton.setOnClickListener {
            val next = when (settings.getString("one_handed", "full")) {
                "full" -> "left"
                "left" -> "right"
                else -> "full"
            }
            settings.edit().putString("one_handed", next).apply()
            refreshOneHandedLabel()
            Toast.makeText(this, "Switch keyboards away and back to apply it.", Toast.LENGTH_SHORT).show()
        }

        // Tenor API key for GIF / sticker search. Free key from the
        // Tenor developer site; without it the GIF panel explains
        // what to enter instead of failing silently.
        val tenorInput = findViewById<EditText>(R.id.tenorInput)
        tenorInput.setText(settings.getString("tenor_api_key", "") ?: "")
        findViewById<Button>(R.id.saveTenorButton).setOnClickListener {
            settings.edit().putString("tenor_api_key", tenorInput.text.toString().trim()).apply()
            Toast.makeText(this, "Tenor API key saved.", Toast.LENGTH_SHORT).show()
        }

        // Auto-update toggle + language packs + error reports.
        val autoUpdateButton = findViewById<Button>(R.id.autoUpdateButton)
        fun refreshAutoUpdateLabel() {
            autoUpdateButton.text = if (UpdateChecker.isAutoCheckEnabled(this)) {
                "Auto-check for updates: On (tap to turn off)"
            } else {
                "Auto-check for updates: Off (tap to turn on)"
            }
        }
        refreshAutoUpdateLabel()
        autoUpdateButton.setOnClickListener {
            UpdateChecker.setAutoCheckEnabled(this, !UpdateChecker.isAutoCheckEnabled(this))
            refreshAutoUpdateLabel()
        }

        fun setLanguage(code: String, name: String) {
            Dictionary.languageCode = code
            settings.edit().putString("language_code", code).apply()
            Toast.makeText(this, "Language: $name. Switch keyboards away and back to apply it.", Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.langEnButton).setOnClickListener { setLanguage("en", "English") }
        findViewById<Button>(R.id.langTlButton).setOnClickListener { setLanguage("tl", "Tagalog") }
        findViewById<Button>(R.id.langEsButton).setOnClickListener { setLanguage("es", "Spanish") }

        val langNameInput = findViewById<EditText>(R.id.langNameInput)
        fun installCustomPack(name: String, rawText: String) {
            val words = rawText.lines()
                .map { it.trim().lowercase() }
                .filter { it.length >= 2 && it.all { c -> c.isLetter() || c == '-' || c == '\'' } }
                .distinct()
                .take(5000)
            if (words.isEmpty()) {
                Toast.makeText(this, "That file had no usable words (one word per line).", Toast.LENGTH_LONG).show()
                return
            }
            val packName = name.ifBlank { "Custom" }
            getSharedPreferences("presspop_languages", MODE_PRIVATE).edit()
                .putString("custom_words", words.joinToString("\n"))
                .putString("custom_name", packName)
                .apply()
            Dictionary.customWords = words
            Dictionary.customLanguageName = packName
            setLanguage("custom", packName)
            findViewById<TextView>(R.id.customLangText).text =
                "Custom pack installed: $packName (${words.size} words). Tap the language button in the keyboard to cycle to it."
            Toast.makeText(this, "Installed $packName: ${words.size} words.", Toast.LENGTH_LONG).show()
        }
        val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                installCustomPack(langNameInput.text.toString().trim(), text)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not read that file.", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.importLangButton).setOnClickListener {
            importLauncher.launch("text/plain")
        }
        findViewById<Button>(R.id.downloadLangButton).setOnClickListener {
            val urlText = findViewById<EditText>(R.id.langUrlInput).text.toString().trim()
            if (urlText.isBlank()) {
                Toast.makeText(this, "Paste a URL to a plain-text word list first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                try {
                    val conn = java.net.URL(urlText).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    val text = conn.inputStream.bufferedReader().readText()
                    runOnUiThread { installCustomPack(langNameInput.text.toString().trim(), text) }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Download failed. Check the URL.", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        val errorToggleButton = findViewById<Button>(R.id.errorToggleButton)
        fun refreshErrorLabel() {
            errorToggleButton.text = if (ErrorReporter.isEnabled(this)) {
                "Anonymous error reports: On (tap to turn off)"
            } else {
                "Anonymous error reports: Off (tap to turn on)"
            }
        }
        refreshErrorLabel()
        errorToggleButton.setOnClickListener {
            ErrorReporter.setEnabled(this, !ErrorReporter.isEnabled(this))
            refreshErrorLabel()
        }
        findViewById<TextView>(R.id.errorInfoText).text =
            ErrorReporter.deviceInfo(this) +
            "\nCollected, if reporting is on: device info above + crash stack traces only.\n" +
            "Never: typed text, clipboard, personal words, accounts, or any identifier."
        findViewById<Button>(R.id.sendErrorsButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "PressPop error reports (anonymous)")
                putExtra(Intent.EXTRA_TEXT, ErrorReporter.reportText(this@MainActivity))
            }
            startActivity(Intent.createChooser(intent, "Send error reports"))
        }
        findViewById<Button>(R.id.clearErrorsButton).setOnClickListener {
            ErrorReporter.clear(this)
            Toast.makeText(this, "Stored error reports cleared.", Toast.LENGTH_SHORT).show()
        }

        // General debug mode (off by default): when on, swipe,
        // autocorrect, and suggestion taps each show one short
        // readout with what the feature saw and decided, so a
        // customer can reproduce a problem and email that line
        // instead of describing it. When off, the keyboard shows
        // no debug popups at all.
        val debugButton = findViewById<Button>(R.id.debugButton)
        fun refreshDebugLabel() {
            debugButton.text = if (DebugMode.isOn(this)) {
                "Debug mode: On (tap to turn off)"
            } else {
                "Debug mode: Off (tap to turn on)"
            }
        }
        refreshDebugLabel()
        debugButton.setOnClickListener {
            DebugMode.setOn(this, !DebugMode.isOn(this))
            refreshDebugLabel()
            Toast.makeText(
                this,
                if (DebugMode.isOn(this)) {
                    "Debug mode on. Swipe, autocorrect, and suggestion taps will show a short readout."
                } else {
                    "Debug mode off. No debug popups."
                },
                Toast.LENGTH_LONG
            ).show()
        }

        // Copy last debug line, Oct 29, 2026 (Wilson): the
        // in-keyboard banner only shows for 5 seconds, too fast
        // to copy by hand. Reproduce the problem once with debug
        // on, open this app, tap this button, then paste the line
        // into a message or email. If nothing has been shown yet,
        // it says so instead of copying a blank line.
        findViewById<Button>(R.id.copyDebugButton).setOnClickListener {
            val line = DebugMode.lastLine
            if (line.isNullOrEmpty()) {
                Toast.makeText(
                    this,
                    "No debug line yet. Turn debug on, reproduce it once, then tap this.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val clipboard =
                    getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("PressPop debug", line)
                )
                Toast.makeText(this, "Debug line copied. Paste it to send it.", Toast.LENGTH_SHORT).show()
            }
        }

        // Key tap pop sounds (on by default): a short pop on every
        // key tap, with a toggle here. Takes effect the next time
        // the keyboard opens.
        val keySoundsButton = findViewById<Button>(R.id.keySoundsButton)
        fun refreshKeySoundsLabel() {
            val on = getSharedPreferences("presspop_settings", MODE_PRIVATE)
                .getBoolean("key_sounds", true)
            keySoundsButton.text = if (on) {
                "Key sounds: On (tap to turn off)"
            } else {
                "Key sounds: Off (tap to turn on)"
            }
        }
        refreshKeySoundsLabel()
        keySoundsButton.setOnClickListener {
            val prefs = getSharedPreferences("presspop_settings", MODE_PRIVATE)
            val on = !prefs.getBoolean("key_sounds", true)
            prefs.edit().putBoolean("key_sounds", on).apply()
            refreshKeySoundsLabel()
            Toast.makeText(
                this,
                if (on) "Key sounds on." else "Key sounds off.",
                Toast.LENGTH_SHORT
            ).show()
        }

        val wordsText = findViewById<TextView>(R.id.wordsText)
        val wordInput = findViewById<EditText>(R.id.wordInput)
        // Code 36 (Wilson, Oct 30, 2026):
        // "Is there a way to see the learned
        // words that I can remove them?"
        // Previously, no: this screen only
        // listed MANUAL words, while learned
        // words (typed twice, or restored by
        // undo) were stored on-device but
        // never shown, so a learned typo like
        // "thr" could not be found to remove
        // it. Now both lists are shown, and
        // the Remove button (code 35, which
        // removes manual AND learned words)
        // works for either list: type the word
        // in the box above and tap Remove, and
        // it disappears from its list on refresh.
        fun refreshWords() {
            val manual = personal.manualWords().sorted()
            val learned = personal.learnedWords().sorted()
            val manualText = if (manual.isEmpty()) {
                "My words: none yet. Type a word above and tap Add."
            } else {
                "My words: " + manual.joinToString(", ")
            }
            val learnedText = if (learned.isEmpty()) {
                "Learned words: none yet."
            } else {
                "Learned words: " + learned.joinToString(", ")
            }
            wordsText.text = manualText + "\n" + learnedText +
                "\nTo remove a word from either list, type it above and tap Remove."
        }
        refreshWords()
        findViewById<Button>(R.id.addWordButton).setOnClickListener {
            val word = wordInput.text.toString()
            if (word.isBlank()) return@setOnClickListener
            personal.addManual(word)
            wordInput.text.clear()
            refreshWords()
            Toast.makeText(this, "Added to My Words.", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.removeWordButton).setOnClickListener {
            val word = wordInput.text.toString()
            if (word.isBlank()) return@setOnClickListener
            // Code 34: removeWord, not removeManual —
            // otherwise a LEARNED word (like "thr",
            // learned from typing it twice) could
            // never be removed from the app.
            personal.removeWord(word)
            wordInput.text.clear()
            refreshWords()
            Toast.makeText(this, "Removed from My Words.", Toast.LENGTH_SHORT).show()
        }
    }
}
