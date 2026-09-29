package com.kabadi.calc

import android.content.Context
import org.json.JSONObject

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
    fun myMobile(ctx: Context) = Otp.mobile10(Account.mobile(ctx).ifBlank { Store.mobile })
    /** everybody named in the hisab with a mobile: mudi malik, khedut, mehta, company partners */
    fun targetsOf(h: Hisab): List<String> =
        (listOf(h.mudiMobile, h.khedMobile, h.mehtaMobile) + h.partners.map { it.mobile }).map { Otp.mobile10(it) }.filter { it.length == 10 }.distinct()
    fun targets(ctx: Context, h: Hisab): List<String> {
        val me = myMobile(ctx)
        return targetsOf(h).filter { it != me }
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("kabadi_share", Context.MODE_PRIVATE)

    /** send one hisab to its mudi malik / khedut; returns how many phones */
    fun publish(ctx: Context, h: Hisab): Int {
        val to = targets(ctx, h)
        if (to.isEmpty()) return 0
        val payload = JSONObject().put("f", Store.owner.ifBlank { Account.name(ctx) }).put("fm", myMobile(ctx))
            .put("t", System.currentTimeMillis()).put("h", Store.hj(h)).toString()
        var n = 0
        to.forEach { m ->
            val sealed = Codec.seal(payload, Otp.shareKey(m))
            val parts = sealed.chunked(PART)
            val k = h.id.toString() + "-" + System.currentTimeMillis()
            var ok = true
            parts.forEachIndexed { i, p -> if (!Relay.post(topic(m), JSONObject().put("k", k).put("i", i).put("n", parts.size).put("p", p))) ok = false }
            if (ok) n++
        }
        if (n > 0) sp(ctx).edit().putLong("t_${h.id}", System.currentTimeMillis()).putInt("x_${h.id}", Store.hj(h).toString().hashCode()).apply()
        return n
    }

    /** on app start: send again what changed, or what was sent more than 6 h ago (last 90 days) */
    fun republish(ctx: Context) {
        val now = System.currentTimeMillis()
        Store.hisabs.toList().filter { now - it.time < 90L * 86400000L && targets(ctx, it).isNotEmpty() }.forEach { h ->
            val changed = sp(ctx).getInt("x_${h.id}", 0) != Store.hj(h).toString().hashCode()
            if (changed || now - sp(ctx).getLong("t_${h.id}", 0) > 6L * 3600_000L) publish(ctx, h)
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
            val h = Store.jh(o.getJSONObject("h"))
            val fm = o.optString("fm"); val t = o.optLong("t")
            val old = Store.shared.firstOrNull { it.h.id == h.id && it.fromMobile == fm }
            if (old == null) { Store.shared.add(Shared(o.optString("f"), fm, t, h)); n++ }
            else if (old.t < t) { old.t = t; old.h = h; old.from = o.optString("f"); n++ }
        }
        return n
    }
}
