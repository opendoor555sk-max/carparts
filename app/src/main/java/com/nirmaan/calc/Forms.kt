package com.nirmaan.calc

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Phase 2-4 functions: roof framing, material estimates, weight, cost.
 * Every screen is a form: inputs on top, results update live.
 */

// ================= helpers =================

fun Engine.lt(v: Double?, imp: String, met: String) = if (v != null && v > 0) lenTxt(v) else if (metric) met else imp

fun Engine.pitchDef(): String {
    val th = T["pitch"]?.v ?: solveTri()?.th
    return if (th != null) num(rnd(th, 2)) else if (metric) "30" else "6/12"
}

/** "47" = degrees, "8/12" or "8in" = inch per foot, "20%" = grade. Returns degrees or NaN. */
fun Engine.parsePitch(s0: String): Double {
    val s = s0.trim().lowercase().replace("°", "").replace(" ", "")
    if (s.isEmpty()) return Double.NaN
    Regex("^(\\d*\\.?\\d+)/12$").find(s)?.let { return atan(it.groupValues[1].toDouble() / 12) / D2R }
    Regex("^(\\d*\\.?\\d+)(in|\")$").find(s)?.let { return atan(it.groupValues[1].toDouble() / 12) / D2R }
    Regex("^(\\d*\\.?\\d+)%$").find(s)?.let { return atan(it.groupValues[1].toDouble() / 100) / D2R }
    return s.toDoubleOrNull() ?: Double.NaN
}

fun Engine.pitchStr(deg: Double) = f2(deg) + "°  (" + fracStr(12 * tan(deg * D2R)).replace("- ", "-") + "/12)"

fun Engine.m3(v: Double) = num(rnd(v, 3)) + " m³  |  " + num(rnd(v / CFT, 2)) + " cft"

private fun Engine.sm() = if (metric) "cm" else "in"
private fun Engine.areaUnit() = if (metric) "m²" else "ft²"
private fun Engine.areaIn(x: Double) = if (!x.isFinite() || x < 0) 0.0 else if (metric) x else x * FT * FT
private fun ok(vararg x: Double?) = x.all { it != null && it.isFinite() && it > 0 }
private fun bad(msg: String) = listOf(Row(msg, "—", true))
private fun seg(s: String?, i: Int, def: String) = s?.split('|')?.getOrNull(i)?.ifEmpty { null } ?: def

/** "4'x8'", "1000x2000" (metric: cm), "6\"x6\"" -> (a, b) in metres */
fun Engine.parseSize(s: String): Pair<Double, Double>? {
    val p = s.lowercase().split(Regex("\\s*[x×*]\\s*"))
    if (p.size != 2) return null
    val a = parseLen(p[0])
    val b = parseLen(p[1])
    return if (a.isFinite() && b.isFinite() && a > 0 && b > 0) a to b else null
}

private fun Engine.sizeName(a: Double, b: Double) = lenTxt(a).let { x -> if (metric) x + "×" + lenTxt(b) + " cm" else x + "×" + lenTxt(b) }

/** standard lumber / pipe lengths to buy */
private fun Engine.stockLen(x: Double): String {
    val list = if (metric) listOf(2.4, 3.0, 3.6, 4.2, 4.8, 5.4, 6.0, 7.2)
    else listOf(8, 10, 12, 14, 16, 18, 20, 22, 24).map { it * FT }
    val s = list.firstOrNull { it >= x - 1e-9 } ?: return "—"
    return fL(s)
}

private fun pitchField(key: String, label: String, value: String) =
    Field(key, label, false, value, "degree (jaise 30) ya x/12 (jaise 8/12)", isPitch = true)

// ================= Hip / Valley =================
class HipGeo(
    val rise: Double, val runB: Double, val hipRun: Double, val hipLen: Double, val hipPitch: Double,
    val planA: Double, val dihedral: Double, val backA: Double, val backB: Double, val comA: Double, val comB: Double
)

private fun norm3(v: DoubleArray): DoubleArray {
    val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); return doubleArrayOf(v[0] / l, v[1] / l, v[2] / l)
}

private fun cross(a: DoubleArray, b: DoubleArray) =
    doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

/** Wall A runs along x, roof A rises toward +y with pitch pA; wall B along y, roof B rises toward +x with pitch pB. */
fun hipGeo(pA: Double, pB: Double, runA: Double): HipGeo {
    val tA = tan(pA * D2R)
    val tB = tan(pB * D2R)
    val rise = runA * tA
    val runB = rise / tB
    val hx = 1 / tB
    val hy = 1 / tA
    val hr = hypot(hx, hy)
    val hipRun = rise * hr
    val h = norm3(doubleArrayOf(hx, hy, 1.0))
    val w = norm3(doubleArrayOf(h[1], -h[0], 0.0))
    val aA = norm3(cross(doubleArrayOf(0.0, -tA, 1.0), h))
    val aB = norm3(cross(doubleArrayOf(-tB, 0.0, 1.0), h))
    return HipGeo(
        rise, runB, hipRun, hypot(hipRun, rise), atan(1 / hr) / D2R,
        atan2(hy, hx) / D2R,
        acos(-cos(pA * D2R) * cos(pB * D2R)) / D2R,
        acos(abs(dot(w, aA)).coerceAtMost(1.0)) / D2R,
        acos(abs(dot(w, aB)).coerceAtMost(1.0)) / D2R,
        runA / cos(pA * D2R), runB / cos(pB * D2R)
    )
}

/** Purlin square to roof plane A, running along wall A, cut against the side face of the hip.
 *  Returns (face cut on the top face, edge cut on the side face), degrees from a square cut. */
fun purlinCuts(pA: Double, pB: Double): Pair<Double, Double> {
    val tA = tan(pA * D2R)
    val tB = tan(pB * D2R)
    val h = doubleArrayOf(1 / tB, 1 / tA, 1.0)
    val nc = cross(h, doubleArrayOf(0.0, 0.0, 1.0))
    val nA = doubleArrayOf(0.0, -tA, 1.0)
    val a = doubleArrayOf(1.0, 0.0, 0.0)
    val side = cross(a, nA)
    fun ang(u: DoubleArray, v: DoubleArray) = acos(abs(dot(norm3(u), norm3(v))).coerceAtMost(1.0)) / D2R
    return ang(cross(nc, nA), cross(nA, a)) to ang(cross(nc, side), cross(side, a))
}

fun Engine.hipForm(irr: Boolean) {
    val runDef = lt(solveTri()?.run ?: T["run"]?.v, "12'", "365")
    val fields = mutableListOf(
        pitchField("pa", "Pitch (A – lambi deewar wali chhat)", pitchDef()),
        pitchField("pb", "Pitch B (sirf Irregular – doosri chhat)", pitchDef()),
        Field("run", "Common Run A (deewar A se ridge tak)", true, runDef),
        Field("bl", "Building length (chhat ka area ke liye, khali chhod sakte)", true, lt(G["len"]?.v, "", ""))
    )
    val defType = if (irr) "Irregular" else "Regular"
    ui.form(FormSpec("Hip / Valley Function", fields, listOf("Miter Saw", "Protractor"), listOf("Regular", "Irregular"),
        seg2Sel = if (irr) 1 else 0) { v, sg ->
        val pr = seg(sg, 0, "Miter Saw") == "Protractor"
        val ir = seg(sg, 1, defType) == "Irregular"
        fun cut(a: Double) = f2(if (pr) 90 - a else a) + "°"
        val pA = v["pa"] ?: Double.NaN
        val pB = if (ir) v["pb"] ?: Double.NaN else pA
        val run = v["run"] ?: Double.NaN
        if (!(pA > 0 && pA < 90 && pB > 0 && pB < 90)) return@FormSpec bad("Pitch 0 se 90 ke beech daaliye")
        if (!ok(run)) return@FormSpec bad("Run daaliye")
        val g = hipGeo(pA, pB, run)
        val hx = 1 / tan(pB * D2R)
        val hy = 1 / tan(pA * D2R)
        val shA = atan(sqrt(hy * hy + 1) / hx) / D2R
        val shB = atan(sqrt(hx * hx + 1) / hy) / D2R
        val rows = mutableListOf(
            sec("Hip / Valley rafter"),
            Row("Plumb Cut", cut(g.hipPitch)),
            Row("Level Cut", cut(90 - g.hipPitch)),
            Row(if (ir) "Cheek Cut A – saw bevel" else "Cheek Cut – saw bevel", cut(90 - g.planA))
        )
        if (ir) rows.add(Row("Cheek Cut B – saw bevel", cut(g.planA)))
        rows.add(Row(if (ir) "Hip Backing Angle A" else "Hip Backing Angle", f2(g.backA) + "°"))
        if (ir) rows.add(Row("Hip Backing Angle B", f2(g.backB) + "°"))
        rows.addAll(
            listOf(
                Row("Dihedral Angle", f2(g.dihedral) + "°"),
                Row("Plan Angle (deewar A se)", f2(g.planA) + "°"),
                Row("Hip/Valley Rafter Pitch", f2(g.hipPitch) + "°"),
                Row("Hip Run", fL(g.hipRun)),
                Row("Hip Rafter Length", fL(g.hipLen)),
                sec("Sheathing (chhat ki sheet) cut"),
                Row(if (ir) "Sheathing angle A (eave se hip)" else "Sheathing angle (eave se hip)", f2(shA) + "°")
            )
        )
        if (ir) rows.add(Row("Sheathing angle B (eave se hip)", f2(shB) + "°"))
        // purlins square to the roof (butt / under purlins)
        val pa = purlinCuts(pA, pB)
        rows.add(sec("Purlin (roof ke square, butt/under) – board par angle"))
        rows.add(Row(if (ir) "Side A: face cut (upar ki satah)" else "Face cut (upar ki satah)", cut(pa.first)))
        rows.add(Row(if (ir) "Side A: edge cut (bagal ki satah)" else "Edge cut (bagal ki satah)", cut(pa.second)))
        if (ir) {
            val pb = purlinCuts(pB, pA)
            rows.add(Row("Side B: face cut (upar ki satah)", cut(pb.first)))
            rows.add(Row("Side B: edge cut (bagal ki satah)", cut(pb.second)))
        }
        rows.add(Row("Vertical purlin: miter = plan angle, bevel 90°", f2(g.planA) + "°"))
        rows.add(Row("Batten (over-purlin): face aur edge cut ulat dein"))
        rows.addAll(listOf(sec("Common rafters"), Row("Rise", fL(g.rise)), Row(if (ir) "Common A length" else "Common length", fL(g.comA))))
        if (ir) {
            rows.add(Row("Run B", fL(g.runB)))
            rows.add(Row("Common B length", fL(g.comB)))
        }
        // roof areas of a hip roof on a rectangle: long sides pitch A (width = 2 × run A), ends pitch B
        val bl = v["bl"] ?: Double.NaN
        if (ok(bl) && bl > 2 * g.runB) {
            val ridge = bl - 2 * g.runB
            val sideA = (bl + ridge) / 2 * run / cos(pA * D2R)
            val endB = run * g.runB / cos(pB * D2R)
            rows.addAll(listOf(
                sec("Hip roof areas (building " + fL(bl) + " × " + fL(2 * run) + ")"),
                Row("Ridge length", fL(ridge)),
                Row("Lambi taraf (trapezoid) × 2", areaStr(sideA) + " × 2"),
                Row("Chhoti taraf (triangle) × 2", areaStr(endB) + " × 2"),
                Row("Kul roof area", areaStr(2 * sideA + 2 * endB))
            ))
        }
        rows.add(Row("Lengths centre-line tak hain (ridge/hip ki moti ka aadha ghataein)"))
        rows
    })
}

// ================= Common rafter (Diag / Pitch / Rise / Run – second press) =================
fun Engine.rafterForm() {
    val s = solveTri()
    val m = metric
    ui.form(FormSpec("Common Rafter", listOf(
        Field("run", "Run (deewar ke bahar se ridge ke centre tak)", true, lt(s?.run ?: T["run"]?.v, "12'", "365")),
        pitchField("p", "Pitch", if (s != null) num(rnd(s.th, 2)) else pitchDef()),
        Field("ridge", "Ridge board ki motai", true, if (m) "4" else "1-1/2\""),
        Field("oh", "Overhang (chhajja, seedha naap)", true, if (m) "45" else "18\""),
        Field("dep", "Rafter ki depth (2x6 = 5-1/2\")", true, if (m) "14" else "5-1/2\""),
        Field("seat", "Seat cut (deewar par baithak)", true, if (m) "9" else "3-1/2\"")
    ), listOf("Miter Saw", "Protractor")) { v, mode ->
        val run = v["run"] ?: Double.NaN
        val p = v["p"] ?: Double.NaN
        if (!ok(run) || !(p > 0 && p < 90)) return@FormSpec bad("Run aur Pitch daaliye")
        val pr = seg(mode, 0, "Miter Saw") == "Protractor"
        fun cut(a: Double) = f2(if (pr) 90 - a else a) + "°"
        val t = tan(p * D2R)
        val c = cos(p * D2R)
        val ridge = (v["ridge"] ?: 0.0).let { if (it.isFinite() && it > 0) it else 0.0 }
        val oh = (v["oh"] ?: 0.0).let { if (it.isFinite() && it > 0) it else 0.0 }
        val dep = v["dep"] ?: Double.NaN
        val seat = (v["seat"] ?: 0.0).let { if (it.isFinite() && it > 0) it else 0.0 }
        val sm = if (m) "cm" else "in"
        val line = run / c
        val ded = ridge / 2 / c
        val body = line - ded
        val tail = oh / c
        val total = body + tail
        val heel = seat * t
        val rows = mutableListOf(
            sec("Triangle"),
            Row("Rise", fL(run * t)), Row("Run", fL(run)), Row("Diagonal (line length)", fL(line)),
            sec("Pitch"),
            Row("Pitch", pitchStr(p)), Row("% Grade", num(rnd(t * 100, 3))),
            sec("Cuts"),
            Row("Plumb Cut (ridge aur tail)", cut(p)), Row("Level Cut (seat)", cut(90 - p)),
            sec("Rafter ki lambai"),
            Row("Ridge deduction (aadhi motai)", fL(ded, 1, sm)),
            Row("Deewar se ridge tak", fL(body)),
            Row("Overhang (tail) length", fL(tail)),
            Row("Kul rafter length", fL(total)),
            Row("Kharidne ke liye lakdi", stockLen(total))
        )
        if (seat > 0) {
            rows.add(sec("Birdsmouth (deewar par kaat)"))
            rows.add(Row("Seat cut", fL(seat, 1, sm)))
            rows.add(Row("Heel (plumb) height", fL(heel, 1, sm), dep.isFinite() && heel > dep / 3))
            if (ok(dep)) rows.add(Row("HAP (plate ke upar height)", fL(dep / c - heel, 1, sm)))
            if (dep.isFinite() && heel > dep / 3) rows.add(Row("Heel depth ke 1/3 se zyada — seat chhota karein", "⚠", true))
        }
        rows
    })
}

// ================= Jack rafters =================
fun Engine.jackForm(irr: Boolean) {
    val fields = mutableListOf(pitchField("pa", if (irr) "Pitch A" else "Pitch", pitchDef()))
    if (irr) fields.add(pitchField("pb", "Pitch B", pitchDef()))
    fields.add(Field("run", if (irr) "Common Run A" else "Common Run", true, lt(solveTri()?.run ?: T["run"]?.v, "12'", "365")))
    fields.add(Field("sp", "Spacing (on-center)", true, lt(M["oc"]?.v, "16\"", "40")))
    ui.form(FormSpec(
        if (irr) "Irregular Jack Rafters" else "Jack Rafters", fields,
        listOf("Chhote se shuru", "Bade se shuru"),
        if (irr) listOf("On-Center dono taraf", "Mating (hip par milte)") else null
    ) { v, sg ->
        val pA = v["pa"] ?: Double.NaN
        val pB = if (irr) v["pb"] ?: Double.NaN else pA
        val run = v["run"] ?: Double.NaN
        val sp = v["sp"] ?: Double.NaN
        if (!(pA > 0 && pA < 90 && pB > 0 && pB < 90)) return@FormSpec bad("Pitch 0 se 90 ke beech daaliye")
        if (!ok(run, sp)) return@FormSpec bad("Run aur spacing daaliye")
        val bigFirst = seg(sg, 0, "") == "Bade se shuru"
        val mating = seg(sg, 1, "").startsWith("Mating")
        val g = hipGeo(pA, pB, run)
        val tA = tan(pA * D2R)
        val tB = tan(pB * D2R)
        val rows = mutableListOf<Row>()
        /** positions along the wall of one side (distance from the corner) */
        fun positions(maxPos: Double): List<Double> {
            val out = mutableListOf<Double>()
            var i = 1
            while (i <= 60) {
                val x = if (bigFirst) maxPos - i * sp else i * sp
                if (x <= 1e-9 || x >= maxPos - 1e-9) break
                out.add(x); i++
            }
            return out
        }
        fun side(name: String, k: Double, cp: Double, maxRun: Double, cheek: Double, pos: List<Double>, spacing: Double) {
            rows.add(sec(name))
            rows.add(Row("Common Difference (har jack ka farak)", fL(spacing * k / cp, 1, if (metric) "cm" else "in")))
            rows.add(Row("Plumb / Level cut", f2(acos(cp) / D2R) + "° / " + f2(90 - acos(cp) / D2R) + "°"))
            rows.add(Row("Cheek (side) cut – saw bevel", f2(cheek) + "°"))
            if (pos.isEmpty()) rows.add(Row("Spacing bahut badi hai — koi jack nahi"))
            pos.forEachIndexed { i, x ->
                val jr = x * k
                if (jr < maxRun - 1e-9) rows.add(Row("Jack ${i + 1}  (" + fL(x) + " se)", fL(jr / cp)))
            }
        }
        val xEndA = run * tA / tB          // along wall A where the jack run reaches the full common run
        val posA = positions(xEndA)
        side(if (irr) "Side A jacks" else "Jacks", tB / tA, cos(pA * D2R), run, 90 - g.planA, posA, sp)
        if (irr) {
            if (mating) {
                // B jacks meet the hip where the A jacks meet it: position on wall B = x·tB/tA, run = x
                val posB = posA.map { it * tB / tA }
                side("Side B jacks (mating)", tA / tB, cos(pB * D2R), g.runB, g.planA, posB, sp * tB / tA)
                rows.add(Row("Side B spacing (wall par)", fL(sp * tB / tA, 1, if (metric) "cm" else "in")))
            } else {
                side("Side B jacks", tA / tB, cos(pB * D2R), g.runB, g.planA, positions(g.runB * tB / tA), sp)
            }
        }
        rows.add(Row("Lengths centre-line tak hain (hip ki moti ka aadha ghataein)"))
        rows
    })
}

// ================= Rake wall =================
fun Engine.rakeWallKey() {
    if (isFresh()) {
        val q = value()
        val b = toLen(q)
        if (b != null && b >= 0) {
            M["rwall"] = lq(b)
            ui.toast("Base wall saved: " + fL(b))
        }
        fresh = false
    }
    rakeWallForm()
}

fun Engine.rakeWallForm() {
    val s = solveTri()
    ui.form(FormSpec("Rake Wall (dhalan wali deewar)", listOf(
        Field("l", "Wall Length (run)", true, lt(s?.run ?: G["len"]?.v, "12'", "365")),
        pitchField("p", "Pitch", if (s != null) num(rnd(s.th, 2)) else pitchDef()),
        Field("h", "Base wall (chhoti taraf ki height)", true, M["rwall"]?.let { lenTxt(it.v) } ?: "0"),
        Field("sp", "Stud spacing (on-center)", true, lt(M["oc"]?.v, "16\"", "40"))
    ), listOf("Lambe stud se shuru", "Chhote stud se shuru")) { v, sg ->
        val l = v["l"] ?: Double.NaN
        val p = v["p"] ?: Double.NaN
        val h = (v["h"] ?: 0.0).let { if (it.isFinite() && it >= 0) it else 0.0 }
        val sp = v["sp"] ?: Double.NaN
        if (!ok(l, sp) || !(p >= 0 && p < 90)) return@FormSpec bad("Length, pitch aur spacing daaliye")
        val longFirst = seg(sg, 0, "Lambe stud se shuru").startsWith("Lambe")
        val t = tan(p * D2R)
        val rows = mutableListOf(
            sec("Results"),
            Row("Lambi taraf ki height", fL(h + l * t)),
            Row("Chhoti taraf ki height", fL(h)),
            Row("Top plate length (dhalan par)", fL(l / cos(p * D2R))),
            Row("Stud top cut angle", f2(p) + "°"),
            Row("Stud difference (har stud ka farak)", fL(sp * t, 1, if (metric) "cm" else "in")),
            sec(if (longFirst) "Studs (lambi taraf se)" else "Studs (chhoti taraf se)")
        )
        var i = 0
        while (i <= 80) {
            val d = min(i * sp, l)
            val x = if (longFirst) l - d else d
            rows.add(Row("Stud ${i + 1}  (" + fL(d) + " par)", fL(h + x * t)))
            if (d >= l - 1e-9) break
            i++
        }
        rows
    })
}

// ================= Roof =================
fun Engine.roofForm() {
    val q = cur
    val areaDef = if (isFresh() && q.t == 'L' && q.d == 2) num(rnd(q.v / (if (metric) 1.0 else FT * FT), 2)) else ""
    if (areaDef.isNotEmpty()) { commit(); fresh = false }
    val s = solveTri()
    ui.form(FormSpec("Roof Function", listOf(
        Field("l", "Building Length", true, lt(G["len"]?.v, "40'", "1200")),
        Field("w", "Building Width", true, lt(G["wid"]?.v, "24'", "730")),
        Field("a", "Ya floor plan area (" + (if (metric) "m²" else "ft²") + ") – khali chhodein to L×W", false, areaDef),
        pitchField("p", "Pitch", if (s != null) num(rnd(s.th, 2)) else pitchDef()),
        Field("oh", "Overhang (chhajja)", true, if (metric) "45" else "18\""),
        Field("sp", "Rafter spacing (on-center)", true, lt(M["oc"]?.v, "24\"", "60")),
        Field("cs", "Apni sheet size (jaise 4'x8' ya 105x300)", false, "", "khali chhod sakte hain", isSize = true)
    ), listOf("Gable (do dhalan)", "Hip (char dhalan)"), sizeCat = "roof") { v, sg ->
        val l = v["l"] ?: Double.NaN
        val w = v["w"] ?: Double.NaN
        val area0 = v["a"] ?: Double.NaN
        val p = v["p"] ?: Double.NaN
        val oh = (v["oh"] ?: 0.0).let { if (it.isFinite() && it > 0) it else 0.0 }
        val sp = v["sp"] ?: Double.NaN
        if (!(p > 0 && p < 90)) return@FormSpec bad("Pitch daaliye")
        val useArea = area0.isFinite() && area0 > 0
        if (!useArea && !ok(l, w)) return@FormSpec bad("Length aur Width (ya floor plan area) daaliye")
        val hip = seg(sg, 0, "").startsWith("Hip")
        val cp = cos(p * D2R)
        val tp = tan(p * D2R)
        val plan = if (useArea) areaIn(area0) else (l + 2 * oh) * (w + 2 * oh)
        val area = plan / cp
        val sq = area / (FT * FT * 100)
        val rows = mutableListOf(
            sec("Area"),
            Row("Floor plan area", areaStr(plan)),
            Row("Roof area (chhajje ke saath)", areaStr(area)),
            Row("Squares (100 ft²)", num(rnd(sq, 2))),
            Row("Shingle bundles (3 per square, +10%)", ceil(sq * 3 * 1.1).toInt().toString()),
            sec("Sheets (+10% waste)"),
        )
        sizeList("roof").forEach { rows.add(Row(sizeLabel(it) + " sheets", ceil(area / (it.first * it.second) * 1.1).toInt().toString())) }
        rows.addAll(customSheetRows(v, area))
        if (!useArea) {
            val l2 = l + 2 * oh
            val w2 = w + 2 * oh
            val runO = w / 2 + oh
            rows.addAll(listOf(
                sec("Framing"),
                Row("Pitch", pitchStr(p)),
                Row("Ridge height (deewar ke upar)", fL(w / 2 * tp)),
                Row("Common rafter length (chhajje ke saath)", fL(runO / cp)),
                Row("Ridge length", fL(if (hip) max(l - w, 0.0) else l2))
            ))
            if (hip) {
                val g = hipGeo(p, p, runO)
                rows.add(Row("Hip rafter length (4 nag)", fL(g.hipLen)))
                rows.add(Row("Hip pitch", f2(g.hipPitch) + "°"))
                if (ok(sp)) rows.add(Row("Common rafters (ridge par)", (2 * (ceil(max(l - w, 0.0) / sp - 1e-9).toInt() + 1)).toString()))
                rows.add(Row("Eave / fascia length", fL(2 * (l2 + w2))))
            } else {
                if (ok(sp)) rows.add(Row("Common rafters", (2 * (ceil(l2 / sp - 1e-9).toInt() + 1)).toString()))
                rows.add(Row("Gable end area (dono taraf ka triangle)", areaStr(w * (w / 2 * tp) / 2 * 2)))
                rows.add(Row("Eave / fascia length (dono taraf)", fL(2 * l2)))
                rows.add(Row("Rake / barge length (4 nag)", fL(runO / cp)))
            }
        }
        rows
    })
}

/** a size field ("4'x8'") arrives as two numbers: key_a and key_b (metres) */
private fun sizeOf(v: Map<String, Double>, key: String = "cs"): Pair<Double, Double>? {
    val a = v[key + "_a"] ?: return null
    val b = v[key + "_b"] ?: return null
    return if (a.isFinite() && b.isFinite() && a > 0 && b > 0) a to b else null
}

private fun Engine.customSheetRows(v: Map<String, Double>, area: Double): List<Row> {
    val sz = sizeOf(v) ?: return emptyList()
    val one = sz.first * sz.second
    return listOf(Row(sizeName(sz.first, sz.second) + " sheets", ceil(area / one * 1.1).toInt().toString()))
}

// ================= Masonry (int / block / tile) =================
fun Engine.masonryForm() {
    val q = cur
    val areaDef = if (isFresh() && q.t == 'L' && q.d == 2) num(rnd(q.v / (if (metric) 1.0 else FT * FT), 2)) else ""
    if (areaDef.isNotEmpty()) { commit(); fresh = false }
    val hDef = G["hei"]?.v ?: G["wid"]?.v
    ui.form(FormSpec("Masonry (int / block / tile)", listOf(
        Field("l", "Length", true, lt(G["len"]?.v, "20'", "600")),
        Field("h", "Height (deewar) / Width (farsh)", true, lt(hDef, "10'", "300")),
        Field("t", "Deewar ki motai (sirf int/block)", true, if (metric) "23" else "9\"", "9\" = 23 cm, 4.5\" = 11.5 cm"),
        Field("a", "Ya seedha area (" + areaUnit() + ") – khali chhodein to L×H", false, areaDef),
        Field("op", "Khidki/Darwaza area (" + areaUnit() + ")", false, "0"),
        Field("cs", "Apni tile/piece size (jaise 2'x2' ya 60x60)", false, "", "khali chhod sakte hain", isSize = true)
    ), listOf("Int (brick)", "Block", "Tile / Paver"), segSel = if (areaDef.isNotEmpty()) 2 else 0, sizeCat = "tile") { v, sg ->
        val l = v["l"] ?: Double.NaN
        val h = v["h"] ?: Double.NaN
        val t = v["t"] ?: Double.NaN
        val a0 = v["a"] ?: Double.NaN
        val mode = seg(sg, 0, "Int (brick)")
        val area = (if (a0.isFinite() && a0 > 0) areaIn(a0) else if (ok(l, h)) l * h else Double.NaN) - areaIn(v["op"] ?: 0.0)
        if (!area.isFinite()) return@FormSpec bad("Length aur Height (ya area) daaliye")
        if (area <= 0) return@FormSpec bad("Khidki/darwaza ka area zyada hai")
        if (mode == "Tile / Paver") {
            val sizes = sizeList("tile")
            val rows = mutableListOf(sec("Area"), Row("Area", areaStr(area)), sec("Pieces (+10% waste)"))
            sizeOf(v)?.let { rows.add(Row(sizeName(it.first, it.second) + " (aapki size)", ceil(area / (it.first * it.second) * 1.1).toLong().toString() + " nag")) }
            sizes.forEach { (x, y) -> rows.add(Row(sizeLabel(x to y), ceil(area / (x * y) * 1.1).toLong().toString() + " nag")) }
            if (ok(l)) {
                rows.add(sec("Border (sirf Length par, ek line)"))
                sizeOf(v)?.let { rows.add(Row(lenTxt(it.first) + " lambi piece", ceil(l / it.first).toLong().toString() + " nag")) }
                listOf(if (metric) 0.6 else 16 * IN, if (metric) 0.3 else FT).forEach { rows.add(Row(lenTxt(it) + " lambi piece", ceil(l / it).toLong().toString() + " nag")) }
            }
            rows.add(Row("Tile adhesive (lagbhag)", num(rnd(area * 5, 0)) + " kg"))
            return@FormSpec rows
        }
        if (!ok(t)) return@FormSpec bad("Deewar ki motai daaliye")
        val vol = area * t
        val block = mode == "Block"
        val count: Double
        val solid: Double
        if (block) {
            count = area / (0.4 * 0.2)
            solid = count * 0.39 * 0.19 * t
        } else {
            count = vol / (0.2 * 0.1 * 0.1)
            solid = count * 0.19 * 0.09 * 0.09
        }
        val wet = max(vol - solid, 0.0)
        val dry = wet * 1.33
        listOf(
            sec("Deewar"),
            Row("Area", areaStr(area)),
            Row("Volume", m3(vol)),
            sec(if (block) "Block (400×200 mm)" else "Int (190×90×90 mm, 10 mm masala)"),
            Row(if (block) "Blocks (+5% waste)" else "Int (+5% waste)", ceil(count * 1.05).toLong().toString() + " nag"),
            sec("Masala 1:6 (cement : ret)"),
            Row("Masala (geela)", m3(wet)),
            Row("Masala (sukha ×1.33)", m3(dry)),
            Row("Cement (50 kg bag)", num(rnd(dry / 7 / BAG, 1)) + " bag"),
            Row("Ret (sand)", m3(dry * 6 / 7))
        )
    })
}

// ================= Footing / concrete =================
fun Engine.footingForm() {
    val L = G["len"]?.v
    val W = G["wid"]?.v
    val strip = L != null && W != null
    ui.form(FormSpec("Footing / Concrete", listOf(
        Field("l", if (strip) "Length (neev ki kul lambai = building ka perimeter)" else "Length", true,
            if (strip) lenTxt(2 * (L!! + W!!)) else lt(L, "4'", "120")),
        Field("w", "Width", true, if (strip) (if (metric) "60" else "24\"") else lt(W, "4'", "120")),
        Field("d", "Depth / motai", true, if (metric) "45" else "18\""),
        Field("n", "Kitne nag", false, "1")
    ), listOf("M15 1:2:4", "M20 1:1.5:3", "M25 1:1:2")) { v, sg ->
        val l = v["l"] ?: Double.NaN
        val w = v["w"] ?: Double.NaN
        val d = v["d"] ?: Double.NaN
        val n = v["n"] ?: Double.NaN
        if (!ok(l, w, d, n)) return@FormSpec bad("Length, Width, Depth aur nag daaliye")
        val mixName = seg(sg, 0, "M15 1:2:4")
        val mix = when {
            mixName.startsWith("M25") -> doubleArrayOf(1.0, 1.0, 2.0)
            mixName.startsWith("M20") -> doubleArrayOf(1.0, 1.5, 3.0)
            else -> doubleArrayOf(1.0, 2.0, 4.0)
        }
        val sum = mix.sum()
        val vol = l * w * d * n
        val dry = vol * 1.54
        val bags = dry * mix[0] / sum / BAG
        listOf(
            sec("Concrete"),
            Row("Geela volume", m3(vol)),
            Row("Cubic yard", num(rnd(vol / YD3, 2)) + " yd³"),
            Row("Sukha volume (×1.54)", m3(dry)),
            sec("Material ($mixName)"),
            Row("Cement (50 kg bag)", num(rnd(bags, 1)) + " bag"),
            Row("Ret (sand)", m3(dry * mix[1] / sum)),
            Row("Kapchi / gitti (aggregate)", m3(dry * mix[2] / sum)),
            Row("Paani (lagbhag, w/c 0.5)", num(rnd(bags * 50 * 0.5, 0)) + " litre"),
            Row("Wazan (2400 kg/m³)", num(rnd(vol * 2400, 0)) + " kg")
        ) + (M["ftarea"]?.let { fa ->
            if (fa.t == 'L' && fa.d == 2) listOf(sec("FtArea memory (" + fmt(fa) + ")"), Row("Length × FtArea", m3(l * fa.v * n))) else emptyList()
        } ?: emptyList())
    })
}

// ================= Drywall / paint =================
fun Engine.drywallForm() {
    val q = cur
    val areaDef = if (isFresh() && q.t == 'L' && q.d == 2) num(rnd(q.v / (if (metric) 1.0 else FT * FT), 2)) else ""
    val lenOnly = isFresh() && q.t == 'L' && q.d == 1
    if (areaDef.isNotEmpty() || lenOnly) { commit(); fresh = false }
    val sel = when {
        areaDef.isNotEmpty() -> 2
        lenOnly -> 3
        G["len"] != null && G["wid"] != null && G["hei"] != null -> 0
        G["len"] != null && G["hei"] == null && G["wid"] == null -> 3
        else -> 1
    }
    ui.form(FormSpec("Drywall / Sheets / Paint", listOf(
        Field("l", "Length", true, if (lenOnly) lenTxt(q.v) else lt(G["len"]?.v, "12'", "365")),
        Field("w", "Width (kamre ke liye)", true, lt(G["wid"]?.v, "10'", "300")),
        Field("h", "Height", true, lt(G["hei"]?.v, "10'", "300")),
        Field("a", "Area (" + areaUnit() + ") – 'Area' option ke liye", false, areaDef),
        Field("op", "Khidki/Darwaza area (" + areaUnit() + ")", false, "0"),
        Field("cs", "Apni sheet size (jaise 1000x2000 mm = 100x200)", false, "", "khali chhod sakte hain", isSize = true)
    ), listOf("Kamra", "Deewar L×H", "Area", "Sirf Length"), segSel = sel, sizeCat = "drywall") { v, sg ->
        val l = v["l"] ?: Double.NaN
        val w = v["w"] ?: Double.NaN
        val h = v["h"] ?: Double.NaN
        val op = areaIn(v["op"] ?: 0.0)
        val mode = seg(sg, 0, "Kamra")
        val sizes = sizeList("drywall").toMutableList()
        sizeOf(v)?.let { sizes.add(0, it) }
        fun name(sz: Pair<Double, Double>) = sizeLabel(sz)
        val rows = mutableListOf<Row>()
        when (mode) {
            "Sirf Length" -> {
                if (!ok(l)) return@FormSpec bad("Length daaliye")
                rows.add(sec("Length " + fL(l) + " ke liye sheets (lambai se)"))
                sizes.forEach { rows.add(Row(name(it), ceil(l / it.second - 1e-9).toInt().toString() + " sheet")) }
                return@FormSpec rows
            }
            "Kamra" -> {
                if (!ok(l, w, h)) return@FormSpec bad("Length, Width aur Height daaliye")
                val wall = max(2 * (l + w) * h - op, 0.0)
                val ceil = l * w
                rows.addAll(listOf(sec("Area"), Row("Deewar area", areaStr(wall)), Row("Chhat (ceiling) area", areaStr(ceil)),
                    Row("Kul area", areaStr(wall + ceil)), sec("Sheets (+10%): deewar + ceiling = kul")))
                sizes.forEach {
                    val a = it.first * it.second
                    val sw = ceil(wall / a * 1.1).toInt()
                    val sc = ceil(ceil / a * 1.1).toInt()
                    rows.add(Row(name(it), "$sw + $sc = ${sw + sc} sheet"))
                }
                rows.addAll(listOf(sec("Paint (2 coat, lagbhag)"), Row("Deewar", num(rnd(wall * 2 / 10, 1)) + " litre"),
                    Row("Deewar + ceiling", num(rnd((wall + ceil) * 2 / 10, 1)) + " litre"),
                    Row("Putty (lagbhag)", num(rnd((wall + ceil) * 1.0, 0)) + " kg")))
                return@FormSpec rows
            }
            else -> {
                val a0 = v["a"] ?: Double.NaN
                val area = if (mode == "Area") (if (ok(a0)) areaIn(a0) - op else Double.NaN)
                else if (ok(l, h)) l * h - op else Double.NaN
                if (!area.isFinite() || area <= 0) return@FormSpec bad(if (mode == "Area") "Area daaliye" else "Length aur Height daaliye")
                rows.addAll(listOf(sec("Area"), Row("Area", areaStr(area)), sec("Sheets (+10%)")))
                sizes.forEach { rows.add(Row(name(it), ceil(area / (it.first * it.second) * 1.1).toInt().toString() + " sheet")) }
                rows.addAll(listOf(sec("Paint (2 coat, lagbhag)"), Row("Paint", num(rnd(area * 2 / 10, 1)) + " litre"),
                    Row("Putty (lagbhag)", num(rnd(area * 1.0, 0)) + " kg")))
                return@FormSpec rows
            }
        }
    })
}

// ================= Board feet / timber =================
fun Engine.boardFeetForm() {
    ui.form(FormSpec("Board Feet / Lakdi (cft)", listOf(
        Field("t", "Motai (thickness)", true, if (metric) "5" else "2\""),
        Field("w", "Chaudai (width)", true, if (metric) "10" else "4\""),
        Field("l", "Lambai (length)", true, if (metric) "244" else "8'"),
        Field("q", "Kitne nag", false, "1"),
        Field("r", "Rate per cft (₹)", false, "0")
    ), null) { v, _ ->
        val t = v["t"] ?: Double.NaN
        val w = v["w"] ?: Double.NaN
        val l = v["l"] ?: Double.NaN
        val q = v["q"] ?: Double.NaN
        if (!ok(t, w, l, q)) return@FormSpec bad("Saari values daaliye")
        val vol = t * w * l * q
        val cft = vol / CFT
        val rate = (v["r"] ?: 0.0).let { if (it.isFinite()) it else 0.0 }
        val rows = mutableListOf(
            sec("Results"),
            Row("Board feet", num(rnd(vol / BDFT, 2)) + " BF"),
            Row("Cubic feet (cft)", num(rnd(cft, 3)) + " cft"),
            Row("Cubic metre", num(rnd(vol, 4)) + " m³"),
            Row("Ek nag", num(rnd(vol / q / BDFT, 2)) + " BF  |  " + num(rnd(vol / q / CFT, 3)) + " cft"),
            Row("Wazan (wt/vol " + num(rnd(dens, 0)) + " kg/m³)", num(rnd(vol * dens, 1)) + " kg")
        )
        if (rate > 0) rows.add(Row("Kul keemat", money(cft * rate)))
        rows
    })
}

// ================= Fence =================
fun Engine.fenceForm() {
    var panels = "0"
    var lenDef = lt(G["len"]?.v, "100'", "3000")
    if (isFresh()) {
        val q = value()
        if (q.t == 'n' && q.v >= 1 && q.v % 1 == 0.0) panels = q.v.toInt().toString()
        else toLen(q)?.let { if (q.t == 'L' && it > 0) lenDef = lenTxt(it) }
        fresh = false
    }
    ui.form(FormSpec("Fence (baad / jaali)", listOf(
        Field("l", "Fence ki kul length", true, lenDef),
        Field("ps", "Post spacing / panel width", true, if (metric) "240" else "8'"),
        Field("pn", "Ya kitne panels (length ki jagah)", false, panels),
        Field("r", "Har section mein rails", false, num(M["rails"]?.v ?: 3.0)),
        Field("pw", "Picket / patti width", true, if (metric) "9" else "3-1/2\""),
        Field("g", "Picket ke beech gap", true, if (metric) "5" else "2\"")
    ), null, sizeCat = "picket") { v, _ ->
        val ps = v["ps"] ?: Double.NaN
        val pn = (v["pn"] ?: 0.0).let { if (it.isFinite() && it >= 1) it.toInt() else 0 }
        val l = if (pn > 0 && ok(ps)) pn * ps else v["l"] ?: Double.NaN
        if (!ok(l, ps)) return@FormSpec bad("Length aur post spacing daaliye")
        val nSec = ceil(l / ps - 1e-9).toInt()
        val rails = (v["r"] ?: 0.0).let { if (it.isFinite() && it > 0) it.toInt() else 0 }
        val pw = v["pw"] ?: Double.NaN
        val g = (v["g"] ?: 0.0).let { if (it.isFinite() && it >= 0) it else 0.0 }
        val rows = mutableListOf(sec("Fence"))
        if (pn > 0) rows.add(Row("$pn panels itni length cover karenge", fL(l)))
        rows.addAll(listOf(
            Row("Kul length", fL(l)),
            sec("Posts"),
            Row("Sections / panels", nSec.toString()),
            Row("Posts (khambe)", (nSec + 1).toString()),
            Row("Asli post spacing", fL(l / nSec)),
            sec("Rails ($rails per section)"),
            Row("Rail lambai = post spacing", (nSec * rails).toString() + " nag")
        ))
        val railLens = if (metric) listOf(3.0, 3.6, 4.8, 6.0) else listOf(10 * FT, 12 * FT, 16 * FT, 20 * FT)
        railLens.filter { it > ps + 1e-9 }.forEach { rl ->
            rows.add(Row(lenTxt(rl) + " lambi rails", (ceil(l / rl - 1e-9).toInt() * rails).toString() + " nag"))
        }
        rows.add(Row("Rail kul length", fL(l * rails)))
        rows.add(sec("Pickets"))
        if (ok(pw)) rows.add(Row("Aapki patti (" + lenTxt(pw + g) + " o.c.)", ceil(l / (pw + g)).toInt().toString() + " nag"))
        val ocs = sizeList("picket").map { it.first }
        ocs.forEach { rows.add(Row(lenTxt(it) + " o.c. par", ceil(l / it).toInt().toString() + " nag")) }
        rows
    })
}

// ================= Quantity @ on-center =================
fun Engine.qtyForm() {
    var nDef = "0"
    var lDef = lt(G["len"]?.v, "20'", "600")
    var sel = 0
    if (isFresh()) {
        val q = value()
        if (q.t == 'n' && q.v >= 2 && q.v % 1 == 0.0) { nDef = q.v.toInt().toString(); sel = 1 }
        else if (q.t == 'L' && q.d == 1 && q.v > 0) lDef = lenTxt(q.v)
        fresh = false
    }
    ui.form(FormSpec("Qty @ On-Center", listOf(
        Field("l", "Length", true, lDef),
        Field("n", "Kitne members ('Length' option ke liye)", false, nDef),
        Field("sp", "Apni spacing (o.c.)", true, M["oc"]?.let { lenTxt(it.v) } ?: "", "khali chhod sakte hain")
    ), listOf("Ginti (length se)", "Length (ginti se)"), segSel = sel, sizeCat = "oc") { v, sg ->
        val sp = sizeList("oc").map { it.first }.toMutableList()
        val own = v["sp"] ?: Double.NaN
        if (ok(own) && sp.none { abs(it - own) < 1e-6 }) sp.add(own)
        sp.sort()
        val lab = { s: Double -> if (metric) num(rnd(s * 1000, 1)) + "mm" else fracStr(s / IN) + "in" }
        if (seg(sg, 0, "").startsWith("Length")) {
            val n = v["n"] ?: Double.NaN
            if (!(n >= 2)) return@FormSpec bad("Kitne members daaliye (2 ya zyada)")
            val rows = mutableListOf(sec("${n.toInt()} members kitni length cover karenge"))
            sp.forEach { rows.add(Row("@ " + lab(it) + " on-center", fL((n - 1) * it))) }
            return@FormSpec rows
        }
        val l = v["l"] ?: Double.NaN
        if (!ok(l)) return@FormSpec bad("Length daaliye")
        val rows = mutableListOf(sec("Length: " + fL(l)))
        sp.forEach { rows.add(Row("@ " + lab(it) + " on-center", (ceil(l / it - 1e-9).toInt() + 1).toString() + " pieces")) }
        rows
    })
}

// ================= Baluster =================
fun Engine.balusterForm() {
    ui.form(FormSpec("Baluster Function", listOf(
        Field("run", "Run (do post ke beech)", true, lt(T["run"]?.v, "12'", "365")),
        Field("rk", "Rake angle (°) – seedha ho to 0", false, "0"),
        Field("w", "Baluster width", true, if (metric) "4" else "1-3/8\""),
        Field("g", "Maximum khali jagah", true, if (metric) "10" else "4\"", "code: 4\" / 10 cm se kam"),
        Field("cnt", "Kitne baluster (sirf Evenly Space ke liye)", false, "0"),
        Field("oc", "Chahiye o.c. spacing (sirf Best Fit ke liye)", true, if (metric) "15" else "6\"")
    ), listOf("Limit Opening", "Evenly Space", "Best Fit")) { v, mode0 ->
        val mode = seg(mode0, 0, "Limit Opening")
        val run = v["run"] ?: Double.NaN
        val w = v["w"] ?: Double.NaN
        val gm = v["g"] ?: Double.NaN
        val rk = (v["rk"] ?: 0.0).let { if (it.isFinite() && it >= 0 && it < 80) it else 0.0 }
        if (!ok(run, w, gm)) return@FormSpec bad("Run, width aur khali jagah daaliye")
        val cnt = (v["cnt"] ?: 0.0).let { if (it.isFinite() && it >= 1) it.toInt() else 0 }
        val want = v["oc"] ?: Double.NaN
        val n = when {
            mode == "Evenly Space" && cnt > 0 -> cnt
            mode == "Best Fit" && ok(want) -> max(1, Math.round(run / want - 1).toInt())
            mode == "Best Fit" -> max(0, Math.round((run - gm) / (w + gm)).toInt())
            else -> max(0, ceil((run - gm) / (w + gm) - 1e-9).toInt())
        }
        if (n * w >= run) return@FormSpec bad("Itne baluster is run mein nahi aayenge")
        val gap = (run - n * w) / (n + 1)
        val oc = gap + w
        val rows = mutableListOf(
            sec("Results"),
            Row("Balusters ki ginti", n.toString()),
            Row("Asli khali jagah", fL(gap, 1, sm()), gap > gm + 1e-9),
            Row("Spacing (on-center)", fL(oc, 1, sm())),
            Row("Spacing rail par (rake ke saath)", fL(oc / cos(rk * D2R), 1, sm())),
            sec("Layout marks (centre, post se)")
        )
        for (i in 1..minOf(n, 80)) rows.add(Row("Baluster $i", fL((gap + w / 2 + (i - 1) * oc) / cos(rk * D2R))))
        rows
    })
}

// ================= Cost =================
fun Engine.costForm() {
    commit()
    val q = cur
    val (qty, unit) = when (q.t) {
        'L' -> {
            if (q.u == "bf") rnd(q.v / BDFT, 4) to "bf"
            else if (q.u == "ltr") rnd(q.v / LTR, 4) to "litre"
            else {
                val uu = baseU(q.u)
                rnd(q.v / scale(q.u, q.d), 4) to (uu + SUP[q.d.coerceIn(0, 3)])
            }
        }
        'W' -> rnd(q.v / wf(q.u), 4) to (WN[q.u] ?: "kg")
        'n', 'p' -> q.v to "nag"
        else -> 0.0 to "nag"
    }
    ui.form(FormSpec("Cost (kharcha)", listOf(
        Field("q", "Quantity ($unit)", false, num(qty).replace(",", "")),
        Field("r", "Rate per $unit (₹)", false, "", "yahan rate likhein"),
        Field("g", "GST %", false, "0")
    ), null) { v, _ ->
        val qq = v["q"] ?: Double.NaN
        val r = v["r"] ?: Double.NaN
        val g = (v["g"] ?: 0.0).let { if (it.isFinite() && it > 0) it else 0.0 }
        if (!qq.isFinite() || !r.isFinite()) return@FormSpec bad("Quantity aur rate daaliye")
        val sub = qq * r
        listOf(
            sec("Hisaab"),
            Row("Rakam", money(sub)),
            Row("GST ($g%)", money(sub * g / 100)),
            Row("Kul", money(sub * (1 + g / 100)))
        )
    })
}

// ================= Weight from volume =================
fun Engine.wtVolForm() {
    commit()
    val q = cur
    val volDef = if (q.t == 'L' && q.d == 3) (if (metric) q.v else q.v / CFT) else 1.0
    val vu = if (metric) "m³" else "ft³"
    ui.form(FormSpec("Wazan (weight) from Volume", listOf(
        Field("v", "Volume ($vu)", false, num(rnd(volDef, 4)).replace(",", "")),
        Field("d", "Density (kg/m³) – 'wt/vol' option ke liye", false, num(rnd(dens, 1)).replace(",", ""))
    ), listOf("wt/vol", "Concrete", "Ret", "Steel", "Int")) { v, sg ->
        val vol0 = v["v"] ?: Double.NaN
        if (!ok(vol0)) return@FormSpec bad("Volume daaliye")
        val vol = if (metric) vol0 else vol0 * CFT
        val name = seg(sg, 0, "wt/vol")
        val d = when (name) {
            "Concrete" -> 2400.0
            "Ret" -> 1600.0
            "Steel" -> 7850.0
            "Int" -> 1920.0
            else -> v["d"] ?: Double.NaN
        }
        if (!ok(d)) return@FormSpec bad("Density daaliye")
        val kg = vol * d
        listOf(
            sec("$name (" + num(rnd(d, 1)) + " kg/m³)"),
            Row("Wazan", num(rnd(kg, 2)) + " kg"),
            Row("Tonne (metric)", num(rnd(kg / 1000, 3)) + " mt"),
            Row("Tons (" + num(lbsPerTon) + " lb)", num(rnd(kg / (lbsPerTon * LB), 3)) + " tons"),
            Row("Pounds", num(rnd(kg / LB, 1)) + " lbs")
        )
    })
}
