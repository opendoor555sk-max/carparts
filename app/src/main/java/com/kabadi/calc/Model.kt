package com.kabadi.calc

import java.math.BigDecimal
import java.math.RoundingMode

/* Data + maths only (no Android) so it can be unit tested. */

/** one line of the hisab: an expense (always a fixed amount) or a part (kg × rate, or fixed) */
class Line(
    var key: String,              // built-in key ("body", "crane") or "" for a custom line
    var name: String,             // what the user sees / edits (any language)
    var fixed: Boolean,
    var kgText: String = "",
    var rateText: String = "",
    var amountText: String = "",
    var litre: Boolean = false,    // kg × rate line measured in litre instead (oil, diesel)
    /** "rokad" (cash) or "udhaar" (credit) */
    var pay: String = "rokad",
    /** udhaar kharch: already paid? */
    var paid: Boolean = false,
    // seller: who bought this item
    var cName: String = "",
    var cMobile: String = "",
    var daysText: String = "",
    // auction sale: guarantor + shop
    var gName: String = "",
    var gMobile: String = "",
    var shop: String = "",
    /** installments received / paid on a credit line */
    val pays: MutableList<Pay> = mutableListOf()
) {
    /** money already received / paid on this credit line */
    fun received(): Double = if (paid) value() else pays.sumOf { it.amount }
    /** still to receive / to pay (0 for cash lines) */
    fun remaining(): Double = if (!udhaar) 0.0 else (value() - received()).coerceAtLeast(0.0)
    val udhaar get() = pay == "udhaar"
    val kg get() = evalExpr(kgText).let { if (it.isFinite()) it else 0.0 }
    val rate get() = evalExpr(rateText).let { if (it.isFinite()) it else 0.0 }
    val amount get() = evalExpr(amountText).let { if (it.isFinite()) it else 0.0 }
    fun value(): Double = if (fixed) amount else kg * rate
}

/** a partner in the "company": share in paise (1 paisa = 1 %) */
/** one installment (kist): when and how much */
class Pay(var time: Long, var amountText: String) {
    val amount get() = evalExpr(amountText).let { if (it.isFinite()) it else 0.0 }
}

class Partner(var name: String, var shareText: String = "") {
    val share get() = evalExpr(shareText).let { if (it.isFinite() && it > 0) it else 0.0 }
}

class Hisab(
    var id: Long,
    var time: Long,
    var party: String = "",
    var vehicle: String = "",
    var note: String = "",
    var priceText: String = "",
    val kharch: MutableList<Line> = mutableListOf(),
    val maal: MutableList<Line> = mutableListOf(),
    /** "gaadi" = vehicle bought and parts sold; "haraji" = auction deal with company partners */
    var type: String = "gaadi",
    var saleText: String = "",          // haraji: resold (auction) amount
    var commText: String = "",          // market commission
    var commPct: Boolean = true,        // commission in % of sale, or a fixed amount
    val partners: MutableList<Partner> = mutableListOf(),
    /** "buyer" or "seller" */
    var role: String = "seller",
    var brand: String = "",
    var variant: String = "",
    var tyres: String = "",
    var year: String = "",
    var place: String = "",
    // A. mudiwala (puts the money) and khedut (does the work): profit / loss split
    var mudiName: String = "",
    var mudiMobile: String = "",
    var mudiPctText: String = "",
    var khedName: String = "",
    var khedMobile: String = "",
    var khedPctText: String = ""
) {
    val mudiPct get() = evalExpr(mudiPctText).let { if (it.isFinite() && it > 0) it else 0.0 }
    val khedPct get() = evalExpr(khedPctText).let { if (it.isFinite() && it > 0) it else 0.0 }
    fun hasSplit() = mudiName.isNotBlank() || khedName.isNotBlank() || mudiPct > 0 || khedPct > 0
    fun mudiShare() = munafa() * mudiPct / 100
    fun khedShare() = munafa() * khedPct / 100

    /** credit sales still to collect / credit expenses still to pay, after installments */
    fun lenaBaaki() = maal.filter { it.udhaar }.sumOf { it.remaining() }
    fun denaBaaki() = kharch.filter { it.udhaar }.sumOf { it.remaining() }
    /** credit expenses not paid yet (dena baaki) */
    fun kharchBaaki() = denaBaaki()
    /** items sold on credit (lena baaki) */
    fun udhaarBikri() = maal.filter { it.udhaar }.sumOf { it.value() }

    /** F: totals re-added a second, independent way (exact decimals). Empty list = all correct. */
    fun verify(): List<String> {
        fun bd(x: Double) = BigDecimal.valueOf(x)
        fun lineBd(l: Line): BigDecimal =
            if (l.fixed) bd(l.amount) else bd(l.kg).multiply(bd(l.rate))
        val bad = mutableListOf<String>()
        val k = kharch.fold(BigDecimal.ZERO) { a, l -> a.add(lineBd(l)) }
        val m = maal.fold(BigDecimal.ZERO) { a, l -> a.add(lineBd(l)) }
        fun off(x: Double, y: BigDecimal) = Math.abs(x - y.toDouble()) > 0.005
        if (off(kharchTotal(), k)) bad.add("kharch")
        if (off(maalTotal(), m)) bad.add("maal")
        if (off(lagat(), bd(price).add(k))) bad.add("lagat")
        if (off(munafa() + commission(), bd(sale).add(m).subtract(bd(price)).subtract(k))) bad.add("munafa")
        return bad
    }
    fun rokadBikri() = maal.filter { !it.udhaar }.sumOf { it.value() }
    fun vehicleInfo() = listOf(brand, variant, if (tyres.isNotBlank()) tyres + " tyre" else "", year).filter { it.isNotBlank() }.joinToString(" • ")
    private fun ev(t: String) = evalExpr(t).let { if (it.isFinite()) it else 0.0 }
    val price get() = ev(priceText)
    val sale get() = ev(saleText)
    fun kharchTotal() = kharch.sumOf { it.value() }
    fun lagat() = price + kharchTotal()
    fun maalTotal() = maal.sumOf { it.value() }
    fun bikri() = sale + maalTotal()
    fun commission() = if (type != "haraji") 0.0 else if (commPct) bikri() * ev(commText) / 100 else ev(commText)
    fun kg() = maal.filter { !it.fixed && !it.litre }.sumOf { it.kg }
    fun litre() = maal.filter { !it.fixed && it.litre }.sumOf { it.kg }
    fun munafa() = bikri() - commission() - lagat()

    fun sharesTotal() = partners.sumOf { it.share }
    /** part of the deal not given to partners (goes to the owner) */
    fun ownerShare() = (100 - sharesTotal()).coerceAtLeast(0.0)
    /** each partner's money in: cost × share % */
    fun partnerLagat(p: Partner) = lagat() * p.share / 100
    fun partnerMunafa(p: Partner) = munafa() * p.share / 100
}

/** "  Body " == "body" : for finding the same item twice */
fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

/** true when a line is the same item as [name] / [key] (built-in names in all 3 languages count) */
fun sameItem(l: Line, key: String, name: String): Boolean {
    if (key.isNotEmpty() && l.key == key) return true
    val n = norm(name)
    if (n.isEmpty()) return false
    if (norm(l.name) == n) return true
    val item = (PARTS + EXPENSES).firstOrNull { it.key == l.key && l.key.isNotEmpty() } ?: return false
    return listOf(item.en, item.hi, item.gu).any { norm(it) == n }
}

/** duplicate names inside one list (after trimming / ignoring case) */
fun duplicates(lines: List<Line>): List<String> =
    lines.filter { it.name.isNotBlank() }.groupBy { norm(it.name) }.filter { it.value.size > 1 }.map { it.value.first().name }

/** built-in parts: key, usually fixed?, names in English / Hindi / Gujarati */
class Item(val key: String, val fixed: Boolean, val en: String, val hi: String, val gu: String, val litre: Boolean = false) {
    fun name(lang: Int) = when (lang) { 1 -> hi; 2 -> gu; else -> en }
}

val PARTS = listOf(
    Item("cabin", false, "Cabin", "केबिन", "કેબિન"),
    Item("body", false, "Body", "बॉडी", "બોડી"),
    Item("chassis", false, "Chassis", "चेसिस", "ચેસિસ"),
    Item("enginegear", true, "Engine + Gear", "इंजन + गियर", "એન્જિન + ગિયર"),
    Item("line", false, "Line", "लाइन", "લાઇન"),
    Item("kaman", false, "Kaman (patta)", "कमान", "કમાન"),
    Item("dharidiff", true, "Dhari + Differential", "धुरी + डिफरेंशियल", "ધરી + ડિફરન્શિયલ"),
    Item("wheel", false, "Wheel plate", "व्हील प्लेट", "વ્હીલ પ્લેટ"),
    Item("tyre", true, "Tyre", "टायर", "ટાયર"),
    Item("tamba", false, "Tamba (copper)", "तांबा", "તાંબુ"),
    Item("alu", false, "Aluminium", "एल्युमिनियम", "એલ્યુમિનિયમ"),
    Item("bhangar", false, "Bhangar", "भंगार", "ભંગાર"),
    Item("battery", true, "Battery", "बैटरी", "બેટરી"),
    Item("seat", true, "Seat", "सीट", "સીટ"),
    Item("wood", false, "Wood / Ply", "लकड़ी / प्लाई", "લાકડું / પ્લાય"),
    Item("oil", false, "Engine oil", "इंजन ऑयल", "એન્જિન ઓઇલ", litre = true),
    Item("diesel", false, "Diesel", "डीज़ल", "ડીઝલ", litre = true)
)

val EXPENSES = listOf(
    Item("dalali", true, "Gaadi kharidi ki dalali", "गाड़ी खरीदी की दलाली", "ગાડી ખરીદીની દલાલી"),
    Item("crane", true, "Crane kiraya", "क्रेन किराया", "ક્રેન ભાડું"),
    Item("loading", true, "Maal bharne ki majuri", "माल भरने की मजदूरी", "માલ ભરવાની મજૂરી"),
    Item("rickshaw", true, "Loading rickshaw", "लोडिंग रिक्शा", "લોડિંગ રિક્ષા"),
    Item("hydra", true, "Hydra charge", "हाइड्रा चार्ज", "હાઇડ્રા ચાર્જ"),
    Item("bhada", true, "Bhada", "भाड़ा", "ભાડું"),
    Item("food", true, "Khana kharcha", "खाना खर्चा", "જમવાનો ખર્ચ"),
    Item("petrol", true, "Petrol", "पेट्रोल", "પેટ્રોલ"),
    Item("kiraya", true, "Kiraya", "किराया", "કિરાયું"),
    Item("majuri", true, "Gaadi kholne ki majuri", "गाड़ी खोलने की मजदूरी", "ગાડી ખોલવાની મજૂરી"),
    Item("gas", true, "Gas cutting", "गैस कटिंग", "ગેસ કટિંગ")
)

/** Indian money format: ₹ 1,23,456 (paise only when present) */
fun money(x: Double): String {
    if (!x.isFinite()) return "—"
    val r = BigDecimal.valueOf(Math.abs(x)).setScale(2, RoundingMode.HALF_UP)
    val whole = r.setScale(0, RoundingMode.DOWN)
    val paise = r.subtract(whole).movePointRight(2).toInt()
    val s = group(whole.toPlainString()) + (if (paise != 0) "." + paise.toString().padStart(2, '0') else "")
    return (if (x < 0) "-" else "") + "₹ " + s
}

fun group(ip: String): String {
    if (ip.length <= 3) return ip
    val last3 = ip.takeLast(3)
    var rest = ip.dropLast(3)
    val parts = mutableListOf<String>()
    while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
    if (rest.isNotEmpty()) parts.add(0, rest)
    return parts.joinToString(",") + "," + last3
}

/** number without trailing zeros: 800, 12.5 */
fun plain(x: Double): String {
    if (!x.isFinite()) return "—"
    val s = BigDecimal.valueOf(x).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    return if (s == "-0") "0" else s
}

/** Small calculator for input boxes: "400+380", "2*55", "1,200", "(10+5)*3". NaN if it can't read it. */
fun evalExpr(s0: String): Double {
    val s = s0.replace(",", "").replace("₹", "").replace("×", "*").replace("x", "*").replace("X", "*")
        .replace("÷", "/").replace("−", "-").replace(" ", "")
    if (s.isEmpty()) return 0.0
    return Parser(s).run()
}

private class Parser(val s: String) {
    var i = 0
    fun run(): Double {
        val v = expr()
        return if (i == s.length) v else Double.NaN
    }
    fun expr(): Double {
        var v = term()
        while (i < s.length && (s[i] == '+' || s[i] == '-')) {
            val op = s[i++]
            val r = term()
            v = if (op == '+') v + r else v - r
        }
        return v
    }
    fun term(): Double {
        var v = factor()
        while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
            val op = s[i++]
            if (op == '%') { v /= 100; continue }
            val r = factor()
            v = if (op == '*') v * r else v / r
        }
        return v
    }
    fun factor(): Double {
        if (i < s.length && s[i] == '-') { i++; return -factor() }
        if (i < s.length && s[i] == '+') { i++; return factor() }
        if (i < s.length && s[i] == '(') {
            i++
            val v = expr()
            if (i < s.length && s[i] == ')') i++ else return Double.NaN
            return v
        }
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        return s.substring(st, i).toDoubleOrNull() ?: Double.NaN
    }
}

val BRANDS = listOf("Tata", "Ashok Leyland", "Mahindra", "Eicher", "BharatBenz", "Volvo", "Scania", "SML Isuzu", "Swaraj Mazda", "Force", "Maruti", "Toyota")
val TYRES = listOf("4", "6", "8", "10", "12", "14", "16")

/** credit due date: entry time + credit days (null when no days given) */
fun dueTime(h: Hisab, l: Line): Long? {
    val d = evalExpr(l.daysText)
    if (l.daysText.isBlank() || !d.isFinite()) return null
    return h.time + (d * 86400000L).toLong()
}

/** one open credit: who owes whom, how much is left, when it is due */
class Due(val h: Hisab, val l: Line, val lena: Boolean, val left: Double, val due: Long?)

/** every open credit in all hisabs, most urgent first */
fun openDues(all: List<Hisab>): List<Due> {
    val out = mutableListOf<Due>()
    all.forEach { h ->
        h.maal.filter { it.udhaar && it.remaining() > 0.004 }.forEach { out.add(Due(h, it, true, it.remaining(), dueTime(h, it))) }
        h.kharch.filter { it.udhaar && it.remaining() > 0.004 }.forEach { out.add(Due(h, it, false, it.remaining(), dueTime(h, it))) }
    }
    return out.sortedWith(compareBy({ it.due ?: Long.MAX_VALUE }, { -it.left }))
}

/** one month in the report */
class MonthSum(val key: String, var count: Int = 0, var kharidi: Double = 0.0, var kharch: Double = 0.0,
               var bikri: Double = 0.0, var commission: Double = 0.0, var munafa: Double = 0.0, var kg: Double = 0.0)

/** report filter: every empty/0 field means "all" */
class RFilter(var who: String = "", var mudi: String = "", var year: Int = 0, var month: Int = 0,
              var brand: String = "", var tyres: String = "", var type: String = "") {
    fun active() = who.isNotBlank() || mudi.isNotBlank() || year != 0 || month != 0 || brand.isNotBlank() || tyres.isNotBlank() || type.isNotBlank()
    fun matches(h: Hisab): Boolean {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = h.time }
        val q = norm(who)
        return (q.isEmpty() || listOf(h.mudiName, h.khedName, h.party, h.vehicle, h.brand, h.variant, h.place, h.note).any { norm(it).contains(q) }) &&
            (mudi.isBlank() || norm(h.mudiName) == norm(mudi) || norm(h.khedName) == norm(mudi)) &&
            (year == 0 || c.get(java.util.Calendar.YEAR) == year) &&
            (month == 0 || c.get(java.util.Calendar.MONTH) + 1 == month) &&
            (brand.isBlank() || norm(h.brand) == norm(brand)) &&
            (tyres.isBlank() || h.tyres.trim() == tyres.trim()) &&
            (type.isBlank() || h.type == type)
    }
}

/** names of all mudi malik / khedut ever written (for the filter list) */
fun peopleNames(all: List<Hisab>): List<String> =
    all.flatMap { listOf(it.mudiName, it.khedName) }.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { norm(it) }.sortedBy { norm(it) }

fun years(all: List<Hisab>): List<Int> =
    all.map { java.util.Calendar.getInstance().apply { timeInMillis = it.time }.get(java.util.Calendar.YEAR) }.distinct().sortedDescending()

/** month-wise totals (newest month first); [who] filters by mudi malik / khedut / party name */
fun monthly(all: List<Hisab>, who: String = ""): List<MonthSum> = monthly(all, RFilter(who = who))

fun monthly(all: List<Hisab>, f: RFilter): List<MonthSum> {
    val map = linkedMapOf<String, MonthSum>()
    all.filter { f.matches(it) }
        .sortedByDescending { it.time }.forEach { h ->
            val c = java.util.Calendar.getInstance().apply { timeInMillis = h.time }
            val key = String.format(java.util.Locale.US, "%04d-%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
            val m = map.getOrPut(key) { MonthSum(key) }
            m.count++; m.kharidi += h.price; m.kharch += h.kharchTotal(); m.bikri += h.bikri()
            m.commission += h.commission(); m.munafa += h.munafa(); m.kg += h.kg()
        }
    return map.values.toList()
}
