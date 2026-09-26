package com.nirmaan.calc

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Phase 2-4 functions: roof framing, material estimates, weight, cost.
 * Every screen is a form: inputs on top, results update live.
 */

// ================= helpers =================
private const val CFT = 0.028316846592      // 1 cubic foot in m³
private const val BAG = 0.0347              // 50 kg cement bag in m³
private const val BDFT = 0.002359737216     // 1 board foot in m³

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
private fun Engine.money(x: Double) = "₹ " + String.format(java.util.Locale.US, "%,.2f", x)

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

fun Engine.hipForm(irr: Boolean) {
    val runDef = lt(T["run"]?.v, "12'", "365")
    val fields = mutableListOf(pitchField("pa", if (irr) "Pitch A (lambi deewar wali chhat)" else "Pitch", pitchDef()))
    if (irr) fields.add(pitchField("pb", "Pitch B (doosri deewar wali chhat)", pitchDef()))
    fields.add(Field("run", if (irr) "Common Run A (deewar A se ridge tak)" else "Common Run (deewar se ridge tak)", true, runDef))
    ui.form(FormSpec(if (irr) "Irregular Hip / Valley" else "Hip / Valley Function", fields, listOf("Miter Saw", "Protractor")) { v, mode ->
        val pr = mode == "Protractor"
        fun cut(a: Double) = f2(if (pr) 90 - a else a) + "°"
        val pA = v["pa"] ?: Double.NaN
        val pB = if (irr) v["pb"] ?: Double.NaN else pA
        val run = v["run"] ?: Double.NaN
        if (!(pA > 0 && pA < 90 && pB > 0 && pB < 90)) return@FormSpec bad("Pitch 0 se 90 ke beech daaliye")
        if (!ok(run)) return@FormSpec bad("Run daaliye")
        val g = hipGeo(pA, pB, run)
        val rows = mutableListOf(
            sec("Hip / Valley rafter"),
            Row("Plumb Cut", cut(g.hipPitch)),
            Row("Level Cut", cut(90 - g.hipPitch)),
            Row(if (irr) "Cheek Cut A – saw bevel" else "Cheek Cut – saw bevel", cut(90 - g.planA))
        )
        if (irr) rows.add(Row("Cheek Cut B – saw bevel", cut(g.planA)))
        rows.add(Row(if (irr) "Hip Backing Angle A" else "Hip Backing Angle", f2(g.backA) + "°"))
        if (irr) rows.add(Row("Hip Backing Angle B", f2(g.backB) + "°"))
        rows.addAll(
            listOf(
                Row("Dihedral Angle", f2(g.dihedral) + "°"),
                Row("Plan Angle (deewar A se)", f2(g.planA) + "°"),
                Row("Hip/Valley Rafter Pitch", f2(g.hipPitch) + "°"),
                Row("Hip Run", fL(g.hipRun)),
                Row("Hip Rafter Length", fL(g.hipLen)),
                sec("Common rafters"),
                Row("Rise", fL(g.rise)),
                Row(if (irr) "Common A length" else "Common length", fL(g.comA))
            )
        )
        if (irr) {
            rows.add(Row("Run B", fL(g.runB)))
            rows.add(Row("Common B length", fL(g.comB)))
        }
        rows.add(Row("Lengths centre-line tak hain (ridge/hip ki moti ka aadha ghataein)"))
        rows
    })
}

// ================= Jack rafters =================
fun Engine.jackForm(irr: Boolean) {
    val fields = mutableListOf(pitchField("pa", if (irr) "Pitch A" else "Pitch", pitchDef()))
    if (irr) fields.add(pitchField("pb", "Pitch B", pitchDef()))
    fields.add(Field("run", if (irr) "Common Run A" else "Common Run", true, lt(T["run"]?.v, "12'", "365")))
    fields.add(Field("sp", "Spacing (on-center)", true, lt(M["oc"]?.v, "16\"", "40")))
    ui.form(FormSpec(if (irr) "Irregular Jack Rafters" else "Jack Rafters", fields, null) { v, _ ->
        val pA = v["pa"] ?: Double.NaN
        val pB = if (irr) v["pb"] ?: Double.NaN else pA
        val run = v["run"] ?: Double.NaN
        val sp = v["sp"] ?: Double.NaN
        if (!(pA > 0 && pA < 90 && pB > 0 && pB < 90)) return@FormSpec bad("Pitch 0 se 90 ke beech daaliye")
        if (!ok(run, sp)) return@FormSpec bad("Run aur spacing daaliye")
        val g = hipGeo(pA, pB, run)
        val tA = tan(pA * D2R)
        val tB = tan(pB * D2R)
        val rows = mutableListOf<Row>()
        fun side(name: String, k: Double, cp: Double, maxRun: Double, cheek: Double) {
            val diff = sp * k / cp
            rows.add(sec(name))
            rows.add(Row("Common Difference (har jack ka farak)", fL(diff, 1, sm())))
            rows.add(Row("Cheek (side) cut – saw bevel", f2(cheek) + "°"))
            var i = 1
            while (i <= 40) {
                val jr = i * sp * k
                if (jr >= maxRun - 1e-9) break
                rows.add(Row("Jack $i  (" + fL(i * sp) + " se)", fL(jr / cp)))
                i++
            }
            if (i == 1) rows.add(Row("Spacing bahut badi hai — koi jack nahi"))
        }
        side(if (irr) "Side A jacks" else "Jacks (sabse chhota pehle)", tB / tA, cos(pA * D2R), run, 90 - g.planA)
        if (irr) side("Side B jacks", tA / tB, cos(pB * D2R), g.runB, g.planA)
        rows.add(Row("Lengths centre-line tak hain (hip ki moti ka aadha ghataein)"))
        rows
    })
}

// ================= Rake wall =================
fun Engine.rakeWallForm() {
    ui.form(FormSpec("Rake Wall (dhalan wali deewar)", listOf(
        Field("l", "Wall Length", true, lt(G["len"]?.v, "12'", "365")),
        pitchField("p", "Pitch", pitchDef()),
        Field("h", "Chhoti taraf ki height", true, lt(G["hei"]?.v, "8'", "244")),
        Field("sp", "Stud spacing (on-center)", true, lt(M["oc"]?.v, "16\"", "40"))
    ), null) { v, _ ->
        val l = v["l"] ?: Double.NaN
        val p = v["p"] ?: Double.NaN
        val h = v["h"] ?: Double.NaN
        val sp = v["sp"] ?: Double.NaN
        if (!ok(l, h, sp) || !(p >= 0 && p < 90)) return@FormSpec bad("Saari values daaliye")
        val t = tan(p * D2R)
        val n = ceil(l / sp - 1e-9).toInt()
        val rows = mutableListOf(
            sec("Results"),
            Row("Lambi taraf ki height", fL(h + l * t)),
            Row("Top plate length (dhalan par)", fL(l / cos(p * D2R))),
            Row("Studs ki ginti", (n + 1).toString()),
            Row("Stud top cut angle", f2(p) + "°"),
            sec("Har stud ki height")
        )
        for (i in 0..minOf(n, 60)) {
            val x = minOf(i * sp, l)
            rows.add(Row("Stud ${i + 1}  (" + fL(x) + " par)", fL(h + x * t)))
        }
        rows
    })
}

// ================= Roof =================
fun Engine.roofForm() {
    ui.form(FormSpec("Roof Function", listOf(
        Field("l", "Building Length", true, lt(G["len"]?.v, "40'", "1200")),
        Field("w", "Building Width", true, lt(G["wid"]?.v, "24'", "730")),
        pitchField("p", "Pitch", pitchDef()),
        Field("oh", "Overhang (chhajja)", true, if (metric) "45" else "18\""),
        Field("sp", "Rafter spacing (on-center)", true, lt(M["oc"]?.v, "24\"", "60"))
    ), listOf("Gable (do dhalan)", "Hip (char dhalan)")) { v, seg ->
        val l = v["l"] ?: Double.NaN
        val w = v["w"] ?: Double.NaN
        val p = v["p"] ?: Double.NaN
        val oh = (v["oh"] ?: 0.0).let { if (it.isFinite() && it > 0) it else 0.0 }
        val sp = v["sp"] ?: Double.NaN
        if (!ok(l, w, sp) || !(p > 0 && p < 90)) return@FormSpec bad("Length, Width, Pitch aur spacing daaliye")
        val hip = seg?.startsWith("Hip") == true
        val cp = cos(p * D2R)
        val tp = tan(p * D2R)
        val l2 = l + 2 * oh
        val w2 = w + 2 * oh
        val runO = w / 2 + oh
        val area = l2 * w2 / cp
        val rows = mutableListOf(
            sec("Area"),
            Row("Roof area (chhajje ke saath)", areaStr(area)),
            Row("Squares (100 ft²)", num(rnd(area / (FT * FT * 100), 2))),
            Row("4×8 sheets (+10%)", ceil(area / (32 * FT * FT) * 1.1).toInt().toString()),
            Row("Shingle bundles (3 per square, +10%)", ceil(area / (FT * FT * 100) * 3 * 1.1).toInt().toString()),
            sec("Framing"),
            Row("Pitch", pitchStr(p)),
            Row("Ridge height (deewar ke upar)", fL(w / 2 * tp)),
            Row("Common rafter length (chhajje ke saath)", fL(runO / cp)),
            Row("Ridge length", fL(if (hip) max(l - w, 0.0) else l2))
        )
        if (hip) {
            val g = hipGeo(p, p, runO)
            rows.add(Row("Hip rafter length (4 nag)", fL(g.hipLen)))
            rows.add(Row("Hip pitch", f2(g.hipPitch) + "°"))
            rows.add(Row("Common rafters (ridge par)", (2 * (ceil(max(l - w, 0.0) / sp - 1e-9).toInt() + 1)).toString()))
            rows.add(Row("Eave / fascia length", fL(2 * (l2 + w2))))
        } else {
            rows.add(Row("Common rafters", (2 * (ceil(l2 / sp - 1e-9).toInt() + 1)).toString()))
            rows.add(Row("Eave / fascia length (dono taraf)", fL(2 * l2)))
            rows.add(Row("Rake / barge length (4 nag)", fL(runO / cp)))
        }
        rows
    })
}

// ================= Masonry (int / block) =================
fun Engine.masonryForm() {
    ui.form(FormSpec("Masonry (int / block)", listOf(
        Field("l", "Deewar ki Length", true, lt(G["len"]?.v, "20'", "600")),
        Field("h", "Deewar ki Height", true, lt(G["hei"]?.v, "10'", "300")),
        Field("t", "Deewar ki motai", true, if (metric) "23" else "9\"", "9\" = 23 cm, 4.5\" = 11.5 cm"),
        Field("op", "Khidki/Darwaza area (" + areaUnit() + ")", false, "0")
    ), listOf("Int (brick)", "Block")) { v, seg ->
        val l = v["l"] ?: Double.NaN
        val h = v["h"] ?: Double.NaN
        val t = v["t"] ?: Double.NaN
        if (!ok(l, h, t)) return@FormSpec bad("Length, Height aur motai daaliye")
        val area = l * h - areaIn(v["op"] ?: 0.0)
        if (area <= 0) return@FormSpec bad("Khidki/darwaza ka area deewar se bada hai")
        val vol = area * t
        val block = seg == "Block"
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
    ui.form(FormSpec("Footing / Concrete", listOf(
        Field("l", "Length", true, lt(G["len"]?.v, "4'", "120")),
        Field("w", "Width", true, lt(G["wid"]?.v, "4'", "120")),
        Field("d", "Depth / motai", true, if (metric) "45" else "18\""),
        Field("n", "Kitne nag", false, "1")
    ), listOf("M15 1:2:4", "M20 1:1.5:3", "M25 1:1:2")) { v, seg ->
        val l = v["l"] ?: Double.NaN
        val w = v["w"] ?: Double.NaN
        val d = v["d"] ?: Double.NaN
        val n = v["n"] ?: Double.NaN
        if (!ok(l, w, d, n)) return@FormSpec bad("Length, Width, Depth aur nag daaliye")
        val mix = when {
            seg?.startsWith("M25") == true -> doubleArrayOf(1.0, 1.0, 2.0)
            seg?.startsWith("M20") == true -> doubleArrayOf(1.0, 1.5, 3.0)
            else -> doubleArrayOf(1.0, 2.0, 4.0)
        }
        val sum = mix.sum()
        val vol = l * w * d * n
        val dry = vol * 1.54
        val bags = dry * mix[0] / sum / BAG
        listOf(
            sec("Concrete"),
            Row("Geela volume", m3(vol)),
            Row("Sukha volume (×1.54)", m3(dry)),
            sec("Material ($seg)"),
            Row("Cement (50 kg bag)", num(rnd(bags, 1)) + " bag"),
            Row("Ret (sand)", m3(dry * mix[1] / sum)),
            Row("Kapchi / gitti (aggregate)", m3(dry * mix[2] / sum)),
            Row("Paani (lagbhag, w/c 0.5)", num(rnd(bags * 50 * 0.5, 0)) + " litre")
        )
    })
}

// ================= Drywall / paint =================
fun Engine.drywallForm() {
    ui.form(FormSpec("Drywall / Paint", listOf(
        Field("l", "Kamre ki Length", true, lt(G["len"]?.v, "12'", "365")),
        Field("w", "Kamre ki Width", true, lt(G["wid"]?.v, "10'", "300")),
        Field("h", "Kamre ki Height", true, lt(G["hei"]?.v, "10'", "300")),
        Field("op", "Khidki/Darwaza area (" + areaUnit() + ")", false, "0")
    ), listOf("4×8 ft", "4×10 ft", "4×12 ft")) { v, seg ->
        val l = v["l"] ?: Double.NaN
        val w = v["w"] ?: Double.NaN
        val h = v["h"] ?: Double.NaN
        if (!ok(l, w, h)) return@FormSpec bad("Length, Width aur Height daaliye")
        val wall = max(2 * (l + w) * h - areaIn(v["op"] ?: 0.0), 0.0)
        val ceil = l * w
        val sheetFt = when (seg) { "4×10 ft" -> 40.0; "4×12 ft" -> 48.0; else -> 32.0 }
        val sheet = sheetFt * FT * FT
        val sw = ceil(wall / sheet * 1.1).toInt()
        val sc = ceil(ceil / sheet * 1.1).toInt()
        listOf(
            sec("Area"),
            Row("Deewar area", areaStr(wall)),
            Row("Chhat (ceiling) area", areaStr(ceil)),
            Row("Kul area", areaStr(wall + ceil)),
            sec("Sheets ($seg, +10%)"),
            Row("Deewar ke liye", "$sw sheet"),
            Row("Ceiling ke liye", "$sc sheet"),
            Row("Kul sheets", "${sw + sc} sheet"),
            Row("Screws (lagbhag)", ((sw + sc) * 32).toString() + " nag"),
            sec("Paint (2 coat, lagbhag)"),
            Row("Deewar", num(rnd(wall * 2 / 10, 1)) + " litre"),
            Row("Deewar + ceiling", num(rnd((wall + ceil) * 2 / 10, 1)) + " litre")
        )
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
            Row("Ek nag", num(rnd(vol / q / BDFT, 2)) + " BF  |  " + num(rnd(vol / q / CFT, 3)) + " cft")
        )
        if (rate > 0) rows.add(Row("Kul keemat", money(cft * rate)))
        rows
    })
}

// ================= Fence =================
fun Engine.fenceForm() {
    ui.form(FormSpec("Fence (baad / jaali)", listOf(
        Field("l", "Fence ki kul length", true, lt(G["len"]?.v, "100'", "3000")),
        Field("ps", "Post spacing", true, if (metric) "240" else "8'"),
        Field("r", "Har section mein rails", false, num(M["rails"]?.v ?: 3.0)),
        Field("pw", "Picket / patti width", true, if (metric) "9" else "3-1/2\""),
        Field("g", "Picket ke beech gap", true, if (metric) "5" else "2\"")
    ), null) { v, _ ->
        val l = v["l"] ?: Double.NaN
        val ps = v["ps"] ?: Double.NaN
        if (!ok(l, ps)) return@FormSpec bad("Length aur post spacing daaliye")
        val nSec = ceil(l / ps - 1e-9).toInt()
        val rails = (v["r"] ?: 0.0).let { if (it.isFinite() && it > 0) it.toInt() else 0 }
        val pw = v["pw"] ?: Double.NaN
        val g = (v["g"] ?: 0.0).let { if (it.isFinite() && it >= 0) it else 0.0 }
        val rows = mutableListOf(
            sec("Posts"),
            Row("Sections", nSec.toString()),
            Row("Posts (khambe)", (nSec + 1).toString()),
            Row("Asli post spacing", fL(l / nSec)),
            sec("Rails"),
            Row("Rails kul", (nSec * rails).toString()),
            Row("Har rail ki length", fL(l / nSec)),
            Row("Rail kul length", fL(l * rails))
        )
        if (ok(pw)) {
            rows.add(sec("Pickets"))
            rows.add(Row("Pickets (lagbhag)", ceil(l / (pw + g)).toInt().toString()))
        }
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
        Field("cnt", "Kitne baluster (sirf Evenly Space ke liye)", false, "0")
    ), listOf("Limit Opening", "Evenly Space", "Best Fit")) { v, mode ->
        val run = v["run"] ?: Double.NaN
        val w = v["w"] ?: Double.NaN
        val gm = v["g"] ?: Double.NaN
        val rk = (v["rk"] ?: 0.0).let { if (it.isFinite() && it >= 0 && it < 80) it else 0.0 }
        if (!ok(run, w, gm)) return@FormSpec bad("Run, width aur khali jagah daaliye")
        val cnt = (v["cnt"] ?: 0.0).let { if (it.isFinite() && it >= 1) it.toInt() else 0 }
        val n = when {
            mode == "Evenly Space" && cnt > 0 -> cnt
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
            val uu = baseU(q.u)
            rnd(q.v / Math.pow(UF.getValue(uu), q.d.toDouble()), 4) to (uu + SUP[q.d.coerceIn(0, 3)])
        }
        'W' -> rnd(q.v / (WF[q.u] ?: 1.0), 4) to (WN[q.u] ?: "kg")
        'n', 'p' -> q.v to "nag"
        else -> 0.0 to "nag"
    }
    ui.form(FormSpec("Cost (kharcha)", listOf(
        Field("q", "Quantity ($unit)", false, num(qty)),
        Field("r", "Rate per $unit (₹)", false, "0"),
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
        Field("v", "Volume ($vu)", false, num(rnd(volDef, 4))),
        Field("d", "Custom density (kg/m³)", false, "2400")
    ), listOf("Concrete", "Ret", "Steel", "Custom")) { v, seg ->
        val vol0 = v["v"] ?: Double.NaN
        if (!ok(vol0)) return@FormSpec bad("Volume daaliye")
        val vol = if (metric) vol0 else vol0 * CFT
        val dens = when (seg) {
            "Concrete" -> 2400.0
            "Ret" -> 1600.0
            "Steel" -> 7850.0
            else -> v["d"] ?: Double.NaN
        }
        if (!ok(dens)) return@FormSpec bad("Density daaliye")
        val kg = vol * dens
        listOf(
            sec("$seg (" + num(dens) + " kg/m³)"),
            Row("Wazan", num(rnd(kg, 2)) + " kg"),
            Row("Tonne (metric)", num(rnd(kg / 1000, 3)) + " mt"),
            Row("Pounds", num(rnd(kg / 0.45359237, 1)) + " lbs")
        )
    })
}
