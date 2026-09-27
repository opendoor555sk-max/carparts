package com.kabadi.calc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** a quick-add button: built-in (key) or the user's own (name) */
class Btn(val key: String, val name: String, val fixed: Boolean, val litre: Boolean = false) {
    /** Kg × Rate → Litre × Rate → Fix → Kg × Rate */
    fun nextMode() = when {
        fixed -> Btn(key, name, false, false)
        litre -> Btn(key, name, true, false)
        else -> Btn(key, name, false, true)
    }
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
    /** buttons removed from the lists: kept here so "Reset" can bring them back */
    val trashParts = mutableListOf<Btn>()
    val trashExp = mutableListOf<Btn>()

    private fun same(a: Btn, b: Btn) = (a.key.isNotEmpty() && a.key == b.key) || (a.key.isEmpty() && b.key.isEmpty() && norm(a.name) == norm(b.name))

    /** built-in buttons that are missing + everything that was deleted come back; own buttons stay */
    fun restore(parts: Boolean) {
        val list = if (parts) this.parts else expenses
        val trash = if (parts) trashParts else trashExp
        val all = (if (parts) defaultParts() else defaultExpenses()) + trash
        all.forEach { b -> if (list.none { same(it, b) }) list.add(b) }
        trash.clear()
    }

    fun remove(parts: Boolean, i: Int) {
        val list = if (parts) this.parts else expenses
        val b = list.removeAt(i)
        val trash = if (parts) trashParts else trashExp
        if (trash.none { same(it, b) }) trash.add(b)
    }

    /** is this name already a button (any language)? */
    fun known(parts: Boolean, name: String): Boolean {
        val n = norm(name)
        val list = if (parts) this.parts else expenses
        return list.any { b ->
            norm(b.name) == n || (b.key.isNotEmpty() && (PARTS + EXPENSES).firstOrNull { it.key == b.key }
                ?.let { listOf(it.en, it.hi, it.gu).any { x -> norm(x) == n } } == true)
        }
    }

    /** after a hisab is saved: new item names typed by the user become buttons (once, no duplicates) */
    fun learn(h: Hisab): List<String> {
        val added = mutableListOf<String>()
        h.kharch.filter { it.key.isEmpty() && it.name.isNotBlank() && it.amount != 0.0 }.forEach {
            if (!known(false, it.name)) { expenses.add(Btn("", it.name.trim(), true)); added.add(it.name.trim()) }
        }
        h.maal.filter { it.key.isEmpty() && it.name.isNotBlank() && it.value() != 0.0 }.forEach {
            if (!known(true, it.name)) { parts.add(Btn("", it.name.trim(), it.fixed, it.litre && !it.fixed)); added.add(it.name.trim()) }
        }
        return added
    }

    fun defaultParts() = PARTS.map { Btn(it.key, "", it.fixed, it.litre) }.toMutableList()
    fun defaultExpenses() = EXPENSES.map { Btn(it.key, "", true) }.toMutableList()

    private fun lj(l: Line) = JSONObject().put("k", l.key).put("n", l.name).put("f", l.fixed)
        .put("kg", l.kgText).put("r", l.rateText).put("a", l.amountText).put("l", l.litre)

    private fun jl(o: JSONObject) = Line(o.optString("k"), o.optString("n"), o.optBoolean("f"),
        o.optString("kg"), o.optString("r"), o.optString("a"), o.optBoolean("l"))

    fun hj(h: Hisab): JSONObject {
        val o = JSONObject().put("id", h.id).put("t", h.time).put("p", h.party).put("v", h.vehicle)
            .put("no", h.note).put("pr", h.priceText)
            .put("ty", h.type).put("sa", h.saleText).put("co", h.commText).put("cp", h.commPct)
        o.put("pa", JSONArray().also { a -> h.partners.forEach { a.put(JSONObject().put("n", it.name).put("s", it.shareText)) } })
        o.put("k", JSONArray().also { a -> h.kharch.forEach { a.put(lj(it)) } })
        o.put("m", JSONArray().also { a -> h.maal.forEach { a.put(lj(it)) } })
        return o
    }

    fun jh(o: JSONObject): Hisab {
        val h = Hisab(o.optLong("id"), o.optLong("t"), o.optString("p"), o.optString("v"), o.optString("no"), o.optString("pr"),
            type = o.optString("ty", "gaadi"), saleText = o.optString("sa"), commText = o.optString("co"), commPct = o.optBoolean("cp", true))
        o.optJSONArray("pa")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { h.partners.add(Partner(it.optString("n"), it.optString("s"))) } }
        o.optJSONArray("k")?.let { a -> for (i in 0 until a.length()) h.kharch.add(jl(a.getJSONObject(i))) }
        o.optJSONArray("m")?.let { a -> for (i in 0 until a.length()) h.maal.add(jl(a.getJSONObject(i))) }
        return h
    }

    private fun bj(l: List<Btn>) = JSONArray().also { a -> l.forEach { a.put(JSONObject().put("k", it.key).put("n", it.name).put("f", it.fixed).put("l", it.litre)) } }
    private fun jb(a: JSONArray) = MutableList(a.length()) { i -> a.getJSONObject(i).let { Btn(it.optString("k"), it.optString("n"), it.optBoolean("f"), it.optBoolean("l")) } }

    fun save(ctx: Context) {
        try {
            val o = JSONObject().put("owner", owner).put("mobile", mobile).put("address", address).put("lang", L.lang).put("litreV1", true)
            o.put("h", JSONArray().also { a -> hisabs.forEach { a.put(hj(it)) } })
            o.put("rates", JSONObject(lastRate as Map<*, *>))
            o.put("parts", bj(parts)).put("exp", bj(expenses)).put("tp", bj(trashParts)).put("te", bj(trashExp))
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
            // one-time: give existing users the new litre buttons (Engine oil, Diesel)
            if (!o.optBoolean("litreV1")) {
                PARTS.filter { it.litre }.forEach { p -> if (parts.none { it.key == p.key }) parts.add(Btn(p.key, "", false, true)) }
            }
            o.optJSONArray("exp")?.let { expenses = jb(it) }
            o.optJSONArray("tp")?.let { trashParts.clear(); trashParts.addAll(jb(it)) }
            o.optJSONArray("te")?.let { trashExp.clear(); trashExp.addAll(jb(it)) }
        } catch (_: Exception) {
        }
    }
}
