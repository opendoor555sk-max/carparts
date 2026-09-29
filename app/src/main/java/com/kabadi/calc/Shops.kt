package com.kabadi.calc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** a shop that may guarantee credit: number ↔ owner (name + mobile) and a limit (default 10 lakh), all written by the admin */
class Shop(var no: String, var owner: String, var mobile: String, var limit: Double = Shops.LIMIT, var market: String = "") {
    /** "Market 3 • Shop 220" */
    fun label() = (if (market.isNotBlank()) L.t("market") + " " + market.trim() + " • " else "") + L.t("shop") + " " + no.trim()
}

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

    /** the same shop number can be in different markets: a shop is (market number + shop number) */
    fun find(market: String, no: String): Shop? = if (no.isBlank()) null else Store.shops.firstOrNull { norm(it.no) == norm(no) && norm(it.market) == norm(market) }

    /** a credit line counts against the limit once it is confirmed ("OK – sold"), or it is in a final / old hisab */
    private fun committed(h: Hisab, l: Line) = l.sold || h.finalAt > 0 || h.step < 0

    /** one credit guaranteed by a shop: from this phone's hisab, or from another phone's (Store.shopUse) */
    class Use(val hid: Long, val market: String, val shop: String, val remaining: Double, val name: String, val mobile: String, val item: String, val time: Long, val line: Line? = null)
    class Remote(val t: Long, val uses: List<Use>)

    /** confirmed shop-guaranteed credit of the hisab on this phone (own + shared with this phone) */
    private fun localUses(): List<Use> {
        val all = Store.hisabs.toList() + Store.shared.map { it.h }
        return all.filter { it.type == "haraji" }.flatMap { h ->
            creditLines(h).filter { it.gBy.isEmpty() && needsGuarantor(h, it) && it.shop.isNotBlank() && committed(h, it) }
                .map { Use(h.id, it.mkt, it.shop, it.remaining(), it.cName, digits10(it.cMobile), it.name, h.time, it) }
        }
    }
    private fun same(u: Use, market: String, no: String) = norm(u.shop) == norm(no) && norm(u.market) == norm(market)

    /** every confirmed credit of shop [no] in [market] still to be paid: this phone's hisab + what the other phones told */
    fun uses(market: String, no: String, skip: Line? = null): List<Use> {
        val mine = localUses()
        val ids = mine.map { it.hid }.toSet() + (Store.hisabs.map { it.id } + Store.shared.map { it.h.id })
        val local = mine.filter { same(it, market, no) && it.line !== skip }
        val remote = Store.shopUse.values.flatMap { it.uses }.filter { it.hid !in ids && same(it, market, no) }
        return local + remote
    }
    /** credit of that shop not yet paid back */
    fun used(market: String, no: String, skip: Line? = null): Double = uses(market, no, skip).sumOf { it.remaining }
    fun left(s: Shop, skip: Line? = null): Double = s.limit - used(s.market, s.no, skip)

    // ---- registry to all phones (through the relay) ----
    private fun sp(ctx: Context) = ctx.getSharedPreferences("kabadi_share", Context.MODE_PRIVATE)
    private fun json(): String = JSONObject().put("t", Store.shopsT).put("s", JSONArray().also { a ->
        Store.shops.forEach { a.put(JSONObject().put("no", it.no).put("ow", it.owner).put("mb", it.mobile).put("lm", it.limit).put("mk", it.market)) } }).toString()

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
    // ---- what each phone has guaranteed by shops (so the limit is counted over ALL phones) ----
    private val USE_TOPIC get() = "kmh-shu-" + Otp.topic("shopuse")
    private fun useJson(): JSONArray = JSONArray().also { a -> localUses().filter { it.line != null && Store.hisabs.any { h -> h.id == it.hid } }.forEach { u ->
        a.put(JSONObject().put("h", u.hid).put("mk", u.market).put("sh", u.shop).put("r", u.remaining).put("n", u.name).put("m", u.mobile).put("i", u.item).put("d", u.time)) } }

    /** this phone's list (own hisab only) is sent when it changed, or every 6 h */
    fun publishUse(ctx: Context): Boolean {
        val arr = useJson(); val hash = arr.toString().hashCode()
        val last = sp(ctx).getLong("shu_pub", 0)
        if (sp(ctx).getInt("shu_hash", 0) == hash && System.currentTimeMillis() - last < 6L * 3600_000L) return false
        if (sp(ctx).getInt("shu_hash", 0) == hash && arr.length() == 0) return false
        val parts = Codec.seal(JSONObject().put("dv", Account.device(ctx)).put("t", System.currentTimeMillis()).put("l", arr).toString(), KEY).chunked(PART)
        val k = "u-" + Account.device(ctx) + "-" + System.currentTimeMillis()
        var ok = true
        parts.forEachIndexed { i, p -> if (!Relay.post(USE_TOPIC, JSONObject().put("k", k).put("i", i).put("n", parts.size).put("p", p))) ok = false }
        if (ok) sp(ctx).edit().putInt("shu_hash", hash).putLong("shu_pub", System.currentTimeMillis()).apply()
        return ok
    }
    /** take the lists of the other phones; true = something changed */
    fun fetchUse(ctx: Context): Boolean {
        val msgs = Relay.poll(USE_TOPIC) ?: return false
        val me = Account.device(ctx); var changed = false
        msgs.map { it.second }.filter { it.has("k") }.groupBy { it.optString("k") }.forEach { (_, parts) ->
            val total = parts.first().optInt("n"); val byI = parts.associateBy { it.optInt("i") }
            if (byI.size < total) return@forEach
            val o = try { JSONObject(Codec.open((0 until total).joinToString("") { byI[it]!!.optString("p") }, KEY) ?: return@forEach) } catch (_: Exception) { return@forEach }
            val dv = o.optString("dv"); val t = o.optLong("t")
            if (dv.isBlank() || dv == me || t <= (Store.shopUse[dv]?.t ?: 0L)) return@forEach
            val a = o.optJSONArray("l") ?: return@forEach
            Store.shopUse[dv] = Remote(t, (0 until a.length()).map { i -> a.getJSONObject(i).let { Use(it.optLong("h"), it.optString("mk"), it.optString("sh"), it.optDouble("r"), it.optString("n"), it.optString("m"), it.optString("i"), it.optLong("d")) } })
            changed = true
        }
        return changed
    }
    /** send mine, take the others (background) */
    fun syncUse(ctx: Context) {
        var ch = false
        try { publishUse(ctx) } catch (_: Exception) {}
        try { ch = fetchUse(ctx) } catch (_: Exception) {}
        if (ch) Store.save(ctx)
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
        for (i in 0 until a.length()) a.getJSONObject(i).let { Store.shops.add(Shop(it.optString("no"), it.optString("ow"), it.optString("mb"), it.optDouble("lm", LIMIT), it.optString("mk"))) }
        Store.shopsT = b.optLong("t")
        return true
    }
}

/** haraji credit sale guaranteed by a shop: the shop must be written by the admin and must have enough limit left. null = fine */
fun shopProblem(h: Hisab, l: Line): String? {
    if (h.type != "haraji" || !needsGuarantor(h, l) || l.gBy.isNotEmpty() || l.shop.isBlank()) return null
    val s = Shops.find(l.mkt, l.shop) ?: return L.t("shop_unknown")
    val left = Shops.left(s, l)
    return if (l.remaining() > left + 0.004) L.t("shop_limit") + " (" + s.label() + "): " + money(left.coerceAtLeast(0.0)) + " < " + money(l.remaining()) else null
}
