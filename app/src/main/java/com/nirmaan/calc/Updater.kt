package com.nirmaan.calc

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * OTA update: reads the GitHub releases of the repo, finds the newest "calc-v1.0.N" release,
 * and if N is bigger than this app's versionCode, downloads NirmaanCalc.apk and opens the installer.
 */
object Updater {
    private const val API = "https://api.github.com/repos/opendoor555sk-max/carparts/releases?per_page=30"
    private const val PREFIX = "calc-v1.0."
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    private class Rel(val code: Int, val name: String, val url: String, val notes: String)

    private val main = Handler(Looper.getMainLooper())

    fun myVersion(ctx: Context): Int {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    }

    fun myVersionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""

    /** Called on app start: checks at most every 6 hours, silent if nothing new or no internet. */
    fun autoCheck(act: Activity) {
        val sp = act.getSharedPreferences("nirmaan_update", Context.MODE_PRIVATE)
        val last = sp.getLong("last", 0)
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return
        sp.edit().putLong("last", System.currentTimeMillis()).apply()
        check(act, manual = false)
    }

    /** manual = true shows "already latest" / error messages too. */
    fun check(act: Activity, manual: Boolean) {
        if (manual) Toast.makeText(act, "Update check ho raha hai…", Toast.LENGTH_SHORT).show()
        Thread {
            val rel = try { fetchLatest() } catch (e: Exception) { null }
            main.post {
                if (act.isFinishing) return@post
                when {
                    rel == null -> if (manual) Toast.makeText(act, "Internet nahi mila, baad mein try karein", Toast.LENGTH_LONG).show()
                    rel.code > myVersion(act) -> ask(act, rel)
                    manual -> Toast.makeText(act, "Aapke paas sabse naya version hai (" + myVersionName(act) + ")", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun fetchLatest(): Rel? {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "NirmaanCalc")
        try {
            if (c.responseCode != 200) return null
            val arr = JSONArray(c.inputStream.bufferedReader().readText())
            var best: Rel? = null
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
                val tag = r.optString("tag_name")
                if (!tag.startsWith(PREFIX)) continue
                val code = tag.removePrefix(PREFIX).toIntOrNull() ?: continue
                val assets = r.optJSONArray("assets") ?: continue
                var url: String? = null
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
                }
                if (url == null) continue
                if (best == null || code > best.code) best = Rel(code, tag.removePrefix("calc-v"), url, r.optString("body"))
            }
            return best
        } finally {
            c.disconnect()
        }
    }

    private fun ask(act: Activity, rel: Rel) {
        val msg = "Naya version " + rel.name + " aaya hai (aapke paas " + myVersionName(act) + ").\n\n" +
            rel.notes.take(600) + "\n\nAbhi update karein?"
        AlertDialog.Builder(act)
            .setTitle("Update available")
            .setMessage(msg)
            .setPositiveButton("Update karein") { _, _ -> download(act, rel) }
            .setNegativeButton("Baad mein", null)
            .show()
    }

    private fun download(act: Activity, rel: Rel) {
        val ctx = act.applicationContext
        val dir = ctx.getExternalFilesDir(null)
        dir?.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val req = DownloadManager.Request(Uri.parse(rel.url))
            .setTitle("Nirmaan Calc " + rel.name)
            .setDescription("Update download ho raha hai")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, null, "NirmaanCalc-" + rel.name + ".apk")
        val id = dm.enqueue(req)
        Toast.makeText(act, "Download shuru… poora hote hi install ka button aayega", Toast.LENGTH_LONG).show()

        val rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                val uri = dm.getUriForDownloadedFile(id)
                if (uri == null) {
                    Toast.makeText(ctx, "Download fail ho gaya, dobara try karein", Toast.LENGTH_LONG).show()
                    return
                }
                val inst = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(inst)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Installer nahi khula: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
        val f = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(rcv, f, Context.RECEIVER_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") ctx.registerReceiver(rcv, f)
    }
}
