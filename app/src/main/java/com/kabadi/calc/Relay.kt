package com.kabadi.calc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online OTP requests through ntfy.sh (free, no account):
 *  user app  → request topic  → admin app (list + accept / reject)
 *  admin app → user's topic   → user app (OTP filled automatically)
 * Messages stay 12 hours on the server. Call these from a background thread.
 */
object Relay {
    private const val HOST = "https://ntfy.sh/"
    val REQ = "kmh-r-" + Otp.topic("requests")
    /** each user's app says "I am using the app" (no money, only counts) */
    val USE = "kmh-s-" + Otp.topic("usage")
    fun userTopic(mobile: String, dev: String) = "kmh-u-" + Otp.topic(Otp.mobile10(mobile) + ":" + dev)

    class Req(val id: String, val time: Long, val name: String, val mobile: String, val dev: String)

    fun post(topic: String, body: JSONObject): Boolean = try {
        val c = URL(HOST + topic).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 10000
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = c.responseCode in 200..299
        c.disconnect(); ok
    } catch (_: Exception) { false }

    /** messages of the last 12 hours (oldest first); null = no internet */
    fun poll(topic: String): List<Pair<JSONObject, JSONObject>>? = try {
        val c = URL(HOST + topic + "/json?poll=1&since=12h").openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        val out = mutableListOf<Pair<JSONObject, JSONObject>>()
        if (c.responseCode == 200) c.inputStream.bufferedReader().forEachLine { line ->
            try {
                val o = JSONObject(line)
                if (o.optString("event") == "message") out.add(o to JSONObject(o.optString("message")))
            } catch (_: Exception) {}
        }
        c.disconnect(); out
    } catch (_: Exception) { null }

    // ---- user side ----
    fun sendRequest(name: String, mobile: String, dev: String) =
        post(REQ, JSONObject().put("n", name).put("m", Otp.mobile10(mobile)).put("d", dev))

    /** admin's answer for this phone: "123456" = approved, "" = rejected, null = nothing yet */
    fun answer(mobile: String, dev: String): String? {
        val last = poll(userTopic(mobile, dev))?.lastOrNull()?.second ?: return null
        return if (last.optBoolean("ok")) last.optString("otp") else ""
    }

    /** latest word from admin for this phone: "ok", "no", "block" (null = nothing / no internet) */
    fun status(mobile: String, dev: String): String? {
        val last = poll(userTopic(mobile, dev))?.lastOrNull()?.second ?: return null
        return when { last.optBoolean("block") -> "block"; last.optBoolean("ok") -> "ok"; else -> "no" }
    }

    /** admin: block a user (his app stops until approved again) */
    fun block(ctx: Context, name: String, mobile: String, dev: String): Boolean {
        val ok = post(userTopic(mobile, dev), JSONObject().put("block", true))
        if (ok) { Account.log(ctx, false, name, mobile, dev, "block"); ctx.getSharedPreferences("kabadi_acct", Context.MODE_PRIVATE).edit().putLong("rb_$mobile:$dev", System.currentTimeMillis()).apply() }
        return ok
    }

    /** server keeps messages 12 h: repeat the block for blocked users every 6 h (admin phone) */
    fun reBlock(ctx: Context) {
        val sp = ctx.getSharedPreferences("kabadi_acct", Context.MODE_PRIVATE)
        Account.users(ctx).filter { it.optString("s") == "block" }.forEach { u ->
            val k = u.optString("m") + ":" + u.optString("d")
            if (System.currentTimeMillis() - sp.getLong("rb_$k", 0) > 6L * 3600_000L &&
                post(userTopic(u.optString("m"), u.optString("d")), JSONObject().put("block", true))) sp.edit().putLong("rb_$k", System.currentTimeMillis()).apply()
        }
    }

    /** user side: at most every 6 hours, tell the admin this phone uses the app */
    fun ping(ctx: Context) {
        val sp = ctx.getSharedPreferences("kabadi_acct", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - sp.getLong("ping", 0) < 6L * 3600_000L) return
        val hs = Store.hisabs
        val o = JSONObject().put("n", Account.name(ctx)).put("m", Account.mobile(ctx)).put("d", Account.device(ctx))
            .put("v", Updater.myVersionName(ctx)).put("ph", (Build.MANUFACTURER + " " + Build.MODEL).trim())
            .put("h", hs.size).put("g", hs.count { it.type == "gaadi" }).put("hr", hs.count { it.type == "haraji" }).put("l", hs.count { it.isLot })
            .put("last", hs.maxOfOrNull { it.time } ?: 0L).put("own", Store.owner).put("adm", Account.isAdmin(ctx))
        if (post(USE, o)) sp.edit().putLong("ping", System.currentTimeMillis()).apply()
    }

    /** admin side: keep every ping (the server forgets after 12 h, this phone remembers) */
    fun collect(ctx: Context): Boolean {
        val all = poll(USE) ?: return false
        val sp = ctx.getSharedPreferences("kabadi_acct", Context.MODE_PRIVATE)
        val map = try { JSONObject(sp.getString("seen", "{}")) } catch (_: Exception) { JSONObject() }
        all.forEach { (o, m) ->
            val k = m.optString("m") + ":" + m.optString("d")
            if (m.optString("m").isEmpty()) return@forEach
            val t = o.optLong("time") * 1000
            val old = map.optJSONObject(k)
            if (old == null || old.optLong("at") < t) {
                map.put(k, JSONObject(m.toString()).put("at", t).put("first", old?.optLong("first")?.takeIf { it > 0 } ?: t))
            }
        }
        sp.edit().putString("seen", map.toString()).apply()
        return true
    }

    fun seen(ctx: Context): Map<String, JSONObject> {
        val map = try { JSONObject(ctx.getSharedPreferences("kabadi_acct", Context.MODE_PRIVATE).getString("seen", "{}")) } catch (_: Exception) { JSONObject() }
        return map.keys().asSequence().associateWith { map.getJSONObject(it) }
    }

    // ---- admin side ----
    /** requests of the last 12 h that are not accepted / rejected yet (newest first) */
    fun pending(ctx: Context): List<Req>? {
        // decided after the request = done (a blocked / cancelled user can ask again)
        val done = Account.users(ctx).associate { (it.optString("m") + ":" + it.optString("d")) to it.optLong("t") }
        val all = poll(REQ) ?: return null
        return all.map { (o, m) -> Req(o.optString("id"), o.optLong("time") * 1000, m.optString("n"), m.optString("m"), m.optString("d")) }
            .filter { it.mobile.length == 10 && it.dev.length == 6 && (done[it.mobile + ":" + it.dev] ?: 0L) < it.time }
            .distinctBy { it.mobile + ":" + it.dev }.reversed()
    }

    fun decide(ctx: Context, r: Req, ok: Boolean): Boolean {
        val body = JSONObject().put("ok", ok)
        if (ok) body.put("otp", Otp.code(r.mobile, r.dev))
        val sent = post(userTopic(r.mobile, r.dev), body)
        if (sent) Account.log(ctx, ok, r.name, r.mobile, r.dev)
        return sent
    }

    /** admin phone, every ~15 min: notification for new requests */
    fun notifyAdmin(ctx: Context) {
        if (!Account.isAdmin(ctx)) return
        try { reBlock(ctx) } catch (_: Exception) {}
        try { collect(ctx) } catch (_: Exception) {}
        val p = pending(ctx) ?: return
        val sp = ctx.getSharedPreferences("kabadi_acct", Context.MODE_PRIVATE)
        val seen = sp.getString("seenReq", "").orEmpty().split(",").toSet()
        val fresh = p.filter { it.id !in seen }
        sp.edit().putString("seenReq", p.joinToString(",") { it.id }).apply()
        if (fresh.isEmpty()) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("otp_req", "OTP", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 14, Intent(ctx, MainActivity::class.java).putExtra("admin", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "otp_req") else @Suppress("DEPRECATION") Notification.Builder(ctx)
        b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("🔔 OTP request (" + p.size + ")")
            .setContentText(fresh.joinToString(", ") { it.name.ifBlank { it.mobile } })
            .setContentIntent(open).setAutoCancel(true)
        try { nm.notify(8, b.build()) } catch (_: Exception) {}
    }
}
