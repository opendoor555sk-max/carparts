package com.kabadimarket.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kabadimarket.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OTA update: checks GitHub for a newer APK, downloads it inside the app and
 * opens Android's installer. Android itself refuses the install unless the new
 * APK is signed with the same key, so only our own builds can update the app.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/opendoor555sk-max/carparts/releases/latest"

    data class Release(val version: Int, val name: String, val notes: String, val url: String, val size: Long)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Newer release than the installed one, or null. Never throws. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val j = JSONObject(resp.body?.string() ?: return@withContext null)
                val version = j.str("tag_name").removePrefix("apk-v").toIntOrNull() ?: return@withContext null
                if (version <= BuildConfig.VERSION_CODE) return@withContext null
                val asset = j.arr("assets").objects().firstOrNull { it.str("name").endsWith(".apk") } ?: return@withContext null
                Release(
                    version = version,
                    name = j.str("name"),
                    notes = j.str("body"),
                    url = asset.str("browser_download_url"),
                    size = asset.optLong("size", 0),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the APK, reporting progress 0..1. Returns the file, or null on failure. */
    suspend fun download(context: Context, r: Release, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "update-${r.version}.apk")
            client.newCall(Request.Builder().url(r.url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val total = if (r.size > 0) r.size else body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            // Safety: the file must be complete (same size as on GitHub).
            if (r.size > 0 && file.length() != r.size) {
                file.delete()
                return@withContext null
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    /** True if Android allows this app to open the installer. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
