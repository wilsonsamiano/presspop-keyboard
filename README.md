# PressPop Keyboard

> Renamed Oct 29, 2026 from KeyPop Keyboard — the KeyPop name was already
> in use by other keyboards (keypop.co, App Store "Keypop: Keycap Keyboard
> Themes"). No features changed in the rename; package, repo, and all
> on-device preference names moved from keypop* to presspop*.

An Android keyboard (IME) with the iPhone-style key popup: press any key
and an enlarged bubble pops up above it while your finger is down.
Slide your finger and the popup follows; lift to type that key.

## What pops up

Every key, not just letters:
- Letters (QWERTY), Space bar (popup reads "space"), Shift (⇧),
  Delete (⌫, repeats while held), 123, Enter (⏎), Emoji (😀).

## Features (v2, all on-device, no network, no data sent anywhere)

- **Predictive hit areas — the original-iPhone trick.** The visible
  keys never move or resize, but the invisible touch zone of the most
  likely next letter is widened, using a built-in dictionary. A finger
  landing between two keys picks the letter the dictionary expects;
  a dead-centre tap on a neighbour still wins. This is how Apple's
  original iPhone keyboard worked (US Patent 8,232,973: hit regions
  grow for likely letters while the visible keyboard stays the same;
  e.g. after "T", the "H" zone grows so "the" is easier to type).
- **Swipe / glide typing.** Glide across letters and lift: the path
  is matched against the dictionary (personal words first). A blue
  trail follows your finger.
- **Emoji page.** Tap 😀 for a page of 30 common emoji, with the same
  enlarged popup. Tap ABC to go back.
- **Autocorrect + suggestion strip.** A strip above the keyboard
  shows completions as you type; tap one to replace the word. On
  Space, a word that is not in the dictionary is replaced by its
  closest dictionary word (edit distance 1–2). The original word
  stays in the left slot — tap it to undo the correction.
  Predictive text and autocorrect are always on — there is no
  toggle for them in the app.
- **Auto-capitalization.** The first letter is capitalized at the
  start of a field, after ". ", "? ", "! ", or a newline. A single
  shift (manual or automatic) applies to one letter only; double-tap
  Shift is still caps lock. It stays off in password, email, URL,
  number and phone fields.
- **Personal keyboard.** My Words in the app (add/remove by hand)
  are never autocorrected and are suggested first. Words you type
  twice in real use are also learned automatically; a one-off typo
  is not. Everything is stored only on the device.
- **Space-bar cursor.** Hold Space, then slide left/right to move
  the text cursor.
- **Double-tap Shift = caps lock. Double-tap Space = ". "**
- **Haptics** on keypress, and a **Dark / Light theme** toggle in
  the app (switch keyboards away and back to apply it).
- **Shift is a toggle** in v1 (tap on, tap off); double-tap locks it.

## Features added in v3 (the ones v2 was missing)

- **Long-press accent picker.** Hold a letter (a, e, i, o, u, n, c, s,
  y, l, z, r, t, d, g) and a row of accents appears above it; slide
  to the one you want and lift. Accented words are never
  autocorrected back to plain English.
- **Voice typing.** Tap 🎤, speak, and the transcript is inserted.
  Needs the microphone permission (tap "Allow microphone" in the
  app first). Honest limit: on most devices this uses the device's
  speech service, so it is NOT fully on-device like the rest of
  PressPop. If no speech service is available, it says so instead of
  crashing.
- **Clipboard history.** Tap 📋 in the suggestion strip to see up
  to 5 recent copies, tap one to paste. History is built while the
  keyboard runs (Android does not hand out full system history),
  stored on this device only, capped at 20 items, with a one-tap
  Clear in the panel. Clear it after pasting anything sensitive.
- **Number row.** Toggle it in the app: a 1–0 row above the letters.
- **One-handed mode.** In the app, cycle Full → Left-handed →
  Right-handed: the keyboard shrinks to 85% width and pins to that
  side. Switch keyboards away and back to apply theme, number row,
  and one-handed changes.

## Features added in v4

- **GIF / sticker search.** Tap GIF in the suggestion strip. The
  panel opens above the keyboard; type a search with the keyboard
  itself, tap Search, tap a GIF to insert it into apps that accept
  GIFs (Messages, WhatsApp, and similar — if the app does not
  accept GIFs, PressPop says so instead of silently doing nothing).
  Tap GIF/Sticker in the panel to switch to sticker-style results,
  Close to go back to typing. This needs a free Tenor API key:
  paste it in the app under "GIF / sticker search — Tenor API
  key". Without a key, the panel explains that instead of failing.
  Honest limit: GIF search is network-based, unlike the rest of
  PressPop, which is on-device.
- **Second language: Tagalog.** Tap EN/TL in the suggestion strip
  to switch the whole dictionary — predictions, swipe matches,
  suggestions and autocorrect — between English and Tagalog
  (a starter list of common Tagalog words; your personal words
  fill the gaps in either language).
- **Number pad.** From the 123 page, tap 🔢 for a telephone-style
  pad: 1-2-3 / 4-5-6 / 7-8-9 / * 0 #, plus ABC, space, delete and
  enter on the bottom row. For dialling, PINs, amounts, or any time
  a full keyboard is in the way. Tap 123 or ABC to go back.

## Features added in v5

- **Auto-update, within the app.** The app now checks GitHub
  releases automatically on open, at most once a day, silently if
  there is no update. If one is found, it offers a one-tap download
  and install: the APK downloads, then Android's installer opens
  and you tap Install. Honest limit: a sideloaded app (not from
  Google Play) cannot silently replace itself — Android always
  shows the Install prompt, and on Android 8+ you must first allow
  "Install unknown apps" for PressPop. That is the closest a
  sideloaded app can get to auto-update. The manual "Check for
  updates" button still works, and the auto-check can be toggled
  off in the app. Update rule, unchanged: release tag vN, where N
  == versionCode.
- **Installable languages — yes, other people can add theirs.**
  Built in now: English, Tagalog, Spanish. Tap the language button
  in the suggestion strip to cycle EN → TL → ES → custom → EN.
  For any other language, anyone can install a custom pack two
  ways, both in the app: import a plain-text word-list file (one
  word per line) or paste a URL to such a list and download it.
  The pack is stored on the device, becomes the active dictionary
  for predictions, swipe, suggestions and autocorrect, and is
  offered in the language cycle. No server, account, or app update
  is needed to add a language.
- **Anonymous error reporting.** Off by default; turn it on in the
  app. When on, crashes are recorded locally, up to 10 reports.
  Each report contains ONLY: device model, manufacturer, brand,
  Android version, CPU ABIs (+ cpuinfo model line if readable),
  total RAM, app version, language code, and the exception type,
  message and stack trace. It never contains typed text, clipboard
  contents, personal words, accounts, location, audio, GIF queries,
  or any identifier (no Android ID, serial, email, or phone).
  Nothing is sent automatically — tap "Send error reports" to
  share the stored reports via the system share sheet, to whoever
  you choose. "Clear stored error reports" deletes them.

## What v5 does NOT do yet

No handwriting input and no full second-language keyboard layout
(each language uses the same QWERTY layout, with its own
dictionary). Those are later features, not v5 bugs.

## Install and enable (after you install the APK)

1. Open the PressPop Keyboard app.
2. Tap "Enable keyboard", then turn on PressPop Keyboard.
3. Tap "Switch keyboard", then pick PressPop.
4. Open any text field and type.

## Build (copy-paste steps for Grok Build)

1. Open this project in Android Studio / Gradle.
2. Use Gradle 8.7+, JDK 17, compileSdk 34, minSdk 26, targetSdk 34.
3. If you have a release keystore, copy `keystore.properties.example`
   to `keystore.properties`, fill it in, and put the .p12 in the
   project root. Without it, the build falls back to debug signing
   and still installs for testing.
4. Build: `./gradlew :app:assembleRelease` (or assembleDebug).
5. Deliverable: `app/build/outputs/apk/release/app-release.apk`
   (or `debug/app-debug.apk` for a test build).
6. Versioning: versionCode 1 = "beta 0.1.0". Every release bumps
   versionCode by exactly 1, and the GitHub release tag is vN where
   N == versionCode (so v1, v2, v3...). The in-app update checker
   reads that tag.

Package: `com.wilson.presspopkeyboard`
Repo (to create): `wilsonsamiano/presspop-keyboard`

## Support

https://buymeacoffee.com/wilsonsamiano
