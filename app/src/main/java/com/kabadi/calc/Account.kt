package com.kabadi.calc

import android.content.Context
import java.security.MessageDigest

/** the phone owner's account: name + mobile + 4-digit PIN (kept on this phone) */
object Account {
    private const val FILE = "kabadi_acct"
    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun hash(mobile: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("kmh:$mobile:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun exists(c: Context) = p(c).getString("mobile", "").orEmpty().isNotEmpty()
    fun name(c: Context) = p(c).getString("name", "").orEmpty()
    fun mobile(c: Context) = p(c).getString("mobile", "").orEmpty()
    fun locked(c: Context) = p(c).getBoolean("lock", true)
    fun setLock(c: Context, on: Boolean) = p(c).edit().putBoolean("lock", on).apply()

    fun create(c: Context, name: String, mobile: String, pin: String) {
        p(c).edit().putString("name", name).putString("mobile", mobile).putString("pin", hash(mobile, pin)).putBoolean("lock", true).apply()
    }
    fun setPin(c: Context, pin: String) = p(c).edit().putString("pin", hash(mobile(c), pin)).apply()
    fun check(c: Context, pin: String) = hash(mobile(c), pin) == p(c).getString("pin", "")
}
