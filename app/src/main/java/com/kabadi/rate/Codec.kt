package com.kabadi.rate

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** gzip + AES-GCM + base64 for hisab sent to a partner's phone (pure JVM, testable) */
object Codec {
    private const val ABC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun b64(b: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < b.size) {
            val n = ((b[i].toInt() and 0xFF) shl 16) or ((if (i + 1 < b.size) b[i + 1].toInt() and 0xFF else 0) shl 8) or (if (i + 2 < b.size) b[i + 2].toInt() and 0xFF else 0)
            sb.append(ABC[(n shr 18) and 63]).append(ABC[(n shr 12) and 63])
            sb.append(if (i + 1 < b.size) ABC[(n shr 6) and 63] else '=').append(if (i + 2 < b.size) ABC[n and 63] else '=')
            i += 3
        }
        return sb.toString()
    }

    fun unb64(s0: String): ByteArray {
        val s = s0.filter { it != '\n' && it != '\r' && it != ' ' }
        val out = ByteArrayOutputStream()
        var i = 0
        while (i + 3 < s.length) {
            val c = IntArray(4) { k -> if (s[i + k] == '=') 0 else ABC.indexOf(s[i + k]) }
            val n = (c[0] shl 18) or (c[1] shl 12) or (c[2] shl 6) or c[3]
            out.write((n shr 16) and 0xFF)
            if (s[i + 2] != '=') out.write((n shr 8) and 0xFF)
            if (s[i + 3] != '=') out.write(n and 0xFF)
            i += 4
        }
        return out.toByteArray()
    }

    fun seal(text: String, key: ByteArray): String {
        val z = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(text.toByteArray()) } }.toByteArray()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOf(16), "AES"), GCMParameterSpec(128, iv))
        return b64(iv + c.doFinal(z))
    }

    /** null = not for this key / broken */
    fun open(data: String, key: ByteArray): String? = try {
        val b = unb64(data)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOf(16), "AES"), GCMParameterSpec(128, b.copyOfRange(0, 12)))
        val z = c.doFinal(b.copyOfRange(12, b.size))
        GZIPInputStream(ByteArrayInputStream(z)).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Exception) { null }
}
