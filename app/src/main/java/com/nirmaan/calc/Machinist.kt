package com.nirmaan.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Machinist calculator (second keypad, opened with the "Switch" half of the Conv key).
 * Layout follows Machinist Calc Pro. All memories are kept in SI (metres, m/min, m/rev)
 * and shown in inch units, or mm / m/min when Metric is on.
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
    "Rcl" to "Recall", "Inch" to "Inches", "Clear All" to "ClrAll", "tons" to "Tons", "metric tons" to "met tons",
    "Sine" to "SIN", "Cosine" to "COS", "Tangent" to "TAN", "ArcSine" to "ASIN", "ArcCos" to "ACOS", "ArcTan" to "ATAN"
)

fun Engine.machKeyDef(r: Int, c: Int): KeyDef {
    val o = MROWS[r][c]
    return KeyDef(o.main, o.conv, o.cls, "")
}

// ---------------- units ----------------
private fun Engine.lenU() = if (metric) "mm" else "in"
private fun Engine.lenF() = if (metric) 0.001 else IN
private fun Engine.speedU() = if (metric) "m/min" else "SFM (ft/min)"
private fun Engine.speedF() = if (metric) 1.0 else FT          // m/min per display unit
private fun Engine.feedU() = if (metric) "mm/min" else "in/min"

/** a typed value as metres: plain number = inch (or mm in Metric), or any length */
private fun Engine.toM(q: Q): Double? = when {
    q.t == 'L' && q.d == 1 -> q.v
    q.t == 'n' -> q.v * lenF()
    else -> null
}

private fun Engine.showLen(m: Double, l: String) = set(Q('L', m, 1, lenU(), "dec"), l).also { fresh = true }
private fun Engine.showNum(x: Double, l: String) = set(if (x.isFinite()) Q('n', rnd(x, 4)) else Q.ERR, l).also { fresh = true }
private fun Engine.showAng(deg: Double, l: String) = set(Q('a', deg), l).also { fresh = true }
/** form value, empty box = 0 */
private fun Map<String, Double>.g(k: String) = this[k]?.takeIf { it.isFinite() } ?: 0.0

private fun Engine.lenStr(m: Double) = num(rnd(m / lenF(), 4)) + " " + lenU()

/** true = the user typed a new value for this key (store it), false = work it out */
private fun Engine.typed(): Boolean = entry != null

// ---------------- key handling ----------------
fun Engine.machHandle(n: String): Boolean {
    when (n) {
        "Clear" -> { clearTemp(); return true }
        "grams" -> { weightKey("g"); return true }
        "wt/vol" -> { wtVolKey(if (isFresh()) "store" else "recall"); return true }
        "1/1000\"" -> { thouKey(); return true }
        "Alpha" -> { ui.toast("Alpha (naam) agle phase mein aayega"); return true }
        "Diam" -> { diamKey(false); return true }
        "Radius" -> { diamKey(true); return true }
        "#Teeth" -> { teethKey(); return true }
        "RPM" -> { rpmKey(); return true }
        "Cut Speed" -> { speedKey(); return true }
        "Feed/Tooth" -> { fptKey(); return true }
        "Cut Feed" -> { iprKey(); return true }
        "Feed Rate" -> { feedKey(); return true }
        "Drill Size" -> { drillSizeKey(); return true }
        "Drill Point" -> { drillPointKey(); return true }
        "Bolt Pattern" -> { boltForm(); return true }
        "Thread Size" -> { threadKey(); return true }
        "%Thread" -> { pctThreadKey(); return true }
        "Wire Size", "3-W Measure" -> { wireKey(n); return true }
        "Thread Class" -> { ui.toast("Thread Class (2A/2B, 6g/6H) agle phase mein. Thread Size abhi kaam karta hai."); return true }
        "RCT" -> { rctForm(); return true }
        "Adj (x)" -> { triKeyM("x"); return true }
        "Opp (y)" -> { triKeyM("y"); return true }
        "Hyp (r)" -> { triKeyM("r"); return true }
        "Angle (Ø)" -> { triKeyM("a"); return true }
    }
    return false
}

private fun Engine.thouKey() {
    val q = value()
    when {
        q.t == 'n' -> set(Q('L', q.v * IN / 1000, 1, "in", "dec"), "1/1000\" (thou)")
        q.t == 'L' && q.d == 1 -> set(Q('n', rnd(q.v / IN * 1000, 3)), "Thou (1/1000\")")
        else -> return ui.toast("Number ya length daaliye")
    }
    fresh = true
}

private fun Engine.diamKey(radius: Boolean) {
    if (typed()) {
        val m = toM(value()) ?: return ui.toast("Diameter number ya length mein daaliye")
        mv["diam"] = if (radius) 2 * m else m
        return if (radius) showLen(m, "Radius stored") else showLen(m, "Diameter stored")
    }
    val d = mv["diam"] ?: return ui.toast("Pehle diameter daal kar Diam dabayein")
    if (radius) showLen(d / 2, "Radius") else showLen(d, "Diameter")
}

private fun Engine.teethKey() {
    if (typed()) {
        val q = value()
        if (q.t != 'n' || q.v < 1) return ui.toast("Daant (teeth) ginti mein daaliye")
        mv["teeth"] = Math.round(q.v).toDouble()
        return showNum(mv["teeth"]!!, "# of Teeth stored")
    }
    val t = mv["teeth"] ?: return ui.toast("Pehle daant ki ginti daal kar #Teeth dabayein")
    showNum(t, "# of Teeth")
}

private fun Engine.rpmKey() {
    if (typed()) {
        val q = value(); if (q.t != 'n' || q.v <= 0) return ui.toast("RPM number mein daaliye")
        mv["rpm"] = q.v
        return showNum(q.v, "RPM stored")
    }
    val v = mv["speed"]; val d = mv["diam"]
    if (v == null || d == null) return mv["rpm"]?.let { showNum(it, "RPM") } ?: ui.toast("Cut Speed + Diam daaliye, phir RPM")
    val rpm = v / (PI * d)
    mv["rpm"] = rpm
    showNum(rpm, "RPM or Spindle Speed")
}

private fun Engine.speedKey() {
    if (typed()) {
        val q = value(); if (q.t != 'n' || q.v <= 0) return ui.toast("Cut Speed number mein daaliye")
        mv["speed"] = q.v * speedF()
        return showNum(q.v, "Cut Speed stored " + speedU())
    }
    val rpm = mv["rpm"]; val d = mv["diam"]
    if (rpm == null || d == null) return mv["speed"]?.let { showNum(it / speedF(), "Cut Speed " + speedU()) } ?: ui.toast("RPM + Diam daaliye, phir Cut Speed")
    val v = rpm * PI * d
    mv["speed"] = v
    showNum(v / speedF(), "Cut Speed " + speedU())
}

private fun Engine.fptKey() {
    if (typed()) {
        val m = toM(value()) ?: return ui.toast("Feed per tooth daaliye")
        mv["fpt"] = m
        return showLen(m, "Feed/Tooth stored")
    }
    val f = mv["feed"]; val rpm = mv["rpm"]; val t = mv["teeth"]
    if (f == null || rpm == null || t == null) return mv["fpt"]?.let { showLen(it, "Feed/Tooth (chip load)") } ?: ui.toast("Feed Rate + RPM + #Teeth daaliye")
    val fpt = f / (rpm * t)
    mv["fpt"] = fpt
    showLen(fpt, "Feed/Tooth (chip load)")
}

private fun Engine.iprKey() {
    if (typed()) {
        val m = toM(value()) ?: return ui.toast("Cut Feed (per rev) daaliye")
        mv["ipr"] = m
        return showLen(m, "Cut Feed stored (per rev)")
    }
    val f = mv["feed"]; val rpm = mv["rpm"]
    if (f == null || rpm == null) return mv["ipr"]?.let { showLen(it, "Cut Feed (per rev)") } ?: ui.toast("Feed Rate + RPM daaliye")
    val ipr = f / rpm
    mv["ipr"] = ipr
    showLen(ipr, "Cut Feed (per rev)")
}

private fun Engine.feedKey() {
    if (typed()) {
        val q = value(); if (q.t != 'n' || q.v <= 0) return ui.toast("Feed Rate number mein daaliye (" + feedU() + ")")
        mv["feed"] = q.v * lenF()
        return showNum(q.v, "Feed Rate stored " + feedU())
    }
    val rpm = mv["rpm"] ?: return mv["feed"]?.let { showNum(it / lenF(), "Feed Rate " + feedU()) } ?: Unit.also { ui.toast("RPM + (#Teeth × Feed/Tooth) ya Cut Feed daaliye") }
    val t = mv["teeth"]; val fpt = mv["fpt"]; val ipr = mv["ipr"]
    val f = when {
        t != null && fpt != null -> rpm * t * fpt
        ipr != null -> rpm * ipr
        else -> return ui.toast("#Teeth + Feed/Tooth, ya Cut Feed bhi daaliye")
    }
    mv["feed"] = f
    showNum(f / lenF(), "Feed Rate " + feedU())
}

// ---------------- drills ----------------
private val NUM_DRILLS = doubleArrayOf(
    .2280, .2210, .2130, .2090, .2055, .2040, .2010, .1990, .1960, .1935, .1910, .1890, .1850, .1820, .1800, .1770, .1730, .1695, .1660, .1610,
    .1590, .1570, .1540, .1520, .1495, .1470, .1440, .1405, .1360, .1285, .1200, .1160, .1130, .1110, .1100, .1065, .1040, .1015, .0995, .0980,
    .0960, .0935, .0890, .0860, .0820, .0810, .0785, .0760, .0730, .0700, .0670, .0635, .0595, .0550, .0520, .0465, .0430, .0420, .0410, .0400,
    .0390, .0380, .0370, .0360, .0350, .0330, .0320, .0310, .0292, .0280, .0260, .0250, .0240, .0225, .0210, .0200, .0180, .0160, .0145, .0135
)
private val LETTER_DRILLS = doubleArrayOf(
    .234, .238, .242, .246, .250, .257, .261, .266, .272, .277, .281, .290, .295, .302, .316, .323, .332, .339, .348, .358, .368, .377, .386, .397, .404, .413
)

private class Drill(val name: String, val inch: Double)

private fun allDrills(): List<Drill> {
    val l = ArrayList<Drill>()
    for (i in 1..64) {
        var n = i; var d = 64
        while (n % 2 == 0 && d > 1) { n /= 2; d /= 2 }
        l.add(Drill(if (d == 1) "$n\"" else "$n/$d\"", i / 64.0))
    }
    NUM_DRILLS.forEachIndexed { i, v -> l.add(Drill("#" + (i + 1), v)) }
    LETTER_DRILLS.forEachIndexed { i, v -> l.add(Drill(('A' + i).toString(), v)) }
    var mm = 0.5
    while (mm <= 32.0001) {
        l.add(Drill(String.format(java.util.Locale.US, "%.1f mm", mm), mm / 25.4))
        mm += if (mm < 13 - 1e-9) 0.1 else 0.5
    }
    return l
}

private fun Engine.drillDia(): Double? {
    if (typed()) return toM(value())?.also { mv["diam"] = it }
    return mv["diam"]
}

private fun Engine.drillSizeKey() {
    val d = drillDia() ?: return ui.toast("Size daaliye (jaise 0.25 ya 6.8 mm) phir Drill Size")
    val inch = d / IN
    val drills = allDrills()
    fun near(f: (Drill) -> Boolean) = drills.filter(f).minByOrNull { abs(it.inch - inch) }
    fun row(label: String, dr: Drill?) = if (dr == null) null else
        Row(label, dr.name + "  =  " + num(rnd(dr.inch, 4)) + "\"  /  " + num(rnd(dr.inch * 25.4, 2)) + " mm  (" + (if (dr.inch >= inch) "+" else "") + num(rnd((dr.inch - inch) * 1000, 1)) + " thou)")
    val over = drills.filter { it.inch >= inch - 1e-9 }.minByOrNull { it.inch }
    val under = drills.filter { it.inch <= inch + 1e-9 }.maxByOrNull { it.inch }
    val rows = listOfNotNull(
        sec("Size"), Row("Diya hua", num(rnd(inch, 4)) + "\"  /  " + num(rnd(inch * 25.4, 3)) + " mm"),
        sec("Sabse paas ki drill"),
        row("Chhoti ya barabar", under), row("Badi ya barabar", over),
        sec("Har type mein sabse paas"),
        row("Fraction", near { it.name.endsWith("\"") }),
        row("Number (#)", near { it.name.startsWith("#") }),
        row("Letter", near { it.name.length == 1 }),
        row("Metric", near { it.name.endsWith("mm") })
    )
    set(Q('L', d, 1, lenU(), "dec"), "Drill Size"); fresh = true
    ui.panel("Drill Size", rows)
}

private fun Engine.drillPointKey() {
    val d = drillDia() ?: return ui.toast("Drill diameter daaliye phir Conv + Drill Size")
    fun pl(angle: Double) = d / 2 / tan(angle / 2 * D2R)
    set(Q('L', pl(118.0), 1, lenU(), "dec"), "Drill Point 118°"); fresh = true
    ui.panel("Drill Point (tip ki lambai)", listOf(
        sec("Drill"), Row("Diameter", lenStr(d)),
        sec("Point length"),
        Row("118°", lenStr(pl(118.0))), Row("135°", lenStr(pl(135.0))), Row("90°", lenStr(pl(90.0))),
        Row("120°", lenStr(pl(120.0))), Row("140°", lenStr(pl(140.0))),
        Row("Tip: poori gehrai = hole depth + point length", "")
    ))
}

// ---------------- bolt pattern ----------------
private fun Engine.boltForm() {
    val defD = mv["bcd"]?.let { num(rnd(it / lenF(), 4)) } ?: ""
    ui.form(FormSpec(
        "Bolt Pattern (bolt circle)",
        listOf(
            Field("n", "Holes (ginti)", false, mv["holes"]?.let { num(it) } ?: "6"),
            Field("d", "Bolt circle diameter (" + lenU() + ")", false, defD),
            Field("a", "Pehle hole ka angle (°)", false, "0"),
            Field("cx", "Center X (" + lenU() + ")", false, "0"),
            Field("cy", "Center Y (" + lenU() + ")", false, "0")
        ),
        null
    ) { v, _ ->
        val n = Math.round(v.g("n")).toInt()
        val dia = v.g("d") * lenF()
        if (n < 1 || n > 360 || dia <= 0) return@FormSpec listOf(Row("Holes aur diameter daaliye", "", warn = true))
        mv["holes"] = n.toDouble(); mv["bcd"] = dia
        val a0 = v.g("a")
        val cx = v.g("cx") * lenF(); val cy = v.g("cy") * lenF()
        val r = dia / 2
        val rows = mutableListOf(
            sec("Pattern"), Row("Holes", n.toString()), Row("Radius", lenStr(r)),
            Row("Angle beech mein", num(rnd(360.0 / n, 4)) + "°"),
            Row("Hole se hole (seedha)", lenStr(2 * r * sin(PI / n))),
            sec("Coordinates (X , Y)")
        )
        for (i in 0 until n) {
            val ang = (a0 + 360.0 * i / n) * D2R
            rows.add(Row("Hole " + (i + 1) + "  (" + num(rnd(a0 + 360.0 * i / n, 2)) + "°)",
                num(rnd((cx + r * cos(ang)) / lenF(), 4)) + " , " + num(rnd((cy + r * sin(ang)) / lenF(), 4))))
        }
        rows
    })
}

// ---------------- threads ----------------
private class ThreadSpec(val name: String, val major: Double, val pitch: Double, val metric: Boolean)  // metres

private val THREADS: List<ThreadSpec> by lazy {
    val l = ArrayList<ThreadSpec>()
    fun un(n: String, d: Double, tpi: Int, s: String) = l.add(ThreadSpec("$n-$tpi $s", d * IN, IN / tpi, false))
    val num = mapOf("#0" to .060, "#1" to .073, "#2" to .086, "#3" to .099, "#4" to .112, "#5" to .125, "#6" to .138, "#8" to .164, "#10" to .190, "#12" to .216)
    listOf("#1" to 64, "#2" to 56, "#3" to 48, "#4" to 40, "#5" to 40, "#6" to 32, "#8" to 32, "#10" to 24, "#12" to 24).forEach { un(it.first, num.getValue(it.first), it.second, "UNC") }
    listOf("#0" to 80, "#1" to 72, "#2" to 64, "#3" to 56, "#4" to 48, "#5" to 44, "#6" to 40, "#8" to 36, "#10" to 32, "#12" to 28).forEach { un(it.first, num.getValue(it.first), it.second, "UNF") }
    listOf(Triple("1/4", .25, 20), Triple("5/16", .3125, 18), Triple("3/8", .375, 16), Triple("7/16", .4375, 14), Triple("1/2", .5, 13),
        Triple("9/16", .5625, 12), Triple("5/8", .625, 11), Triple("3/4", .75, 10), Triple("7/8", .875, 9), Triple("1", 1.0, 8)).forEach { un(it.first, it.second, it.third, "UNC") }
    listOf(Triple("1/4", .25, 28), Triple("5/16", .3125, 24), Triple("3/8", .375, 24), Triple("7/16", .4375, 20), Triple("1/2", .5, 20),
        Triple("9/16", .5625, 18), Triple("5/8", .625, 18), Triple("3/4", .75, 16), Triple("7/8", .875, 14), Triple("1", 1.0, 12)).forEach { un(it.first, it.second, it.third, "UNF") }
    fun m(d: Double, p: Double, fine: Boolean) = l.add(ThreadSpec("M" + (if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()) + "×" + p + (if (fine) " fine" else ""), d / 1000, p / 1000, true))
    listOf(1.0 to .25, 1.2 to .25, 1.6 to .35, 2.0 to .4, 2.5 to .45, 3.0 to .5, 4.0 to .7, 5.0 to .8, 6.0 to 1.0, 8.0 to 1.25, 10.0 to 1.5,
        12.0 to 1.75, 14.0 to 2.0, 16.0 to 2.0, 18.0 to 2.5, 20.0 to 2.5, 22.0 to 2.5, 24.0 to 3.0, 27.0 to 3.0, 30.0 to 3.5).forEach { m(it.first, it.second, false) }
    listOf(8.0 to 1.0, 10.0 to 1.25, 10.0 to 1.0, 12.0 to 1.5, 12.0 to 1.25, 14.0 to 1.5, 16.0 to 1.5, 18.0 to 1.5, 20.0 to 1.5, 22.0 to 1.5, 24.0 to 2.0).forEach { m(it.first, it.second, true) }
    l
}

private fun Engine.threadRows(t: ThreadSpec, pct: Double): List<Row> {
    val P = t.pitch
    val E = t.major - 0.649519 * P                       // basic pitch diameter
    val minor = t.major - 1.082532 * P                   // basic minor (internal)
    val tap = t.major - 1.299038 * P * pct / 100         // drill for pct thread
    val w = 0.57735 * P                                   // best wire
    val m3 = E + 3 * w - 0.866025 * P                     // measurement over wires
    return listOf(
        sec(t.name),
        Row("Major diameter", lenStr(t.major)),
        Row(if (t.metric) "Pitch" else "TPI", if (t.metric) num(rnd(P * 1000, 3)) + " mm" else num(rnd(IN / P, 2))),
        Row("Pitch diameter (basic)", lenStr(E)),
        Row("Minor diameter (basic)", lenStr(minor)),
        sec("Tap drill"),
        Row("Drill for " + num(rnd(pct, 0)) + "% thread", lenStr(tap)),
        Row("Standard (D − P)", lenStr(t.major - P)).takeIf { t.metric } ?: Row("Standard 75%", lenStr(t.major - 1.299038 * P * 0.75)),
        sec("3-wire measure"),
        Row("Best wire size", lenStr(w)),
        Row("Measurement over wires", lenStr(m3))
    )
}

private fun Engine.lastThread(): ThreadSpec? = mv["thIdx"]?.toInt()?.let { THREADS.getOrNull(it) }

private fun Engine.threadKey() {
    if (!typed()) {
        val t = lastThread() ?: return ui.toast("Size daaliye (jaise 0.25 ya 10 mm) phir Thread Size")
        return ui.panel("Thread " + t.name, threadRows(t, mv["pct"] ?: 75.0))
    }
    val q = value()
    val d = when {
        q.t == 'L' && q.d == 1 -> q.v
        q.t == 'n' -> if (metric || q.v > 1.5) q.v / 1000 else q.v * IN   // > 1.5 is surely mm
        else -> return ui.toast("Thread ka size daaliye")
    }
    val matches = THREADS.filter { abs(it.major - d) <= 0.06 * d + 0.0002 }.sortedBy { abs(it.major - d) }
    if (matches.isEmpty()) return ui.toast("Is size ka standard thread nahi mila")
    mv["thIdx"] = THREADS.indexOf(matches[0]).toDouble()
    set(Q('L', matches[0].major, 1, lenU(), "dec"), "Thread " + matches[0].name); fresh = true
    val rows = ArrayList<Row>()
    matches.take(6).forEach { rows.addAll(threadRows(it, mv["pct"] ?: 75.0)) }
    ui.panel("Thread Size", rows)
}

/** Conv + Thread Size: typed drill size → % thread for the last thread; typed number ≤ 100 with no thread → sets the % used for tap drills */
private fun Engine.pctThreadKey() {
    val t = lastThread() ?: return ui.toast("Pehle Thread Size chunen")
    if (!typed()) {
        val pct = mv["pct"] ?: 75.0
        showNum(pct, "% Thread (tap drill ke liye)")
        return ui.panel("Thread " + t.name, threadRows(t, pct))
    }
    val q = value()
    if (q.t == 'p' || (q.t == 'n' && q.v in 40.0..100.0)) {
        mv["pct"] = q.v
        showNum(q.v, "% Thread set")
        return ui.panel("Thread " + t.name, threadRows(t, q.v))
    }
    val drill = toM(q) ?: return ui.toast("Drill size ya % daaliye")
    val pct = (t.major - drill) / (1.299038 * t.pitch) * 100
    showNum(pct, "% Thread (" + t.name + ")")
}

private fun Engine.wireKey(n: String) {
    val t = lastThread() ?: return ui.toast("Pehle Thread Size chunen")
    val P = t.pitch
    val w = 0.57735 * P
    if (n == "Wire Size") {
        set(Q('L', w, 1, lenU(), "dec"), "Best wire size " + t.name); fresh = true
        return ui.panel("Wire Size " + t.name, listOf(
            Row("Best wire", lenStr(w)), Row("Sabse chhota wire", lenStr(0.505 * P)), Row("Sabse bada wire", lenStr(1.010 * P))
        ))
    }
    val wire = if (typed()) toM(value()) ?: w else w
    val E = t.major - 0.649519 * P
    val m = E + 3 * wire - 0.866025 * P
    set(Q('L', m, 1, lenU(), "dec"), "3-Wire measure " + t.name); fresh = true
    ui.panel("3-Wire Measure " + t.name, listOf(
        Row("Wire size", lenStr(wire)), Row("Pitch diameter", lenStr(E)), Row("Measurement over wires (M)", lenStr(m))
    ))
}

// ---------------- radial chip thinning ----------------
private fun Engine.rctForm() {
    ui.form(FormSpec(
        "RCT – Radial Chip Thinning",
        listOf(
            Field("d", "Cutter diameter (" + lenU() + ")", false, mv["diam"]?.let { num(rnd(it / lenF(), 4)) } ?: ""),
            Field("ae", "Radial width of cut / stepover (" + lenU() + ")", false, ""),
            Field("f", "Chip load chahiye (" + lenU() + "/tooth)", false, mv["fpt"]?.let { num(rnd(it / lenF(), 5)) } ?: "")
        ),
        null
    ) { v, _ ->
        val d = v.g("d") * lenF(); val ae = v.g("ae") * lenF(); val f = v.g("f") * lenF()
        if (d <= 0 || ae <= 0 || ae > d) return@FormSpec listOf(Row("Diameter aur stepover sahi daaliye", "", warn = true))
        val factor = if (ae >= d / 2) 1.0 else d / (2 * sqrt(d * ae - ae * ae))
        val rows = mutableListOf(
            sec("Result"),
            Row("Stepover %", num(rnd(ae / d * 100, 1)) + "%"),
            Row("Thinning factor", num(rnd(factor, 3)))
        )
        if (f > 0) {
            val adj = f * factor
            mv["fpt"] = adj
            rows.add(Row("Adjusted Feed/Tooth", lenStr(adj)))
            mv["rpm"]?.let { rpm -> mv["teeth"]?.let { t -> rows.add(Row("Adjusted Feed Rate", num(rnd(rpm * t * adj / lenF(), 2)) + " " + feedU())) } }
        }
        rows
    })
}

// ---------------- right triangle ----------------
private fun Engine.triKeyM(k: String) {
    val names = mapOf("x" to "Adjacent (x)", "y" to "Opposite (y)", "r" to "Hypotenuse (r)", "a" to "Angle (Ø)")
    if (typed()) {
        val q = value()
        val v = if (k == "a") (if (q.t == 'a' || q.t == 'n') q.v else return ui.toast("Angle degree mein daaliye"))
        else toM(q) ?: return ui.toast("Length daaliye")
        mv["t$k"] = v
        triOrder.remove(k); triOrder.add(k)
        return if (k == "a") showAng(v, names.getValue(k) + " stored") else showLen(v, names.getValue(k) + " stored")
    }
    // work it out from the two most recent other values
    val others = triOrder.filter { it != k && mv["t$it"] != null }.takeLast(2)
    if (others.size < 2) return mv["t$k"]?.let { if (k == "a") showAng(it, names.getValue(k)) else showLen(it, names.getValue(k)) }
        ?: ui.toast("Koi bhi 2 value daaliye (x, y, r ya Ø), phir teesri dabayein")
    val x = mv["tx"].takeIf { "x" in others }; val y = mv["ty"].takeIf { "y" in others }
    val r = mv["tr"].takeIf { "r" in others }; val a = mv["ta"].takeIf { "a" in others }
    val ar = a?.times(D2R)
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
