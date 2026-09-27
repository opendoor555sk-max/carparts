package com.kabadimarket.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Share {
    /** Opens WhatsApp with the text ready to send (falls back to the normal share menu). */
    fun whatsApp(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            intent.setPackage(null)
            try {
                context.startActivity(Intent.createChooser(intent, "Share"))
            } catch (_: Exception) {
            }
        }
    }

    /** WhatsApp chat with a specific number (old app: wa.me link). */
    fun waMe(context: Context, text: String, phone: String) {
        val digits = phone.filter { it.isDigit() }
        openUrl(context, "https://wa.me/$digits?text=" + Uri.encode(text))
    }

    /** Saves a downloaded file (e.g. Excel) and opens the share menu for it. */
    fun file(context: Context, bytes: ByteArray, fileName: String, mime: String) {
        try {
            // Names like "Shree Auto/Garage" must not break the file path.
            val safe = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, safe)
            file.writeBytes(bytes)
            val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, safe))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, e.message ?: "Share failed", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
}
