package com.kabadi.calc

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The phone owner's account: name + mobile + 4-digit PIN.
 * First time the mobile must be approved with an OTP that the admin sends on WhatsApp.
 */
object Account {
    /** login / OTP on or off (off for now: the app opens straight away) */
    const val ENABLED = false
    private const val FILE = "kabadi_acct"
    /** admin's WhatsApp number (can be changed without an app update) */
    private const val ADMIN_URL = "https://raw.githubusercontent.com/opendoor555sk-max/carparts/kabadi-calc/kabadi-admin.txt"

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun hash(mobile: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("kmh:$mobile:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun exists(c: Context) = p(c).getString("mobile", "").orEmpty().isNotEmpty()
    fun name(c: Context) = p(c).getString("name", "").orEmpty()
    fun mobile(c: Context) = p(c).getString("mobile", "").orEmpty()
    fun locked(c: Context) = p(c).getBoolean("lock", false)
    fun setLock(c: Context, on: Boolean) = p(c).edit().putBoolean("lock", on).apply()

    /** approved with OTP for this mobile on this phone? */
    fun verified(c: Context) = p(c).getString("ok", "") == okToken(c, mobile(c))
    private fun okToken(c: Context, m: String) = hash("ok:" + m, device(c))
    fun signedIn(c: Context) = p(c).getBoolean("in", false)
    fun signOut(c: Context) = p(c).edit().putBoolean("in", false).apply()
    fun signIn(c: Context) = p(c).edit().putBoolean("in", true).apply()

    fun isAdmin(c: Context) = p(c).getString("adm", "") == hash("admin", device(c))
    fun makeAdmin(c: Context) = p(c).edit().putString("adm", hash("admin", device(c))).apply()

    @SuppressLint("HardwareIds")
    fun device(c: Context): String = Otp.deviceCode(Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "none")

    /** new account (not approved yet) */
    fun create(c: Context, name: String, mobile: String, pin: String) {
        // a new PIN / number always needs a fresh OTP (so nobody can reset a PIN without admin)
        p(c).edit().putString("name", name).putString("mobile", mobile).putString("pin", hash(mobile, pin))
            .remove("ok").putBoolean("in", false).apply()
    }

    /** OTP right → approved + signed in */
    fun approve(c: Context, otp: String): Boolean {
        if (!Otp.check(mobile(c), device(c), otp)) return false
        p(c).edit().putString("ok", okToken(c, mobile(c))).putBoolean("in", true).apply()
        return true
    }
    fun approveAsAdmin(c: Context) = p(c).edit().putString("ok", okToken(c, mobile(c))).putBoolean("in", true).apply()

    fun setPin(c: Context, pin: String) = p(c).edit().putString("pin", hash(mobile(c), pin)).apply()
    fun check(c: Context, pin: String) = hash(mobile(c), pin) == p(c).getString("pin", "")

    // ---- admin WhatsApp number ----
    fun adminNumber(c: Context) = p(c).getString("admNo", "").orEmpty()
    /** read the admin number from GitHub (background) */
    fun refreshAdminNumber(c: Context) {
        Thread {
            try {
                val con = URL(ADMIN_URL + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection
                con.connectTimeout = 8000; con.readTimeout = 8000
                if (con.responseCode == 200) {
                    val n = con.inputStream.bufferedReader().readText().filter { it.isDigit() }
                    if (n.length in 10..13) p(c).edit().putString("admNo", n).apply()
                }
                con.disconnect()
            } catch (_: Exception) {}
        }.start()
    }

    // ---- admin: list of approved / rejected requests ----
    fun log(c: Context, ok: Boolean, name: String, mobile: String, dev: String = "") {
        val a = try { JSONArray(p(c).getString("log", "[]")) } catch (_: Exception) { JSONArray() }
        a.put(JSONObject().put("t", System.currentTimeMillis()).put("ok", ok).put("n", name).put("m", mobile).put("d", dev))
        while (a.length() > 300) a.remove(0)
        p(c).edit().putString("log", a.toString()).apply()
    }
    fun logs(c: Context): List<JSONObject> {
        val a = try { JSONArray(p(c).getString("log", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).map { a.getJSONObject(it) }.reversed()
    }
}
