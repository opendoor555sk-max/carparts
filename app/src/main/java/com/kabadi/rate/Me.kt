package com.kabadi.rate

import android.content.Context
import android.provider.Settings
import org.json.JSONObject

/** this trader's phone: who he is, admin's answer, today's rates and the metal format */
object Me {
    private fun sp(c: Context) = c.getSharedPreferences("scrap_trader", Context.MODE_PRIVATE)

    fun dev(c: Context): String = Otp.deviceCode(Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "x")

    fun name(c: Context) = sp(c).getString("name", "") ?: ""
    fun mobile(c: Context) = sp(c).getString("mobile", "") ?: ""
    /** none / asked / active / block */
    fun state(c: Context) = sp(c).getString("state", "none") ?: "none"
    fun reqAt(c: Context) = sp(c).getLong("req", 0)
    fun approved(c: Context) = sp(c).getBoolean("appr", false)
    fun lang(c: Context) = sp(c).getInt("lang", 2)
    fun setLang(c: Context, l: Int) = sp(c).edit().putInt("lang", l).apply()

    fun ratesAt(c: Context) = sp(c).getLong("rat", 0)
    fun unsent(c: Context) = sp(c).getBoolean("uns", false)

    /** today's rates (empty after 7 PM) */
    fun rates(c: Context): Map<String, Double> {
        if (!Scrap.alive(ratesAt(c))) return emptyMap()
        return try { Scrap.ratesOf(JSONObject(sp(c).getString("rates", "{}") ?: "{}").let { JSONObject().put("r", it) }) } catch (_: Exception) { emptyMap() }
    }

    fun format(c: Context): List<Metal> = Scrap.parseFormat(sp(c).getString("fmt", "") ?: "")?.second?.takeIf { it.isNotEmpty() } ?: Scrap.DEFAULT
    private fun fmtVer(c: Context) = sp(c).getLong("fver", 0)

    fun register(c: Context, name: String, mobile: String, t: Long) =
        sp(c).edit().putString("name", name).putString("mobile", mobile).putString("state", "asked").putLong("req", t).putBoolean("appr", false).putBoolean("act", false).apply()

    /** OTP was right */
    fun activate(c: Context) = sp(c).edit().putString("state", "active").putBoolean("act", false).apply()

    /** admin removed me (or I cancel): back to the first screen, can register again */
    fun reset(c: Context) = sp(c).edit().putString("state", "none").putBoolean("appr", false).remove("rates").remove("rat").putBoolean("uns", false).apply()

    fun saveRates(c: Context, r: Map<String, Double>) {
        val j = JSONObject(); r.forEach { (k, v) -> j.put(k, v) }
        sp(c).edit().putString("rates", j.toString()).putLong("rat", System.currentTimeMillis()).putBoolean("uns", true).apply()
    }

    /** post today's rates to the admin (background) */
    private fun publish(c: Context): Boolean {
        val ids = format(c).map { it.id }.toSet()
        val r = rates(c).filterKeys { it in ids }
        if (r.isEmpty()) { sp(c).edit().putBoolean("uns", false).apply(); return true }
        val ok = Scrap.postSealed(Scrap.RATES, Scrap.rateJson(name(c), mobile(c), dev(c), ratesAt(c), r))
        if (ok) sp(c).edit().putBoolean("uns", false).putLong("pub", System.currentTimeMillis()).apply()
        return ok
    }

    fun sendNow(c: Context): Boolean = try { publish(c) } catch (_: Exception) { false }

    /**
     * Ask the relay: block? admin's answer? new format? Also repeats what was not sent yet.
     * Returns true when the screen has to be drawn again.
     */
    fun sync(c: Context): Boolean {
        var st = state(c)
        if (st == "block") return false
        var changed = false
        val mob = mobile(c); val dev = dev(c)
        if (mob.length == 10 && st != "none") {
            val blk = Relay.poll(Scrap.blockTopic(mob))?.any { it.second.optBoolean("block") } == true
            val u = Relay.poll(Scrap.userTopic(mob, dev))
            if (blk || u?.any { it.second.optBoolean("block") } == true) {
                sp(c).edit().putString("state", "block").apply(); return true
            }
            val req = reqAt(c)
            val last = u?.map { it.second }?.lastOrNull { val rt = it.optLong("rt"); rt == req || rt == 0L }
            if (last != null) {
                if (last.optBoolean("remove")) { reset(c); return true }
                if (last.optBoolean("ok") && !approved(c)) { sp(c).edit().putBoolean("appr", true).apply(); changed = true }
            }
        }
        st = state(c)
        // format written by the admin
        try {
            val best = Scrap.readSealed(Scrap.FORMAT)?.map { it.second }?.maxByOrNull { it.optLong("ver") }
            if (best != null && best.optLong("ver") > fmtVer(c)) {
                sp(c).edit().putString("fmt", best.toString()).putLong("fver", best.optLong("ver")).apply(); changed = true
            }
        } catch (_: Exception) {}
        if (st == "active") {
            if (!sp(c).getBoolean("act", false)) {
                if (Relay.post(Scrap.REG, JSONObject().put("n", name(c)).put("m", mob).put("d", dev).put("t", System.currentTimeMillis()).put("a", 1)))
                    sp(c).edit().putBoolean("act", true).apply()
            }
            val old = System.currentTimeMillis() - sp(c).getLong("pub", 0) > 5L * 3600_000L
            if (Scrap.alive(ratesAt(c)) && (unsent(c) || old)) publish(c)
        }
        return changed
    }
}
