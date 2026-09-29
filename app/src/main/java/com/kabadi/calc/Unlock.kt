package com.kabadi.calc

import android.content.Context
import org.json.JSONObject

/**
 * A user wants to correct a final hisab: he asks the admin, the admin says yes / no,
 * and on yes the hisab opens again on the user's phone (he presses Final again after the correction).
 * Messages go through ntfy.sh (kept 12 hours). Network calls: from a background thread.
 */
object Unlock {
    val REQ = "kmh-x-" + Otp.topic("unlock")
    fun userTopic(mobile: String, dev: String) = "kmh-ul-" + Otp.topic(Otp.mobile10(mobile) + ":" + dev)

    class Req(val id: String, val time: Long, val name: String, val mobile: String, val dev: String, val hid: Long, val title: String)

    private fun sp(ctx: Context) = ctx.getSharedPreferences("kabadi_unl", Context.MODE_PRIVATE)
    private fun me(ctx: Context) = Account.mobile(ctx).ifBlank { Store.mobile }.filter { it.isDigit() }.takeLast(10)

    // ---- user side ----
    fun ask(ctx: Context, h: Hisab, title: String): Boolean {
        val ok = Relay.post(REQ, JSONObject().put("n", Account.name(ctx).ifBlank { Store.owner }).put("m", me(ctx))
            .put("d", Account.device(ctx)).put("h", h.id).put("t", title))
        if (ok) sp(ctx).edit().putLong("ask_" + h.id, System.currentTimeMillis()).apply()
        return ok
    }

    fun asked(ctx: Context, hid: Long) = sp(ctx).getLong("ask_$hid", 0L) > 0L

    /** reads the admin's answers; a "yes" opens that hisab again. Returns (hisab id, yes?) for new answers. */
    fun check(ctx: Context): List<Pair<Long, Boolean>> {
        val msgs = Relay.poll(userTopic(me(ctx), Account.device(ctx))) ?: return emptyList()
        val s = sp(ctx)
        val done = (s.getStringSet("done", emptySet()) ?: emptySet()).toMutableSet()
        val out = mutableListOf<Pair<Long, Boolean>>()
        msgs.forEach { (o, m) ->
            val id = o.optString("id")
            if (id.isEmpty() || id in done) return@forEach
            done.add(id)
            val hid = m.optLong("h")
            if (s.getLong("ask_$hid", 0L) == 0L) return@forEach
            s.edit().remove("ask_$hid").apply()
            val ok = m.optBoolean("ok")
            if (ok) Store.hisabs.firstOrNull { it.id == hid }?.let { it.finalAt = 0L; if (it.step >= 0) it.step = 0 }
            out.add(hid to ok)
        }
        s.edit().putStringSet("done", done).apply()
        return out
    }

    // ---- admin side ----
    /** requests of the last 12 hours not answered yet (newest first); null = no internet */
    fun pending(ctx: Context): List<Req>? {
        val all = Relay.poll(REQ) ?: return null
        val dec = sp(ctx).getStringSet("dec", emptySet()) ?: emptySet()
        return all.map { (o, m) -> Req(o.optString("id"), o.optLong("time") * 1000, m.optString("n"), m.optString("m"), m.optString("d"), m.optLong("h"), m.optString("t")) }
            .filter { it.id !in dec && it.mobile.length == 10 }
            .distinctBy { it.mobile + ":" + it.dev + ":" + it.hid }
            .reversed()
    }

    fun decide(ctx: Context, r: Req, ok: Boolean): Boolean {
        val sent = Relay.post(userTopic(r.mobile, r.dev), JSONObject().put("h", r.hid).put("ok", ok))
        if (sent) {
            val s = sp(ctx)
            val dec = (s.getStringSet("dec", emptySet()) ?: emptySet()).toMutableSet()
            // every copy of the same request is answered
            Relay.poll(REQ)?.forEach { (o, m) ->
                if (m.optString("m") == r.mobile && m.optString("d") == r.dev && m.optLong("h") == r.hid) dec.add(o.optString("id"))
            }
            dec.add(r.id)
            s.edit().putStringSet("dec", dec).apply()
        }
        return sent
    }
}
