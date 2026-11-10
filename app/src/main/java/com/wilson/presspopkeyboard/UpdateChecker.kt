package com.wilson.presspopkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

/**
 * In-app updating for PressPop.
 *
 * What it is: the app checks GitHub releases (tag vN, where N ==
 * versionCode, so v1 is this build). If a newer tag exists, it offers
 * a download with a visible progress bar ("12.4 MB of 79.5 MB
 * (16%)"), then an Install button IN THE APP once the file is down
 * — the APK is downloaded to cache, and tapping Install opens
 * Android's installer. The button stays on the main screen until
 * the update is installed, so a missed or dismissed system prompt
 * never strands a finished download.
 *
 * What it is NOT: silent auto-update. A sideloaded app (not from
 * Google Play) cannot silently replace itself — Android always shows
 * the install prompt, and on Android 8+ the user must first allow
 * "Install unknown apps" for PressPop. That is an Android safety rule,
 * not a PressPop limit. The closest a sideloaded app can get is what
 * this does: check automatically, show the download, then one tap
 * on Install here + one system Install tap.
 *
 * Auto-check: MainActivity calls checkAuto() on open. It runs at
 * most once a day, silently if there is no update, and shows the
 * update dialog only if one is found. The manual button calls
 * check(manual = true), which always reports the result.
 * The repo below is a placeholder until the real repo is created;
 * until then the check simply reports that it could not reach the
 * feed, and never crashes the app.
 */
object UpdateChecker {
    private const val REPO = "wilsonsamiano/presspop-keyboard"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PREFS = "presspop_settings"
    private const val KEY_LAST_CHECK = "last_update_check"
    private const val KEY_DOWNLOADED_TAG = "downloaded_update_tag"
    private const val UPDATE_FILE = "presspop-update.apk"

    private fun updateFile(activity: Activity): File = File(activity.cacheDir, UPDATE_FILE)

    private fun installedCode(activity: Activity): Int =
        activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode.toInt()

    private fun formatMb(bytes: Long): String =
        String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

    /** The tag of a downloaded update that is newer than the installed
     * app, or null — the file alone is not enough, it has to be for
     * a version ahead of this one. */
    fun downloadedUpdateTag(activity: Activity): String? {
        val file = updateFile(activity)
        if (!file.exists() || file.length() <= 0) return null
        val tag = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOADED_TAG, null) ?: return null
        val code = tag.removePrefix("v").toIntOrNull() ?: return null
        return if (code > installedCode(activity)) tag else null
    }

    /** Fire the system installer for the downloaded APK. False when
     * there is nothing to install (file gone, or already installed). */
    fun installDownloadedApk(activity: Activity): Boolean {
        val tag = downloadedUpdateTag(activity) ?: return false
        val file = updateFile(activity)
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not open the installer for $tag.", Toast.LENGTH_LONG).show()
            false
        }
    }

    /** "v45 is downloaded" dialog, with the Install button in the app.
     * Shown when a download finishes, and instead of re-downloading
     * when the file is already there. */
    private fun showInstallDialog(activity: Activity, tag: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle("SafePop $tag is ready")
            .setMessage("The update is downloaded. Tap Install — Android shows its Install prompt, then the keyboard updates in place. If you tap Later, the Install button stays on this screen.")
            .setPositiveButton("Install") { _, _ -> installDownloadedApk(activity) }
            .setNegativeButton("Later", null)
            .show()
    }

    fun isAutoCheckEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("auto_update_check", true)

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("auto_update_check", enabled).apply()
    }

    /** Automatic check: at most once a day, silent when up to date. */
    fun checkAuto(activity: Activity) {
        if (!isAutoCheckEnabled(activity)) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < 24 * 60 * 60 * 1000) return
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        check(activity, manual = false)
    }

    fun check(activity: Activity, manual: Boolean = true) {
        Thread {
            try {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val tag = json.getString("tag_name") // e.g. "v2"
                val latestCode = tag.removePrefix("v").toIntOrNull() ?: 0
                val assets = json.optJSONArray("assets")
                var downloadUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name") == "app-release.apk") {
                            downloadUrl = asset.optString("browser_download_url")
                        }
                    }
                }
                if (downloadUrl.isNullOrEmpty()) {
                    downloadUrl = "https://github.com/$REPO/releases/download/$tag/app-release.apk"
                }
                val currentCode = activity.packageManager
                    .getPackageInfo(activity.packageName, 0).longVersionCode.toInt()
                val url = downloadUrl
                activity.runOnUiThread {
                    if (latestCode > currentCode) {
                        AlertDialog.Builder(activity)
                            .setTitle("SafePop update available: $tag")
                            .setMessage("Download it now? You will see the download progress, then an Install button here in the app.")
                            .setPositiveButton("Download") { _, _ ->
                                downloadAndInstall(activity, url, tag)
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    } else if (manual) {
                        Toast.makeText(activity, "You are up to date.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Could not check for updates. Check your connection and repo name.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }.start()
    }

    // Code 45 (Wilson, Nov 9, 2026): the download used to run
    // in total silence — one toast that vanished in 2 seconds,
    // then minutes of nothing while a ~79 MB file came down,
    // and the only way to install was the system prompt that
    // fired once when it finished. Now the download shows a
    // progress bar with MB and percent, and when it is done
    // there is an Install button in the app (plus one that
    // stays on the main screen), so a missed system prompt
    // never strands a finished download.
    private fun downloadAndInstall(activity: Activity, url: String, tag: String) {
        // Already downloaded for this tag? Skip the wait
        // and go straight to the Install button.
        if (downloadedUpdateTag(activity) == tag) {
            showInstallDialog(activity, tag)
            return
        }
        val statusText = TextView(activity).apply {
            text = "Starting download…"
            setPadding(48, 24, 48, 16)
        }
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
            addView(
                statusText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                progressBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        // Local vars cannot be @Volatile, so the cancel
        // flag and the connection live in atomics — the
        // Cancel button runs on the UI thread, the loop
        // on a worker thread.
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val connRef = AtomicReference<HttpURLConnection?>()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Downloading SafePop $tag")
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                cancelled.set(true)
                connRef.get()?.disconnect()
            }
            .create()
        dialog.show()
        Thread {
            var failed = false
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                connRef.set(conn)
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val total = conn.contentLengthLong
                val file = updateFile(activity)
                var downloaded = 0L
                var lastUiUpdate = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (cancelled.get()) break
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            downloaded += n
                            val now = SystemClock.uptimeMillis()
                            if (now - lastUiUpdate >= 200) {
                                lastUiUpdate = now
                                val done = downloaded
                                activity.runOnUiThread {
                                    if (total > 0) {
                                        val pct = (done * 100 / total).toInt()
                                        progressBar.isIndeterminate = false
                                        progressBar.progress = pct
                                        statusText.text =
                                            "${formatMb(done)} of ${formatMb(total)} ($pct%)"
                                    } else {
                                        progressBar.isIndeterminate = true
                                        statusText.text = "${formatMb(done)} downloaded…"
                                    }
                                }
                            }
                        }
                    }
                }
                if (cancelled.get()) {
                    file.delete()
                } else if (total > 0 && downloaded < total) {
                    // Stream ended early — a partial file must
                    // never be offered as an update.
                    file.delete()
                    failed = true
                } else if (downloaded <= 0) {
                    file.delete()
                    failed = true
                } else {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(KEY_DOWNLOADED_TAG, tag).apply()
                }
            } catch (e: Exception) {
                failed = true
                updateFile(activity).delete()
            }
            val wasCancelled = cancelled.get()
            val didFail = failed
            activity.runOnUiThread {
                if (dialog.isShowing) dialog.dismiss()
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                when {
                    wasCancelled -> {
                        Toast.makeText(activity, "Download cancelled.", Toast.LENGTH_SHORT).show()
                    }
                    didFail -> {
                        AlertDialog.Builder(activity)
                            .setTitle("Download failed")
                            .setMessage("SafePop $tag did not finish downloading. Check your connection and try again.")
                            .setPositiveButton("Retry") { _, _ ->
                                downloadAndInstall(activity, url, tag)
                            }
                            .setNegativeButton("Open releases") { _, _ ->
                                activity.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/$REPO/releases")
                                    )
                                )
                            }
                            .show()
                    }
                    else -> showInstallDialog(activity, tag)
                }
            }
        }.start()
    }
}
