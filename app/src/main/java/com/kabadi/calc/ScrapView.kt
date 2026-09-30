package com.kabadi.calc

import android.content.Context
import org.json.JSONObject

/** every Kabadi phone (not the admin): today's rate board sent by the admin. View only. */
object ScrapView {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("scrap_view", Context.MODE_PRIVATE)

    fun stamp(ctx: Context) = sp(ctx).getLong("t", 0)

    private fun data(ctx: Context): JSONObject? = try { JSONObject(sp(ctx).getString("d", "") ?: "") } catch (_: Exception) { null }

    fun format(ctx: Context): List<Metal> = data(ctx)?.optJSONObject("f")?.let { Scrap.parseFormat(it.toString()) }?.second?.takeIf { it.isNotEmpty() } ?: Scrap.DEFAULT

    /** rate sheets kept on this phone (the 7 PM rule is applied when they are shown) */
    fun entries(ctx: Context): List<Entry> {
        val a = data(ctx)?.optJSONArray("e") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { Entry(it.optString("k"), it.optString("n"), it.optString("m"), it.optLong("at"), Scrap.ratesOf(it)) }
    }
    fun board(ctx: Context): List<Entry> = entries(ctx).filter { Scrap.alive(it.at) }

    /** take the newest board from the relay; true = changed; false = nothing new / no internet */
    fun fetch(ctx: Context): Boolean {
        val best = Scrap.readSealed(Scrap.BOARD)?.map { it.second }?.maxByOrNull { it.optLong("t") } ?: return false
        if (best.optLong("t") <= stamp(ctx)) return false
        sp(ctx).edit().putString("d", best.toString()).putLong("t", best.optLong("t")).apply()
        return true
    }
}
