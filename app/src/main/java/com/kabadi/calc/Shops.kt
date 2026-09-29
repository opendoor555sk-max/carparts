package com.kabadi.calc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** a shop that may guarantee credit: number ↔ owner (name + mobile) and a limit (default 10 lakh), all written by the admin */
class Shop(var no: String, var owner: String, var mobile: String, var limit: Double = Shops.LIMIT)

/**
 * Shop guarantee register (haraji / company hisab). Admin writes the shops; every phone gets the list.
 * A credit sale guaranteed by a shop uses the shop's limit: limit − (credit still to receive on all lines that shop guarantees).
 * When money comes in (kist / paid) the credit left gets smaller, so the limit grows back by itself.
 */
object Shops {
    const val LIMIT = 1_000_000.0
    private const val PART = 3000
    private val TOPIC get() = "kmh-shp-" + Otp.topic("shops")
    private val KEY get() = Otp.shareKey("1000000001")

    fun find(no: String): Shop? = if (no.isBlank()) null else Store.shops.firstOrNull { norm(it.no) == norm(no) }

    /** a credit line counts against the limit once it is confirmed ("OK – sold"), or it is in a final / old hisab */
    private fun committed(h: Hisab, l: Line) = l.sold || h.finalAt > 0 || h.step < 0

    /** every confirmed credit line (and its hisab) guaranteed by shop [no] */
    fun lines(no: String, skip: Line? = null): List<Pair<Hisab, Line>> {
        val all = Store.hisabs.toList() + Store.shared.map { it.h }
        return all.filter { it.type == "haraji" }.flatMap { h ->
            creditLines(h).filter { it !== skip && it.gBy.isEmpty() && needsGuarantor(h, it) && norm(it.shop) == norm(no) && committed(h, it) }.map { h to it }
        }
    }
    /** credit of that shop not yet paid back */
    fun used(no: String, skip: Line? = null): Double = lines(no, skip).sumOf { it.second.remaining() }
    fun left(s: Shop, skip: Line? = null): Double = s.limit - used(s.no, skip)

    // ---- registry to all phones (through the relay) ----
    private fun sp(ctx: Context) = ctx.getSharedPreferences("kabadi_share", Context.MODE_PRIVATE)
    private fun json(): String = JSONObject().put("t", Store.shopsT).put("s", JSONArray().also { a ->
        Store.shops.forEach { a.put(JSONObject().put("no", it.no).put("ow", it.owner).put("mb", it.mobile).put("lm", it.limit)) } }).toString()

    fun publish(ctx: Context): Boolean {
        val parts = Codec.seal(json(), KEY).chunked(PART)
        val k = "s-" + System.currentTimeMillis()
        var ok = true
        parts.forEachIndexed { i, p -> if (!Relay.post(TOPIC, JSONObject().put("k", k).put("i", i).put("n", parts.size).put("p", p))) ok = false }
        if (ok) sp(ctx).edit().putLong("shp_pub", System.currentTimeMillis()).apply()
        return ok
    }
    /** admin phone: the relay forgets after 12 h, so send again about every 6 h */
    fun republishIfDue(ctx: Context) {
        if (Account.ENABLED && !Account.isAdmin(ctx)) return
        if (Store.shops.isEmpty()) return
        if (System.currentTimeMillis() - sp(ctx).getLong("shp_pub", 0) > 6L * 3600_000L) publish(ctx)
    }
    /** other phones: take the newest list from the admin; true = changed */
    fun fetch(ctx: Context): Boolean {
        if (Account.ENABLED && Account.isAdmin(ctx)) return false
        val msgs = Relay.poll(TOPIC) ?: return false
        var best: JSONObject? = null
        msgs.map { it.second }.filter { it.has("k") }.groupBy { it.optString("k") }.forEach { (_, parts) ->
            val total = parts.first().optInt("n"); val byI = parts.associateBy { it.optInt("i") }
            if (byI.size < total) return@forEach
            val o = try { JSONObject(Codec.open((0 until total).joinToString("") { byI[it]!!.optString("p") }, KEY) ?: return@forEach) } catch (_: Exception) { return@forEach }
            if (best == null || o.optLong("t") > best!!.optLong("t")) best = o
        }
        val b = best ?: return false
        if (b.optLong("t") <= Store.shopsT) return false
        val a = b.optJSONArray("s") ?: return false
        Store.shops.clear()
        for (i in 0 until a.length()) a.getJSONObject(i).let { Store.shops.add(Shop(it.optString("no"), it.optString("ow"), it.optString("mb"), it.optDouble("lm", LIMIT))) }
        Store.shopsT = b.optLong("t")
        return true
    }
}

/** haraji credit sale guaranteed by a shop: the shop must be written by the admin and must have enough limit left. null = fine */
fun shopProblem(h: Hisab, l: Line): String? {
    if (h.type != "haraji" || !needsGuarantor(h, l) || l.gBy.isNotEmpty() || l.shop.isBlank()) return null
    val s = Shops.find(l.shop) ?: return L.t("shop_unknown")
    val left = Shops.left(s, l)
    return if (l.remaining() > left + 0.004) L.t("shop_limit") + " (" + L.t("shop") + " " + s.no + "): " + money(left.coerceAtLeast(0.0)) + " < " + money(l.remaining()) else null
}
