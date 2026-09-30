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
    val pays: MutableList<Pay> = mutableListOf(),
    /** bought item ↔ its sale line (same id on both); "" = not linked */
    var link: String = "",
    /** sale line: the bought quantity last copied in (so a changed purchase qty follows until the user types his own) */
    var syncKg: String = "",
    /** sale line: "OK – sold" was pressed: the buyer got his message and the line is locked */
    var sold: Boolean = false,
    /** credit sale: who is the guarantor: "" = a shopkeeper (name, mobile, shop), "mudi" = the mudi malik, "khed" = the khedut */
    var gBy: String = "",
    /** shop guarantee (haraji): market number – the same shop number can exist in different markets */
    var mkt: String = ""
) {
    /** for the vehicle price / auction sale lines: value comes from the hisab */
    var calc: (() -> Double)? = null
    /** money already received / paid on this credit line */
    fun received(): Double = if (paid) value() else pays.sumOf { it.amount }
    /** still to receive / to pay (0 for cash lines) */
    fun remaining(): Double = if (!udhaar) 0.0 else (value() - received()).coerceAtLeast(0.0)
    val udhaar get() = pay == "udhaar"
    val kg get() = evalExpr(kgText).let { if (it.isFinite()) it else 0.0 }
    val rate get() = evalExpr(rateText).let { if (it.isFinite()) it else 0.0 }
    val amount get() = evalExpr(amountText).let { if (it.isFinite()) it else 0.0 }
    fun value(): Double = calc?.invoke() ?: if (fixed) amount else kg * rate
}

/** a partner in the "company": share in paise (1 paisa = 1 %) */
/** one installment (kist): when and how much */
class Pay(var time: Long, var amountText: String) {
    val amount get() = evalExpr(amountText).let { if (it.isFinite()) it else 0.0 }
}

/** one vehicle inside a lot */
class Veh(var brand: String = "", var variant: String = "", var tyres: String = "", var year: String = "", var no: String = "", var priceText: String = "") {
    val price get() = evalExpr(priceText).let { if (it.isFinite()) it else 0.0 }
    fun info() = listOf(brand, variant, if (tyres.isNotBlank()) tyres + " tyre" else "", year, no).filter { it.isNotBlank() }.joinToString(" • ")
}

/** one lot: its own name, vehicles and any other things (scrap …), one price */
class Lot(var name: String = "", var priceText: String = "", val vehicles: MutableList<Veh> = mutableListOf(), val items: MutableList<Line> = mutableListOf()) {
    fun itemsValue() = items.sumOf { it.value() }
    /** no price written = vehicles' prices + items' value */
    val price get() = if (priceText.isBlank()) vehicles.sumOf { it.price } + itemsValue() else evalExpr(priceText).let { if (it.isFinite()) it else 0.0 }
    fun kg() = items.filter { !it.fixed && !it.litre }.sumOf { it.kg }
}

class Partner(var name: String, var shareText: String = "", var mobile: String = "", var done: Boolean = false) {
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
    /** who is writing this hisab: "mudi" (mudi malik) or "khed" (khedut); "" = old hisab */
    var writer: String = "",
    /** gaadi / haraji: other goods bought with (or instead of) the vehicle, each with its own name */
    val buyItems: MutableList<Line> = mutableListOf(),
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
    var khedPctText: String = "",
    /** lot: many vehicles bought together */
    val vehicles: MutableList<Veh> = mutableListOf(),
    /** lot hisab: one or more lots */
    val lots: MutableList<Lot> = mutableListOf(),
    /** haraji "Rit B": the whole vehicle given to the company at a fixed price (saleText) */
    var coMode: Boolean = false,
    /** mudi malik / khedut want their company share added into their own hisab */
    var mudiAddCo: Boolean = false,
    var khedAddCo: Boolean = false,
    /** when "Final" was pressed (0 = still open) */
    var finalAt: Long = 0L,
    /** haraji / lot: credit time for everything, counted from the haraji day: "30" days or "2m" months */
    var muddatText: String = "",
    /** Rit B: office commission paid by the company (added to its purchase) instead of the mudi malik */
    var commByCo: Boolean = false,
    /** haraji: the mehta who runs the auction (name + mobile are required) */
    var mehtaName: String = "",
    var mehtaMobile: String = "",
    /** haraji with lots: many vehicles / goods bought together (the old separate "lot" hisab feature) */
    var lotMode: Boolean = false,
    /** step by step entry: 0 names, 1 buying, 2 expenses, 3 selling open. Each step is locked when OK is pressed. -1 = older hisab (no steps) */
    var step: Int = -1
) {
    /** vehicle bought on credit (we pay) and auction / company sale on credit (we get) */
    var buyLine = Line("veh", "", true)
    var saleLine = Line("sale", "", true)
    fun bind() { buyLine.calc = { price }; saleLine.calc = { sale } }
    init { bind() }
    val isLot get() = type == "lot" || (type == "haraji" && lotMode)
    val isCo get() = type == "haraji" && coMode
    fun allVehicles() = vehicles + lots.flatMap { it.vehicles }
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
        if (isCo) {
            if (off(munafa() + ownerComm(), bd(sale).subtract(bd(price)).subtract(k))) bad.add("munafa")
            if (off(companyResult() + coComm(), m.subtract(bd(sale)))) bad.add("company")
            if (off(munafa() + companyResult() + commission(), m.subtract(bd(price)).subtract(k))) bad.add("total")
        } else if (off(munafa() + commission(), bd(sale).add(m).subtract(bd(price)).subtract(k))) bad.add("munafa")
        if (isLot && lots.isNotEmpty() && off(price, lots.fold(BigDecimal.ZERO) { a, t ->
                a.add(if (t.priceText.isBlank()) t.vehicles.fold(BigDecimal.ZERO) { b, v -> b.add(bd(v.price)) }.add(t.items.fold(BigDecimal.ZERO) { b, l -> b.add(lineBd(l)) }) else bd(t.price)) })) bad.add("lot")
        return bad
    }
    fun rokadBikri() = maal.filter { !it.udhaar }.sumOf { it.value() }
    fun vehicleInfo() = if (isLot) (if (lots.size > 1) lots.size.toString() + " Lot • " else "Lot • ") + allVehicles().size + " 🚚" +
            allVehicles().map { it.brand }.filter { it.isNotBlank() }.distinct().let { if (it.isEmpty()) "" else " (" + it.joinToString(", ") + ")" }
        else listOf(brand, variant, if (tyres.isNotBlank()) tyres + " tyre" else "", year).filter { it.isNotBlank() }.joinToString(" • ")
    /** for messages / bill text: every part with its name so nothing is unclear (Brand: Tata • Model: 1612 • Year: 2000 • No.: GJ01…) */
    fun vehicleMsg(withNo: Boolean = true): String {
        if (isLot) return vehicleInfo()
        fun lb(k: String, v: String) = if (v.isBlank()) "" else L.t(k).substringBefore(" (").substringBefore(" /").trim() + ": " + v.trim()
        return listOf(lb("brand", brand), lb("variant", variant), if (tyres.isNotBlank()) tyres + " tyre" else "", lb("year", year), if (withNo) lb("vehicle", vehicle) else "")
            .filter { it.isNotBlank() }.joinToString(" • ")
    }
    private fun ev(t: String) = evalExpr(t).let { if (it.isFinite()) it else 0.0 }
    /** lot with no total written = sum of the vehicles' prices */
    val price get() = when {
        isLot && lots.isNotEmpty() -> lots.sumOf { it.price }
        isLot && priceText.isBlank() -> vehicles.sumOf { it.price }
        else -> ev(priceText) + buyItems.sumOf { it.value() }
    }
    /** gaadi / haraji: price written for the vehicle only */
    fun vehPrice() = if (isLot) 0.0 else ev(priceText)

    /** other goods bought (lot items or gaadi / haraji extra goods) */
    fun boughtItems(): List<Line> = if (isLot) lots.flatMap { it.items } else buyItems

    /** a vehicle was bought: only then the vehicle-part buttons are offered for sale */
    fun hasVehicle(): Boolean = if (isLot) allVehicles().isNotEmpty()
        else vehPrice() != 0.0 || listOf(brand, variant, vehicle, year).any { it.isNotBlank() }

    /** the bought line of a linked sale line */
    fun boughtOf(sale: Line): Line? = if (sale.link.isEmpty()) null else boughtItems().firstOrNull { it.link == sale.link }

    /**
     * Whatever was bought (other goods) is always in the sale list too, same name and quantity by default.
     * Returns true when lines were added or removed (the screen must be redrawn).
     */
    fun syncSale(): Boolean {
        if (finalAt > 0) return false                 // a final hisab is never changed by itself
        var changed = false
        val bought = boughtItems()
        bought.forEach { b -> if (b.link.isEmpty()) b.link = java.util.UUID.randomUUID().toString().take(10) }
        val ids = bought.map { it.link }.toSet()
        if (maal.removeAll { it.link.isNotEmpty() && it.link !in ids }) changed = true
        bought.forEach { b ->
            var s = maal.firstOrNull { it.link == b.link }
            // an older hisab may already have this item in the sale list (typed by hand): use that line
            if (s == null && b.name.isNotBlank()) s = maal.firstOrNull { it.link.isEmpty() && sameItem(it, b.key, b.name) }?.also {
                it.link = b.link; it.syncKg = it.kgText
            }
            if (s == null) {
                s = Line(b.key, b.name, b.fixed, kgText = b.kgText, litre = b.litre, link = b.link, syncKg = b.kgText)
                maal.add(s); changed = true
            }
            if (s.fixed != b.fixed || s.litre != b.litre) changed = true
            s.name = b.name; s.key = b.key; s.fixed = b.fixed; s.litre = b.litre
            // quantity follows the purchase until the user writes a different sold quantity
            if (s.kgText == s.syncKg && s.kgText != b.kgText) s.kgText = b.kgText
            s.syncKg = b.kgText
        }
        return changed
    }

    /** sold less than bought: (missing qty, loss at the purchase rate); null when nothing missing */
    fun shortage(sale: Line): Pair<Double, Double>? {
        val b = boughtOf(sale) ?: return null
        if (b.fixed || sale.fixed) return null
        val miss = b.kg - sale.kg
        if (miss <= 1e-9) return null
        return miss to miss * b.rate
    }
    val sale get() = ev(saleText)
    fun kharchTotal() = kharch.sumOf { it.value() }
    fun lagat() = price + kharchTotal()
    fun maalTotal() = maal.sumOf { it.value() }
    /** Rit B: the company's sales are only the parts (the vehicle price is paid to the owner) */
    fun bikri() = if (isCo) maalTotal() else sale + maalTotal()
    /** office commission. Rit B: % of the price the company pays */
    fun commission() = if (type != "haraji") 0.0 else if (commPct) (if (isCo) sale else bikri()) * ev(commText) / 100 else ev(commText)
    private fun ownerComm() = if (isCo && commByCo) 0.0 else commission()
    private fun coComm() = if (isCo && commByCo) commission() else 0.0
    fun kg() = maal.filter { !it.fixed && !it.litre }.sumOf { it.kg }
    fun litre() = maal.filter { !it.fixed && it.litre }.sumOf { it.kg }
    /** the owner's (mudi malik + khedut) result. Rit B: company price − cost */
    fun munafa() = if (isCo) sale - lagat() - ownerComm() else bikri() - commission() - lagat()
    /** company's result, shown apart. Rit B: parts sold − price paid − commission; Rit A: the whole deal */
    fun companyResult() = if (isCo) maalTotal() - sale - coComm() else munafa()
    /** company's money in (Rit B: price + commission when the company pays it) */
    fun companyLagat() = if (isCo) sale + coComm() else lagat()
    /** a person's company share (when mudi malik / khedut are partners too) */
    fun coShareOf(name: String) = if (norm(name).isEmpty()) 0.0 else partners.filter { norm(it.name) == norm(name) }.sumOf { partnerMunafa(it) }
    fun inCompany(name: String) = norm(name).isNotEmpty() && partners.any { norm(it.name) == norm(name) }

    fun sharesTotal() = partners.sumOf { it.share }
    /** part of the deal not given to partners (goes to the owner) */
    fun ownerShare() = (100 - sharesTotal()).coerceAtLeast(0.0)
    /** each partner's money in: cost × share % */
    fun partnerLagat(p: Partner) = companyLagat() * p.share / 100
    fun partnerMunafa(p: Partner) = companyResult() * p.share / 100
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
/** "30" = 30 days, "2m" / "2 mahina" = 2 months (calendar), from [start] */
fun muddatEnd(start: Long, text: String): Long? {
    val t = text.trim().lowercase()
    if (t.isEmpty()) return null
    val months = t.endsWith("m") || t.contains("mah") || t.contains("मही") || t.contains("મહિ")
    val n = evalExpr(t.filter { it.isDigit() || it == '.' })
    if (!n.isFinite() || n < 0) return null
    if (months) return java.util.Calendar.getInstance().apply { timeInMillis = start; add(java.util.Calendar.MONTH, n.toInt()) }.timeInMillis
    return start + (n * 86400000L).toLong()
}

/** a line's own credit time, else the haraji's common one */
fun lineMuddat(h: Hisab, l: Line) = l.daysText.ifBlank { if (l.udhaar) h.muddatText else "" }

fun dueTime(h: Hisab, l: Line): Long? {
    return muddatEnd(h.time, lineMuddat(h, l))
}

/** one open credit: who owes whom, how much is left, when it is due */
class Due(val h: Hisab, val l: Line, val lena: Boolean, val left: Double, val due: Long?)

/** every open credit in all hisabs, most urgent first */
fun openDues(all: List<Hisab>): List<Due> {
    val out = mutableListOf<Due>()
    all.forEach { h ->
        h.maal.filter { it.udhaar && it.remaining() > 0.004 }.forEach { out.add(Due(h, it, true, it.remaining(), dueTime(h, it))) }
        h.buyLine.let { if (it.udhaar && it.remaining() > 0.004) out.add(Due(h, it, false, it.remaining(), dueTime(h, it))) }
        h.saleLine.let { if (it.udhaar && it.remaining() > 0.004) out.add(Due(h, it, true, it.remaining(), dueTime(h, it))) }
        h.kharch.filter { it.udhaar && it.remaining() > 0.004 }.forEach { out.add(Due(h, it, false, it.remaining(), dueTime(h, it))) }
    }
    return out.sortedWith(compareBy({ it.due ?: Long.MAX_VALUE }, { -it.left }))
}

/** 0 = open, 1 = final (admin PIN can open it), 2 = locked for ever (credit time over / haraji after 48 h) */
fun lockState(h: Hisab, now: Long = System.currentTimeMillis()): Int {
    if (h.finalAt == 0L) return 0
    val dueOver = (h.maal + h.kharch + h.buyLine + h.saleLine).any { l -> l.udhaar && (dueTime(h, l) ?: Long.MAX_VALUE) < now }
    if (dueOver) return 2
    if (h.type == "haraji" && now > h.finalAt + 48L * 3600_000L) return 2
    return 1
}

/** one line of a person's own account book */
class RoleLine(val h: Hisab, val role: String, val pct: Double, val amount: Double)

/** everything [name] is part of: as mudi malik, khedut, or company partner — only his own share */
fun personBook(all: List<Hisab>, name: String): List<RoleLine> {
    val n = norm(name)
    if (n.isEmpty()) return emptyList()
    val out = mutableListOf<RoleLine>()
    all.sortedByDescending { it.time }.forEach { h ->
        if (norm(h.mudiName) == n) out.add(RoleLine(h, "mudi", h.mudiPct, h.mudiShare()))
        if (norm(h.khedName) == n) out.add(RoleLine(h, "khed", h.khedPct, h.khedShare()))
        h.partners.filter { norm(it.name) == n }.forEach { p -> out.add(RoleLine(h, "co", p.share, h.partnerMunafa(p))) }
    }
    return out
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
        return (q.isEmpty() || listOf(h.mudiName, h.khedName, h.party, h.vehicle, h.brand, h.variant, h.place, h.note).plus(h.allVehicles().flatMap { listOf(it.brand, it.variant, it.no) }).plus(h.lots.flatMap { t -> listOf(t.name) + t.items.map { it.name } }).any { norm(it).contains(q) }) &&
            (mudi.isBlank() || norm(h.mudiName) == norm(mudi) || norm(h.khedName) == norm(mudi) || h.partners.any { norm(it.name) == norm(mudi) }) &&
            (year == 0 || c.get(java.util.Calendar.YEAR) == year) &&
            (month == 0 || c.get(java.util.Calendar.MONTH) + 1 == month) &&
            (brand.isBlank() || norm(h.brand) == norm(brand) || h.allVehicles().any { norm(it.brand) == norm(brand) }) &&
            (tyres.isBlank() || h.tyres.trim() == tyres.trim() || h.allVehicles().any { it.tyres.trim() == tyres.trim() }) &&
            (type.isBlank() || h.type == type)
    }
}

/** names of all mudi malik / khedut ever written (for the filter list) */
fun peopleNames(all: List<Hisab>): List<String> =
    all.flatMap { listOf(it.mudiName, it.khedName) + it.partners.map { p -> p.name } }.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { norm(it) }.sortedBy { norm(it) }

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

/** one buyer (party) across all hisab: everything he took from us */
class Party(val key: String, var name: String, var mobile: String) {
    /** what he took from us (we get money) */
    val items = mutableListOf<Pair<Hisab, Line>>()
    /** what we took from him: vehicle bought (we pay) */
    val buys = mutableListOf<Pair<Hisab, Line>>()
    fun buyTotal() = buys.sumOf { it.second.value() }
    fun dena() = buys.sumOf { it.second.remaining() }
    fun total() = items.sumOf { it.second.value() }
    fun left() = items.sumOf { it.second.remaining() }
    fun got() = total() - left()
    fun nextDue(): Long? = (items + buys).filter { it.second.remaining() > 0.004 }.mapNotNull { dueTime(it.first, it.second) }.minOrNull()
}

fun digits10(m: String) = m.filter { it.isDigit() }.takeLast(10)

/** same buyer = same 10-digit mobile, else same name */
fun partyKey(name: String, mobile: String): String = digits10(mobile).let { if (it.length == 10) it else if (norm(name).isEmpty()) "" else "n:" + norm(name) }

/** every buyer: parts sold (seller hisab) + vehicle sold in auction / to company. Most money due first. */
fun parties(all: List<Hisab>): List<Party> {
    val map = linkedMapOf<String, Party>()
    fun add(h: Hisab, l: Line, buy: Boolean = false) {
        val k = partyKey(l.cName, l.cMobile)
        if (k.isEmpty() || l.value() == 0.0) return
        val p = map.getOrPut(k) { Party(k, l.cName.trim(), digits10(l.cMobile)) }
        if (p.name.isBlank()) p.name = l.cName.trim()
        if (p.mobile.isBlank()) p.mobile = digits10(l.cMobile)
        if (buy) p.buys.add(h to l) else p.items.add(h to l)
    }
    all.sortedByDescending { it.time }.forEach { h ->
        if (h.role == "seller") h.maal.forEach { add(h, it) }
        if (h.type == "haraji" || h.isLot) add(h, h.saleLine)
        add(h, h.buyLine, buy = true)
    }
    return map.values.sortedWith(compareByDescending<Party> { it.left() + it.dena() }.thenByDescending { it.items.firstOrNull()?.first?.time ?: 0L })
}

/** lines of one hisab that went to this buyer */
fun buyerLines(h: Hisab, key: String): List<Line> =
    (h.maal + h.saleLine).filter { partyKey(it.cName, it.cMobile) == key && it.value() != 0.0 }

/** this month's totals (null = nothing this month) */
fun thisMonth(all: List<Hisab>, now: Long = System.currentTimeMillis()): MonthSum? {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    return monthly(all, RFilter(year = c.get(java.util.Calendar.YEAR), month = c.get(java.util.Calendar.MONTH) + 1)).firstOrNull()
}

/** haraji: names whose 10-digit mobile is missing (buyers of items / vehicle, company partners) */
// ---------- guarantor (jamindar) rule for every credit sale ----------
private fun sameParty(n1: String, m1: String, n2: String, m2: String): Boolean {
    val a = digits10(m1); val b = digits10(m2)
    if (a.length == 10 && b.length == 10) return a == b
    return n1.isNotBlank() && norm(n1) == norm(n2)
}
/** the buyer of this line is the mudi malik / khedut himself */
fun isMudiBuyer(h: Hisab, l: Line) = (h.mudiPct > 0 || h.mudiName.isNotBlank()) && sameParty(h.mudiName, h.mudiMobile, l.cName, l.cMobile)
fun isKhedBuyer(h: Hisab, l: Line) = (h.khedPct > 0 || h.khedName.isNotBlank()) && sameParty(h.khedName, h.khedMobile, l.cName, l.cMobile)
/** credit lines where we get money from somebody: goods sold, and the auction sale to a party */
fun creditLines(h: Hisab): List<Line> =
    h.maal.filter { it.udhaar && it.value() != 0.0 } + (if (h.type == "haraji" && !h.isCo && h.sale != 0.0 && h.saleLine.udhaar) listOf(h.saleLine) else emptyList())
/** a credit sale needs a guarantor – only when the mudi malik himself takes the goods it is not needed */
fun needsGuarantor(h: Hisab, l: Line) = l.udhaar && l.value() != 0.0 && !isMudiBuyer(h, l)
/** guarantor complete: name + mobile + (shop number, or the mudi malik / khedut himself is the guarantor) */
fun guarantorOk(h: Hisab, l: Line): Boolean {
    // compulsory in the haraji / company hisab; in a plain gaadi hisab the guarantor can be written but is not forced
    if (h.type != "haraji" || !needsGuarantor(h, l)) return true
    if (l.gName.isBlank() || digits10(l.gMobile).length != 10) return false
    if (digits10(l.gMobile) == digits10(l.cMobile)) return false
    return l.gBy == "mudi" || l.gBy == "khed" || l.shop.isNotBlank()
}
fun guarantorProblems(h: Hisab): List<String> =
    creditLines(h).mapNotNull { l ->
        val why = if (!guarantorOk(h, l)) L.t("g_need") else shopProblem(h, l)
        if (why == null) null else "🤝 " + l.name.ifBlank { L.ln(l) } + " → " + l.cName.ifBlank { "?" } + ": " + why
    }

/** the same company partner (same mobile, or same name) written twice in one haraji – every partner only once */
fun partnerDup(h: Hisab, p: Partner): Boolean = h.partners.any { o -> o !== p &&
    ((digits10(p.mobile).length == 10 && digits10(o.mobile) == digits10(p.mobile)) || (p.name.isNotBlank() && norm(o.name) == norm(p.name))) }
fun partnerDuplicates(h: Hisab): List<String> = h.partners.filter { partnerDup(h, it) }.map { "🏢 " + it.name.ifBlank { digits10(it.mobile) } + ": " + L.t("p_dup") }

fun missingMobile(h: Hisab): List<String> {
    val out = mutableListOf<String>()
    // new hisab (writer chosen): mudi malik and khedut mobile are required
    if (h.writer.isNotBlank()) {
        // a person with 0% (mudi malik 100% = no khedut) needs no mobile
        if ((h.mudiPct > 0 || h.mudiName.isNotBlank()) && digits10(h.mudiMobile).length != 10) out.add("💰 " + L.t("mudi_h") + " " + h.mudiName)
        if ((h.khedPct > 0 || h.khedName.isNotBlank()) && digits10(h.khedMobile).length != 10) out.add("🚚 " + L.t("khed_h") + " " + h.khedName)
    }
    if (h.type != "haraji") return out
    h.maal.filter { it.value() != 0.0 && digits10(it.cMobile).length != 10 }.forEach { out.add(it.name.ifBlank { "?" } + " → " + it.cName.ifBlank { "?" }) }
    if (!h.isCo && h.sale != 0.0 && digits10(h.saleLine.cMobile).length != 10) out.add("sale → " + h.saleLine.cName.ifBlank { "?" })
    h.partners.filter { (it.name.isNotBlank() || it.share > 0) && digits10(it.mobile).length != 10 }.forEach { out.add("🏢 " + it.name.ifBlank { "?" }) }
    return out
}

/** one person to remind today: money to collect (buyer) or a partner's share to settle */
class Remind(val h: Hisab, val name: String, val mobile: String, val amount: Double, val due: Long,
             val line: Line? = null, val partner: Partner? = null) {
    val key get() = partyKey(name, mobile)
}

/** everything whose credit time ends within [before] days (or is over) and is not paid: remind every day */
fun reminders(all: List<Hisab>, now: Long = System.currentTimeMillis(), before: Int = 3): List<Remind> {
    val limit = now + before * 86400000L
    val out = mutableListOf<Remind>()
    all.forEach { h ->
        if (h.role == "seller" || h.type == "haraji" || h.isLot) (h.maal + h.saleLine).forEach { l ->
            val d = dueTime(h, l)
            if (l.udhaar && l.remaining() > 0.004 && d != null && d <= limit) out.add(Remind(h, l.cName, digits10(l.cMobile), l.remaining(), d, line = l))
        }
        if (h.type == "haraji" && h.partners.isNotEmpty()) {
            val d = muddatEnd(h.time, h.muddatText)
            if (d != null && d <= limit) h.partners.filter { !it.done && Math.abs(h.partnerMunafa(it)) > 0.004 }.forEach { p ->
                out.add(Remind(h, p.name, digits10(p.mobile), h.partnerMunafa(p), d, partner = p))
            }
        }
    }
    return out.sortedBy { it.due }
}

/** reasons why a hisab cannot be made final yet (% totals, mehta of a haraji) */
fun finalProblems(h: Hisab): List<String> {
    val out = mutableListOf<String>()
    if (h.type == "haraji" && (h.mehtaName.isBlank() || digits10(h.mehtaMobile).length != 10)) out.add("🔨 " + L.t("mehta_need"))
    if ((h.writer.isNotBlank() || h.hasSplit()) && Math.abs(h.mudiPct + h.khedPct - 100) > 0.001)
        out.add(L.t("not100") + ": " + L.t("mudi_h") + " " + plain(h.mudiPct) + "% + " + L.t("khed_h") + " " + plain(h.khedPct) + "%")
    if (h.type == "haraji" && Math.abs(h.sharesTotal() - 100) > 0.001)
        out.add(L.t("pt_not100") + " (" + plain(h.sharesTotal()) + "%)")
    out.addAll(guarantorProblems(h))
    if (h.type == "haraji") out.addAll(partnerDuplicates(h))
    return out
}


/** what is still missing before step [s] (0 names, 1 buying) can be locked with OK */
fun stepProblems(h: Hisab, s: Int): List<String> {
    val out = mutableListOf<String>()
    when (s) {
        0 -> {
            val mp = h.mudiPct; val kp = h.khedPct
            if (mp <= 0.0 && kp <= 0.0) out.add(L.t("st_need_pct"))
            else {
                if (Math.abs(mp + kp - 100) > 0.001) out.add(L.t("not100") + ": " + L.t("mudi_h") + " " + plain(mp) + "% + " + L.t("khed_h") + " " + plain(kp) + "%")
                if (mp > 0 && (h.mudiName.isBlank() || digits10(h.mudiMobile).length != 10)) out.add("💰 " + L.t("mudi_h") + ": " + L.t("st_need_nm"))
                if (kp > 0 && (h.khedName.isBlank() || digits10(h.khedMobile).length != 10)) out.add("🚚 " + L.t("khed_h") + ": " + L.t("st_need_nm"))
            }
            if (h.type == "haraji") {
                if (h.mehtaName.isBlank() || digits10(h.mehtaMobile).length != 10) out.add("🔨 " + L.t("mehta_need"))
                if (Math.abs(h.sharesTotal() - 100) > 0.001) out.add(L.t("pt_not100") + " (" + plain(h.sharesTotal()) + "%)")
                h.partners.filter { it.share > 0 && (it.name.isBlank() || digits10(it.mobile).length != 10) }.forEach { out.add("🏢 " + it.name.ifBlank { "?" } + ": " + L.t("st_need_nm")) }
                out.addAll(partnerDuplicates(h))
            }
        }
        1 -> if (h.price <= 0.0) out.add(L.t("st_need_buy"))
    }
    return out
}
