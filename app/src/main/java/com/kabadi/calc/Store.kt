package com.kabadi.calc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** a quick-add button: built-in (key) or the user's own (name) */
class Btn(val key: String, val name: String, val fixed: Boolean) {
    fun label(): String {
        if (key.isNotEmpty()) (PARTS + EXPENSES).firstOrNull { it.key == key }?.let { return it.name(L.lang) }
        return name
    }
}

object Store {
    private const val FILE = "kabadi_calc"

    var owner = ""
    var mobile = ""
    var address = ""
    val hisabs = mutableListOf<Hisab>()
    val lastRate = mutableMapOf<String, String>()
    var parts: MutableList<Btn> = defaultParts()
    var expenses: MutableList<Btn> = defaultExpenses()

    fun defaultParts() = PARTS.map { Btn(it.key, "", it.fixed) }.toMutableList()
    fun defaultExpenses() = EXPENSES.map { Btn(it.key, "", true) }.toMutableList()

    private fun lj(l: Line) = JSONObject().put("k", l.key).put("n", l.name).put("f", l.fixed)
        .put("kg", l.kgText).put("r", l.rateText).put("a", l.amountText)

    private fun jl(o: JSONObject) = Line(o.optString("k"), o.optString("n"), o.optBoolean("f"),
        o.optString("kg"), o.optString("r"), o.optString("a"))

    fun hj(h: Hisab): JSONObject {
        val o = JSONObject().put("id", h.id).put("t", h.time).put("p", h.party).put("v", h.vehicle)
            .put("no", h.note).put("pr", h.priceText)
        o.put("k", JSONArray().also { a -> h.kharch.forEach { a.put(lj(it)) } })
        o.put("m", JSONArray().also { a -> h.maal.forEach { a.put(lj(it)) } })
        return o
    }

    fun jh(o: JSONObject): Hisab {
        val h = Hisab(o.optLong("id"), o.optLong("t"), o.optString("p"), o.optString("v"), o.optString("no"), o.optString("pr"))
        o.optJSONArray("k")?.let { a -> for (i in 0 until a.length()) h.kharch.add(jl(a.getJSONObject(i))) }
        o.optJSONArray("m")?.let { a -> for (i in 0 until a.length()) h.maal.add(jl(a.getJSONObject(i))) }
        return h
    }

    private fun bj(l: List<Btn>) = JSONArray().also { a -> l.forEach { a.put(JSONObject().put("k", it.key).put("n", it.name).put("f", it.fixed)) } }
    private fun jb(a: JSONArray) = MutableList(a.length()) { i -> a.getJSONObject(i).let { Btn(it.optString("k"), it.optString("n"), it.optBoolean("f")) } }

    fun save(ctx: Context) {
        try {
            val o = JSONObject().put("owner", owner).put("mobile", mobile).put("address", address).put("lang", L.lang)
            o.put("h", JSONArray().also { a -> hisabs.forEach { a.put(hj(it)) } })
            o.put("rates", JSONObject(lastRate as Map<*, *>))
            o.put("parts", bj(parts)).put("exp", bj(expenses))
            ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("state", o.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun load(ctx: Context) {
        try {
            val s = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("state", null) ?: return
            val o = JSONObject(s)
            owner = o.optString("owner")
            mobile = o.optString("mobile")
            address = o.optString("address")
            L.lang = o.optInt("lang", 0)
            hisabs.clear()
            o.optJSONArray("h")?.let { a -> for (i in 0 until a.length()) hisabs.add(jh(a.getJSONObject(i))) }
            o.optJSONObject("rates")?.let { r -> r.keys().forEach { k -> lastRate[k] = r.optString(k) } }
            o.optJSONArray("parts")?.let { parts = jb(it) }
            o.optJSONArray("exp")?.let { expenses = jb(it) }
        } catch (_: Exception) {
        }
    }
}
