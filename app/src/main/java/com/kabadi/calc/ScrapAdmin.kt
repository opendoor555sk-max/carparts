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

/** a trader in the admin's list. st: pending (asked) / otp (OTP given) / active / block (permanent) / removed */
class TraderRec(val key: String, var name: String, var mobile: String, var dev: String, var first: Long, var req: Long, var st: String,
                var decAt: Long = 0, var last: Long = 0, var manual: Boolean = false)

/**
 * Admin side of the traders' rate app (Scrap Bhav): trader list (OTP / block / remove), format of metals, rates board.
 * Everything is kept on the admin phone (the relay forgets after 12 h) and re-sent about every 6 h.
 */
object ScrapAdmin {
    /** the traders install this app (direct download) */
    const val APP_LINK = "https://github.com/opendoor555sk-max/carparts/releases/download/scrap-latest/ScrapBhav.apk"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("scrap_admin", Context.MODE_PRIVATE)

    // ---------- storage ----------
    fun traders(ctx: Context): MutableList<TraderRec> = try {
        val a = JSONArray(sp(ctx).getString("tr", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            TraderRec(it.optString("k"), it.optString("n"), it.optString("m"), it.optString("d"), it.optLong("f"), it.optLong("q"), it.optString("s"),
                it.optLong("da"), it.optLong("l"), it.optBoolean("man"))
        }.toMutableList()
    } catch (_: Exception) { mutableListOf() }

    private fun saveTraders(ctx: Context, l: List<TraderRec>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("k", it.key).put("n", it.name).put("m", it.mobile).put("d", it.dev).put("f", it.first).put("q", it.req).put("s", it.st).put("da", it.decAt).put("l", it.last).put("man", it.manual)) }
        sp(ctx).edit().putString("tr", a.toString()).apply()
    }

    private fun entries(ctx: Context): MutableList<JSONObject> = try {
        val a = JSONArray(sp(ctx).getString("en", "[]")); (0 until a.length()).map { a.getJSONObject(it) }.toMutableList()
    } catch (_: Exception) { mutableListOf() }

    private fun saveEntries(ctx: Context, l: List<JSONObject>) {
        val cut = System.currentTimeMillis() - Scrap.KEEP_DAYS * 86_400_000L
        val a = JSONArray(); l.filter { it.optLong("at") > cut }.forEach { a.put(it) }
        sp(ctx).edit().putString("en", a.toString()).apply()
    }

    /** every rate sheet kept (30 days), as entries */
    fun history(ctx: Context): List<Entry> {
        val tr = traders(ctx).associateBy { it.key }
        return entries(ctx).filter { tr[it.optString("k")]?.st !in listOf("block", "removed") }.map {
            val t = tr[it.optString("k")]
            Entry(it.optString("k"), t?.name ?: it.optString("n"), t?.mobile ?: it.optString("m"), it.optLong("at"), Scrap.ratesOf(it))
        }
    }
    /** entries that are still alive (before 7 PM) */
    fun board(ctx: Context): List<Entry> = history(ctx).filter { Scrap.alive(it.at) }

    // ---------- format ----------
    fun formatVer(ctx: Context) = sp(ctx).getLong("fver", 0)
    fun format(ctx: Context): List<Metal> = Scrap.parseFormat(sp(ctx).getString("fmt", "") ?: "")?.second?.takeIf { it.isNotEmpty() } ?: Scrap.DEFAULT

    /** save the format and send it to all trader apps */
    fun saveFormat(ctx: Context, list: List<Metal>): Boolean {
        val ver = System.currentTimeMillis()
        sp(ctx).edit().putString("fmt", Scrap.formatJson(ver, list)).putLong("fver", ver).putLong("fpub", 0).apply()
        try { publishBoard(ctx, true) } catch (_: Exception) {}
        return publishFormat(ctx)
    }
    fun publishFormat(ctx: Context): Boolean {
        val j = sp(ctx).getString("fmt", "") ?: ""
        if (j.isBlank()) return true
        val ok = Scrap.postSealed(Scrap.FORMAT, j)
        if (ok) sp(ctx).edit().putLong("fpub", System.currentTimeMillis()).apply()
        return ok
    }

    // ---------- collect from the relay ----------
    /** take new requests + rates from the relay; true = something changed; false = nothing / no internet */
    fun collect(ctx: Context): Boolean {
        var changed = false
        val reg = Relay.poll(Scrap.REG)
        val recs = traders(ctx)
        if (reg != null) {
            reg.map { it.second }.filter { it.has("t") }.sortedBy { it.optLong("t") }.forEach { m ->
                val mob = Otp.mobile10(m.optString("m")); val dev = m.optString("d"); val t = m.optLong("t")
                if (mob.length != 10 || dev.length != 6) return@forEach
                val key = "$mob:$dev"; val r = recs.firstOrNull { it.key == key }
                val blockedMobile = recs.any { it.mobile == mob && it.st == "block" }
                if (m.optInt("a") == 1) {   // trader typed the OTP
                    if (r != null && r.st in listOf("pending", "otp") && t >= r.req) { r.st = "active"; changed = true }
                } else if (r == null) {
                    recs.add(TraderRec(key, m.optString("n"), mob, dev, t, t, if (blockedMobile) "block" else "pending")); changed = true
                } else if (t > r.req && r.st != "block") {
                    r.req = t; r.name = m.optString("n").ifBlank { r.name }; r.st = if (blockedMobile) "block" else "pending"; changed = true
                }
            }
        }
        val rates = Scrap.readSealed(Scrap.RATES)
        if (rates != null) {
            val en = entries(ctx); val have = en.map { it.optString("k") + "|" + it.optLong("at") }.toHashSet()
            rates.map { it.second }.forEach { o ->
                val mob = Otp.mobile10(o.optString("m")); val dev = o.optString("d"); val at = o.optLong("at")
                if (mob.length != 10 || dev.length != 6 || at <= 0 || Scrap.ratesOf(o).isEmpty()) return@forEach
                val key = "$mob:$dev"
                if (!have.add("$key|$at")) return@forEach
                var r = recs.firstOrNull { it.key == key }
                if (r == null) {   // admin phone lost its list: a trader who sends rates is a real trader
                    if (recs.any { it.mobile == mob && it.st == "block" }) return@forEach
                    r = TraderRec(key, o.optString("n"), mob, dev, at, 0, "active"); recs.add(r)
                }
                if (r.st in listOf("block", "removed")) return@forEach
                if (r.st != "active") r.st = "active"
                if (at > r.last) r.last = at
                en.add(JSONObject().put("k", key).put("n", o.optString("n")).put("m", mob).put("at", at).put("r", o.optJSONObject("r") ?: JSONObject()))
                changed = true
            }
            if (changed) saveEntries(ctx, en)
        }
        if (changed) saveTraders(ctx, recs)
        return changed
    }

    // ---------- admin actions ----------
    /** the OTP for this trader is shown to the admin; the trader's app is told "approved – enter OTP" */
    fun giveOtp(ctx: Context, t: TraderRec): String {
        val otp = Scrap.otp(t.mobile, t.dev)
        val all = traders(ctx); val r = all.firstOrNull { it.key == t.key } ?: return otp
        if (r.st == "pending") r.st = "otp"
        r.decAt = System.currentTimeMillis(); saveTraders(ctx, all)
        Thread { Relay.post(Scrap.userTopic(t.mobile, t.dev), JSONObject().put("ok", true).put("rt", t.req)) }.start()
        return otp
    }

    fun block(ctx: Context, t: TraderRec): Boolean {
        val all = traders(ctx)
        all.filter { it.mobile == t.mobile }.forEach { it.st = "block"; it.decAt = System.currentTimeMillis() }   // every phone of that number
        saveTraders(ctx, all)
        publishBoardAsync(ctx)
        return sendBlock(t.mobile, t.dev)
    }
    private fun sendBlock(mobile: String, dev: String): Boolean {
        val a = Relay.post(Scrap.userTopic(mobile, dev), JSONObject().put("block", true))
        val b = Relay.post(Scrap.blockTopic(mobile), JSONObject().put("block", true))
        return a || b
    }

    fun remove(ctx: Context, t: TraderRec) {
        val all = traders(ctx)
        if (t.manual) all.removeAll { it.key == t.key }
        else all.firstOrNull { it.key == t.key }?.let { it.st = "removed"; it.decAt = System.currentTimeMillis() }
        saveTraders(ctx, all)
        publishBoardAsync(ctx)
        if (!t.manual) Thread { Relay.post(Scrap.userTopic(t.mobile, t.dev), JSONObject().put("remove", true).put("rt", t.req)) }.start()
    }

    /** a trader who has no phone app yet: admin writes his name and his rates */
    fun addManual(ctx: Context, name: String, mobile: String): TraderRec {
        val all = traders(ctx)
        val key = "man:" + System.currentTimeMillis()
        val r = TraderRec(key, name.trim(), Otp.mobile10(mobile).takeIf { it.length == 10 } ?: "", "", System.currentTimeMillis(), 0, "active", manual = true)
        all.add(r); saveTraders(ctx, all); return r
    }

    fun saveManual(ctx: Context, t: TraderRec, rates: Map<String, Double>) {
        if (rates.isEmpty()) return
        val now = System.currentTimeMillis()
        val en = entries(ctx)
        en.add(JSONObject().put("k", t.key).put("n", t.name).put("m", t.mobile).put("at", now)
            .put("r", JSONObject().also { r -> rates.forEach { (k, v) -> r.put(k, v) } }))
        saveEntries(ctx, en)
        val all = traders(ctx); all.firstOrNull { it.key == t.key }?.last = now; saveTraders(ctx, all)
        publishBoardAsync(ctx)
    }

    /** rates of this trader that are alive now (to fill the tiles again) */
    fun aliveRates(ctx: Context, key: String): Map<String, Double> {
        val m = HashMap<String, Double>()
        board(ctx).filter { it.key == key }.sortedBy { it.at }.forEach { m.putAll(it.rates) }
        return m
    }

    // ---------- board for every Kabadi phone (view only) ----------
    private fun boardJson(ctx: Context): String {
        val cut = System.currentTimeMillis()
        val a = JSONArray()
        entries(ctx).filter { Scrap.alive(it.optLong("at"), cut) }.forEach { e ->
            val t = traders(ctx).firstOrNull { it.key == e.optString("k") }
            if (t != null && t.st in listOf("block", "removed")) return@forEach
            a.put(JSONObject().put("k", e.optString("k")).put("n", t?.name ?: e.optString("n")).put("m", t?.mobile ?: e.optString("m")).put("at", e.optLong("at")).put("r", e.optJSONObject("r") ?: JSONObject()))
        }
        return JSONObject().put("t", System.currentTimeMillis()).put("f", JSONObject(sp(ctx).getString("fmt", "").takeIf { !it.isNullOrBlank() } ?: Scrap.formatJson(0, Scrap.DEFAULT))).put("e", a).toString()
    }

    /** send today's board to all phones when it changed, or every 6 h while there are rates (background thread) */
    fun publishBoard(ctx: Context, force: Boolean = false): Boolean {
        val j = boardJson(ctx)
        val body = JSONObject(j); body.remove("t")
        val hash = body.toString().hashCode()
        val now = System.currentTimeMillis()
        if (!force && sp(ctx).getInt("bhash", 0) == hash && now - sp(ctx).getLong("bpub2", 0) < 6L * 3600_000L) return false
        if (!force && body.getJSONArray("e").length() == 0 && sp(ctx).getInt("bhash", 0) == hash) return false
        val ok = Scrap.postSealed(Scrap.BOARD, j)
        if (ok) sp(ctx).edit().putInt("bhash", hash).putLong("bpub2", now).apply()
        return ok
    }
    fun publishBoardAsync(ctx: Context) { Thread { try { publishBoard(ctx) } catch (_: Exception) {} }.start() }

    fun pendingCount(ctx: Context) = traders(ctx).count { it.st == "pending" }

    // ---------- admin phone, every ~15 min ----------
    /** re-send the format and the blocks about every 6 h (the relay forgets after 12 h), collect, ring for new traders */
    fun tick(ctx: Context) {
        val now = System.currentTimeMillis()
        try { collect(ctx) } catch (_: Exception) {}
        try { publishBoard(ctx) } catch (_: Exception) {}
        if (now - sp(ctx).getLong("fpub", 0) > 6L * 3600_000L && sp(ctx).getString("fmt", "").orEmpty().isNotBlank()) try { publishFormat(ctx) } catch (_: Exception) {}
        if (now - sp(ctx).getLong("bpub", 0) > 6L * 3600_000L) {
            traders(ctx).filter { it.st == "block" && now - it.decAt < Scrap.KEEP_DAYS * 86_400_000L }.forEach { try { sendBlock(it.mobile, it.dev) } catch (_: Exception) {} }
            sp(ctx).edit().putLong("bpub", now).apply()
        }
        val pend = traders(ctx).filter { it.st == "pending" }
        val seen = sp(ctx).getString("seenP", "").orEmpty().split(",").toSet()
        val fresh = pend.filter { (it.key + "|" + it.req) !in seen }
        sp(ctx).edit().putString("seenP", pend.joinToString(",") { it.key + "|" + it.req }).apply()
        if (fresh.isEmpty()) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("otp_req", "OTP", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 17, Intent(ctx, MainActivity::class.java).putExtra("admin", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "otp_req") else @Suppress("DEPRECATION") Notification.Builder(ctx)
        b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("📊 " + L.t("sc_new_tr") + " (" + pend.size + ")")
            .setContentText(fresh.joinToString(", ") { it.name.ifBlank { it.mobile } }).setContentIntent(open).setAutoCancel(true)
        try { nm.notify(17, b.build()) } catch (_: Exception) {}
    }

    /** text of the board for WhatsApp */
    fun boardText(ctx: Context): String = boardText(format(ctx), board(ctx))

    fun boardText(fmt: List<Metal>, b: List<Entry>): String {
        val lg = L.lang
        val sb = StringBuilder("📊 " + L.t("sc_title") + "\n")
        fmt.forEach { m ->
            val q = Scrap.rank(m.id, b)
            if (q.isEmpty()) return@forEach
            sb.append("\n*").append(m.name(lg)).append("* (").append(m.unitText(lg)).append(")\n")
            q.forEachIndexed { i, x -> sb.append(i + 1).append(". ").append(Scrap.price(x.price)).append(" – ").append(x.name).append(if (x.mobile.isNotBlank()) " 📞 " + x.mobile else "").append("\n") }
        }
        return sb.toString().trim()
    }
}
