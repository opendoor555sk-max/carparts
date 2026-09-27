package com.nirmaan.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Machinist calculator (second keypad, opened with the "Switch" half of the Conv key).
 * Works like Calculated Industries' Machinist Calc Pro (4087 user's guide):
 *  - type a value + key = store it ("$"), key alone = calculate / show,
 *  - pressing the same key again steps through related values.
 * Memories are kept in SI (metres, m/min) and shown in inch (US) or mm (Metric).
 */

private fun mk(a: String, b: String, c: String) = KeyDef(a, b, c, "")

private val MROWS = arrayOf(
    arrayOf(mk("Cut Speed", "RCT", "fn"), mk("RPM", "3-W Measure", "fn"), mk("Feed Rate", "Wire Size", "fn"), mk("Bolt Pattern", "Thread Class", "fn"), mk("Thread Size", "%Thread", "fn")),
    arrayOf(mk("#Teeth", "Sine", "fn"), mk("Feed/Tooth", "Cosine", "fn"), mk("Cut Feed", "Tangent", "fn"), mk("Diam", "Radius", "fn"), mk("Drill Size", "Drill Point", "fn")),
    arrayOf(mk("Adj (x)", "ArcSine", "fn"), mk("Opp (y)", "ArcCos", "fn"), mk("Hyp (r)", "ArcTan", "fn"), mk("Angle (Ø)", "", "fn"), mk("%", "x²", "fn")),
    arrayOf(mk("mm", "", "unit"), mk("Inch", "", "unit"), mk("/", "", "unit"), mk("1/1000\"", "", "unit"), mk("Clear", "√x", "red")),
    arrayOf(mk("Conv", "", "conv"), mk("7", "Feet", "num"), mk("8", "Alpha", "num"), mk("9", "m", "num"), mk("÷", "1/x", "op")),
    arrayOf(mk("Store", "Prefs", "st"), mk("4", "lbs", "num"), mk("5", "cm", "num"), mk("6", "tons", "num"), mk("×", "Clear All", "op")),
    arrayOf(mk("Rcl", "M-R/C", "st"), mk("1", "kg", "num"), mk("2", "grams", "num"), mk("3", "metric tons", "num"), mk("−", "+/-", "op")),
    arrayOf(mk("M+", "M-", "op"), mk("0", "wt/vol", "num"), mk(".", "dms⇄deg", "num"), mk("=", "Tape", "op"), mk("+", "π", "op"))
)

/** Machinist labels that do the same job as a Nirmaan key. */
val MACH_ALIAS = mapOf(
    "Rcl" to "Recall", "Inch" to "Inches", "tons" to "Tons", "metric tons" to "met tons",
    "Sine" to "SIN", "Cosine" to "COS", "Tangent" to "TAN", "ArcSine" to "ASIN", "ArcCos" to "ACOS", "ArcTan" to "ATAN"
)

fun Engine.machKeyDef(r: Int, c: Int): KeyDef {
    val o = MROWS[r][c]
    return KeyDef(o.main, o.conv, o.cls, "")
}

// ================= units & display =================
private fun Engine.lenU() = if (metric) "mm" else "in"
private fun Engine.lenF() = if (metric) 0.001 else IN
private fun Engine.speedU() = if (metric) "m/min" else "SFM"
private fun Engine.speedF() = if (metric) 1.0 else FT          // m/min in one display unit
private fun Engine.feedU() = if (metric) "mm/min" else "in/min"

/** typed value as metres: plain number = inch (mm in Metric), or any length */
private fun Engine.toM(q: Q): Double? = when {
    q.t == 'L' && q.d == 1 -> q.v
    q.t == 'n' -> q.v * lenF()
    else -> null
}

private fun Engine.showLen(m: Double, l: String) {
    set(if (m.isFinite()) Q('L', m, 1, lenU(), "dec") else Q.ERR, l); fresh = true
}

private fun Engine.showArea(m2: Double, l: String) {
    set(Q('L', m2, 2, lenU(), "dec"), l); fresh = true
}

private fun Engine.showNum(x: Double, l: String, places: Int = 4) {
    set(if (x.isFinite()) Q('n', rnd(x, places)) else Q.ERR, l); fresh = true
}

private fun Engine.showAng(deg: Double, l: String) {
    set(if (deg.isFinite()) Q('a', deg) else Q.ERR, l); fresh = true
}

private fun Engine.lenStr(m: Double) = num(rnd(m / lenF(), 4)) + " " + lenU()

private fun Engine.typed(): Boolean = entry != null

/** remember that the user typed this value (newest typed value wins when two formulas are possible) */
private fun Engine.mark(k: String) {
    val s = (mv["seq"] ?: 0.0) + 1
    mv["seq"] = s
    mv["t_$k"] = s
}

private fun Engine.newer(a: String, b: String) = (mv["t_$a"] ?: 0.0) >= (mv["t_$b"] ?: 0.0)

/** repeated presses of the same key step through [items] */
private fun Engine.cycle(key: String, prev: String, items: List<() -> Unit>) {
    if (items.isEmpty()) return
    val s = if (prev == key) (mStep + 1) % items.size else 0
    mKey = key
    mStep = s
    items[s]()
}

// ================= key handling =================
fun Engine.machMem(n: String, md: String) {
    if (md == "store") {
        val q = value()
        M["mm$n"] = q
        set(q, "M$n $"); fresh = true
    } else {
        val q = M["mm$n"] ?: return ui.toast("M$n khali hai")
        set(q, "M$n"); fresh = true
    }
}

fun Engine.machHandle(n: String): Boolean {
    val prev = mKey
    if (prev == "Alpha" && n == "8" && entry == null) { alphaKey(prev); return true }
    if (prev == "ThrCand" && n == "=" && entry == null) { threadFinalize(); return true }
    mKey = ""
    when (n) {
        "Clear" -> clearTemp()
        "Clear All" -> { clearTemp(); mv.clear(); triOrder.clear(); ui.toast("Machinist memory saaf (Teeth 1, 118°, 75%, 2B/6H)") }
        "grams" -> weightKey("g")
        "wt/vol" -> wtVolKey(if (isFresh()) "store" else "recall")
        "1/1000\"" -> thouKey()
        "Alpha" -> alphaKey(prev)
        "Diam" -> diamKey(prev)
        "Radius" -> radiusKey()
        "#Teeth" -> teethKey()
        "RPM" -> rpmKey(prev)
        "Cut Speed" -> speedKey(prev)
        "Feed/Tooth" -> fptKey(prev)
        "Cut Feed" -> iprKey(prev)
        "Feed Rate" -> feedKey(prev)
        "Drill Size" -> drillSizeKey(prev)
        "Drill Point" -> drillPointKey()
        "Bolt Pattern" -> boltKey(prev)
        "Thread Size" -> threadKey(prev)
        "Thread Class" -> classKey(prev)
        "%Thread" -> pctKey()
        "Wire Size" -> wireKey(prev)
        "3-W Measure" -> threeWireKey(prev)
        "RCT" -> rctKey(prev)
        "Adj (x)" -> triKeyM("x", prev)
        "Opp (y)" -> triKeyM("y", prev)
        "Hyp (r)" -> triKeyM("r", prev)
        "Angle (Ø)" -> triKeyM("a", prev)
        else -> return false
    }
    return true
}

private fun Engine.thouKey() {
    val q = value()
    when {
        q.t == 'n' -> showLen(q.v * IN / 1000, "INCH (1/1000\")")
        q.t == 'L' && q.d == 1 -> showLen(q.v, "INCH")
        else -> ui.toast("Number ya length daaliye")
    }
}

// ================= speeds & feeds =================
private fun Engine.teeth() = mv["teeth"] ?: 1.0

private fun Engine.teethKey() {
    if (typed()) {
        val q = value()
        if (q.t != 'n' || q.v < 1) return ui.toast("Daant (teeth) ginti mein daaliye")
        mv["teeth"] = Math.round(q.v).toDouble(); mark("teeth")
        return showNum(teeth(), "# TEETH $", 0)
    }
    showNum(teeth(), "# TEETH", 0)
}

private fun Engine.itemRpm(): (() -> Unit)? = mv["rpm"]?.let { v -> { showNum(v, "RPM $", 0) } }
private fun Engine.itemSpeed(): (() -> Unit)? = mv["speed"]?.let { v -> { showNum(v / speedF(), "CUT SPEED $ " + speedU(), 0) } }
private fun Engine.itemDia(): (() -> Unit)? = mv["diam"]?.let { v -> { showLen(v, "DIA $") } }
private fun Engine.itemFpt(): (() -> Unit)? = mv["fpt"]?.let { v -> { showLen(v, "FEED/TOOTH $") } }
private fun Engine.itemIpr(): (() -> Unit)? = mv["ipr"]?.let { v -> { showLen(v, "CUT FEED $ per rev") } }
private fun Engine.itemTeeth(): () -> Unit = { showNum(teeth(), "# TEETH $", 0) }

private fun Engine.rpmKey(prev: String) {
    if (typed()) {
        val q = value(); if (q.t != 'n' || q.v <= 0) return ui.toast("RPM number mein daaliye")
        mv["rpm"] = q.v; mark("rpm")
        return showNum(q.v, "RPM $", 0)
    }
    val first: () -> Unit = {
        val v = mv["speed"]; val d = mv["diam"]
        if (v != null && d != null && d > 0) {
            val r = v / (PI * d)
            mv["rpm"] = r
            showNum(r, "RPM or Spindle Speed", 0)
        } else mv["rpm"]?.let { showNum(it, "RPM $", 0) } ?: ui.toast("Cut Speed + Diam daaliye, phir RPM")
    }
    cycle("RPM", prev, listOfNotNull(first, itemSpeed(), itemDia()))
}

private fun Engine.speedKey(prev: String) {
    if (typed()) {
        val q = value(); if (q.t != 'n' || q.v <= 0) return ui.toast("Cut Speed number mein daaliye (" + speedU() + ")")
        mv["speed"] = q.v * speedF(); mark("speed")
        return showNum(q.v, "CUT SPEED $ " + speedU(), 0)
    }
    val first: () -> Unit = {
        val r = mv["rpm"]; val d = mv["diam"]
        if (r != null && d != null) {
            val v = r * PI * d
            mv["speed"] = v
            showNum(v / speedF(), "Cutting Speed " + speedU(), 0)
        } else mv["speed"]?.let { showNum(it / speedF(), "CUT SPEED $ " + speedU(), 0) } ?: ui.toast("RPM + Diam daaliye, phir Cut Speed")
    }
    cycle("Cut Speed", prev, listOfNotNull(first, itemDia(), itemRpm()))
}

private fun Engine.feedKey(prev: String) {
    if (typed()) {
        val q = value(); if (q.t != 'n' || q.v <= 0) return ui.toast("Feed Rate number mein daaliye (" + feedU() + ")")
        mv["feed"] = q.v * lenF(); mark("feed")
        return showNum(q.v, "FEED RATE $ " + feedU())
    }
    val useIpr = mv["ipr"] != null && (mv["fpt"] == null || newer("ipr", "fpt"))
    val first: () -> Unit = {
        val r = mv["rpm"]; val ipr = mv["ipr"]; val fpt = mv["fpt"]
        val f = when {
            r == null -> null
            useIpr && ipr != null -> r * ipr
            fpt != null -> r * teeth() * fpt
            else -> null
        }
        if (f != null) {
            mv["feed"] = f
            showNum(f / lenF(), "Feed Rate " + feedU())
        } else mv["feed"]?.let { showNum(it / lenF(), "FEED RATE $ " + feedU()) } ?: ui.toast("RPM + Cut Feed, ya RPM + Feed/Tooth + #Teeth daaliye")
    }
    val rest = if (useIpr) listOfNotNull(itemRpm(), itemIpr()) else listOfNotNull(itemRpm(), itemFpt(), itemTeeth())
    cycle("Feed Rate", prev, listOf(first) + rest)
}

private fun Engine.fptKey(prev: String) {
    if (typed()) {
        val m = toM(value()) ?: return ui.toast("Feed per tooth daaliye")
        mv["fpt"] = m; mark("fpt")
        return showLen(m, "FEED/TOOTH $")
    }
    val first: () -> Unit = {
        val ipr = mv["ipr"]; val f = mv["feed"]; val r = mv["rpm"]
        val v = when {
            ipr != null && (f == null || newer("ipr", "feed")) -> ipr / teeth()
            f != null && r != null -> f / (r * teeth())
            else -> null
        }
        if (v != null) {
            mv["fpt"] = v
            showLen(v, "Feed per Tooth (chip load)")
        } else mv["fpt"]?.let { showLen(it, "FEED/TOOTH $") } ?: ui.toast("Cut Feed, ya Feed Rate + RPM daaliye")
    }
    cycle("Feed/Tooth", prev, listOfNotNull(first, itemTeeth(), itemIpr()))
}

private fun Engine.iprKey(prev: String) {
    if (typed()) {
        val m = toM(value()) ?: return ui.toast("Cut Feed (per rev) daaliye")
        mv["ipr"] = m; mark("ipr")
        return showLen(m, "CUT FEED $ per rev")
    }
    val first: () -> Unit = {
        val fpt = mv["fpt"]; val f = mv["feed"]; val r = mv["rpm"]
        val v = when {
            fpt != null && (f == null || r == null || newer("fpt", "feed")) -> fpt * teeth()
            f != null && r != null -> f / r
            else -> null
        }
        if (v != null) {
            mv["ipr"] = v
            showLen(v, "Cutting Feed per rev")
        } else mv["ipr"]?.let { showLen(it, "CUT FEED $ per rev") } ?: ui.toast("Feed/Tooth + #Teeth, ya Feed Rate + RPM daaliye")
    }
    cycle("Cut Feed", prev, listOfNotNull(first, itemFpt(), itemTeeth(), itemRpm()))
}

// ================= diameter =================
private fun Engine.diamKey(prev: String) {
    if (typed()) {
        val m = toM(value()) ?: return ui.toast("Diameter number ya length mein daaliye")
        mv["diam"] = m; mark("diam")
        return showLen(m, "DIA $")
    }
    val d = mv["diam"] ?: return ui.toast("Pehle diameter daal kar Diam dabayein")
    cycle("Diam", prev, listOf(
        { showLen(d, "DIA $") },
        { showArea(PI * d * d / 4, "AREA") },
        { showLen(PI * d, "CIRC") }
    ))
}

private fun Engine.radiusKey() {
    if (typed()) {
        val m = toM(value()) ?: return ui.toast("Radius daaliye")
        mv["diam"] = 2 * m; mark("diam")
        return showLen(m, "RAD $")
    }
    val d = mv["diam"] ?: return ui.toast("Pehle diameter ya radius daaliye")
    showLen(d / 2, "RAD")
}

// ================= drills =================
private val NUM_DRILLS = doubleArrayOf(
    .2280, .2210, .2130, .2090, .2055, .2040, .2010, .1990, .1960, .1935, .1910, .1890, .1850, .1820, .1800, .1770, .1730, .1695, .1660, .1610,
    .1590, .1570, .1540, .1520, .1495, .1470, .1440, .1405, .1360, .1285, .1200, .1160, .1130, .1110, .1100, .1065, .1040, .1015, .0995, .0980,
    .0960, .0935, .0890, .0860, .0820, .0810, .0785, .0760, .0730, .0700, .0670, .0635, .0595, .0550, .0520, .0465, .0430, .0420, .0410, .0400,
    .0390, .0380, .0370, .0360, .0350, .0330, .0320, .0310, .0292, .0280, .0260, .0250, .0240, .0225, .0210, .0200, .0180, .0160, .0145, .0135
)
private val LETTER_DRILLS = doubleArrayOf(
    .234, .238, .242, .246, .250, .257, .261, .266, .272, .277, .281, .290, .295, .302, .316, .323, .332, .339, .348, .358, .368, .377, .386, .397, .404, .413
)

/** sys: f = fraction, n = number, l = letter, m = metric */
private class Drill(val name: String, val inch: Double, val sys: Char)

private fun fracName(inch: Double): String {
    val n64 = Math.round(inch * 64).toInt()
    val whole = n64 / 64
    var n = n64 % 64
    var d = 64
    while (n != 0 && n % 2 == 0) { n /= 2; d /= 2 }
    return when {
        n == 0 -> "$whole\""
        whole == 0 -> "$n/$d\""
        else -> "$whole-$n/$d\""
    }
}

private val DRILLS: List<Drill> by lazy {
    val l = ArrayList<Drill>()
    for (i in 1..128) l.add(Drill(fracName(i / 64.0), i / 64.0, 'f'))
    NUM_DRILLS.forEachIndexed { i, v -> l.add(Drill("#" + (i + 1), v, 'n')) }
    LETTER_DRILLS.forEachIndexed { i, v -> l.add(Drill(('A' + i).toString(), v, 'l')) }
    for (h in 6..280) { // 0.30 .. 14.00 mm in 0.05 mm steps
        val mm = h * 0.05
        l.add(Drill(String.format(java.util.Locale.US, "%.2f mm", mm), mm / 25.4, 'm'))
    }
    var mm = 14.5
    while (mm <= 50.0001) {
        l.add(Drill(String.format(java.util.Locale.US, "%.1f mm", mm), mm / 25.4, 'm'))
        mm += 0.5
    }
    l.sortedBy { it.inch }
}

private fun nearestDrill(inch: Double, metricSys: Boolean): Drill? =
    DRILLS.filter { (it.sys == 'm') == metricSys }.minByOrNull { abs(it.inch - inch) }

private fun smallestAtLeast(inch: Double, metricSys: Boolean): Drill? =
    DRILLS.filter { (it.sys == 'm') == metricSys && it.inch >= inch - 1e-9 }.minByOrNull { it.inch }

private fun Engine.showDrill(i: Int) {
    val dr = DRILLS[i]
    mv["drillIdx"] = i.toDouble()
    mv["drill"] = dr.inch * IN
    showLen(dr.inch * IN, dr.name + " DRILL SIZE $")
}

private fun Engine.alphaKey(prev: String) {
    val idx = if (typed()) {
        val q = value()
        if (q.t != 'n' || q.v < 1 || q.v > 26) return ui.toast("1 se 26 tak (A–Z)")
        Math.round(q.v).toInt() - 1
    } else if (prev == "Alpha") ((mv["alpha"] ?: 0.0).toInt() + 1) % 26 else 0
    mv["alpha"] = idx.toDouble()
    mKey = "Alpha"
    set(Q('n', (idx + 1).toDouble()), "ALPHA " + ('A' + idx) + "  → Drill Size"); fresh = true
}

private fun Engine.drillSizeKey(prev: String) {
    if (prev == "Alpha" && !typed()) {
        val letter = ('A' + (mv["alpha"] ?: 0.0).toInt()).toString()
        val i = DRILLS.indexOfFirst { it.sys == 'l' && it.name == letter }
        mKey = "Drill Size"
        return showDrill(i)
    }
    if (typed()) {
        val q = value()
        val i = if (!metric && q.t == 'n' && q.v >= 1 && q.v <= 80 && q.v % 1.0 == 0.0) {
            DRILLS.indexOfFirst { it.sys == 'n' && it.name == "#" + q.v.toInt() }
        } else {
            val m = toM(q) ?: return ui.toast("Drill size daaliye (jaise 36, .25 Inch, 6.8 mm)")
            val inch = m / IN
            val exact = DRILLS.indexOfFirst { abs(it.inch - inch) < 0.00005 }
            if (exact >= 0) exact else DRILLS.indexOfLast { it.inch <= inch }.coerceAtLeast(0)
        }
        if (i < 0) return ui.toast("Drill nahi mili")
        mKey = "Drill Size"
        return showDrill(i)
    }
    val cur0 = mv["drillIdx"]?.toInt() ?: return ui.toast("Drill size daal kar Drill Size dabayein")
    mKey = "Drill Size"
    showDrill(if (prev == "Drill Size") (cur0 + 1).coerceAtMost(DRILLS.size - 1) else cur0)
}

private fun Engine.drillPointKey() {
    if (typed()) {
        val q = value()
        if ((q.t != 'n' && q.t != 'a') || q.v <= 0 || q.v >= 180) return ui.toast("Point angle degree mein (jaise 118)")
        mv["dpa"] = q.v
        return showAng(q.v, "DRILL POINT ANGLE $")
    }
    val d = mv["drill"] ?: mv["diam"] ?: return ui.toast("Pehle Drill Size ya Diam daaliye")
    val a = mv["dpa"] ?: 118.0
    showLen(d / 2 / tan(a / 2 * D2R), "DRILL POINT " + num(a) + "° (tip length)")
}

// ================= bolt pattern =================
private fun Engine.boltKey(prev: String) {
    if (typed()) {
        val q = value()
        if (q.t != 'n' || q.v < 1 || q.v > 360) return ui.toast("Bolt holes ki ginti daaliye")
        mv["bolts"] = Math.round(q.v).toDouble()
        return showNum(Math.round(q.v).toDouble(), "BOLTS $", 0)
    }
    val n = mv["bolts"]?.toInt() ?: return ui.toast("Diam (bolt circle) + holes ki ginti + Bolt Pattern")
    val d = mv["diam"] ?: return ui.toast("Pehle bolt circle diameter: value + Diam")
    val cx = mv["tx"] ?: 0.0
    val cy = mv["ty"] ?: 0.0
    val a0 = mv["ta"] ?: 0.0
    val r = d / 2
    val items = ArrayList<() -> Unit>()
    items.add { showLen(d * sin(PI / n), "OC-OC (hole se hole)") }
    for (i in 0 until n) {
        val ang = (a0 + 360.0 * i / n) * D2R
        val tag = (i + 1).toString().padStart(2, '0')
        items.add { showLen(cx + r * cos(ang), "X-$tag") }
        items.add { showLen(cy + r * sin(ang), "Y-$tag") }
    }
    items.add { showLen(d, "DIA $") }
    items.add { showLen(cx, "Xoc $ (Adj)") }
    items.add { showLen(cy, "Yoc $ (Opp)") }
    items.add { showAng(a0, "ANGLE $ (pehla hole)") }
    cycle("Bolt Pattern", prev, items)
}

// ================= threads =================
private val NUMBERED = mapOf(0 to .060, 1 to .073, 2 to .086, 3 to .099, 4 to .112, 5 to .125, 6 to .138, 8 to .164, 10 to .190, 12 to .216, 14 to .242)

/** standard threads (major, pitch in metres) — offered when you press Thread Size to pick a pitch */
private class Std(val major: Double, val pitch: Double, val metric: Boolean, val tag: String)

private val STD: List<Std> by lazy {
    val l = ArrayList<Std>()
    fun un(d: Double, tpi: Double, t: String) = l.add(Std(d * IN, IN / tpi, false, t))
    mapOf(1 to 64, 2 to 56, 3 to 48, 4 to 40, 5 to 40, 6 to 32, 8 to 32, 10 to 24, 12 to 24, 14 to 20).forEach { (s, t) -> un(NUMBERED.getValue(s), t.toDouble(), "UNC") }
    mapOf(0 to 80, 1 to 72, 2 to 64, 3 to 56, 4 to 48, 5 to 44, 6 to 40, 8 to 36, 10 to 32, 12 to 28).forEach { (s, t) -> un(NUMBERED.getValue(s), t.toDouble(), "UNF") }
    listOf(.25 to 20.0, .3125 to 18.0, .375 to 16.0, .4375 to 14.0, .5 to 13.0, .5625 to 12.0, .625 to 11.0, .75 to 10.0, .875 to 9.0, 1.0 to 8.0,
        1.125 to 7.0, 1.25 to 7.0, 1.375 to 6.0, 1.5 to 6.0, 1.75 to 5.0, 2.0 to 4.5).forEach { un(it.first, it.second, "UNC") }
    listOf(.25 to 28.0, .3125 to 24.0, .375 to 24.0, .4375 to 20.0, .5 to 20.0, .5625 to 18.0, .625 to 18.0, .75 to 16.0, .875 to 14.0, 1.0 to 12.0,
        1.125 to 12.0, 1.25 to 12.0, 1.375 to 12.0, 1.5 to 12.0).forEach { un(it.first, it.second, "UNF") }
    listOf(.25 to 32.0, .3125 to 32.0, .375 to 32.0, .4375 to 28.0, .5 to 28.0, .5625 to 24.0, .625 to 24.0, .75 to 20.0, .875 to 20.0, 1.0 to 20.0)
        .forEach { un(it.first, it.second, "UNEF") }
    fun m(d: Double, p: Double, t: String) = l.add(Std(d / 1000, p / 1000, true, t))
    listOf(1.0 to .25, 1.2 to .25, 1.4 to .3, 1.6 to .35, 2.0 to .4, 2.5 to .45, 3.0 to .5, 3.5 to .6, 4.0 to .7, 5.0 to .8, 6.0 to 1.0, 7.0 to 1.0,
        8.0 to 1.25, 10.0 to 1.5, 12.0 to 1.75, 14.0 to 2.0, 16.0 to 2.0, 18.0 to 2.5, 20.0 to 2.5, 22.0 to 2.5, 24.0 to 3.0, 27.0 to 3.0,
        30.0 to 3.5, 33.0 to 3.5, 36.0 to 4.0, 42.0 to 4.5, 48.0 to 5.0).forEach { m(it.first, it.second, "coarse") }
    listOf(5.0 to .5, 6.0 to .75, 8.0 to 1.0, 10.0 to 1.25, 10.0 to 1.0, 12.0 to 1.5, 12.0 to 1.25, 14.0 to 1.5, 16.0 to 1.5, 18.0 to 1.5, 18.0 to 2.0,
        20.0 to 1.5, 20.0 to 2.0, 22.0 to 1.5, 24.0 to 2.0, 27.0 to 2.0, 30.0 to 2.0, 36.0 to 3.0).forEach { m(it.first, it.second, "fine") }
    l
}

private fun Engine.hasThread() = mv["thD"] != null && mv["thP"] != null
private fun Engine.thMetric() = (mv["thM"] ?: 0.0) == 1.0

private fun Engine.sizeName(d: Double, isMetric: Boolean, numbered: Int): String = when {
    isMetric -> "M" + num(rnd(d * 1000, 3))
    numbered >= 0 -> "#$numbered"
    else -> fracName(d / IN).removeSuffix("\"")
}

private fun Engine.threadName(): String {
    val d = mv["thD"] ?: return ""
    val p = mv["thP"] ?: return ""
    val num0 = (mv["thNum"] ?: -1.0).toInt()
    return if (thMetric()) sizeName(d, true, -1) + " × " + num(rnd(p * 1000, 3))
    else sizeName(d, false, num0) + "-" + num(rnd(IN / p, 2))
}

private fun Engine.threadKey(prev: String) {
    if (typed()) {
        val q = value()
        if ((mv["thAwait"] ?: 0.0) == 1.0 && q.t == 'n' && q.v > 0) {
            // second number = TPI (inch) or pitch in mm (metric)
            val isM = (mv["thSizeM"] ?: 0.0) == 1.0
            if (isM && q.v > 10) return ui.toast("Pitch 10 mm se kam")
            mv["thD"] = mv["thSizeD"] ?: return
            mv["thP"] = if (isM) q.v / 1000 else IN / q.v
            mv["thM"] = if (isM) 1.0 else 0.0
            mv["thNum"] = mv["thSizeNum"] ?: -1.0
            mv["thAwait"] = 0.0
            return showLen(mv["thD"]!!, "THREAD SIZE $ " + threadName())
        }
        var numbered = -1
        val isM: Boolean
        val d: Double
        when {
            q.t == 'L' && q.d == 1 -> { d = q.v; isM = q.u in listOf("mm", "cm", "m") }
            q.t == 'n' && !metric && q.v % 1.0 == 0.0 && NUMBERED.containsKey(q.v.toInt()) -> {
                numbered = q.v.toInt(); d = NUMBERED.getValue(numbered) * IN; isM = false
            }
            q.t == 'n' && metric -> { d = q.v / 1000; isM = true }
            q.t == 'n' -> { d = q.v * IN; isM = false }
            else -> return ui.toast("Thread ka size daaliye (8, 1/4 Inch, 10 mm)")
        }
        if (isM && (d < 0.001 - 1e-9 || d > 0.3)) return ui.toast("Metric thread 1–300 mm")
        if (!isM && numbered < 0 && (d < 0.06 * IN - 1e-9 || d > 6 * IN)) return ui.toast("Inch thread 0.06\"–6\"")
        mv["thSizeD"] = d
        mv["thSizeM"] = if (isM) 1.0 else 0.0
        mv["thSizeNum"] = numbered.toDouble()
        mv["thAwait"] = 1.0
        return showLen(d, "SIZE " + sizeName(d, isM, numbered) + " –" + (if (isM) " MM  (ab pitch)" else "  (ab TPI)"))
    }
    if ((mv["thAwait"] ?: 0.0) == 1.0) {
        // press Thread Size again = step through the standard pitches, then "=" to keep one
        val d = mv["thSizeD"] ?: return
        val isM = (mv["thSizeM"] ?: 0.0) == 1.0
        val cands = STD.filter { it.metric == isM && abs(it.major - d) < 0.00002 }
        if (cands.isEmpty()) return ui.toast(if (isM) "Pitch (mm) daal kar Thread Size" else "TPI daal kar Thread Size")
        val items = cands.map { s ->
            {
                mv["thD"] = s.major
                mv["thP"] = s.pitch
                mv["thM"] = if (isM) 1.0 else 0.0
                mv["thNum"] = mv["thSizeNum"] ?: -1.0
                showLen(s.major, threadName() + " " + s.tag + "  (= dabayein)")
            }
        }
        cycle("ThrCand", prev, items)
        return
    }
    if (!hasThread()) return ui.toast("Size + Thread Size, phir TPI/pitch + Thread Size")
    cycle("Thread Size", prev, if (isExt()) externalItems() else internalItems())
}

private fun Engine.threadFinalize() {
    mv["thAwait"] = 0.0
    mKey = ""
    showLen(mv["thD"] ?: 0.0, "THREAD SIZE $ " + threadName())
}

// ---------- thread class ----------
private val MPOS = listOf("H", "g", "G", "h", "f", "e")

private fun Engine.usClass() = (mv["cls"] ?: 2.0).toInt()
private fun Engine.mGrade() = (mv["mg"] ?: 6.0).toInt()
private fun Engine.mPos() = MPOS[(mv["mpos"] ?: 0.0).toInt().coerceIn(0, MPOS.size - 1)]
private fun Engine.metricClassMode() = if (hasThread()) thMetric() else metric

private fun Engine.isExt(): Boolean = if (metricClassMode()) mPos()[0].isLowerCase() else (mv["ext"] ?: 0.0) == 1.0

private fun Engine.className(): String =
    if (metricClassMode()) (if (isExt()) "EXT MM " else "INT MM ") + mGrade() + mPos()
    else (if (isExt()) "EXT " else "INT ") + usClass() + (if (isExt()) "A" else "B")

private fun Engine.classKey(prev: String) {
    if (typed()) {
        val q = value()
        val n = Math.round(q.v).toInt()
        if (metricClassMode()) {
            if (n !in 3..9) return ui.toast("Metric grade 3–9")
            mv["mg"] = n.toDouble()
        } else {
            if (n !in 1..3) return ui.toast("Class 1, 2 ya 3")
            mv["cls"] = n.toDouble()
        }
    } else if (prev == "Thread Class") {
        if (metricClassMode()) mv["mpos"] = (((mv["mpos"] ?: 0.0).toInt() + 1) % MPOS.size).toDouble()
        else mv["ext"] = if ((mv["ext"] ?: 0.0) == 1.0) 0.0 else 1.0
    }
    mKey = "Thread Class"
    set(Q('n', (if (metricClassMode()) mGrade() else usClass()).toDouble()), className()); fresh = true
}

// ---------- tolerances ----------
private val R40 = doubleArrayOf(1.0, 1.06, 1.12, 1.18, 1.25, 1.32, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.12, 2.24, 2.36, 2.5, 2.65, 2.8, 3.0,
    3.15, 3.35, 3.55, 3.75, 4.0, 4.25, 4.5, 4.75, 5.0, 5.3, 5.6, 6.0, 6.3, 6.7, 7.1, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

private fun r40(x: Double): Double {
    if (x <= 0) return 0.0
    var dec = 1.0
    while (x / dec >= 10) dec *= 10
    while (x / dec < 1) dec /= 10
    val v = x / dec
    return R40.minByOrNull { abs(it - v) }!! * dec
}

/** ISO 965-1 fundamental deviations (µm) by pitch: G/g, e, f (0 = not defined) */
private val DEV = mapOf(
    0.2 to Triple(17, 0, 0), 0.25 to Triple(18, 0, 0), 0.3 to Triple(18, 0, 0), 0.35 to Triple(19, 0, 34), 0.4 to Triple(19, 0, 34),
    0.45 to Triple(20, 0, 35), 0.5 to Triple(20, 50, 36), 0.6 to Triple(21, 53, 36), 0.7 to Triple(22, 56, 38), 0.75 to Triple(22, 56, 38),
    0.8 to Triple(24, 60, 38), 1.0 to Triple(26, 60, 40), 1.25 to Triple(28, 63, 42), 1.5 to Triple(32, 67, 45), 1.75 to Triple(34, 71, 48),
    2.0 to Triple(38, 71, 52), 2.5 to Triple(42, 80, 58), 3.0 to Triple(48, 85, 63), 3.5 to Triple(53, 90, 70), 4.0 to Triple(60, 95, 75),
    4.5 to Triple(63, 100, 80), 5.0 to Triple(71, 106, 85), 5.5 to Triple(75, 112, 90), 6.0 to Triple(80, 118, 95)
)

private fun devUm(pos: Char, pMm: Double): Double {
    val row = DEV.entries.firstOrNull { abs(it.key - pMm) < 1e-6 }?.value
    return when (pos.lowercaseChar()) {
        'g' -> row?.first?.toDouble() ?: r40(15 + 11 * pMm)
        'f' -> row?.third?.takeIf { it > 0 }?.toDouble() ?: r40(30 + 11 * pMm)
        'e' -> row?.second?.takeIf { it > 0 }?.toDouble() ?: r40(50 + 11 * pMm)
        else -> 0.0
    }
}

private fun gradeF(g: Int, table: Map<Int, Double>): Double {
    table[g]?.let { return it }
    val lo = table.keys.minOrNull()!!
    val hi = table.keys.maxOrNull()!!
    if (g < lo) return table.getValue(lo) / 1.25.pow(lo - g)
    if (g > hi) return table.getValue(hi) * 1.25.pow(g - hi)
    // missing middle grade: geometric mean of neighbours
    val a = table.keys.filter { it < g }.maxOrNull()!!
    val b = table.keys.filter { it > g }.minOrNull()!!
    return sqrt(table.getValue(a) * table.getValue(b))
}

private val TD2_EXT = mapOf(3 to 0.5, 4 to 0.63, 5 to 0.8, 6 to 1.0, 7 to 1.25, 8 to 1.6, 9 to 2.0)
private val TD2_INT = mapOf(4 to 0.85, 5 to 1.06, 6 to 1.32, 7 to 1.7, 8 to 2.12)
private val TD_EXT = mapOf(4 to 0.63, 6 to 1.0, 8 to 1.6)
private val TD1_INT = mapOf(4 to 0.63, 5 to 0.8, 6 to 1.0, 7 to 1.25, 8 to 1.6)
private val BANDS = doubleArrayOf(1.0, 1.4, 2.8, 5.6, 11.2, 22.4, 45.0, 90.0, 180.0, 300.0)

/** thread limits in metres */
private class Limits(val pdMin: Double, val pdMax: Double, val majMin: Double, val majMax: Double, val minMin: Double, val minMax: Double)

private fun round3(x: Double) = Math.round(x * 1000) / 1000.0

private fun Engine.limits(forceExt: Boolean = false): Limits {
    val dM = mv["thD"]!!
    val pM = mv["thP"]!!
    val ext = forceExt || isExt()
    if (!thMetric()) {
        // ASME B1.1 (inches)
        val d = dM / IN
        val p = pM / IN
        val e = d - 0.649519 * p
        val t2a = 0.0015 * d.pow(1.0 / 3) + 0.0015 * sqrt(d) + 0.015 * p.pow(2.0 / 3)
        val c = usClass()
        return if (ext) {
            val allow = if (c == 3) 0.0 else 0.3 * t2a
            val mult = when (c) { 1 -> 1.5; 3 -> 0.75; else -> 1.0 }
            val majMax = d - allow
            val majMin = majMax - (if (c == 1) 0.09 else 0.06) * p.pow(2.0 / 3)
            val pdMax = e - allow
            Limits((pdMax - t2a * mult) * IN, pdMax * IN, majMin * IN, majMax * IN, 0.0, (d - 1.190785 * p - allow) * IN)
        } else {
            val mult = when (c) { 1 -> 1.95; 3 -> 0.975; else -> 1.3 }
            val minRaw = d - 1.082532 * p
            val base = 0.05 * p.pow(2.0 / 3) + 0.03 * p / d - 0.002
            val tol = if (c == 3) max(min(base, 0.394 * p), 0.23 * p - 1.5 * p * p)
            else if (d < 0.25) base else 0.25 * p - 0.4 * p * p
            Limits(e * IN, (e + t2a * mult) * IN, d * IN, Double.NaN, round3(minRaw) * IN, round3(minRaw + tol) * IN)
        }
    }
    // ISO 965-1 (mm and µm)
    val d = dM * 1000
    val p = pM * 1000
    val e = round3(d - 0.649519 * p)
    val bi = (1 until BANDS.size).firstOrNull { d <= BANDS[it] } ?: (BANDS.size - 1)
    val dm = sqrt(BANDS[bi - 1] * BANDS[bi])
    // ISO: grade-6 values are rounded first, other grades = factor × grade 6, rounded again
    val td26 = r40(90 * p.pow(0.4) * dm.pow(0.1))
    val g = mGrade()
    val pos = mPos()[0]
    return if (ext) {
        val ep = pos.lowercaseChar()
        val es = -devUm(ep, p) / 1000
        val td2 = r40(td26 * gradeF(g, TD2_EXT)) / 1000
        val td = r40(r40(180 * p.pow(2.0 / 3) - 3.15 / sqrt(p)) * gradeF(g, TD_EXT)) / 1000
        val majMax = d + es
        Limits((e + es - td2) / 1000, (e + es) / 1000, (majMax - td) / 1000, majMax / 1000, 0.0, round3(d - 1.082532 * p + es) / 1000)
    } else {
        val ip = pos.uppercaseChar()
        val ei = (if (ip == 'G') devUm('g', p) else 0.0) / 1000
        val td2 = r40(td26 * gradeF(g, TD2_INT)) / 1000
        val td16 = r40(if (p <= 0.8) 433 * p - 190 * p.pow(1.22) else 230 * p.pow(0.7))
        val td1 = r40(td16 * gradeF(g, TD1_INT)) / 1000
        val minMin = round3(d - 1.082532 * p + ei)
        Limits((e + ei) / 1000, (e + ei + td2) / 1000, (d + ei) / 1000, Double.NaN, minMin / 1000, (minMin + td1) / 1000)
    }
}

/** clearance holes: close / free (metric: ISO 273 fine / coarse) */
private val ISO273 = mapOf(1.0 to (1.1 to 1.3), 1.2 to (1.3 to 1.5), 1.6 to (1.7 to 2.0), 2.0 to (2.2 to 2.6), 2.5 to (2.7 to 3.1), 3.0 to (3.2 to 3.6),
    4.0 to (4.3 to 4.8), 5.0 to (5.3 to 5.8), 6.0 to (6.4 to 7.0), 8.0 to (8.4 to 10.0), 10.0 to (10.5 to 12.0), 12.0 to (13.0 to 14.5),
    14.0 to (15.0 to 16.5), 16.0 to (17.0 to 18.5), 20.0 to (21.0 to 24.0), 24.0 to (25.0 to 28.0), 30.0 to (31.0 to 35.0))

private fun Engine.drillItem(label: String, targetM: Double, metricSys: Boolean): () -> Unit = {
    val big = if (metricSys) targetM > 0.050 else targetM > 2 * IN
    val dr = if (big) null else nearestDrill(targetM / IN, metricSys)
    if (dr == null) showLen(targetM, "$label (hole size)") else showLen(dr.inch * IN, label + " " + dr.name)
}

private fun Engine.clearItem(label: String, holeM: Double, metricSys: Boolean): () -> Unit = {
    val dr = smallestAtLeast(holeM / IN - 1e-6, metricSys)
    if (dr == null) showLen(holeM, "$label HOLE") else showLen(dr.inch * IN, "$label DRILL " + dr.name)
}

private fun Engine.internalItems(): List<() -> Unit> {
    val d = mv["thD"]!!
    val p = mv["thP"]!!
    val pct = mv["pct"] ?: 75.0
    val lim = limits()
    val cut = d - 1.299038 * p * pct / 100
    val roll = d - 0.68 * p * pct / 100
    val close: () -> Unit
    val free: () -> Unit
    if (thMetric()) {
        val iso = ISO273.entries.firstOrNull { abs(it.key / 1000 - d) < 1e-7 }?.value
        close = clearItem("CLOSE", (iso?.first ?: d * 1060) / 1000, true)
        free = clearItem("FREE", (iso?.second ?: d * 1160) / 1000, true)
    } else {
        close = clearItem("CLOSE", d * 1.02, false)
        free = clearItem("FREE", d * 1.06, false)
    }
    val cls = className()
    return listOf(
        { showLen(d, "THREAD SIZE $ " + threadName() + " " + cls) },
        drillItem("TAP DRILL (" + num(pct) + "%)", cut, metric),
        drillItem("R-TAP DRILL (roll)", roll, !metric),
        close, free,
        { showLen(lim.pdMin, "PTCH- (pitch dia min)") },
        { showLen(lim.pdMax, "PTCH+ (pitch dia max)") },
        { showLen(lim.minMin, "MINR- (minor dia min)") },
        { showLen(lim.minMax, "MINR+ (minor dia max)") },
        { showLen(lim.majMin, "MAJR- (major dia min)") }
    )
}

private fun Engine.externalItems(): List<() -> Unit> {
    val d = mv["thD"]!!
    val p = mv["thP"]!!
    val lim = limits()
    return listOf(
        { showLen(d, "THREAD SIZE $ " + threadName() + " " + className()) },
        { showLen(d, "ROD SIZE (cut thread)") },
        { showLen(d - 0.730709 * p, "CFORM SIZE (roll thread)") },
        { showLen(lim.pdMax, "PTCH+ (pitch dia max)") },
        { showLen(lim.pdMin, "PTCH- (pitch dia min)") },
        { showLen(lim.majMax, "MAJR+ (major dia max)") },
        { showLen(lim.majMin, "MAJR- (major dia min)") },
        { showLen(lim.minMax, "MINR+ (minor dia max)") }
    )
}

private fun Engine.pctKey() {
    if (typed()) {
        val q = value()
        if ((q.t == 'n' || q.t == 'p') && q.v > 0 && q.v <= 100) {
            mv["pct"] = q.v
            return showNum(q.v, "THREAD % $ (tap drill)")
        }
        if (!hasThread()) return ui.toast("Pehle Thread Size chunen")
        val drill = toM(q) ?: return ui.toast("% (jaise 65) ya drill size daaliye")
        return showNum((mv["thD"]!! - drill) / (1.299038 * mv["thP"]!!) * 100, "% THREAD is drill se")
    }
    showNum(mv["pct"] ?: 75.0, "THREAD % $")
}

private fun Engine.wireKey(prev: String) {
    if (typed()) {
        val m = toM(value()) ?: return ui.toast("Wire size daaliye")
        mv["wire"] = m
        return showLen(m, "WIRE SIZE $")
    }
    if (!hasThread()) return ui.toast("Pehle Thread Size chunen")
    val p = mv["thP"]!!
    cycle("Wire Size", prev, listOf(
        { showLen(0.57735 * p, "WIRE IDEAL " + threadName()) },
        { showLen(0.90 * p, "WIRE MAX") },
        { showLen(0.56 * p, "WIRE MIN") }
    ))
}

private fun Engine.threeWireKey(prev: String) {
    if (!hasThread()) return ui.toast("Pehle Thread Size chunen")
    val p = mv["thP"]!!
    val w = mv["wire"] ?: (0.57735 * p)
    if (typed()) {
        val m = toM(value()) ?: return ui.toast("Wires ke upar ka naap daaliye")
        return showLen(m - 3 * w + 0.866025 * p, "P-DIA (3-wire se)")
    }
    val lim = limits(forceExt = true)
    cycle("3-W Measure", prev, listOf(
        { showLen(lim.pdMin - 0.866025 * p + 3 * w, "3W MIN (wire " + lenStr(w) + ")") },
        { showLen(lim.pdMax - 0.866025 * p + 3 * w, "3W MAX") },
        { showLen(lim.pdMin, "P-DIA MIN") },
        { showLen(lim.pdMax, "P-DIA MAX") }
    ))
}

// ================= radial chip thinning =================
private fun Engine.rctKey(prev: String) {
    val isTyped = typed()
    if (isTyped) {
        val m = toM(value()) ?: return ui.toast("Radial cut depth (stepover) daaliye")
        mv["ae"] = m
    }
    val ae = mv["ae"] ?: return ui.toast("Stepover (radial depth) daal kar Conv + Cut Speed")
    val d = mv["diam"] ?: return ui.toast("Pehle cutter diameter: value + Diam")
    if (ae >= d / 2) return showNum(1.0, "RCT factor 1 (stepover ≥ D/2)")
    val factor = d / (2 * sqrt(d * ae - ae * ae))
    val items = mutableListOf<() -> Unit>({ showNum(factor, "RCT FACTOR (stepover " + lenStr(ae) + ")") })
    val f = mv["fpt"]
    val r = mv["rpm"]
    if (f != null) items.add { showLen(f * factor, "ADJ FEED/TOOTH") }
    if (f != null && r != null) items.add { showNum(r * teeth() * f * factor / lenF(), "ADJ FEED RATE " + feedU()) }
    cycle("RCT", if (isTyped) "" else prev, items)
}

// ================= right triangle =================
private fun Engine.triKeyM(k: String, prev: String) {
    val names = mapOf("x" to "ADJ (x)", "y" to "OPP (y)", "r" to "HYP (r)", "a" to "ANGLE (Ø)")
    if (typed()) {
        val q = value()
        val v = if (k == "a") (if (q.t == 'a' || q.t == 'n') q.v else return ui.toast("Angle degree mein daaliye"))
        else toM(q) ?: return ui.toast("Length daaliye")
        mv["t$k"] = v
        triOrder.remove(k); triOrder.add(k)
        return if (k == "a") showAng(v, names.getValue(k) + " $") else showLen(v, names.getValue(k) + " $")
    }
    if (k == "a" && prev == "Angle (Ø)") {
        mKey = ""
        val a = mv["ta"] ?: return
        return showAng(90 - a, "ADJ<Ø (doosra angle)")
    }
    mKey = if (k == "a") "Angle (Ø)" else ""
    val others = triOrder.filter { it != k && mv["t$it"] != null }.takeLast(2)
    if (others.size < 2) {
        val v = mv["t$k"] ?: return ui.toast("Koi bhi 2 value daaliye (x, y, r ya Ø), phir baaki dabayein")
        return if (k == "a") showAng(v, names.getValue(k)) else showLen(v, names.getValue(k))
    }
    val x = mv["tx"].takeIf { "x" in others }
    val y = mv["ty"].takeIf { "y" in others }
    val r = mv["tr"].takeIf { "r" in others }
    val ar = mv["ta"].takeIf { "a" in others }?.times(D2R)
    val res: Double = when (k) {
        "x" -> when { r != null && ar != null -> r * cos(ar); y != null && ar != null -> y / tan(ar); r != null && y != null -> sqrt(r * r - y * y); else -> Double.NaN }
        "y" -> when { r != null && ar != null -> r * sin(ar); x != null && ar != null -> x * tan(ar); r != null && x != null -> sqrt(r * r - x * x); else -> Double.NaN }
        "r" -> when { x != null && y != null -> sqrt(x * x + y * y); x != null && ar != null -> x / cos(ar); y != null && ar != null -> y / sin(ar); else -> Double.NaN }
        else -> when { x != null && y != null -> atan2(y, x) / D2R; x != null && r != null -> acos(x / r) / D2R; y != null && r != null -> asin(y / r) / D2R; else -> Double.NaN }
    }
    if (!res.isFinite()) return set(Q.ERR, names.getValue(k))
    mv["t$k"] = res
    if (k == "a") showAng(res, names.getValue(k)) else showLen(res, names.getValue(k))
}
