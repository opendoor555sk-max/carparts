package com.kabadi.calc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

class Note(val id: String, var t: Long, var text: String)

/**
 * Office notice board (Kabadi market). ONLY the admin writes; every phone reads.
 * The admin phone keeps the notices and sends the list (sealed) through the relay; other phones keep the newest list.
 */
object Notice {
    private const val MAX = 40
    private val TOPIC get() = "kmh-tn-" + Otp.topic("notice-board")
    private fun sp(ctx: Context) = ctx.getSharedPreferences("kabadi_office", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Note> = try {
        val a = JSONArray(sp(ctx).getString("n", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Note(it.optString("id"), it.optLong("t"), it.optString("x")) }.sortedByDescending { it.t }
    } catch (_: Exception) { emptyList() }

    private fun store(ctx: Context, l: List<Note>, stamp: Long) {
        val a = JSONArray(); l.sortedByDescending { it.t }.take(MAX).forEach { a.put(JSONObject().put("id", it.id).put("t", it.t).put("x", it.text)) }
        sp(ctx).edit().putString("n", a.toString()).putLong("stamp", stamp).apply()
    }

    // ---- admin ----
    fun add(ctx: Context, text: String) {
        val now = System.currentTimeMillis()
        store(ctx, list(ctx) + Note("n$now", now, text.trim().take(2000)), now); publishAsync(ctx)
    }
    fun edit(ctx: Context, n: Note, text: String) {
        store(ctx, list(ctx).map { if (it.id == n.id) Note(it.id, it.t, text.trim().take(2000)) else it }, System.currentTimeMillis()); publishAsync(ctx)
    }
    fun remove(ctx: Context, n: Note) { store(ctx, list(ctx).filter { it.id != n.id }, System.currentTimeMillis()); publishAsync(ctx) }

    fun publish(ctx: Context): Boolean {
        val j = JSONObject().put("t", sp(ctx).getLong("stamp", 0)).put("n", JSONArray().also { a ->
            list(ctx).forEach { a.put(JSONObject().put("id", it.id).put("t", it.t).put("x", it.text)) } }).toString()
        val ok = Scrap.postSealed(TOPIC, j)
        if (ok) sp(ctx).edit().putLong("pub", System.currentTimeMillis()).apply()
        return ok
    }
    fun publishAsync(ctx: Context) { Thread { try { publish(ctx) } catch (_: Exception) {} }.start() }

    /** admin phone, every ~15 min: the relay forgets after 12 h, so send again about every 6 h */
    fun tick(ctx: Context) {
        if (list(ctx).isEmpty()) return
        if (System.currentTimeMillis() - sp(ctx).getLong("pub", 0) > 6L * 3600_000L) publish(ctx)
    }

    // ---- everybody else ----
    /** take the newest list from the admin; true = changed */
    fun fetch(ctx: Context): Boolean {
        val best = Scrap.readSealed(TOPIC)?.map { it.second }?.maxByOrNull { it.optLong("t") } ?: return false
        if (best.optLong("t") <= sp(ctx).getLong("stamp", 0)) return false
        val a = best.optJSONArray("n") ?: return false
        store(ctx, (0 until a.length()).map { a.getJSONObject(it) }.map { Note(it.optString("id"), it.optLong("t"), it.optString("x")) }, best.optLong("t"))
        return true
    }

    // ---- unread ----
    fun unread(ctx: Context): Int { val seen = sp(ctx).getLong("seen", 0); return list(ctx).count { it.t > seen } }
    fun markSeen(ctx: Context) { sp(ctx).edit().putLong("seen", list(ctx).maxOfOrNull { it.t } ?: 0L).apply() }

    /** background (not the admin): fetch, and ring once for new notices */
    fun fetchAndNotify(ctx: Context, notify: Boolean) {
        if (Account.isAdmin(ctx)) return
        if (!fetch(ctx) || !notify) return
        val fresh = list(ctx).filter { it.t > sp(ctx).getLong("rung", 0) && it.t > sp(ctx).getLong("seen", 0) }
        if (fresh.isEmpty()) return
        sp(ctx).edit().putLong("rung", fresh.maxOf { it.t }).apply()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("office", L.t("off_title"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 18, Intent(ctx, MainActivity::class.java).putExtra("office", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "office") else @Suppress("DEPRECATION") Notification.Builder(ctx)
        b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("🏢 " + L.t("off_title") + " (" + fresh.size + ")")
            .setContentText(fresh.first().text.take(120)).setStyle(Notification.BigTextStyle().bigText(fresh.take(3).joinToString("\n\n") { it.text.take(300) }))
            .setContentIntent(open).setAutoCancel(true)
        try { nm.notify(18, b.build()) } catch (_: Exception) {}
    }
}
