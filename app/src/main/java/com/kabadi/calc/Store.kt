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
    /** hisab of others where this phone is mudi malik / khedut (view only) */
    val shared = mutableListOf<Shared>()
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

    /** variants typed before (1612, 2515 ...) for quick buttons */
    val variants = mutableListOf("407", "709", "1109", "1612", "2515", "3118", "4018")
    /** v1 → v2: Engine+Gear and Dhari+Differential merged, Chapani removed, Dalali added */
    private fun migrate1() {
        fun fix(l: MutableList<Btn>) {
            val out = mutableListOf<Btn>()
            l.forEach { b ->
                when (b.key) {
                    "engine" -> out.add(Btn("enginegear", "", true))
                    "dhari" -> out.add(Btn("dharidiff", "", true))
                    "gear", "diff", "chapani" -> {}
                    else -> out.add(b)
                }
            }
            l.clear(); l.addAll(out.distinctBy { if (it.key.isNotEmpty()) it.key else "n:" + norm(it.name) })
        }
        fix(parts); fix(expenses); fix(trashParts); fix(trashExp)
        if (expenses.none { it.key == "dalali" }) expenses.add(0, Btn("dalali", "", true))
        if (parts.none { it.key == "enginegear" } && trashParts.none { it.key == "enginegear" }) parts.add(Btn("enginegear", "", true))
        if (parts.none { it.key == "dharidiff" } && trashParts.none { it.key == "dharidiff" }) parts.add(Btn("dharidiff", "", true))
    }

    fun defaultParts() = PARTS.map { Btn(it.key, "", it.fixed, it.litre) }.toMutableList()
    fun defaultExpenses() = EXPENSES.map { Btn(it.key, "", true) }.toMutableList()

    private fun lj(l: Line) = JSONObject().put("k", l.key).put("n", l.name).put("f", l.fixed)
        .put("kg", l.kgText).put("r", l.rateText).put("a", l.amountText).put("l", l.litre)
        .put("pay", l.pay).put("paid", l.paid).put("cn", l.cName).put("cm", l.cMobile).put("d", l.daysText)
        .put("gn", l.gName).put("gm", l.gMobile).put("sh", l.shop)
        .put("py", JSONArray().also { a -> l.pays.forEach { a.put(JSONObject().put("t", it.time).put("a", it.amountText)) } })

    private fun jl(o: JSONObject) = Line(o.optString("k"), o.optString("n"), o.optBoolean("f"),
        kgText = o.optString("kg"), rateText = o.optString("r"), amountText = o.optString("a"), litre = o.optBoolean("l"),
        pay = o.optString("pay", "rokad"), paid = o.optBoolean("paid"), cName = o.optString("cn"), cMobile = o.optString("cm"),
        daysText = o.optString("d"), gName = o.optString("gn"), gMobile = o.optString("gm"), shop = o.optString("sh")).also { l ->
        o.optJSONArray("py")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { l.pays.add(Pay(it.optLong("t"), it.optString("a"))) } }
    }

    private fun vj(l: List<Veh>) = JSONArray().also { a -> l.forEach { v ->
        a.put(JSONObject().put("b", v.brand).put("v", v.variant).put("t", v.tyres).put("y", v.year).put("n", v.no).put("p", v.priceText)) } }
    private fun jv(a: JSONArray) = (0 until a.length()).map { i -> a.getJSONObject(i).let {
        Veh(it.optString("b"), it.optString("v"), it.optString("t"), it.optString("y"), it.optString("n"), it.optString("p")) } }

    fun hj(h: Hisab): JSONObject {
        val o = JSONObject().put("id", h.id).put("t", h.time).put("p", h.party).put("v", h.vehicle)
            .put("no", h.note).put("pr", h.priceText)
            .put("ty", h.type).put("sa", h.saleText).put("co", h.commText).put("cp", h.commPct)
            .put("ro", h.role).put("br", h.brand).put("va", h.variant).put("tr", h.tyres).put("yr", h.year).put("pl", h.place)
            .put("mn", h.mudiName).put("mm", h.mudiMobile).put("mp", h.mudiPctText)
            .put("kn", h.khedName).put("km", h.khedMobile).put("kp", h.khedPctText)
            .put("mu", h.muddatText).put("bl", lj(h.buyLine)).put("sl", lj(h.saleLine))
            .put("cbc", h.commByCo).put("cmo", h.coMode).put("mac", h.mudiAddCo).put("kac", h.khedAddCo).put("fin", h.finalAt)
        o.put("vh", vj(h.vehicles))
        o.put("lt", JSONArray().also { a -> h.lots.forEach { t ->
            a.put(JSONObject().put("n", t.name).put("p", t.priceText).put("vh", vj(t.vehicles)).put("it", JSONArray().also { b -> t.items.forEach { b.put(lj(it)) } })) } })
        o.put("pa", JSONArray().also { a -> h.partners.forEach { a.put(JSONObject().put("n", it.name).put("s", it.shareText).put("m", it.mobile).put("d", it.done)) } })
        o.put("k", JSONArray().also { a -> h.kharch.forEach { a.put(lj(it)) } })
        o.put("m", JSONArray().also { a -> h.maal.forEach { a.put(lj(it)) } })
        return o
    }

    fun jh(o: JSONObject): Hisab {
        val h = Hisab(o.optLong("id"), o.optLong("t"), o.optString("p"), o.optString("v"), o.optString("no"), o.optString("pr"),
            type = o.optString("ty", "gaadi"), saleText = o.optString("sa"), commText = o.optString("co"), commPct = o.optBoolean("cp", true),
            role = o.optString("ro", "seller"), brand = o.optString("br"), variant = o.optString("va"), tyres = o.optString("tr"),
            year = o.optString("yr"), place = o.optString("pl"),
            mudiName = o.optString("mn"), mudiMobile = o.optString("mm"), mudiPctText = o.optString("mp"),
            khedName = o.optString("kn"), khedMobile = o.optString("km"), khedPctText = o.optString("kp"),
            muddatText = o.optString("mu"), commByCo = o.optBoolean("cbc"), coMode = o.optBoolean("cmo"), mudiAddCo = o.optBoolean("mac"), khedAddCo = o.optBoolean("kac"), finalAt = o.optLong("fin"))
        o.optJSONObject("bl")?.let { h.buyLine = jl(it) }
        o.optJSONObject("sl")?.let { h.saleLine = jl(it) }
        h.bind()
        o.optJSONArray("vh")?.let { h.vehicles.addAll(jv(it)) }
        o.optJSONArray("lt")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { t ->
            val lot = Lot(t.optString("n"), t.optString("p"))
            t.optJSONArray("vh")?.let { lot.vehicles.addAll(jv(it)) }
            t.optJSONArray("it")?.let { b -> for (j in 0 until b.length()) lot.items.add(jl(b.getJSONObject(j))) }
            h.lots.add(lot) } }
        // older lot hisab (vehicles directly in the hisab) → "Lot 1"
        if (h.isLot && h.lots.isEmpty() && h.vehicles.isNotEmpty()) {
            h.lots.add(Lot("Lot 1", h.priceText, h.vehicles.toMutableList())); h.vehicles.clear(); h.priceText = ""
        }
        o.optJSONArray("pa")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { h.partners.add(Partner(it.optString("n"), it.optString("s"), it.optString("m"), it.optBoolean("d"))) } }
        o.optJSONArray("k")?.let { a -> for (i in 0 until a.length()) h.kharch.add(jl(a.getJSONObject(i))) }
        o.optJSONArray("m")?.let { a -> for (i in 0 until a.length()) h.maal.add(jl(a.getJSONObject(i))) }
        return h
    }

    private fun bj(l: List<Btn>) = JSONArray().also { a -> l.forEach { a.put(JSONObject().put("k", it.key).put("n", it.name).put("f", it.fixed).put("l", it.litre)) } }
    private fun jb(a: JSONArray) = MutableList(a.length()) { i -> a.getJSONObject(i).let { Btn(it.optString("k"), it.optString("n"), it.optBoolean("f"), it.optBoolean("l")) } }

    /** everything the app keeps, as one JSON (also the backup file) */
    fun toJson(): JSONObject {
        val o = JSONObject().put("owner", owner).put("mobile", mobile).put("address", address).put("lang", L.lang).put("litreV1", true).put("ver", 2)
        o.put("variants", JSONArray(variants))
        o.put("h", JSONArray().also { a -> hisabs.forEach { a.put(hj(it)) } })
        o.put("sh", JSONArray().also { a -> shared.forEach { a.put(JSONObject().put("f", it.from).put("fm", it.fromMobile).put("t", it.t).put("h", hj(it.h))) } })
        o.put("rates", JSONObject(lastRate as Map<*, *>))
        o.put("parts", bj(parts)).put("exp", bj(expenses)).put("tp", bj(trashParts)).put("te", bj(trashExp))
        return o
    }

    /** Backup restore: adds hisab that are not on this phone (same id = already here, kept as is). Returns how many were added. */
    fun restore(json: String): Int {
        val o = JSONObject(json)
        val a = o.optJSONArray("h") ?: throw IllegalArgumentException("no hisab")
        var added = 0
        for (i in 0 until a.length()) {
            val h = jh(a.getJSONObject(i))
            if (hisabs.none { it.id == h.id }) { hisabs.add(h); added++ }
        }
        if (owner.isBlank()) owner = o.optString("owner")
        if (mobile.isBlank()) mobile = o.optString("mobile")
        if (address.isBlank()) address = o.optString("address")
        o.optJSONObject("rates")?.let { r -> r.keys().forEach { k -> if (!lastRate.containsKey(k)) lastRate[k] = r.optString(k) } }
        o.optJSONArray("parts")?.let { jb(it).forEach { b -> if (parts.none { x -> same(x, b) }) parts.add(b) } }
        o.optJSONArray("exp")?.let { jb(it).forEach { b -> if (expenses.none { x -> same(x, b) }) expenses.add(b) } }
        return added
    }

    fun save(ctx: Context) {
        try {
            val o = toJson()
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
            shared.clear()
            o.optJSONArray("sh")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { shared.add(Shared(it.optString("f"), it.optString("fm"), it.optLong("t"), jh(it.getJSONObject("h")))) } }
            o.optJSONObject("rates")?.let { r -> r.keys().forEach { k -> lastRate[k] = r.optString(k) } }
            o.optJSONArray("parts")?.let { parts = jb(it) }
            // one-time: give existing users the new litre buttons (Engine oil, Diesel)
            if (!o.optBoolean("litreV1")) {
                PARTS.filter { it.litre }.forEach { p -> if (parts.none { it.key == p.key }) parts.add(Btn(p.key, "", false, true)) }
            }
            o.optJSONArray("exp")?.let { expenses = jb(it) }
            o.optJSONArray("tp")?.let { trashParts.clear(); trashParts.addAll(jb(it)) }
            o.optJSONArray("te")?.let { trashExp.clear(); trashExp.addAll(jb(it)) }
            o.optJSONArray("variants")?.let { a -> variants.clear(); for (i in 0 until a.length()) variants.add(a.getString(i)) }
            if (o.optInt("ver", 1) < 2) migrate1()
        } catch (_: Exception) {
        }
    }
}
