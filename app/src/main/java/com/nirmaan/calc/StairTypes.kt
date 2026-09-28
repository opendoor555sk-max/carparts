package com.nirmaan.calc

import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * Stair Planner: give the room (length × width) and the floor-to-floor height,
 * and see which stair types fit, how each looks (top view + unfolded side view),
 * and all measurements (steps, landings, marking, RCC, steel).
 * Types are added part by part; each type is one entry in STAIR_TYPES.
 */

// ================= generic drawing data (used by PlanView) =================
class P(val x: Double, val y: Double)

class Shape(
    val pts: List<P>,
    val fill: Int,
    val stroke: Int = 0xFF333333.toInt(),
    val dashed: Boolean = false,
    val closed: Boolean = true,
    val width: Float = 1.4f
)

class Label(
    val p: P,
    val text: String,
    val size: Float = 10f,
    val color: Int = 0xFF1565C0.toInt(),
    val align: Int = 1,          // 0 left, 1 centre, 2 right
    val rot: Float = 0f,
    val bold: Boolean = false
)

/** world units = metres, y points up */
class PlanDrawing(
    val title: String,
    val shapes: List<Shape>,
    val labels: List<Label>,
    val arrows: List<List<P>>,
    val info: List<String>
)

private const val WOOD = 0xFFE8C99B.toInt()
private const val LANDING = 0xFFCFD8DC.toInt()
private const val ROOM = 0x00FFFFFF
private const val FLOOR = 0xFF9E9E9E.toInt()
private const val CONC = 0xFFBDBDBD.toInt()
private const val RED = 0xFFC62828.toInt()
private const val DARK = 0xFF222222.toInt()

private fun rect(x0: Double, y0: Double, x1: Double, y1: Double) = listOf(P(x0, y0), P(x1, y0), P(x1, y1), P(x0, y1))

// ================= building rules (approximate, NBC 2016 style) =================
private class Rules(val rMax: Double, val tMin: Double, val wMin: Double, val maxPerFlight: Int, val highRise: Boolean)

private fun rulesFor(b: String) = when (b) {
    "Commercial" -> Rules(0.15, 0.30, 1.5, 15, false)
    "High-rise" -> Rules(0.15, 0.30, 1.5, 15, true)
    else -> Rules(0.19, 0.25, 1.0, 15, false)
}

// ================= a stair design =================
private class Design(
    val id: Int,
    val name: String,
    val flights: List<Int>,          // risers per flight
    val landingCount: Int,           // landings between flights
    val needL: Double,
    val needW: Double,
    val fits: Boolean?,              // null = room size not given
    val rotated: Boolean,
    val why: String,
    val maxW: Double,
    val code: List<String>,          // code / comfort warnings
    val highRiseOk: Boolean,
    val plan: PlanDrawing,
    val side: PlanDrawing,
    val landingLen: Double,
    val turnNote: String = ""
)

private class Input(val L: Double, val W: Double, val H: Double, val sw: Double, val n: Int, val ur: Double, val ut: Double, val rules: Rules, val well: Double = 0.0)

/** all stair types (added part by part) */
private val STAIR_TYPES = listOf(
    1 to "1 Straight",
    2 to "2 Straight + landing",
    3 to "3 L-shape",
    4 to "4 Double-L",
    5 to "5 Dog-legged",
    6 to "6 Open-well"
)

/** preferred order when several fit */
private fun preference(b: String): List<Int> = when (b) {
    "High-rise" -> listOf(14, 6, 5, 2, 4, 3, 1)
    "Commercial" -> listOf(6, 5, 3, 4, 2, 1, 13, 12)
    else -> listOf(5, 3, 2, 6, 8, 4, 1, 11, 12)
}

// ---------- fit test ----------
private fun fitOf(inp: Input, needL: Double, needW: Double): Triple<Boolean?, Boolean, String> {
    if (!(inp.L > 0) || !(inp.W > 0)) return Triple(null, false, "jagya nahi di")
    if (needL <= inp.L + 1e-6 && needW <= inp.W + 1e-6) return Triple(true, false, "")
    if (needL <= inp.W + 1e-6 && needW <= inp.L + 1e-6) return Triple(true, true, "ghuma kar")
    return Triple(false, false, "")
}

// ---------- straight family (1, 2) ----------
private fun Engine.straightDesign(id: Int, name: String, flights: List<Int>, inp: Input): Design {
    val ut = inp.ut
    val ur = inp.ur
    val sw = inp.sw
    val treads = flights.sumOf { it - 1 }
    val going = treads * ut
    val k = flights.size                     // landings in the length: between flights + top
    val needL = going + k * sw
    val needW = sw
    val (fits, rotated, note) = fitOf(inp, needL, needW)
    val why = when (fits) {
        null -> ""
        true -> note
        false -> {
            val shortL = needL - max(inp.L, inp.W)
            if (shortL > 0) "lambai " + fL(shortL) + " kam" else "pohlai kam"
        }
    }
    // widest stair this room allows (length must still fit with landings as wide as the stair)
    val maxW = if (inp.L > 0 && inp.W > 0) max(min(inp.W, (inp.L - going) / k), min(inp.L, (inp.W - going) / k)) else Double.NaN
    val code = ArrayList<String>()
    flights.forEachIndexed { i, r -> if (r > inp.rules.maxPerFlight) code.add("Flight " + (i + 1) + ": " + r + " riser (max " + inp.rules.maxPerFlight + ") — landing chahiye") }

    // ---- top view ----
    val shapes = ArrayList<Shape>()
    val labels = ArrayList<Label>()
    if (inp.L > 0 && inp.W > 0) {
        val rl = if (rotated) inp.W else inp.L
        val rw = if (rotated) inp.L else inp.W
        shapes.add(Shape(rect(0.0, 0.0, rl, rw), ROOM, 0xFF607D8B.toInt(), dashed = true))
        labels.add(Label(P(rl / 2, rw), "Jagya " + fL(rl) + " × " + fL(rw), 9.5f, 0xFF455A64.toInt()))
    }
    var x = 0.0
    var step = 1
    val sm = if (metric) "cm" else "in"
    flights.forEachIndexed { fi, r ->
        val x0 = x
        for (t in 1 until r) {
            shapes.add(Shape(rect(x, 0.0, x + ut, sw), WOOD))
            labels.add(Label(P(x + ut / 2, sw / 2 - sw * 0.28), step.toString(), 8.5f, DARK))
            x += ut; step++
        }
        labels.add(Label(P((x0 + x) / 2, -sw * 0.12), "Flight " + (fi + 1) + ": " + r + " riser", 9f))
        // landing after this flight (last one = upper floor edge)
        val last = fi == flights.size - 1
        shapes.add(Shape(rect(x, 0.0, x + sw, sw), if (last) FLOOR else LANDING))
        labels.add(Label(P(x + sw / 2, sw / 2), if (last) "Upar floor" else "Landing", 9f, DARK))
        labels.add(Label(P(x + sw / 2, -sw * 0.12), fL(sw, 1, sm) + " × " + fL(sw, 1, sm), 8.5f))
        x += sw; step++
    }
    labels.add(Label(P(going / 2 + (k - 1) * sw / 2, sw + sw * 0.12), "Seedhi ki pohlai " + fL(sw, 1, sm) + "   •   Tread " + fL(ut, 1, sm), 9f))
    val arrow = listOf(P(ut * 0.3, sw / 2), P(x - sw / 2, sw / 2))
    val plan = PlanDrawing(
        "Upar se (plan) — " + name.substringAfter(' '), shapes, labels, listOf(arrow),
        listOf("Chahiye: " + fL(needL) + " × " + fL(needW) + if (rotated) "  (ghuma kar)" else "")
    )
    return Design(id, name, flights, flights.size - 1, needL, needW, fits, rotated, why, maxW, code,
        highRiseOk = flights.all { it <= inp.rules.maxPerFlight }, plan = plan, side = sideView(flights, sw, inp), landingLen = sw)
}

// ---------- turning stairs built from flights + square landings (3 L, 4 Double-L, ...) ----------
private typealias Dir = Pair<Int, Int>
private val EAST: Dir = 1 to 0
private val NORTH: Dir = 0 to 1
private val WEST: Dir = -1 to 0

private class Layout(val shapes: List<Shape>, val labels: List<Label>, val arrow: List<P>,
                     val x0: Double, val y0: Double, val x1: Double, val y1: Double)

/** Lays the flights out along [dirs]; every flight ends on a square landing (last one = upper floor). */
private fun Engine.layoutTurns(flights: List<Int>, dirs: List<Dir>, inp: Input, gap: Double = 0.0): Layout {
    val sw = inp.sw
    val ut = inp.ut
    val sm = if (metric) "cm" else "in"
    val shapes = ArrayList<Shape>()
    val labels = ArrayList<Label>()
    val arrow = ArrayList<P>()
    // (cx, cy) = middle of the edge where the next piece starts
    var cx = 0.0
    var cy = sw / 2
    var step = 1
    fun piece(x: Double, y: Double, d: Dir, len: Double): List<P> {
        val px = -d.second * sw / 2
        val py = d.first * sw / 2
        val ex = x + d.first * len
        val ey = y + d.second * len
        return listOf(P(x - px, y - py), P(ex - px, ey - py), P(ex + px, ey + py), P(x + px, y + py))
    }
    flights.forEachIndexed { i, r ->
        val d = dirs[i]
        val rx = d.second.toDouble()          // right-hand side (outside of a left turn)
        val ry = -d.first.toDouble()
        if (i == 0) arrow.add(P(cx + d.first * ut * 0.3, cy + d.second * ut * 0.3))
        val sx = cx; val sy = cy
        for (t in 1 until r) {
            shapes.add(Shape(piece(cx, cy, d, ut), WOOD))
            labels.add(Label(P(cx + d.first * ut / 2 + rx * sw * 0.28, cy + d.second * ut / 2 + ry * sw * 0.28), step.toString(), 8.5f, DARK))
            cx += d.first * ut; cy += d.second * ut; step++
        }
        val vertical = d.first == 0
        labels.add(Label(P((sx + cx) / 2 + rx * sw * 0.64, (sy + cy) / 2 + ry * sw * 0.64),
            "F" + (i + 1) + ": " + r + " riser", 9f, 0xFF1565C0.toInt(), 1, if (vertical) -90f else 0f))
        val last = i == flights.size - 1
        val nd = if (last) null else dirs[i + 1]
        if (nd != null && nd.first == -d.first && nd.second == -d.second) {
            // half turn (180°): one wide landing across both flights and the well
            val lvx = -d.second.toDouble()     // left of the walking direction
            val lvy = d.first.toDouble()
            val off = sw + gap
            val ax = cx - lvx * sw / 2; val ay = cy - lvy * sw / 2
            val bx = cx + lvx * (off + sw / 2); val by = cy + lvy * (off + sw / 2)
            shapes.add(Shape(listOf(P(ax, ay), P(bx, by), P(bx + d.first * sw, by + d.second * sw), P(ax + d.first * sw, ay + d.second * sw)), LANDING))
            val mx = cx + d.first * sw / 2
            val my = cy + d.second * sw / 2
            arrow.add(P(mx, my)); arrow.add(P(mx + lvx * off, my + lvy * off))
            val clx = mx + lvx * off / 2; val cly = my + lvy * off / 2
            labels.add(Label(P(clx, cly + sw * 0.12), "Landing", 9f, DARK))
            labels.add(Label(P(clx, cly - sw * 0.16), fL(sw, 1, sm) + " × " + fL(2 * sw + gap, 1, sm), 8f, 0xFF1565C0.toInt()))
            if (gap > 1e-6) labels.add(Label(P(cx - d.first * ut * 1.5 + lvx * (sw / 2 + gap / 2), cy - d.second * ut * 1.5 + lvy * (sw / 2 + gap / 2)), "well " + fL(gap, 1, sm), 8f, RED, 1, if (d.first == 0) -90f else 0f))
            step++
            cx += lvx * off; cy += lvy * off
            return@forEachIndexed
        }
        shapes.add(Shape(piece(cx, cy, d, sw), if (last) FLOOR else LANDING))
        val lx = cx + d.first * sw / 2
        val ly = cy + d.second * sw / 2
        arrow.add(P(lx, ly))
        labels.add(Label(P(lx, ly + sw * 0.12), if (last) "Upar floor" else "Landing", 9f, DARK))
        labels.add(Label(P(lx, ly - sw * 0.16), fL(sw, 1, sm) + " × " + fL(sw, 1, sm), 8f, 0xFF1565C0.toInt()))
        step++
        if (!last) {
            val d2 = dirs[i + 1]
            if (d2 == d) { cx += d.first * sw; cy += d.second * sw }
            else { cx = lx + d2.first * sw / 2; cy = ly + d2.second * sw / 2 }
        }
    }
    val pts = shapes.flatMap { it.pts }
    return Layout(shapes, labels, arrow, pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
}

/** how much the footprint sticks out of the room (0 = fits), best of both orientations */
private fun overflow(inp: Input, nL: Double, nW: Double): Double {
    if (!(inp.L > 0) || !(inp.W > 0)) return 0.0
    // (tiny rounding differences count as a fit)
    return min(max(0.0, nL - inp.L) + max(0.0, nW - inp.W), max(0.0, nL - inp.W) + max(0.0, nW - inp.L))
}

/** all ways to split n risers into k flights (each at least [minR]) */
private fun splits(n: Int, k: Int, minR: Int): List<List<Int>> {
    val out = ArrayList<List<Int>>()
    fun rec(left: Int, parts: List<Int>) {
        if (parts.size == k - 1) { if (left >= minR) out.add(parts + left); return }
        for (a in minR..left - minR * (k - 1 - parts.size)) rec(left - a, parts + a)
    }
    rec(n, emptyList())
    return out
}

private fun Engine.turnDesign(
    id: Int, name: String, inp: Input, k: Int, dirs: List<Dir>, turnNote: String, gap: Double = 0.0, landLen: Double = inp.sw,
    foot: (List<Int>, Double) -> Pair<Double, Double>
): Design {
    val ut = inp.ut
    val tooFew = inp.n < 2 * k
    var cands = splits(inp.n, k, if (tooFew) 1 else 2)
    if (cands.isEmpty()) cands = listOf(List(k) { if (it < inp.n) 1 else 0 }.let { l -> l.mapIndexed { i, v -> if (i == 0) v + max(0, inp.n - l.sum()) else v } })
    val maxF = inp.rules.maxPerFlight
    val best = cands.minWithOrNull(compareBy<List<Int>>(
        { if (overflow(inp, foot(it, inp.sw).first, foot(it, inp.sw).second) > 1e-6) 1 else 0 },
        { overflow(inp, foot(it, inp.sw).first, foot(it, inp.sw).second).let { o -> if (o < 1e-6) 0.0 else o } },
        { f -> f.count { it > maxF } },
        { f -> f.maxOf { it } - f.minOf { it } }
    ))!!
    val (needL, needW) = foot(best, inp.sw)
    var (fits, rotated, note) = fitOf(inp, needL, needW)
    var why = note
    if (fits == false) {
        val a = max(0.0, needL - inp.L) to max(0.0, needW - inp.W)
        val b = max(0.0, needL - inp.W) to max(0.0, needW - inp.L)
        val (sl, sw2) = if (a.first + a.second <= b.first + b.second) a else b
        why = listOfNotNull(if (sl > 1e-6) "lambai " + fL(sl) + " kam" else null, if (sw2 > 1e-6) "pohlai " + fL(sw2) + " kam" else null).joinToString(", ")
    }
    if (tooFew) { fits = false; why = "oonchai bahu ochhi" }
    // widest stair this room allows
    val maxW = if (inp.L > 0 && inp.W > 0) {
        var lo = 0.0
        var hi = max(inp.L, inp.W)
        repeat(36) {
            val mid = (lo + hi) / 2
            if (cands.any { overflow(inp, foot(it, mid).first, foot(it, mid).second) <= 1e-6 }) lo = mid else hi = mid
        }
        lo
    } else Double.NaN
    val code = ArrayList<String>()
    best.forEachIndexed { i, r -> if (r > maxF) code.add("Flight " + (i + 1) + ": " + r + " riser (max " + maxF + ") — landing chahiye") }

    val lay = layoutTurns(best, dirs, inp, gap)
    val shapes = ArrayList<Shape>()
    val labels = ArrayList<Label>()
    if (inp.L > 0 && inp.W > 0) {
        val rl = if (rotated) inp.W else inp.L
        val rw = if (rotated) inp.L else inp.W
        shapes.add(Shape(rect(lay.x0, lay.y0, lay.x0 + rl, lay.y0 + rw), ROOM, 0xFF607D8B.toInt(), dashed = true))
        labels.add(Label(P(lay.x0 + rl / 2, lay.y0 + rw + inp.sw * 0.15), "Jagya " + fL(rl) + " × " + fL(rw), 9.5f, 0xFF455A64.toInt()))
    }
    shapes.addAll(lay.shapes)
    labels.addAll(lay.labels)
    val sm = if (metric) "cm" else "in"
    val plan = PlanDrawing(
        "Upar se (plan) — " + name.substringAfter(' '), shapes, labels, listOf(lay.arrow),
        listOf(
            "Chahiye: " + fL(needL) + " × " + fL(needW) + if (rotated) "  (ghuma kar)" else "",
            "Pohlai " + fL(inp.sw, 1, sm) + "  •  Tread " + fL(ut, 1, sm) + "  •  " + turnNote,
            "Ulti baju (jamne vadank) pan bane — drawing no aaino (mirror)"
        )
    )
    return Design(id, name, best, k - 1, needL, needW, fits, rotated, why, maxW, code,
        highRiseOk = best.all { it <= maxF }, plan = plan, side = sideView(best, inp.sw, inp), landingLen = landLen, turnNote = turnNote)
}

// ---------- unfolded side view (works for every type) ----------
private fun Engine.sideView(flights: List<Int>, landingLen: Double, inp: Input): PlanDrawing {
    val ur = inp.ur
    val ut = inp.ut
    val waist = waistOf(flights, inp)
    val top = ArrayList<P>()
    val under = ArrayList<P>()
    var x = 0.0
    var y = 0.0
    top.add(P(-ut * 0.8, 0.0)); top.add(P(0.0, 0.0))
    val labels = ArrayList<Label>()
    val th = atan(ur / ut)
    val off = waist / kotlin.math.cos(th)
    flights.forEachIndexed { fi, r ->
        val fx = x; val fy = y
        for (i in 1..r) {
            y += ur; top.add(P(x, y))
            if (i < r) { x += ut; top.add(P(x, y)) }
        }
        // underside of this flight: parallel to the nosing line
        under.add(P(fx, max(fy + ur - off, fy - waist)))
        under.add(P(x, y - off))
        labels.add(Label(P((fx + x) / 2 - ut, (fy + y) / 2 + ur * 1.5), "F" + (fi + 1) + ": " + r + " × " + fL(ur, 1, if (metric) "cm" else "in"), 9f, 0xFF1565C0.toInt(), 2))
        val last = fi == flights.size - 1
        val ll = if (last) landingLen * 0.8 else landingLen
        under.add(P(x, y - waist))
        x += ll; top.add(P(x, y))
        if (!last) under.add(P(x, y - waist))
        labels.add(Label(P(x - ll / 2, y + ur * 0.4), (if (last) "Upar floor " else "Landing ") + fL(y), 8.5f, DARK))
    }
    val poly = ArrayList<P>(top)
    poly.add(P(x, y - waist))
    for (i in under.indices.reversed()) poly.add(under[i])
    poly.add(P(0.0, -waist))
    poly.add(P(-ut * 0.8, -waist))
    val shapes = listOf(
        Shape(rect(-ut * 1.2, -waist * 2.2, x + ut * 0.5, -waist), FLOOR),
        Shape(poly, CONC)
    )
    labels.add(Label(P(-ut * 1.1, inp.H / 2), "H " + fL(inp.H), 9.5f, RED, 0, -90f))
    return PlanDrawing("Baju se (khula hua) — oonchai", shapes, labels, emptyList(),
        listOf("Riser " + fL(ur, 1, if (metric) "cm" else "in") + " × " + inp.n + "   •   Waist slab " + fL(waist, 1, if (metric) "cm" else "in")))
}

private fun Engine.waistOf(flights: List<Int>, inp: Input): Double {
    val longest = flights.maxOf { hypot(it * inp.ur, (it - 1) * inp.ut) }
    return max(longest / 25, if (metric) 0.10 else 4 * IN)
}

private fun Engine.buildDesign(id: Int, name: String, inp: Input): Design = when (id) {
    1 -> straightDesign(id, name, listOf(inp.n), inp)
    // L: flight 1 along the length, square landing, flight 2 at 90°, upper floor
    3 -> turnDesign(id, name, inp, 2, listOf(EAST, NORTH), "1 vadank 90° (landing par)") { f, sw ->
        ((f[0] - 1) * inp.ut + sw) to ((f[1] - 1) * inp.ut + 2 * sw)
    }
    // Double-L: three flights, two square landings, two 90° turns (flight 3 comes back)
    4 -> turnDesign(id, name, inp, 3, listOf(EAST, NORTH, WEST), "2 vadank 90° (2 landing)") { f, sw ->
        val g1 = (f[0] - 1) * inp.ut
        val g3 = (f[2] - 1) * inp.ut
        max(g1 + sw, g3 + 2 * sw) to ((f[1] - 1) * inp.ut + 2 * sw)
    }
    // Dog-legged: two flights side by side (no gap), half-space landing, 180° turn
    5 -> turnDesign(id, name, inp, 2, listOf(EAST, WEST), "Aadho vadank 180° (paholi landing)", 0.0, 2 * inp.sw) { f, sw ->
        max((f[0] - 1) * inp.ut + sw, (f[1] - 1) * inp.ut + 2 * sw) to (2 * sw)
    }
    // Open-well: like dog-legged but with an open gap (well) between the flights
    6 -> turnDesign(id, name, inp, 2, listOf(EAST, WEST), "Aadho vadank 180°, vachche well " + fL(inp.well, 1, if (metric) "cm" else "in"),
        inp.well, 2 * inp.sw + inp.well) { f, sw ->
        max((f[0] - 1) * inp.ut + sw, (f[1] - 1) * inp.ut + 2 * sw) to (2 * sw + inp.well)
    }
    else -> {
        val a = (inp.n + 1) / 2
        straightDesign(id, name, listOf(a, inp.n - a), inp)
    }
}

// ================= the planner screen =================
fun Engine.stairPlanner() {
    val m = metric
    val sm = if (m) "cm" else "in"
    ui.form(
        FormSpec(
            "Stair Planner", listOf(
                Field("L", "Jagya ki lambai (stair ka kamra)", true, if (m) "600" else "20'", if (m) "cm mein" else "jaise 20' ya 18' 6\""),
                Field("W", "Jagya ki pohlai", true, if (m) "250" else "8'"),
                Field("H", "Oonchai (floor se floor)", true, lenTxt(T["rise"]?.v).ifEmpty { if (m) "300" else "10'" }),
                Field("sw", "Seedhi ki pohlai (khali = code ki kam se kam)", true, "", if (m) "jaise 100 (cm)" else "jaise 3' 6\""),
                Field("dr", "Riser (sabse zyada)", true, if (m) "17.5" else "7\""),
                Field("dt", "Tread (pag rakhne ki jagah)", true, if (m) "25" else "10\""),
                Field("wg", "Open-well: be flight vachche khali jagya", true, if (m) "30" else "1'", "Dog-legged ma 0, Open-well ma aa")
            ),
            listOf("Ghar", "Commercial", "High-rise"),
            STAIR_TYPES.map { it.second }
        ) { v, sg ->
            val parts = sg?.split('|') ?: emptyList()
            val bld = parts.getOrNull(0)?.ifEmpty { null } ?: "Ghar"
            val pick = parts.getOrNull(1) ?: STAIR_TYPES.first().second
            val rules = rulesFor(bld)
            val H = v["H"] ?: Double.NaN
            val dr = v["dr"] ?: Double.NaN
            val dt = v["dt"] ?: Double.NaN
            if (!(H > 0) || !(dr > 0) || !(dt > 0)) return@FormSpec listOf(Row("Oonchai, Riser aur Tread daaliye", "—", true))
            val n = ceil(H / dr - 1e-9).toInt()
            if (n > 80) return@FormSpec listOf(Row("Bahut zyada pagthiye — values check karein", "—", true))
            val swIn = v["sw"] ?: Double.NaN
            val inp = Input(v["L"] ?: Double.NaN, v["W"] ?: Double.NaN, H, if (swIn > 0) swIn else rules.wMin, n, H / n, dt, rules, (v["wg"] ?: 0.0).let { if (it > 0) it else 0.0 })
            val designs = STAIR_TYPES.map { buildDesign(it.first, it.second, inp) }
            val order = preference(bld)
            val best = designs.filter { it.fits != false && it.code.isEmpty() && (!rules.highRise || it.highRiseOk) }
                .minByOrNull { d -> order.indexOf(d.id).let { if (it < 0) 99 else it } }

            val rows = ArrayList<Row>()
            rows.add(sec("Kaunsi seedhi bethegi"))
            for (d in designs) {
                val mark = if (d === best) "⭐ " else ""
                val status = when (d.fits) {
                    null -> "—"
                    true -> "✓ bethegi" + (if (d.rotated) " (ghuma kar)" else "")
                    false -> "✗ " + d.why
                }
                rows.add(Row(mark + d.name, status + (if (d.code.isNotEmpty()) "  ⚠" else ""), d.fits == false))
            }
            if (best != null) rows.add(Row("⭐ Salah: " + best.name.substringAfter(' '), bld))

            val d = designs.firstOrNull { it.name == pick } ?: designs.first()
            val ang = atan(inp.ur / inp.ut) / D2R
            rows.add(sec("Pagthiye (sab prakar ke liye)"))
            rows.add(Row("Riser (oonchai) × ginti", fL(inp.ur, 1, sm) + " × " + n, inp.ur > rules.rMax + 1e-6))
            rows.add(Row("Tread (pag ki jagah)", fL(inp.ut, 1, sm), inp.ut < rules.tMin - 1e-6))
            rows.add(Row("Seedhi ki pohlai", fL(inp.sw, 1, sm) + if (swIn > 0) "" else "  (code ki kam se kam)", inp.sw < rules.wMin - 1e-6))
            rows.add(Row("2R + T (60–65 cm theek)", fL(2 * inp.ur + inp.ut, 1, sm)))
            rows.add(Row("Dhalan (angle)", f2(ang) + "°", ang > 42))
            if (inp.ur > rules.rMax + 1e-6) rows.add(Row("$bld: riser max " + fL(rules.rMax, 1, sm), "⚠", true))
            if (inp.ut < rules.tMin - 1e-6) rows.add(Row("$bld: tread kam se kam " + fL(rules.tMin, 1, sm), "⚠", true))
            if (inp.sw < rules.wMin - 1e-6) rows.add(Row("$bld: pohlai kam se kam " + fL(rules.wMin, 1, sm), "⚠", true))

            rows.add(sec(d.name))
            rows.add(Row("", plan = d.plan))
            rows.add(Row("", plan = d.side))
            rows.add(Row("Jagya chahiye (lambai × pohlai)", fL(d.needL) + " × " + fL(d.needW)))
            if (d.maxW.isFinite()) rows.add(Row("Is jagya mein sabse chaudi seedhi", if (d.maxW > 0) fL(d.maxW, 1, sm) else "nahi banegi", d.maxW < rules.wMin))
            d.flights.forEachIndexed { i, r -> rows.add(Row("Flight " + (i + 1), r.toString() + " riser, " + (r - 1) + " tread  •  lambai " + fL((r - 1) * inp.ut))) }
            if (d.turnNote.isNotEmpty()) rows.add(Row("Vadank (turn)", d.turnNote))
            if (d.landingCount > 0) rows.add(Row("Landing (" + d.landingCount + ")", fL(d.landingLen) + " × " + fL(inp.sw)))
            rows.add(Row("Upar floor par jagya", fL(inp.sw) + " × " + fL(inp.sw)))
            d.code.forEach { rows.add(Row(it, "⚠", true)) }
            if (rules.highRise) rows.add(Row(if (d.highRiseOk) "High-rise: chalegi (seedhi flight, landing)" else "High-rise: nahi chalegi", if (d.highRiseOk) "✓" else "✗", !d.highRiseOk))

            // marking per flight
            rows.add(sec("Deewar par marking (har flight)"))
            var h0 = 0.0
            d.flights.forEachIndexed { i, r ->
                for (k in 1..r) rows.add(Row("F" + (i + 1) + " step " + k, "upar " + fL(h0 + k * inp.ur, 1, sm) + "   •   aage " + fL((k - 1) * inp.ut, 1, sm)))
                h0 += r * inp.ur
            }

            // RCC + steel for the whole design
            val waist = waistOf(d.flights, inp)
            var vol = 0.0
            var shutter = 0.0
            d.flights.forEach { r ->
                val lf = hypot(r * inp.ur, (r - 1) * inp.ut)
                vol += lf * waist * inp.sw + (r - 1) * (inp.ur * inp.ut / 2) * inp.sw
                shutter += lf * inp.sw
            }
            val landArea = (d.landingCount) * d.landingLen * inp.sw
            vol += landArea * waist
            shutter += landArea
            val dry = vol * 1.54
            rows.add(sec("RCC (concrete) — " + d.name.substringAfter(' ')))
            rows.add(Row("Waist slab motai", fL(waist, 1, sm)))
            rows.add(Row("Concrete", num(rnd(vol, 3)) + " m³  (" + num(rnd(vol / CFT, 1)) + " cft)"))
            rows.add(Row("M20 cement (1:1.5:3)", num(rnd(dry / 5.5 / BAG, 1)) + " bag"))
            rows.add(Row("Ret", num(rnd(dry * 1.5 / 5.5 / CFT, 1)) + " cft"))
            rows.add(Row("Kapchi (20 mm)", num(rnd(dry * 3 / 5.5 / CFT, 1)) + " cft"))
            rows.add(Row("Sariya (lagbhag 90 kg/m³)", num(rnd(vol * 90, 0)) + " kg"))
            rows.add(Row("Shuttering (neeche)", num(rnd(shutter, 2)) + " m²  (" + num(rnd(shutter / (FT * FT), 1)) + " sq ft)"))
            rows.add(sec("Steel (MS) — " + d.name.substringAfter(' ')))
            d.flights.forEachIndexed { i, r ->
                rows.add(Row("Flight " + (i + 1) + " stringer (channel)", fL(hypot(r * inp.ur, (r - 1) * inp.ut) + inp.ut) + " × 2"))
            }
            rows.add(Row("Tread plate", d.flights.sumOf { it - 1 }.toString() + " nag × " + fL(inp.sw, 1, sm)))
            rows
        }.apply { links = listOf("← Seedhi (ek flight, stringer layout)" to { stairKey() }) }
    )
}
