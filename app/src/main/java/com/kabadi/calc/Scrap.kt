package com.kabadi.calc

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Scrap / metal rates. SAME file is used by the traders' app (Scrap Bhav) and by the admin (Kabadi Market Hisab).
 * Traders send ₹ rates of the metals the ADMIN wrote in the format; rates are alive until 7 PM.
 */
class Metal(val id: String, var gu: String, var hi: String, var en: String, var unit: String = "kg") {
    /** lang: 0 English, 1 Hindi, 2 Gujarati */
    fun name(lang: Int): String = (when (lang) { 0 -> en; 1 -> hi; else -> gu }).ifBlank { gu.ifBlank { en.ifBlank { hi } } }
    fun unitText(lang: Int): String = if (unit == "pc") (when (lang) { 0 -> "per piece"; 1 -> "प्रति नग"; else -> "દર નંગ" }) else "kg"
}

/** one rate sheet sent by one trader (the metals he filled) */
class Entry(val key: String, val name: String, val mobile: String, val at: Long, val rates: Map<String, Double>)
class Quote(val name: String, val mobile: String, val price: Double, val at: Long)

object Scrap {
    /** rates disappear at this hour (7 PM) */
    const val HOUR = 19
    const val KEEP_DAYS = 30
    private const val PART = 3000

    val REG get() = "kmh-tr-" + Otp.topic("trader-reg")
    val RATES get() = "kmh-tp-" + Otp.topic("trader-rates")
    val FORMAT get() = "kmh-tf-" + Otp.topic("trader-format")
    private val KEY get() = Otp.shareKey("1000000002")
    fun userTopic(mobile: String, dev: String) = "kmh-tu-" + Otp.topic(Otp.mobile10(mobile) + ":" + dev)
    /** a permanent block is written here too, so a trader cannot come back with a new phone code */
    fun blockTopic(mobile: String) = "kmh-tb-" + Otp.topic("blk:" + Otp.mobile10(mobile))

    /** the OTP the admin gives to one trader (different from the Kabadi app OTPs) */
    fun otp(mobile: String, dev: String): String = Otp.code(mobile, "TRADER-" + dev.trim().uppercase())
    fun otpOk(mobile: String, dev: String, input: String) = Otp.mobile10(mobile).length == 10 && otp(mobile, dev) == Otp.asciiDigits(input)

    /** the first 7 PM after [at] */
    fun expiry(at: Long): Long {
        val c = Calendar.getInstance(); c.timeInMillis = at
        c.set(Calendar.HOUR_OF_DAY, HOUR); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= at) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
    fun alive(at: Long, now: Long = System.currentTimeMillis()) = at > 0 && now < expiry(at)

    /** per metal: one price per trader (his newest alive price for that metal), highest first */
    fun rank(metal: String, entries: List<Entry>, now: Long = System.currentTimeMillis()): List<Quote> {
        val best = HashMap<String, Quote>()
        entries.filter { alive(it.at, now) }.sortedBy { it.at }.forEach { e ->
            val p = e.rates[metal] ?: return@forEach
            if (p > 0) best[e.key] = Quote(e.name, e.mobile, p, e.at)
        }
        return best.values.sortedWith(compareByDescending<Quote> { it.price }.thenBy { it.at })
    }

    /** "₹ 245" / "₹ 245.5" */
    fun price(p: Double) = "₹ " + (if (p == Math.floor(p)) p.toLong().toString() else String.format(java.util.Locale.US, "%.2f", p).trimEnd('0').trimEnd('.'))

    // ---------------- format (list of metals, written by the admin) ----------------
    // NOTE: chassis / engine / gear are NOT here – the admin decides the list
    val DEFAULT: List<Metal> get() = listOf(
        Metal("cabin", "કેબિન", "केबिन", "Cabin"),
        Metal("body", "બોડી", "बॉडी", "Body"),
        Metal("copper", "તાંબુ", "तांबा", "Copper"),
        Metal("alum", "એલ્યુમિનિયમ", "एल्युमिनियम", "Aluminium"),
        Metal("brass", "પિત્તળ", "पीतल", "Brass"),
        Metal("lead", "સીસું", "सीसा", "Lead"),
        Metal("iron", "લોખંડ", "लोहा", "Iron"),
        Metal("steel", "સ્ટીલ", "स्टील", "Steel"),
        Metal("battery", "બેટરી", "बैटरी", "Battery")
    )

    fun formatJson(ver: Long, list: List<Metal>): String = JSONObject().put("ver", ver).put("m", JSONArray().also { a ->
        list.forEach { a.put(JSONObject().put("id", it.id).put("gu", it.gu).put("hi", it.hi).put("en", it.en).put("u", it.unit)) } }).toString()

    fun parseFormat(s: String): Pair<Long, List<Metal>>? = try {
        val o = JSONObject(s); val a = o.getJSONArray("m")
        val list = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("id").isNotBlank() && it.optString("gu").isNotBlank() }
            .map { Metal(it.optString("id"), it.optString("gu"), it.optString("hi"), it.optString("en"), if (it.optString("u") == "pc") "pc" else "kg") }
        o.optLong("ver") to list
    } catch (_: Exception) { null }

    fun newId(): String = "m" + java.lang.Long.toString(System.currentTimeMillis(), 36)

    // ---------------- sealed messages on the relay (only these apps can read) ----------------
    fun postSealed(topic: String, json: String): Boolean {
        val parts = Codec.seal(json, KEY).chunked(PART)
        val k = "x-" + System.currentTimeMillis() + "-" + (Math.random() * 1e6).toInt()
        var ok = true
        parts.forEachIndexed { i, p -> if (!Relay.post(topic, JSONObject().put("k", k).put("i", i).put("n", parts.size).put("p", p))) ok = false }
        return ok
    }

    /** every complete sealed message of the last 12 h: (server time ms, content); null = no internet */
    fun readSealed(topic: String): List<Pair<Long, JSONObject>>? {
        val msgs = Relay.poll(topic) ?: return null
        val out = mutableListOf<Pair<Long, JSONObject>>()
        msgs.filter { it.second.has("k") }.groupBy { it.second.optString("k") }.forEach { (_, parts) ->
            val total = parts.first().second.optInt("n"); val byI = parts.associateBy { it.second.optInt("i") }
            if (total <= 0 || byI.size < total) return@forEach
            val o = try { JSONObject(Codec.open((0 until total).joinToString("") { byI[it]!!.second.optString("p") }, KEY) ?: return@forEach) } catch (_: Exception) { return@forEach }
            out.add(parts.first().first.optLong("time") * 1000 to o)
        }
        return out.sortedBy { it.first }
    }

    fun rateJson(name: String, mobile: String, dev: String, at: Long, rates: Map<String, Double>): String =
        JSONObject().put("n", name).put("m", Otp.mobile10(mobile)).put("d", dev).put("at", at)
            .put("r", JSONObject().also { r -> rates.forEach { (k, v) -> r.put(k, v) } }).toString()

    fun ratesOf(o: JSONObject): Map<String, Double> {
        val r = o.optJSONObject("r") ?: return emptyMap()
        return r.keys().asSequence().associateWith { r.optDouble(it, 0.0) }.filterValues { it > 0 }
    }
}
