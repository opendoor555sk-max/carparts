package com.nirmaan.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Stair Planner: give the room (length × width) and the floor-to-floor height,
 * and see which of the 14 stair types fit, how each looks (top view + unfolded side view),
 * and all measurements (steps, landings, winder widths, marking, RCC, steel).
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
private const val WOOD_B = 0xFFC5E1A5.toInt()   // second stair of a scissor
private const val WINDER = 0xFFFFCC80.toInt()
private const val LANDING = 0xFFCFD8DC.toInt()
private const val ROOM = 0x00FFFFFF
private const val FLOOR = 0xFF9E9E9E.toInt()
private const val CONC = 0xFFBDBDBD.toInt()
private const val WALL = 0xFF616161.toInt()
private const val RED = 0xFFC62828.toInt()
private const val DARK = 0xFF222222.toInt()
private const val BLUE = 0xFF1565C0.toInt()

private fun rect(x0: Double, y0: Double, x1: Double, y1: Double) = listOf(P(x0, y0), P(x1, y0), P(x1, y1), P(x0, y1))

/** ring sector (r0 = 0 gives a pie slice) */
private fun sector(cx: Double, cy: Double, r0: Double, r1: Double, a0: Double, a1: Double): List<P> {
    val n = max(2, ceil(abs(a1 - a0) / 0.1).toInt())
    val out = ArrayList<P>()
    for (i in 0..n) { val a = a0 + (a1 - a0) * i / n; out.add(P(cx + r1 * cos(a), cy + r1 * sin(a))) }
    if (r0 <= 1e-9) out.add(P(cx, cy))
    else for (i in n downTo 0) { val a = a0 + (a1 - a0) * i / n; out.add(P(cx + r0 * cos(a), cy + r0 * sin(a))) }
    return out
}

private fun arc(cx: Double, cy: Double, r: Double, a0: Double, a1: Double): List<P> {
    val n = max(2, ceil(abs(a1 - a0) / 0.1).toInt())
    return (0..n).map { val a = a0 + (a1 - a0) * it / n; P(cx + r * cos(a), cy + r * sin(a)) }
}

private fun centroid(p: List<P>) = P(p.sumOf { it.x } / p.size, p.sumOf { it.y } / p.size)

// ================= building rules (approximate, NBC 2016 style) =================
private class Rules(val name: String, val rMax: Double, val tMin: Double, val wMin: Double, val maxPerFlight: Int, val highRise: Boolean)

private fun rulesFor(b: String) = when (b) {
    "Commercial" -> Rules(b, 0.15, 0.30, 1.5, 15, false)
    "High-rise" -> Rules(b, 0.15, 0.30, 1.5, 15, true)
    else -> Rules("Ghar", 0.19, 0.25, 1.0, 15, false)
}

// ================= a stair design =================
/** a run of risers: a straight flight, a group of winders or a curved flight */
private class Run(
    val risers: Int,
    val going: Double,          // tread going (on the walking line)
    val width: Double,
    val land: Double,           // flat length after the last riser (landing / floor); 0 = next run follows directly
    val winder: Boolean = false,
    val copies: Int = 1         // same flight built more than once (bifurcated sides, scissor)
) {
    val treads get() = if (land > 0) risers - 1 else risers
}

private class Design(
    val id: Int,
    val name: String,
    val runs: List<Run>,
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
    val landings: List<String>,
    val landArea: Double,
    val turnNote: String = "",
    val extra: List<Row> = emptyList(),
    val rcc: Boolean = true
)

private class Input(
    val L: Double, val W: Double, val H: Double, val sw: Double, val swGiven: Boolean,
    val n: Int, val ur: Double, val ut: Double, val rules: Rules, val well: Double
)

/** all stair types */
private val STAIR_TYPES = listOf(
    1 to "1 Straight",
    2 to "2 Straight + landing",
    3 to "3 L-shape",
    4 to "4 Double-L",
    5 to "5 Dog-legged",
    6 to "6 Open-well",
    7 to "7 Three-quarter",
    8 to "8 L-Winder",
    9 to "9 Two-quarter winder",
    10 to "10 U-Winder",
    11 to "11 Spiral",
    12 to "12 Helical (gol)",
    13 to "13 Bifurcated",
    14 to "14 Scissor"
)
private const val ALL_OPT = "★ Badhi sidi (PDF)"

/** preferred order when several fit */
private fun preference(b: String): List<Int> = when (b) {
    "High-rise" -> listOf(14, 6, 5, 7, 2, 4, 3, 1)
    "Commercial" -> listOf(6, 5, 7, 3, 4, 2, 13, 1, 12)
    else -> listOf(5, 3, 2, 6, 8, 4, 7, 10, 9, 1, 12, 11, 13)
}

// ---------- fit test ----------
private fun fitOf(inp: Input, needL: Double, needW: Double): Triple<Boolean?, Boolean, String> {
    if (!(inp.L > 0) || !(inp.W > 0)) return Triple(null, false, "jagya nahi di")
    if (needL <= inp.L + 1e-6 && needW <= inp.W + 1e-6) return Triple(true, false, "")
    if (needL <= inp.W + 1e-6 && needW <= inp.L + 1e-6) return Triple(true, true, "ghuma kar")
    return Triple(false, false, "")
}

/** how much the footprint sticks out of the room (0 = fits), best of both orientations */
private fun overflow(inp: Input, nL: Double, nW: Double): Double {
    if (!(inp.L > 0) || !(inp.W > 0)) return 0.0
    val o = min(max(0.0, nL - inp.L) + max(0.0, nW - inp.W), max(0.0, nL - inp.W) + max(0.0, nW - inp.L))
    return if (o < 1e-6) 0.0 else o      // tiny rounding differences count as a fit
}

private fun Engine.shortText(inp: Input, needL: Double, needW: Double): String {
    val a = max(0.0, needL - inp.L) to max(0.0, needW - inp.W)
    val b = max(0.0, needL - inp.W) to max(0.0, needW - inp.L)
    val (sl, sw2) = if (a.first + a.second <= b.first + b.second) a else b
    return listOfNotNull(if (sl > 1e-6) "lambai " + fL(sl) + " kam" else null, if (sw2 > 1e-6) "pohlai " + fL(sw2) + " kam" else null)
        .joinToString(", ").ifEmpty { "jagya kam" }
}

/** all ways to split n risers into k flights (each at least [minR]) */
private fun splits(n: Int, k: Int, minR: Int): List<List<Int>> {
    val out = ArrayList<List<Int>>()
    fun rec(left: Int, parts: List<Int>) {
        if (parts.size == k - 1) { if (left >= minR) out.add(parts + left); return }
        for (a in minR..left - minR * (k - 1 - parts.size)) rec(left - a, parts + a)
    }
    if (k >= 1 && n >= k * minR) rec(n, emptyList())
    return out
}

private class Pick(val f: List<Int>, val needL: Double, val needW: Double, val fits: Boolean?, val rotated: Boolean, val why: String, val maxW: Double)

/** chooses the riser split that fits the room, keeps flights within the code limit and as even as possible */
private fun Engine.pickSplit(
    inp: Input, cands0: List<List<Int>>, goings: (List<Int>) -> List<Double>,
    foot: (List<Double>, Double) -> Pair<Double, Double>,
    valid: (List<Double>, Double) -> Boolean = { _, _ -> true }
): Pick {
    val maxF = inp.rules.maxPerFlight
    val cands = if (cands0.size > 3000) cands0.sortedBy { f -> f.maxOf { it } - f.minOf { it } }.take(3000) else cands0
    class Ev(val f: List<Int>, val g: List<Double>, val nL: Double, val nW: Double, val ok: Boolean, val over: Double)
    val evs = cands.map { f ->
        val g = goings(f)
        val (a, b) = foot(g, inp.sw)
        Ev(f, g, a, b, valid(g, inp.sw), overflow(inp, a, b))
    }
    val best = evs.minWithOrNull(compareBy<Ev>(
        { if (it.ok) 0 else 1 },
        { if (it.over > 0) 1 else 0 },
        { it.over },
        { e -> e.f.count { it > maxF } },
        { e -> e.f.maxOf { it } - e.f.minOf { it } }
    ))!!
    var (fits, rotated, why) = fitOf(inp, best.nL, best.nW)
    if (fits == false) why = shortText(inp, best.nL, best.nW)
    if (!best.ok) { fits = false; why = "flight ek-bija par chadhe" }
    val maxW = if (inp.L > 0 && inp.W > 0) {
        var lo = 0.0
        var hi = max(inp.L, inp.W)
        repeat(24) {
            val mid = (lo + hi) / 2
            if (evs.any { e -> valid(e.g, mid) && foot(e.g, mid).let { overflow(inp, it.first, it.second) } <= 0.0 }) lo = mid else hi = mid
        }
        lo
    } else Double.NaN
    return Pick(best.f, best.nL, best.nW, fits, rotated, why, maxW)
}

private fun Engine.roomShape(inp: Input, rotated: Boolean, x0: Double, y0: Double, shapes: MutableList<Shape>, labels: MutableList<Label>) {
    if (!(inp.L > 0 && inp.W > 0)) return
    val rl = if (rotated) inp.W else inp.L
    val rw = if (rotated) inp.L else inp.W
    shapes.add(0, Shape(rect(x0, y0, x0 + rl, y0 + rw), ROOM, 0xFF607D8B.toInt(), dashed = true))
    labels.add(Label(P(x0 + rl / 2, y0 + rw + inp.sw * 0.15), "Jagya " + fL(rl) + " × " + fL(rw), 9.5f, 0xFF455A64.toInt()))
}

// ================= winders =================
private fun cross(ax: Double, ay: Double, bx: Double, by: Double) = ax * by - ay * bx

/** keeps the part of [poly] on the left (sign 1) or right (sign -1) of the line through (ox,oy) with direction (dx,dy) */
private fun clipHalf(poly: List<P>, ox: Double, oy: Double, dx: Double, dy: Double, sign: Double): List<P> {
    if (poly.isEmpty()) return poly
    fun side(p: P) = sign * cross(dx, dy, p.x - ox, p.y - oy)
    val out = ArrayList<P>()
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val sa = side(a)
        val sb = side(b)
        val ina = sa >= -1e-12
        val inb = sb >= -1e-12
        if (ina) out.add(a)
        if (ina != inb) {
            val t = sa / (sa - sb)
            out.add(P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
        }
    }
    return out
}

private class Fan(
    val polys: List<List<P>>, val walk: Double, val inner: Double, val outer: List<Double>,
    val arc: List<P>, val angle: Double, val rWalk: Double
)

/** splits [region] into [w] winder treads fanning from (ox,oy), from angle a0 turning left by [sweep] */
private fun fan(region: List<P>, ox: Double, oy: Double, a0: Double, sweep: Double, w: Int, rWalk: Double, rIn: Double): Fan {
    val polys = ArrayList<List<P>>()
    val outer = ArrayList<Double>()
    val da = sweep / w
    for (k in 0 until w) {
        val aa = a0 + k * da
        val ab = aa + da
        var p = clipHalf(region, ox, oy, cos(aa), sin(aa), 1.0)
        p = clipHalf(p, ox, oy, cos(ab), sin(ab), -1.0)
        polys.add(p)
        fun onRay(q: P, a: Double) = abs(cross(cos(a), sin(a), q.x - ox, q.y - oy)) < 1e-7 &&
            cos(a) * (q.x - ox) + sin(a) * (q.y - oy) > -1e-7
        var per = 0.0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            if ((onRay(a, aa) && onRay(b, aa)) || (onRay(a, ab) && onRay(b, ab))) continue
            per += hypot(b.x - a.x, b.y - a.y)
        }
        outer.add(per)
    }
    return Fan(polys, rWalk * da, rIn * da, outer, arc(ox, oy, rWalk, a0, a0 + sweep), da * 180 / PI, rWalk)
}

private fun winderCount(sweep: Double, inp: Input): Int {
    val rw = min(0.45, inp.sw / 2)
    val w = (sweep * rw / inp.ut).roundToInt()
    return if (sweep > 2) w.coerceIn(4, 8) else w.coerceIn(2, 4)
}

// ================= turning stairs: flights + landings / winders =================
private typealias Dir = Pair<Int, Int>
private val EAST: Dir = 1 to 0
private val NORTH: Dir = 0 to 1
private val WEST: Dir = -1 to 0
private val SOUTH: Dir = 0 to -1

private fun isU(a: Dir, b: Dir) = a.first == -b.first && a.second == -b.second

private class Layout(
    val shapes: List<Shape>, val labels: List<Label>, val arrow: List<P>,
    val x0: Double, val y0: Double, val x1: Double, val y1: Double, val fans: List<Fan>
)

/**
 * Lays the flights out along [dirs]. After each flight comes a landing (corners[i] = 0)
 * or corners[i] winders; the last flight ends on the upper floor. Turns are to the left.
 */
private fun Engine.layoutTurns(flights: List<Int>, dirs: List<Dir>, corners: List<Int>, inp: Input, gap: Double): Layout {
    val sw = inp.sw
    val ut = inp.ut
    val sm = if (metric) "cm" else "in"
    val shapes = ArrayList<Shape>()
    val labels = ArrayList<Label>()
    val arrow = ArrayList<P>()
    val fans = ArrayList<Fan>()
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
        val lvx = -rx                          // left-hand side
        val lvy = -ry
        val last = i == flights.size - 1
        val c = if (last) 0 else corners[i]
        val nd = if (last) null else dirs[i + 1]
        val uturn = nd != null && isU(d, nd)
        if (i == 0) arrow.add(P(cx + d.first * ut * 0.3, cy + d.second * ut * 0.3))
        val sx = cx
        val sy = cy
        val nt = if (c > 0) r else r - 1
        for (t in 1..nt) {
            shapes.add(Shape(piece(cx, cy, d, ut), WOOD))
            labels.add(Label(P(cx + d.first * ut / 2 + rx * sw * 0.28, cy + d.second * ut / 2 + ry * sw * 0.28), step.toString(), 8.5f, DARK))
            cx += d.first * ut; cy += d.second * ut; step++
        }
        val vertical = d.first == 0
        labels.add(Label(P((sx + cx) / 2 + rx * sw * 0.64, (sy + cy) / 2 + ry * sw * 0.64),
            "F" + (i + 1) + ": " + r + " riser", 9f, BLUE, 1, if (vertical) -90f else 0f))
        if (c > 0 && nd != null) {
            // winders fanning from the inner corner (90°) or from the middle between the flights (180°)
            val sweep = if (uturn) PI else PI / 2
            val half = if (uturn) gap / 2 else 0.0
            val ox = cx + lvx * (sw / 2 + half)
            val oy = cy + lvy * (sw / 2 + half)
            val region = if (uturn) {
                val ax = cx - lvx * sw / 2; val ay = cy - lvy * sw / 2
                val bx = cx + lvx * (sw + gap + sw / 2); val by = cy + lvy * (sw + gap + sw / 2)
                listOf(P(ax, ay), P(ax + d.first * sw, ay + d.second * sw), P(bx + d.first * sw, by + d.second * sw), P(bx, by))
            } else piece(cx, cy, d, sw)
            val a0 = atan2(-lvy, -lvx)
            val fn = fan(region, ox, oy, a0, sweep, c, half + min(0.45, sw / 2), half + 0.15)
            fans.add(fn)
            for (p in fn.polys) {
                if (p.size < 3) continue
                shapes.add(Shape(p, WINDER))
                val m = centroid(p)
                labels.add(Label(P(m.x + (m.x - ox) * 0.15, m.y + (m.y - oy) * 0.15), step.toString(), 8.5f, DARK))
                step++
            }
            arrow.addAll(fn.arc)
            if (uturn) { cx += lvx * (sw + gap); cy += lvy * (sw + gap) }
            else {
                val lx = cx + d.first * sw / 2
                val ly = cy + d.second * sw / 2
                cx = lx + nd.first * sw / 2; cy = ly + nd.second * sw / 2
            }
            return@forEachIndexed
        }
        if (uturn) {
            // half turn (180°): one wide landing across both flights and the well
            val off = sw + gap
            val ax = cx - lvx * sw / 2; val ay = cy - lvy * sw / 2
            val bx = cx + lvx * (off + sw / 2); val by = cy + lvy * (off + sw / 2)
            shapes.add(Shape(listOf(P(ax, ay), P(bx, by), P(bx + d.first * sw, by + d.second * sw), P(ax + d.first * sw, ay + d.second * sw)), LANDING))
            val mx = cx + d.first * sw / 2
            val my = cy + d.second * sw / 2
            arrow.add(P(mx, my)); arrow.add(P(mx + lvx * off, my + lvy * off))
            val clx = mx + lvx * off / 2; val cly = my + lvy * off / 2
            labels.add(Label(P(clx, cly + sw * 0.12), "Landing", 9f, DARK))
            labels.add(Label(P(clx, cly - sw * 0.16), fL(sw, 1, sm) + " × " + fL(2 * sw + gap, 1, sm), 8f, BLUE))
            if (gap > 1e-6) labels.add(Label(P(cx - d.first * ut * 1.5 + lvx * (sw / 2 + gap / 2), cy - d.second * ut * 1.5 + lvy * (sw / 2 + gap / 2)),
                "well " + fL(gap, 1, sm), 8f, RED, 1, if (d.first == 0) -90f else 0f))
            step++
            cx += lvx * off; cy += lvy * off
            return@forEachIndexed
        }
        shapes.add(Shape(piece(cx, cy, d, sw), if (last) FLOOR else LANDING))
        val lx = cx + d.first * sw / 2
        val ly = cy + d.second * sw / 2
        arrow.add(P(lx, ly))
        labels.add(Label(P(lx, ly + sw * 0.12), if (last) "Upar floor" else "Landing", 9f, DARK))
        labels.add(Label(P(lx, ly - sw * 0.16), fL(sw, 1, sm) + " × " + fL(sw, 1, sm), 8f, BLUE))
        step++
        if (nd != null) {
            if (nd == d) { cx += d.first * sw; cy += d.second * sw }
            else { cx = lx + nd.first * sw / 2; cy = ly + nd.second * sw / 2 }
        }
    }
    val pts = shapes.flatMap { it.pts }
    return Layout(shapes, labels, arrow, pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y }, fans)
}

private class TurnSpec(
    val k: Int, val dirs: List<Dir>, val corners: List<Int>, val gap: Double, val note: String,
    val foot: (List<Double>, Double) -> Pair<Double, Double>,
    val valid: (List<Double>, Double) -> Boolean = { _, _ -> true }
)

private fun Engine.turnDesign(id: Int, name: String, inp: Input, s: TurnSpec): Design {
    val ut = inp.ut
    val sw = inp.sw
    val k = s.k
    val sm = if (metric) "cm" else "in"
    val wSum = s.corners.sum()
    val nf = inp.n - wSum
    val tooFew = nf < 2 * k
    var cands = splits(nf, k, if (tooFew) 1 else 2)
    if (cands.isEmpty()) cands = listOf(List(k) { if (it == 0) max(1, nf - (k - 1)) else 1 })
    fun treadsOf(i: Int, r: Int) = max(0, if (i == k - 1 || s.corners[i] == 0) r - 1 else r)
    val pk = pickSplit(inp, cands, { f -> f.mapIndexed { i, r -> treadsOf(i, r) * ut } }, s.foot, s.valid)
    val fits = if (tooFew) false else pk.fits
    val why = if (tooFew) "oonchai bahu ochhi" else pk.why
    val maxF = inp.rules.maxPerFlight
    val code = ArrayList<String>()
    pk.f.forEachIndexed { i, r -> if (r > maxF) code.add("Flight " + (i + 1) + ": " + r + " riser (max " + maxF + ") — landing chahiye") }
    if (wSum > 0 && inp.rules.name != "Ghar") code.add("Winder: " + inp.rules.name + " ma na chale (exit seedhi ma manai)")

    val lay = layoutTurns(pk.f, s.dirs, s.corners, inp, s.gap)
    val runs = ArrayList<Run>()
    val landings = ArrayList<String>()
    var landArea = 0.0
    val extra = ArrayList<Row>()
    var wi = 0
    pk.f.forEachIndexed { i, r ->
        val last = i == k - 1
        val c = if (last) 0 else s.corners[i]
        runs.add(Run(r, ut, sw, if (last || c == 0) sw else 0.0))
        if (!last && c == 0) {
            if (isU(s.dirs[i], s.dirs[i + 1])) { landings.add(fL(2 * sw + s.gap) + " × " + fL(sw)); landArea += sw * (2 * sw + s.gap) }
            else { landings.add(fL(sw) + " × " + fL(sw)); landArea += sw * sw }
        }
        if (c > 0) {
            val fn = lay.fans[wi++]
            runs.add(Run(c, fn.walk, sw, 0.0, winder = true))
            val small = fn.walk < min(ut, inp.rules.tMin) - 0.03
            extra.add(sec("Winder " + wi + " — pagthiya ni pohlai"))
            extra.add(Row("Winder pagthiya", c.toString() + " nag × " + f2(fn.angle) + "°"))
            extra.add(Row("Andar (kinaar thi 15 cm par)", fL(fn.inner, 1, sm), fn.inner < 0.075))
            extra.add(Row("Chalvani line (" + fL(fn.rWalk, 1, sm) + " par)", fL(fn.walk, 1, sm), small))
            extra.add(Row("Bahar (deewar paase)", fL(fn.outer.minOf { it }, 1, sm) + " – " + fL(fn.outer.maxOf { it }, 1, sm)))
            if (small) code.add("Winder " + wi + ": chalvani line par pag nano (" + fL(fn.walk, 1, sm) + ")")
        }
    }
    val shapes = ArrayList<Shape>(lay.shapes)
    val labels = ArrayList<Label>(lay.labels)
    roomShape(inp, pk.rotated, lay.x0, lay.y0, shapes, labels)
    val plan = PlanDrawing(
        "Upar se (plan) — " + name.substringAfter(' '), shapes, labels, listOf(lay.arrow),
        listOf(
            "Chahiye: " + fL(pk.needL) + " × " + fL(pk.needW) + if (pk.rotated) "  (ghuma kar)" else "",
            "Pohlai " + fL(sw, 1, sm) + "  •  Tread " + fL(ut, 1, sm) + "  •  " + s.note,
            if (k > 1) "Ulti baju (jamne vadank) pan bane — drawing no aaino (mirror)" else "Laal teer = chadvani disha"
        )
    )
    return Design(id, name, runs, pk.needL, pk.needW, fits, pk.rotated, why, pk.maxW, code,
        highRiseOk = pk.f.all { it <= maxF } && wSum == 0, plan = plan, side = sideView(runs, inp),
        landings = landings, landArea = landArea, turnNote = s.note, extra = extra)
}

// ================= bifurcated (13) =================
private fun Engine.bifurcatedDesign(id: Int, name: String, inp: Input): Design {
    val sw = inp.sw
    val ut = inp.ut
    val sm = if (metric) "cm" else "in"
    val tooFew = inp.n < 4
    var cands = splits(inp.n, 2, if (tooFew) 1 else 2)
    if (cands.isEmpty()) cands = listOf(listOf(max(1, inp.n - 1), 1))
    val pk = pickSplit(inp, cands, { f -> f.map { max(0, it - 1) * ut } }, { g, s -> max(g[0] + s, g[1] + 2 * s) to (4 * s) })
    val r1 = pk.f[0]
    val r2 = pk.f[1]
    val g1 = max(0, r1 - 1) * ut
    val g2 = max(0, r2 - 1) * ut
    val shapes = ArrayList<Shape>()
    val labels = ArrayList<Label>()
    // wide middle flight (2 × width) in the middle band
    for (t in 0 until r1 - 1) {
        val x = t * ut
        shapes.add(Shape(rect(x, sw, x + ut, 3 * sw), WOOD))
        labels.add(Label(P(x + ut / 2, 1.5 * sw), (t + 1).toString(), 8.5f, DARK))
    }
    labels.add(Label(P(g1 / 2, 2.55 * sw), "F1: $r1 riser (pohlai " + fL(2 * sw, 1, sm) + ")", 9f, BLUE))
    // landing across the full width
    shapes.add(Shape(rect(g1, 0.0, g1 + sw, 4 * sw), LANDING))
    labels.add(Label(P(g1 + sw / 2, 2.1 * sw), "Landing", 9f, DARK))
    labels.add(Label(P(g1 + sw / 2, 1.8 * sw), fL(4 * sw, 1, sm) + " × " + fL(sw, 1, sm), 8f, BLUE))
    // two side flights coming back
    for (band in listOf(0.0, 3 * sw)) {
        for (t in 0 until r2 - 1) {
            val x = g1 - (t + 1) * ut
            shapes.add(Shape(rect(x, band, x + ut, band + sw), WOOD))
            labels.add(Label(P(x + ut / 2, band + sw * 0.25), (r1 + 1 + t).toString(), 8.5f, DARK))
        }
        shapes.add(Shape(rect(g1 - g2 - sw, band, g1 - g2, band + sw), FLOOR))
        labels.add(Label(P(g1 - g2 - sw / 2, band + sw / 2), "Upar floor", 8.5f, DARK))
        labels.add(Label(P(g1 - g2 / 2, band + sw * 0.72), "F2: $r2 riser", 9f, BLUE))
    }
    val arrows = listOf(
        listOf(P(ut * 0.3, 2 * sw), P(g1 + sw / 2, 2 * sw), P(g1 + sw / 2, sw / 2), P(g1 - g2 - sw / 2, sw / 2)),
        listOf(P(g1 + sw / 2, 2 * sw), P(g1 + sw / 2, 3.5 * sw), P(g1 - g2 - sw / 2, 3.5 * sw))
    )
    val pts = shapes.flatMap { it.pts }
    roomShape(inp, pk.rotated, pts.minOf { it.x }, pts.minOf { it.y }, shapes, labels)
    val maxF = inp.rules.maxPerFlight
    val code = ArrayList<String>()
    pk.f.forEachIndexed { i, r -> if (r > maxF) code.add("Flight " + (i + 1) + ": " + r + " riser (max " + maxF + ")") }
    val runs = listOf(Run(r1, ut, 2 * sw, sw), Run(r2, ut, sw, sw, copies = 2))
    val plan = PlanDrawing("Upar se (plan) — Bifurcated", shapes, labels, arrows, listOf(
        "Chahiye: " + fL(pk.needL) + " × " + fL(pk.needW) + if (pk.rotated) "  (ghuma kar)" else "",
        "Vachche paholi flight, landing pachhi daabi-jamni 2 flight",
        "Mota bungalow / hotel lobby mate — fire exit mate nahi"))
    return Design(id, name, runs, pk.needL, pk.needW, if (tooFew) false else pk.fits, pk.rotated, if (tooFew) "oonchai bahu ochhi" else pk.why,
        pk.maxW, code, highRiseOk = false, plan = plan, side = sideView(runs, inp),
        landings = listOf(fL(4 * sw) + " × " + fL(sw)), landArea = 4 * sw * sw,
        turnNote = "Landing par 2 baju vahechay (180°)",
        extra = listOf(Row("Vachchena flight ni pohlai", fL(2 * sw, 1, sm)), Row("Baju na 2 flight ni pohlai", fL(sw, 1, sm) + " darek")))
}

// ================= scissor (14) =================
private fun Engine.scissorDesign(id: Int, name: String, inp: Input): Design {
    val sw = inp.sw
    val ut = inp.ut
    val sm = if (metric) "cm" else "in"
    val wall = if (metric) 0.20 else 8 * IN
    val maxF = inp.rules.maxPerFlight
    var cands = if (inp.n <= maxF) listOf(listOf(inp.n)) else splits(inp.n, 2, 2)
    if (cands.isEmpty()) cands = listOf(listOf(inp.n))
    val pk = pickSplit(inp, cands, { f -> f.map { max(0, it - 1) * ut } }, { g, s -> (g.sum() + (g.size + 1) * s) to (2 * s + wall) })
    val fl = pk.f
    val span = fl.sumOf { max(0, it - 1) * ut } + (fl.size - 1) * sw
    val x1 = sw + span
    val bB = sw + wall
    val shapes = ArrayList<Shape>()
    val labels = ArrayList<Label>()
    shapes.add(Shape(rect(0.0, 0.0, sw, 2 * sw + wall), FLOOR))
    shapes.add(Shape(rect(x1, 0.0, x1 + sw, 2 * sw + wall), FLOOR))
    labels.add(Label(P(sw / 2, sw + wall / 2), "Floor", 8.5f, DARK, 1, -90f))
    labels.add(Label(P(x1 + sw / 2, sw + wall / 2), "Floor", 8.5f, DARK, 1, -90f))
    shapes.add(Shape(rect(sw, sw, x1, sw + wall), WALL))
    labels.add(Label(P(sw + span / 2, sw + wall / 2), "fire wall", 7.5f, 0xFFFFFFFF.toInt()))
    // stair A goes one way, stair B the other way beside it
    for (b in 0..1) {
        var x = if (b == 0) sw else x1
        val dir = if (b == 0) 1 else -1
        val y0 = if (b == 0) 0.0 else bB
        var step = 1
        fl.forEachIndexed { fi, r ->
            for (t in 1 until r) {
                val xa = if (dir > 0) x else x - ut
                shapes.add(Shape(rect(xa, y0, xa + ut, y0 + sw), if (b == 0) WOOD else WOOD_B))
                labels.add(Label(P(xa + ut / 2, y0 + sw * 0.25), step.toString(), 8.5f, DARK))
                x += dir * ut; step++
            }
            if (fi < fl.size - 1) {
                val xa = if (dir > 0) x else x - sw
                shapes.add(Shape(rect(xa, y0, xa + sw, y0 + sw), LANDING))
                labels.add(Label(P(xa + sw / 2, y0 + sw / 2), "Landing", 8.5f, DARK))
                x += dir * sw; step++
            }
        }
        labels.add(Label(P(sw + span / 2, y0 + sw * 0.72), (if (b == 0) "Seedhi A → " else "← Seedhi B ") + fl.joinToString(" + ") + " riser", 9f, BLUE))
    }
    val arrows = listOf(
        listOf(P(sw + ut * 0.3, sw / 2), P(x1 + sw / 2, sw / 2)),
        listOf(P(x1 - ut * 0.3, bB + sw / 2), P(sw / 2, bB + sw / 2))
    )
    val pts = shapes.flatMap { it.pts }
    roomShape(inp, pk.rotated, pts.minOf { it.x }, pts.minOf { it.y }, shapes, labels)
    val code = ArrayList<String>()
    fl.forEachIndexed { i, r -> if (r > maxF) code.add("Flight " + (i + 1) + ": " + r + " riser (max " + maxF + ")") }
    val runs = fl.map { Run(it, ut, sw, sw, copies = 2) }
    val nl = (fl.size - 1) * 2
    val plan = PlanDrawing("Upar se (plan) — Scissor (2 seedhi)", shapes, labels, arrows, listOf(
        "Chahiye: " + fL(pk.needL) + " × " + fL(pk.needW) + if (pk.rotated) "  (ghuma kar)" else "",
        "2 alag seedhi ek j jagya ma — vachche fire wall " + fL(wall, 1, sm),
        "High-rise mate: 2 alag bahar javana raste (fire escape)"))
    return Design(id, name, runs, pk.needL, pk.needW, pk.fits, pk.rotated, pk.why, pk.maxW, code,
        highRiseOk = fl.all { it <= maxF }, plan = plan, side = sideView(runs, inp),
        landings = List(nl) { fL(sw) + " × " + fL(sw) }, landArea = nl * sw * sw,
        turnNote = "2 seedhi, ulti disha ma, vachche deewar",
        extra = listOf(Row("Fire wall (be seedhi vachche)", fL(wall, 1, sm)), Row("Alag bahar javana raste", "2  ✓")))
}

// ================= spiral (11) =================
private fun Engine.spiralDesign(id: Int, name: String, inp: Input): Design {
    val sm = if (metric) "cm" else "in"
    val pr = if (metric) 0.075 else 3 * IN              // centre pole radius (6" / 15 cm pipe)
    val sw = if (inp.swGiven) inp.sw else if (metric) 0.76 else 30 * IN
    val R = pr + sw
    val rw = pr + min(0.45, sw / 2)
    val th = inp.ut / rw
    val t = max(0, inp.n - 1)
    val landA = PI / 2
    val total = t * th + landA
    val D = 2 * R
    var (fits, rotated, why) = fitOf(inp, D, D)
    if (fits == false) why = "gol mate " + fL(D) + " × " + fL(D) + " joie"
    val perTurn = 2 * PI / th * inp.ur
    val code = ArrayList<String>()
    if (inp.rules.name != "Ghar") code.add("Spiral: " + inp.rules.name + " ma exit seedhi tarike na chale")
    if (perTurn < 2.0) code.add("Sir takrase: ek pheri ma oonchai " + fL(perTurn) + " (" + (if (metric) "2.0 m" else "6'6\"") + " joie)")
    if (sw < 0.66) code.add("Spiral ni pohlai " + (if (metric) "66 cm" else "26\"") + " thi ochhi")
    // plan: centre at (R, R)
    val cx = R
    val cy = R
    val a0 = -PI / 2
    val shapes = ArrayList<Shape>()
    val labels = ArrayList<Label>()
    shapes.add(Shape(sector(cx, cy, 0.0, R, 0.0, 2 * PI), 0x00FFFFFF, 0xFF90A4AE.toInt(), dashed = true))
    for (k in 0 until t) {
        val a = a0 + k * th
        shapes.add(Shape(sector(cx, cy, pr, R, a, a + th), WOOD))
        if (t * th - k * th <= 2 * PI) {          // numbers only for the treads seen from above
            val am = a + th / 2
            labels.add(Label(P(cx + (R * 0.72) * cos(am), cy + (R * 0.72) * sin(am)), (k + 1).toString(), 8.5f, DARK))
        }
    }
    val la = a0 + t * th
    shapes.add(Shape(sector(cx, cy, pr, R, la, la + landA), FLOOR))
    labels.add(Label(P(cx + R * 0.6 * cos(la + landA / 2), cy + R * 0.6 * sin(la + landA / 2)), "Upar floor", 8.5f, 0xFFFFFFFF.toInt()))
    shapes.add(Shape(sector(cx, cy, 0.0, pr, 0.0, 2 * PI), DARK))
    labels.add(Label(P(cx, cy - R - sw * 0.15), "Gol " + fL(D) + "  •  pole Ø " + fL(2 * pr, 1, sm), 9f, BLUE))
    val arrowEnd = a0 + min(t * th + landA / 2, 2 * PI * 0.92)
    val arrows = listOf(arc(cx, cy, rw, a0 + th * 0.3, arrowEnd))
    roomShape(inp, rotated, 0.0, 0.0, shapes, labels)
    val runs = listOf(Run(inp.n, inp.ut, sw, sw))
    val deg = th * 180 / PI
    val plan = PlanDrawing("Upar se (plan) — Spiral (gol ghumti)", shapes, labels, arrows, listOf(
        "Chahiye: " + fL(D) + " × " + fL(D) + "  •  pohlai " + fL(sw, 1, sm),
        "Pag no khuno " + f2(deg) + "°  •  kul ghumav " + f2(total * 180 / PI) + "°",
        "Neecha pagthiya upar na pagthiya neeche dhankay"))
    val extra = listOf(
        sec("Spiral na maap"),
        Row("Gol (diameter)", fL(D)),
        Row("Vachche pole (pipe)", "Ø " + fL(2 * pr, 1, sm) + "  •  lambai " + fL(inp.H + 0.9)),
        Row("Pag no khuno (angle)", f2(deg) + "°  •  ek pheri ma " + num(rnd(2 * PI / th, 1)) + " pag"),
        Row("Kul ghumav", f2(total * 180 / PI) + "°  (" + num(rnd(total / (2 * PI), 2)) + " pheri)"),
        Row("Pag ni pohlai — pole paase", fL(pr * th, 1, sm)),
        Row("Pag — chalvani line (" + fL(rw - pr, 1, sm) + " andar thi)", fL(inp.ut, 1, sm)),
        Row("Pag — bahar ni kinaar", fL(R * th, 1, sm)),
        Row("Ek pheri ma oonchai (sir mate)", fL(perTurn), perTurn < 2.0),
        sec("Steel (MS) — Spiral"),
        Row("Tread plate", t.toString() + " nag  •  lambai " + fL(sw, 1, sm) + ", bahar pohlai " + fL(R * th, 1, sm)),
        Row("Railing (bahar, lagbhag)", fL(hypot(total * R, inp.H)))
    )
    return Design(id, name, runs, D, D, fits, rotated, why,
        if (inp.L > 0 && inp.W > 0) min(inp.L, inp.W) / 2 - pr else Double.NaN, code,
        highRiseOk = false, plan = plan, side = sideView(runs, inp), landings = emptyList(), landArea = 0.0,
        turnNote = "Gol ghumti, vachche pole", extra = extra, rcc = false)
}

// ================= helical / curved (12) =================
private class Helix(val ri: Double, val rw: Double, val th: Double, val sweep: Double, val x0: Double, val y0: Double, val x1: Double, val y1: Double)

private fun helix(inp: Input, ri: Double, s: Double): Helix {
    val rw = ri + min(0.45, s / 2)
    val th = inp.ut / rw
    val sweep = max(0, inp.n - 1) * th + s / rw
    val ro = ri + s
    if (sweep >= 2 * PI) return Helix(ri, rw, th, sweep, -ro, -ro, ro, ro)
    var x0 = 1e9; var y0 = 1e9; var x1 = -1e9; var y1 = -1e9
    val steps = max(8, ceil(sweep / 0.05).toInt())
    for (i in 0..steps) {
        val a = -PI / 2 + sweep * i / steps
        for (r in doubleArrayOf(ri, ro)) {
            val x = r * cos(a); val y = r * sin(a)
            x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
        }
    }
    return Helix(ri, rw, th, sweep, x0, y0, x1, y1)
}

private fun Engine.helicalDesign(id: Int, name: String, inp: Input): Design {
    val sm = if (metric) "cm" else "in"
    val sw = inp.sw
    val room = inp.L > 0 && inp.W > 0
    fun over(h: Helix) = overflow(inp, h.x1 - h.x0, h.y1 - h.y0)
    val hx = if (room) {
        val all = (0..114).map { helix(inp, 0.3 + it * 0.05, sw) }
        all.filter { over(it) <= 0.0 }.maxByOrNull { it.ri } ?: all.minByOrNull { over(it) }!!
    } else helix(inp, 1.0, sw)
    val needL = hx.x1 - hx.x0
    val needW = hx.y1 - hx.y0
    var (fits, rotated, why) = fitOf(inp, needL, needW)
    if (fits == false) why = shortText(inp, needL, needW)
    if (inp.n < 2) { fits = false; why = "oonchai bahu ochhi" }
    val maxW = if (room) {
        var lo = 0.0
        var hi = max(inp.L, inp.W)
        repeat(20) {
            val mid = (lo + hi) / 2
            if ((0..57).any { over(helix(inp, 0.3 + it * 0.1, mid)) <= 0.0 }) lo = mid else hi = mid
        }
        lo
    } else Double.NaN
    val ri = hx.ri
    val ro = ri + sw
    val th = hx.th
    val t = max(0, inp.n - 1)
    val a0 = -PI / 2
    val shapes = ArrayList<Shape>()
    val labels = ArrayList<Label>()
    for (k in 0 until t) {
        val a = a0 + k * th
        shapes.add(Shape(sector(0.0, 0.0, ri, ro, a, a + th), WOOD))
        if (t * th - k * th <= 2 * PI) {
            val am = a + th / 2
            val rr = ri + sw * 0.72
            labels.add(Label(P(rr * cos(am), rr * sin(am)), (k + 1).toString(), 8.5f, DARK))
        }
    }
    val la = a0 + t * th
    shapes.add(Shape(sector(0.0, 0.0, ri, ro, la, la + sw / hx.rw), FLOOR))
    labels.add(Label(P(0.0, 0.0), "+ kendra", 8f, 0xFF607D8B.toInt()))
    labels.add(Label(P(0.0, -ri * 0.45), "andar R " + fL(ri), 8.5f, BLUE))
    val arrows = listOf(arc(0.0, 0.0, hx.rw, a0 + th * 0.3, a0 + min(t * th + sw / hx.rw / 2, 2 * PI * 0.92)))
    roomShape(inp, rotated, hx.x0, hx.y0, shapes, labels)
    val maxF = inp.rules.maxPerFlight
    val code = ArrayList<String>()
    if (inp.n > maxF) code.add("Ek j gol flight ma " + inp.n + " riser (max " + maxF + ")")
    val innerG = ri * th
    if (innerG < 0.15) code.add("Andar ni kinaar par pag nano (" + fL(innerG, 1, sm) + ")")
    val perTurn = 2 * PI / th * inp.ur
    if (hx.sweep > 2 * PI && perTurn < 2.0) code.add("Sir takrase: ek pheri ma oonchai " + fL(perTurn))
    val runs = listOf(Run(inp.n, inp.ut, sw, sw))
    val plan = PlanDrawing("Upar se (plan) — Helical / gol (vachche khullu)", shapes, labels, arrows, listOf(
        "Chahiye: " + fL(needL) + " × " + fL(needW) + if (rotated) "  (ghuma kar)" else "",
        "Andar R " + fL(ri) + "  •  bahar R " + fL(ro) + "  •  kul ghumav " + f2(hx.sweep * 180 / PI) + "°",
        "Pohlai " + fL(sw, 1, sm) + "  •  vachche pole nathi"))
    val extra = listOf(
        sec("Gol seedhi na maap"),
        Row("Andar ni trijya (radius)", fL(ri)),
        Row("Bahar ni trijya (radius)", fL(ro)),
        Row("Kul ghumav", f2(hx.sweep * 180 / PI) + "°"),
        Row("Pag no khuno", f2(th * 180 / PI) + "°"),
        Row("Pag ni pohlai — andar ni kinaar", fL(innerG, 1, sm), innerG < 0.15),
        Row("Pag — chalvani line (" + fL(hx.rw - ri, 1, sm) + " andar thi)", fL(inp.ut, 1, sm)),
        Row("Pag — bahar ni kinaar", fL(ro * th, 1, sm))
    )
    return Design(id, name, runs, needL, needW, fits, rotated, why, maxW, code,
        highRiseOk = false, plan = plan, side = sideView(runs, inp), landings = emptyList(), landArea = 0.0,
        turnNote = "Gol vadank, vachche khali (pole nathi)", extra = extra)
}

// ---------- unfolded side view (works for every type) ----------
private fun Engine.sideView(runs: List<Run>, inp: Input): PlanDrawing {
    val ur = inp.ur
    val waist = waistOf(runs, inp)
    val top = ArrayList<P>()
    val under = ArrayList<P>()
    val labels = ArrayList<Label>()
    val g0 = runs.first().going
    var x = 0.0
    var y = 0.0
    top.add(P(-g0 * 0.8, 0.0)); top.add(P(0.0, 0.0))
    var fi = 0
    runs.forEachIndexed { ri, run ->
        val fx = x
        val fy = y
        val g = max(run.going, 1e-6)
        val off = waist / cos(atan(ur / g))
        for (i in 1..run.risers) {
            y += ur; top.add(P(x, y))
            if (i <= run.treads) { x += g; top.add(P(x, y)) }
        }
        val xr = if (run.land > 0) x else x - g
        under.add(P(fx, max(fy + ur - off, fy - waist)))
        under.add(P(xr, y - off))
        fi += if (run.winder) 0 else 1
        val tag = if (run.winder) "W" else "F$fi"
        labels.add(Label(P((fx + xr) / 2 - g, (fy + y) / 2 + ur * 1.5), tag + ": " + run.risers + " × " + fL(ur, 1, if (metric) "cm" else "in"), 9f, BLUE, 2))
        val last = ri == runs.size - 1
        if (run.land > 0) {
            val ll = if (last) run.land * 0.8 else run.land
            under.add(P(x, y - waist))
            x += ll; top.add(P(x, y))
            if (!last) under.add(P(x, y - waist))
            labels.add(Label(P(x - ll / 2, y + ur * 0.4), (if (last) "Upar floor " else "Landing ") + fL(y), 8.5f, DARK))
        }
    }
    val poly = ArrayList<P>(top)
    poly.add(P(x, y - waist))
    for (i in under.indices.reversed()) poly.add(under[i])
    poly.add(P(0.0, -waist))
    poly.add(P(-g0 * 0.8, -waist))
    val shapes = listOf(
        Shape(rect(-g0 * 1.2, -waist * 2.2, x + g0 * 0.5, -waist), FLOOR),
        Shape(poly, CONC)
    )
    labels.add(Label(P(-g0 * 1.1, inp.H / 2), "H " + fL(inp.H), 9.5f, RED, 0, -90f))
    return PlanDrawing("Baju se (khula hua, chalvani line par) — oonchai", shapes, labels, emptyList(),
        listOf("Riser " + fL(ur, 1, if (metric) "cm" else "in") + " × " + inp.n + "   •   Waist slab " + fL(waist, 1, if (metric) "cm" else "in")))
}

private fun Engine.waistOf(runs: List<Run>, inp: Input): Double {
    val longest = runs.maxOf { hypot(it.risers * inp.ur, it.treads * it.going) }
    return max(longest / 25, if (metric) 0.10 else 4 * IN)
}

private fun lFoot(g: List<Double>, s: Double) = (g[0] + s) to (g[1] + 2 * s)
private fun dlFoot(g: List<Double>, s: Double) = max(g[0] + s, g[2] + 2 * s) to (g[1] + 2 * s)
private fun uFoot(g: List<Double>, s: Double, gap: Double) = max(g[0] + s, g[1] + 2 * s) to (2 * s + gap)

private fun Engine.buildDesign(id: Int, name: String, inp: Input): Design {
    val w90 = winderCount(PI / 2, inp)
    val w180 = winderCount(PI, inp)
    return when (id) {
        1 -> turnDesign(id, name, inp, TurnSpec(1, listOf(EAST), emptyList(), 0.0, "vadank nathi", { g, s -> (g[0] + s) to s }))
        2 -> turnDesign(id, name, inp, TurnSpec(2, listOf(EAST, EAST), listOf(0), 0.0, "vachche landing, vadank nathi", { g, s -> (g[0] + g[1] + 2 * s) to s }))
        // L: flight 1, square landing, flight 2 at 90°
        3 -> turnDesign(id, name, inp, TurnSpec(2, listOf(EAST, NORTH), listOf(0), 0.0, "1 vadank 90° (landing par)", { g, s -> lFoot(g, s) }))
        // Double-L: three flights, two square landings (flight 3 comes back)
        4 -> turnDesign(id, name, inp, TurnSpec(3, listOf(EAST, NORTH, WEST), listOf(0, 0), 0.0, "2 vadank 90° (2 landing)", { g, s -> dlFoot(g, s) }))
        // Dog-legged: two flights side by side, half-space landing, 180°
        5 -> turnDesign(id, name, inp, TurnSpec(2, listOf(EAST, WEST), listOf(0), 0.0, "Aadho vadank 180° (paholi landing)", { g, s -> uFoot(g, s, 0.0) }))
        // Open-well: like dog-legged with an open gap between the flights
        6 -> turnDesign(id, name, inp, TurnSpec(2, listOf(EAST, WEST), listOf(0), inp.well,
            "Aadho vadank 180°, vachche well " + fL(inp.well, 1, if (metric) "cm" else "in"), { g, s -> uFoot(g, s, inp.well) }))
        // Three-quarter turn: four flights, three landings, 270°
        7 -> turnDesign(id, name, inp, TurnSpec(4, listOf(EAST, NORTH, WEST, SOUTH), listOf(0, 0, 0), 0.0, "3 vadank 90° (kul 270°)",
            { g, s -> (g[0] + s - min(0.0, g[0] - g[2] - s)) to (2 * s + g[1] - min(0.0, g[1] - g[3])) },
            { g, s -> g[2] >= g[0] - 1e-9 || g[1] - g[3] >= s - 1e-9 }))
        // L with winders in the corner instead of a landing
        8 -> turnDesign(id, name, inp, TurnSpec(2, listOf(EAST, NORTH), listOf(w90), 0.0, "1 vadank 90° — khune $w90 winder", { g, s -> lFoot(g, s) }))
        // two quarter turns with winders (180° in two corners)
        9 -> turnDesign(id, name, inp, TurnSpec(3, listOf(EAST, NORTH, WEST), listOf(w90, w90), 0.0, "2 vadank 90° — darek khune $w90 winder", { g, s -> dlFoot(g, s) }))
        // half turn with winders
        10 -> turnDesign(id, name, inp, TurnSpec(2, listOf(EAST, WEST), listOf(w180), 0.0, "Aadho vadank 180° — $w180 winder", { g, s -> uFoot(g, s, 0.0) }))
        11 -> spiralDesign(id, name, inp)
        12 -> helicalDesign(id, name, inp)
        13 -> bifurcatedDesign(id, name, inp)
        else -> scissorDesign(id, name, inp)
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
            STAIR_TYPES.map { it.second } + ALL_OPT
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
            // code minimum width, rounded to a whole inch / cm so it is easy to mark
            val swDef = if (m) kotlin.math.round(rules.wMin * 100) / 100 else kotlin.math.round(rules.wMin / IN) * IN
            val inp = Input(v["L"] ?: Double.NaN, v["W"] ?: Double.NaN, H, if (swIn > 0) swIn else swDef, swIn > 0,
                n, H / n, dt, rules, (v["wg"] ?: 0.0).let { if (it > 0) it else 0.0 })
            val designs = STAIR_TYPES.map { buildDesign(it.first, it.second, inp) }
            val order = preference(bld)
            val best = designs.filter { it.fits != false && it.code.isEmpty() && (!rules.highRise || it.highRiseOk) }
                .minByOrNull { d -> order.indexOf(d.id).let { if (it < 0) 99 else it } }
            fun status(d: Design) = when (d.fits) {
                null -> "—"
                true -> "✓ bethegi" + (if (d.rotated) " (ghuma kar)" else "")
                false -> "✗ " + d.why
            } + (if (d.code.isNotEmpty()) "  ⚠" else "")

            val rows = ArrayList<Row>()
            rows.add(sec("Kaunsi seedhi bethegi"))
            for (d in designs) rows.add(Row((if (d === best) "⭐ " else "") + d.name, status(d), d.fits == false))
            if (best != null) rows.add(Row("⭐ Salah: " + best.name.substringAfter(' '), bld))
            rows.add(Row("High-rise mate saari", designs.filter { it.highRiseOk }.joinToString(", ") { it.name.substringAfter(' ') }))

            val ang = atan(inp.ur / inp.ut) * 180 / PI
            rows.add(sec("Pagthiye (sab prakar ke liye)"))
            // your own riser / tread are used as given; the code limits are only shown as advice
            rows.add(Row("Riser (oonchai) × ginti", fL(inp.ur, 1, sm) + " × " + n + "  (tamaru maap)"))
            rows.add(Row("Tread (pag ki jagah)", fL(inp.ut, 1, sm) + "  (tamaru maap)"))
            rows.add(Row("Seedhi ki pohlai", fL(inp.sw) + if (swIn > 0) "" else "  (code ki kam se kam)"))
            rows.add(Row(if (m) "2R + T (60–65 cm aaramdayak)" else "2R + T (24–25 in aaramdayak)", fL(2 * inp.ur + inp.ut, 1, sm)))
            rows.add(Row("Dhalan (angle)", f2(ang) + "°"))
            val adv = ArrayList<String>()
            if (inp.ur > rules.rMax + 1e-6) adv.add("$bld code: riser vadhu ma vadhu " + fL(rules.rMax, 1, sm))
            if (inp.ut < rules.tMin - 1e-6) adv.add("$bld code: tread ochha ma ochha " + fL(rules.tMin, 1, sm))
            if (inp.sw < rules.wMin - 1e-6) adv.add("$bld code: pohlai ochha ma ochhi " + fL(rules.wMin))
            if (ang > 42) adv.add("Dhalan 42° thi vadhu — seedhi ubhi (chadvama bhari)")
            if (adv.isNotEmpty()) {
                rows.add(sec("ⓘ Salah (code mujab) — seedhi tamara maap thi j bane che"))
                adv.forEach { rows.add(Row(it, "salah")) }
            }

            if (pick == ALL_OPT) {
                // every type one after another (for the PDF)
                for (d in designs) {
                    rows.add(sec(d.name + "   " + status(d)))
                    rows.add(Row("", plan = d.plan))
                    rows.add(Row("Jagya chahiye (lambai × pohlai)", fL(d.needL) + " × " + fL(d.needW)))
                    rows.add(Row("Riser", d.runs.joinToString(" + ") { (if (it.winder) "W" else "") + it.risers + (if (it.copies > 1) "×" + it.copies else "") }))
                    if (d.turnNote.isNotEmpty()) rows.add(Row("Vadank (turn)", d.turnNote))
                    rows.add(Row("High-rise", if (d.highRiseOk) "✓ chalegi" else "✗ nahi", !d.highRiseOk))
                    d.code.forEach { rows.add(Row(it, "⚠", true)) }
                }
                rows.add(Row("PDF: neeche PDF button dabavo", ""))
                return@FormSpec rows
            }

            val d = designs.firstOrNull { it.name == pick } ?: designs.first()
            rows.add(sec(d.name))
            rows.add(Row("", plan = d.plan))
            rows.add(Row("", plan = d.side))
            rows.add(Row("Jagya chahiye (lambai × pohlai)", fL(d.needL) + " × " + fL(d.needW)))
            if (d.maxW.isFinite()) rows.add(Row("Is jagya mein sabse chaudi seedhi", if (d.maxW > 0) fL(d.maxW, 1, sm) else "nahi banegi", d.maxW < rules.wMin))
            if (d.turnNote.isNotEmpty()) rows.add(Row("Vadank (turn)", d.turnNote))
            var fi = 0
            for (run in d.runs) {
                if (run.winder) continue
                fi++
                rows.add(Row("Flight $fi", run.risers.toString() + " riser, " + run.treads + " tread  •  lambai " + fL(run.treads * run.going) +
                    (if (run.copies > 1) "  × " + run.copies else "") +
                    (if (abs(run.width - inp.sw) > 1e-6) "  •  pohlai " + fL(run.width, 1, sm) else "")))
            }
            if (d.landings.isNotEmpty()) rows.add(Row("Landing (" + d.landings.size + ")", d.landings.distinct().joinToString(", ")))
            rows.add(Row("Upar floor par jagya", fL(inp.sw) + " × " + fL(inp.sw)))
            rows.addAll(d.extra)
            d.code.forEach { rows.add(Row(it, "⚠", true)) }
            rows.add(Row(if (d.highRiseOk) "High-rise: chalegi" else "High-rise: nahi chalegi", if (d.highRiseOk) "✓" else "✗", rules.highRise && !d.highRiseOk))

            // marking per run
            rows.add(sec("Deewar par marking (har flight)"))
            var h0 = 0.0
            fi = 0
            for (run in d.runs) {
                fi += if (run.winder) 0 else 1
                val tag = if (run.winder) "W" else "F$fi"
                for (k in 1..run.risers) rows.add(Row("$tag step $k", "upar " + fL(h0 + k * inp.ur, 1, sm) + "   •   aage " + fL((k - 1) * run.going, 1, sm)))
                h0 += run.risers * inp.ur
            }

            if (d.rcc) {
                val waist = waistOf(d.runs, inp)
                var vol = 0.0
                var shutter = 0.0
                for (run in d.runs) {
                    val lf = hypot(run.risers * inp.ur, run.treads * run.going)
                    vol += (lf * waist * run.width + run.treads * (inp.ur * run.going / 2) * run.width) * run.copies
                    shutter += lf * run.width * run.copies
                }
                vol += d.landArea * waist
                shutter += d.landArea
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
                fi = 0
                for (run in d.runs) {
                    if (run.winder) { rows.add(Row("Winder " + run.risers + " pag", "plate khuna pramane kaapvi")); continue }
                    fi++
                    rows.add(Row("Flight $fi stringer (channel)", fL(hypot(run.risers * inp.ur, run.treads * run.going) + run.going) + " × " + 2 * run.copies))
                }
                rows.add(Row("Tread plate", d.runs.filter { !it.winder }.sumOf { it.treads * it.copies }.toString() + " nag × " + fL(inp.sw, 1, sm)))
            }
            rows
        }.apply { links = listOf("← Seedhi (ek flight, stringer layout)" to { stairKey() }) }
    )
}
