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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PressPop setup screen + personal keyboard controls.
 * - Enable / switch the keyboard (Android requires this by hand).
 * - Dark / light theme for the keyboard.
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
            val dark = settings.getBoolean("dark_mode", false)
            themeButton.text = if (dark) "Theme: Dark (tap for Light)" else "Theme: Light (tap for Dark)"
        }
        refreshThemeLabel()
        themeButton.setOnClickListener {
            val dark = !settings.getBoolean("dark_mode", false)
            settings.edit().putBoolean("dark_mode", dark).apply()
            refreshThemeLabel()
            Toast.makeText(
                this,
                "Theme saved. Switch keyboards away and back to apply it.",
                Toast.LENGTH_LONG
            ).show()
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
            Dictionary.setLanguageCode(code)
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

        val wordsText = findViewById<TextView>(R.id.wordsText)
        val wordInput = findViewById<EditText>(R.id.wordInput)
        fun refreshWords() {
            val words = personal.manualWords().sorted()
            wordsText.text = if (words.isEmpty()) {
                "My words: none yet. Type a word above and tap Add."
            } else {
                "My words: " + words.joinToString(", ")
            }
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
            personal.removeManual(word)
            wordInput.text.clear()
            refreshWords()
            Toast.makeText(this, "Removed from My Words.", Toast.LENGTH_SHORT).show()
        }
    }
}
