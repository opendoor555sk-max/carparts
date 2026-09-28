package com.kabadi.calc

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Offline OTP: the admin's app and the user's app compute the same 6 digits
 * from (mobile + this phone's code). So the OTP works only for that mobile on that phone.
 */
object Otp {
    private const val K = "82cbafce597738edfb98f6ffb26f4eb65a37161ceb82ccd0"
    private const val ADMIN = "33013f11aa4e05aa7f874fc9d7d10e1d8b1bc93e66e4c514cdf4e98db0e84c82"

    private fun hmac(s: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(K.toByteArray(), "HmacSHA256")); doFinal(s.toByteArray())
    }

    /** short phone code shown to the user (6 letters/digits, easy to read) */
    fun deviceCode(androidId: String): String {
        val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val b = hmac("dev:" + androidId)
        return (0 until 6).joinToString("") { abc[(b[it].toInt() and 0xFF) % abc.length].toString() }
    }

    /** hard-to-guess topic name for the online relay */
    fun topic(s: String): String = hmac("topic:" + s).take(10).joinToString("") { "%02x".format(it) }

    /** secret key for hisab shared with one mobile number */
    fun shareKey(mobile: String): ByteArray = hmac("share:" + mobile10(mobile))

    fun mobile10(m: String) = m.filter { it.isDigit() }.takeLast(10)

    /** the 6-digit OTP for this mobile + phone code */
    fun code(mobile: String, dev: String): String {
        val b = hmac("otp:" + mobile10(mobile) + ":" + dev.trim().uppercase())
        val n = ((b[0].toInt() and 0x7F) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        return String.format(java.util.Locale.US, "%06d", n % 1_000_000)
    }

    fun check(mobile: String, dev: String, otp: String) = mobile10(mobile).length == 10 && code(mobile, dev) == otp.trim()

    fun isAdminCode(c: String): Boolean = MessageDigest.getInstance("SHA-256").digest(("kmh-admin:" + c.trim()).toByteArray())
        .joinToString("") { "%02x".format(it) } == ADMIN

    /** read mobile + phone code from a pasted WhatsApp request */
    fun parse(text: String): Pair<String, String>? {
        val mob = Regex("(?<!\\d)(?:\\+?91[ -]?)?([6-9]\\d{9})(?!\\d)").find(text)?.groupValues?.get(1) ?: return null
        val dev = Regex("#([A-Z2-9]{6})\\b").find(text.uppercase())?.groupValues?.get(1) ?: return null
        return mob to dev
    }
}
