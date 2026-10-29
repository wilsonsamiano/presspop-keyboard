package com.wilson.presspopkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * In-app updating for PressPop.
 *
 * What it is: the app checks GitHub releases (tag vN, where N ==
 * versionCode, so v1 is this build). If a newer tag exists, it offers
 * a one-tap download and install: the APK is downloaded to cache,
 * then Android's installer opens and the user taps Install.
 *
 * What it is NOT: silent auto-update. A sideloaded app (not from
 * Google Play) cannot silently replace itself — Android always shows
 * the install prompt, and on Android 8+ the user must first allow
 * "Install unknown apps" for PressPop. That is an Android safety rule,
 * not a PressPop limit. The closest a sideloaded app can get is what
 * this does: check automatically, then one tap + one system Install
 * tap.
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
                            .setTitle("PressPop update available: $tag")
                            .setMessage("Download and install it now? Android will show an Install prompt — tap Install to finish.")
                            .setPositiveButton("Download and install") { _, _ ->
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

    private fun downloadAndInstall(activity: Activity, url: String, tag: String) {
        Toast.makeText(activity, "Downloading $tag…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val file = File(activity.cacheDir, "presspop-update.apk")
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                val uri: Uri = FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.runOnUiThread { activity.startActivity(intent) }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Download failed. Opening releases instead.",
                        Toast.LENGTH_LONG
                    ).show()
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/$REPO/releases")
                        )
                    )
                }
            }
        }.start()
    }
}
