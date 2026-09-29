package com.kabadi.calc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** one line of another person's hisab that concerns this phone's number (he sold us / we owe / we get) */
class LinkLine(val name: String, val amount: Double, val credit: Boolean, val due: Long, val got: Double, val left: Double, val youPay: Boolean, val info: Boolean = false)
/** the lines of one hisab of someone else that concern this phone (a buyer, a seller, a service) – not the whole hisab */
class Linked(var from: String, var fromMobile: String, var hid: Long, var title: String, var time: Long, var t: Long, var lines: List<LinkLine>)

/** a hisab someone else made, where this phone's number is mudi malik / khedut (view only) */
class Shared(var from: String, var fromMobile: String, var t: Long, var h: Hisab)

/**
 * Mudi malik / khedut link by mobile number: the maker's app sends the hisab (encrypted, only that
 * number's app can open it) through the relay; the partner's app shows it read-only.
 * The relay keeps messages 12 h, so the maker's app sends again when the hisab changes or every 6 h.
 */
object Share {
    private const val PART = 3000
    fun topic(m: String) = "kmh-p-" + Otp.topic("p:" + Otp.mobile10(m))
    /** this phone's number for hisab sharing: the number written in Settings (the owner's own); the login number only if Settings is empty */
    fun myMobile(ctx: Context): String {
        val st = Otp.mobile10(Store.mobile)
        return if (st.length == 10) st else Otp.mobile10(Account.mobile(ctx))
    }
    /** who gets the WHOLE hisab (view only): mudi malik, khedut, mehta. Company partners get only their own share and what concerns them (see contacts) */
    fun targetsOf(h: Hisab): List<String> =
        listOf(h.mudiMobile, h.khedMobile, h.mehtaMobile).map { Otp.mobile10(it) }.filter { it.length == 10 }.distinct()
    fun targets(ctx: Context, h: Hisab): List<String> {
        val me = myMobile(ctx)
        return targetsOf(h).filter { it != me }
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("kabadi_share", Context.MODE_PRIVATE)

    private fun post(ctx: Context, m: String, payload: String, id: String): Boolean {
        val parts = Codec.seal(payload, Otp.shareKey(m)).chunked(PART)
        val k = id + "-" + System.currentTimeMillis()
        var ok = true
        parts.forEachIndexed { i, p -> if (!Relay.post(topic(m), JSONObject().put("k", k).put("i", i).put("n", parts.size).put("p", p))) ok = false }
        return ok
    }

    /**
     * Others in this hisab who are not partners (buyers, the one we bought from, service givers): each gets only
     * HIS lines, once they are confirmed (sold OK / buying OK / expenses OK / final).
     */
    fun contacts(ctx: Context, h: Hisab): Map<String, List<LinkLine>> {
        val skip = targetsOf(h).toSet() + myMobile(ctx)
        val guard = h.step >= 0; val fin = h.finalAt > 0
        val buyOk = fin || (guard && h.step >= 2); val kOk = fin || (guard && h.step >= 3)
        val out = LinkedHashMap<String, MutableList<LinkLine>>()
        fun add(l: Line, youPay: Boolean, name: String) {
            val m = Otp.mobile10(l.cMobile)
            if (m.length != 10 || m in skip || l.value() == 0.0) return
            out.getOrPut(m) { mutableListOf() }.add(LinkLine(name, l.value(), l.udhaar, dueTime(h, l) ?: 0L, l.received(), l.remaining(), youPay))
        }
        if (buyOk) add(h.buyLine, false, L.ln(h.buyLine) + " " + h.vehicleMsg())
        if (kOk) h.kharch.forEach { add(it, false, it.name) }
        h.maal.filter { fin || (guard && it.sold) }.forEach { l ->
            add(l, true, l.name + (if (!l.fixed && l.kg != 0.0) " " + plain(l.kg) + (if (l.litre) " L" else " kg") + " × " + plain(l.rate) else "") + Notify.guarText(h, l).let { if (it.isBlank()) "" else "\n" + it })
        }
        if (fin && h.sale != 0.0) add(h.saleLine, true, L.ln(h.saleLine))
        // company partners: only the vehicle (in the title), his invest, and after Final the auction price + his profit / loss. No cost, no place.
        if (h.type == "haraji") h.partners.filter { it.share > 0 }.forEach { p ->
            val m = Otp.mobile10(p.mobile)
            if (m.length != 10 || m in skip || !buyOk) return@forEach
            val list = out.getOrPut(m) { mutableListOf() }
            val inv = if (kOk) h.partnerLagat(p) else h.price * p.share / 100
            list.add(LinkLine("🏢 " + L.t("your_invest") + " (" + plain(p.share) + "%)", inv, false, 0L, 0.0, 0.0, true, true))
            if (fin) {
                if (h.sale != 0.0) list.add(LinkLine("🔨 " + L.t(if (h.isCo) "co_give" else "sale_s"), h.sale, false, 0L, 0.0, 0.0, false, true))
                val pm = h.partnerMunafa(p)
                list.add(LinkLine("📊 " + (if (pm >= 0) L.t("your_profit") else L.t("your_loss")), Math.abs(pm), false, 0L, 0.0, 0.0, pm < 0, true))
            }
        }
        return out
    }

    /** somebody must be told (partners and / or contacts) */
    fun anyone(ctx: Context, h: Hisab) = targets(ctx, h).isNotEmpty() || contacts(ctx, h).isNotEmpty()

    private fun publishContacts(ctx: Context, h: Hisab): Int {
        var n = 0
        val title = h.party.ifBlank { listOf(h.mudiName, h.khedName).filter { it.isNotBlank() }.joinToString(" / ") }.ifBlank { h.vehicleInfo() }
        contacts(ctx, h).forEach { (m, lines) ->
            val c = JSONObject().put("hid", h.id).put("ti", title).put("tm", h.time).put("ln", JSONArray().also { a -> lines.forEach { l ->
                a.put(JSONObject().put("n", l.name).put("a", l.amount).put("cr", l.credit).put("du", l.due).put("gt", l.got).put("lf", l.left).put("yp", l.youPay).put("in", l.info)) } })
            val hash = c.toString().hashCode()
            if (sp(ctx).getInt("cx_${h.id}_$m", 0) == hash) return@forEach
            val payload = JSONObject().put("f", Store.owner.ifBlank { Account.name(ctx) }).put("fm", myMobile(ctx)).put("t", System.currentTimeMillis()).put("c", c).toString()
            if (post(ctx, m, payload, "c" + h.id)) { sp(ctx).edit().putInt("cx_${h.id}_$m", hash).apply(); n++ }
        }
        return n
    }

    /** send one hisab to its partners (whole hisab) and each contact (his lines); returns how many phones */
    fun publish(ctx: Context, h: Hisab): Int {
        var n = 0
        val to = targets(ctx, h)
        if (to.isNotEmpty()) {
            val payload = JSONObject().put("f", Store.owner.ifBlank { Account.name(ctx) }).put("fm", myMobile(ctx))
                .put("t", System.currentTimeMillis()).put("h", Store.hj(h)).toString()
            to.forEach { m -> if (post(ctx, m, payload, h.id.toString())) n++ }
        }
        n += publishContacts(ctx, h)
        if (n > 0) sp(ctx).edit().putLong("t_${h.id}", System.currentTimeMillis()).putInt("x_${h.id}", Store.hj(h).toString().hashCode()).apply()
        return n
    }

    /** send only when the hisab is different from what was sent last */
    fun publishIfChanged(ctx: Context, h: Hisab): Int {
        if (sp(ctx).getInt("x_${h.id}", 0) == Store.hj(h).toString().hashCode()) return 0
        return publish(ctx, h)
    }

    /** on app start: send again what changed, or what was sent more than 6 h ago (last 90 days) */
    fun republish(ctx: Context) {
        val now = System.currentTimeMillis()
        Store.hisabs.toList().filter { now - it.time < 90L * 86400000L && anyone(ctx, it) }.forEach { h ->
            val changed = sp(ctx).getInt("x_${h.id}", 0) != Store.hj(h).toString().hashCode()
            if (changed || now - sp(ctx).getLong("t_${h.id}", 0) > 6L * 3600_000L) publish(ctx, h) else publishContacts(ctx, h)
        }
    }

    /** read what others sent to this phone's number; returns how many new / changed */
    fun fetch(ctx: Context): Int {
        val me = myMobile(ctx)
        if (me.length != 10) return 0
        val msgs = Relay.poll(topic(me)) ?: return 0
        val key = Otp.shareKey(me)
        var n = 0
        msgs.map { it.second }.filter { it.has("k") }.groupBy { it.optString("k") }.forEach { (_, parts) ->
            val total = parts.first().optInt("n")
            val byI = parts.associateBy { it.optInt("i") }
            if (byI.size < total) return@forEach
            val text = Codec.open((0 until total).joinToString("") { byI[it]!!.optString("p") }, key) ?: return@forEach
            val o = JSONObject(text)
            val fm = o.optString("fm"); val t = o.optLong("t")
            o.optJSONObject("c")?.let { c ->
                val hid = c.optLong("hid")
                val ln = c.optJSONArray("ln")
                val lines = (0 until (ln?.length() ?: 0)).map { i -> ln!!.getJSONObject(i).let { LinkLine(it.optString("n"), it.optDouble("a"), it.optBoolean("cr"), it.optLong("du"), it.optDouble("gt"), it.optDouble("lf"), it.optBoolean("yp"), it.optBoolean("in")) } }
                val old = Store.linked.firstOrNull { it.hid == hid && it.fromMobile == fm }
                if (old == null) { Store.linked.add(Linked(o.optString("f"), fm, hid, c.optString("ti"), c.optLong("tm"), t, lines)); n++ }
                else if (old.t < t) { old.t = t; old.lines = lines; old.title = c.optString("ti"); old.from = o.optString("f"); n++ }
                return@forEach
            }
            val h = Store.jh(o.getJSONObject("h"))
            val old = Store.shared.firstOrNull { it.h.id == h.id && it.fromMobile == fm }
            if (old == null) { Store.shared.add(Shared(o.optString("f"), fm, t, h)); n++ }
            else if (old.t < t) { old.t = t; old.h = h; old.from = o.optString("f"); n++ }
        }
        return n
    }
}
