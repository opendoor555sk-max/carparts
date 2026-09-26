package com.nirmaan.calc

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

const val IN = 0.0254
const val FT = 0.3048
const val YD = 0.9144
const val ACRE = 4046.8564224
const val D2R = PI / 180
val UF = mapOf("in" to IN, "ft" to FT, "ftin" to FT, "yd" to YD, "m" to 1.0, "cm" to 0.01, "mm" to 0.001)
val SUP = arrayOf("", "", "²", "³")
val WF = mapOf("kg" to 1.0, "lbs" to 0.45359237, "tons" to 907.18474, "mt" to 1000.0)
val WN = mapOf("kg" to "kg", "lbs" to "lbs", "tons" to "tons", "mt" to "mt")
const val SOON = "Ye function agle phase mein aayega"

/** t: 'n' number, 'p' percent, 'a' angle (deg), 'L' length/area/volume (SI, d = dimension), 'e' error */
data class Q(
    val t: Char,
    val v: Double,
    val d: Int = 0,
    val u: String = "",
    val f: String = "frac",
    val dms: Boolean = false
) {
    companion object {
        val ERR = Q('e', Double.NaN)
    }
}

data class Mem(
    val v: Double = 0.0,
    val ts: Int = 0,
    val u: String = "",
    val f: String = "",
    val ang: Double? = null,
    val len: Double? = null
)

class Row(
    val label: String,
    val value: String = "",
    val warn: Boolean = false,
    val section: Boolean = false,
    val stair: StairPlan? = null
)

/** Everything the stair drawing needs (lengths in metres, labels already formatted). */
class StairPlan(
    val n: Int,
    val ur: Double,
    val ut: Double,
    val bw: Double,
    val marks: List<String>,
    val info: List<String>
)

fun sec(t: String) = Row(t, section = true)

class Field(
    val key: String,
    val label: String,
    val isLen: Boolean,
    val value: String,
    val hint: String = "",
    val isPitch: Boolean = false
)

class FormSpec(
    val title: String,
    val fields: List<Field>,
    val seg: List<String>?,
    val compute: (Map<String, Double>, String?) -> List<Row>
)

class KeyDef(var main: String, var conv: String, val cls: String, val blue: String)

interface Ui {
    fun toast(m: String)
    fun panel(title: String, rows: List<Row>)
    fun form(spec: FormSpec)
    fun prefs()
    fun help()
}

class Entry {
    var tot = 0.0
    var d = 0
    var u: String? = null
    var lu: String? = null
    var dig = ""
    var num: String? = null
    var neg = false
}

class Pend(val a: Q, val op: String)

class Engine(val ui: Ui) {
    // preferences
    var res = 16
    var metric = false
    var trig = false
    var light = false

    // live state
    var cur = Q('n', 0.0)
    var entry: Entry? = null
    var pend: Pend? = null
    var fresh = false
    var lastKey = ""
    var label = ""
    var conv = false
    var mode: String? = null

    // memories
    val tape = mutableListOf<String>()
    var cumQ: Q? = null
    var cumN = 0
    val M = mutableMapOf<String, Q>()
    val T = mutableMapOf<String, Mem>()
    var triU: String? = null
    val G = mutableMapOf<String, Mem>()
    var TS = 0

    // ================= FORMATTING =================
    private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    fun fracStr(x: Double, r0: Int = res): String {
        val rr = r0.toLong()
        val n = Math.round(x * r0)
        val w = Math.floorDiv(n, rr)
        val r = Math.floorMod(n, rr)
        if (r == 0L) return w.toString()
        val g = gcd(r, rr)
        return "$w- ${r / g}/${rr / g}"
    }

    fun rnd(x: Double, places: Int): Double =
        if (!x.isFinite()) x else BigDecimal.valueOf(x).setScale(places, RoundingMode.HALF_UP).toDouble()

    fun num(x: Double?): String {
        if (x == null || !x.isFinite()) return "Error"
        val a = abs(x)
        if (a != 0.0 && (a >= 1e12 || a < 1e-6)) return String.format(Locale.US, "%.5e", x)
        var s = BigDecimal.valueOf(x).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        if (s == "-0") s = "0"
        return s
    }

    fun f2(x: Double) = String.format(Locale.US, "%.2f", x)

    fun dms(deg0: Double): String {
        val s = if (deg0 < 0) "-" else ""
        val deg = abs(deg0)
        var d = floor(deg).toLong()
        var m = floor((deg - d) * 60).toLong()
        var sec = Math.round(((deg - d) * 60 - m) * 60)
        if (sec == 60L) { sec = 0; m++ }
        if (m == 60L) { m = 0; d++ }
        return s + d + ":" + m.toString().padStart(2, '0') + ":" + sec.toString().padStart(2, '0')
    }

    fun baseU(u: String) = if (u == "ftin") "ft" else if (u == "acre") "m" else u

    fun fmt(q: Q?): String {
        if (q == null || q.t == 'e') return "Error"
        return when (q.t) {
            'n' -> if (q.dms) dms(q.v) else num(q.v)
            'p' -> num(q.v) + "%"
            'a' -> if (q.dms) dms(q.v) else if (q.v.isFinite()) f2(q.v) + "°" else "Error"
            'L' -> fmtL(q)
            'W' -> num(rnd(q.v / (WF[q.u] ?: 1.0), 4)) + (WN[q.u] ?: "kg")
            else -> "?"
        }
    }

    private fun fmtL(q: Q): String {
        if (!q.v.isFinite()) return "Error"
        val u = q.u
        val d = q.d
        if (u == "acre") return num(q.v / ACRE) + "acre"
        if (d == 1 && q.f != "dec" && (u == "ftin" || u == "in")) {
            val neg = q.v < 0
            val n = Math.round(abs(q.v) / IN * res).toDouble() / res
            val s = if (u == "in") fracStr(n) + "in" else {
                val f = floor(n / 12 + 1e-9).toLong()
                val r = max(0.0, n - f * 12)
                (if (f != 0L) "${f}ft" else "") +
                    (if (r > 1e-9 || f == 0L) (if (f != 0L) " " else "") + fracStr(r) + "in" else "")
            }
            return (if (neg) "-" else "") + s
        }
        val uu = baseU(u)
        val x = q.v / UF.getValue(uu).pow(d)
        return num(rnd(x, if (d > 1) 2 else 4)) + uu + SUP[d.coerceIn(0, 3)]
    }

    fun defU() = if (metric) "m" else "ftin"

    fun lq(v: Double, d: Int = 1, u0: String? = null): Q {
        var u = u0 ?: defU()
        if (d > 1 && u == "ftin") u = if (d == 3 && !metric) "yd" else "ft"
        return Q('L', v, d, u)
    }

    fun fL(v: Double, d: Int = 1, u: String? = null) = fmt(lq(v, d, u))

    // ================= ENTRY =================
    fun isFresh() = entry != null || fresh

    private fun pf(s: String?): Double? {
        if (s == null) return null
        val m = Regex("^\\d*\\.?\\d*").find(s)?.value ?: return null
        return m.toDoubleOrNull()
    }

    private fun pendAmt(e: Entry): Double? {
        if (e.num != null) {
            val dn = pf(e.dig)
            val nu = pf(e.num)
            return if (dn != null && dn != 0.0 && nu != null) nu / dn else null
        }
        return if (e.dig.isNotEmpty() && e.dig != ".") pf(e.dig) else null
    }

    fun entryText(): String {
        val e = entry ?: return fmt(cur)
        var s = ""
        if (e.d > 0) s = fmt(Q('L', e.tot, e.d, e.u ?: "m"))
        val p = if (e.num != null) e.num + "/" + e.dig else e.dig
        if (p.isNotEmpty()) s += (if (s.isNotEmpty()) " " else "") + p
        return (if (e.neg) "-" else "") + s.ifEmpty { "0" }
    }

    fun commit(): Q {
        val e = entry ?: return cur
        var q: Q
        if (e.dig.count { it == '.' } >= 2 && e.d == 0) {
            val p = e.dig.split('.').map { it.toDoubleOrNull() ?: 0.0 }
            q = Q('a', p[0] + p[1] / 60 + p.getOrElse(2) { 0.0 } / 3600, dms = true)
        } else {
            val a = pendAmt(e)
            if (e.d == 0) {
                q = if (e.num != null) Q('L', (a ?: 0.0) * IN, 1, "in") else Q('n', a ?: 0.0)
            } else {
                var tot = e.tot
                var u = e.u ?: "m"
                if (a != null && e.d == 1) {
                    val pu = if (e.num != null) "in" else (mapOf("ft" to "in", "yd" to "ft", "m" to "cm")[e.lu] ?: e.lu ?: "m")
                    tot += a * UF.getValue(pu)
                    if (pu == "in" && e.lu == "ft") u = "ftin"
                }
                q = Q('L', tot, e.d, u)
            }
        }
        if (e.neg) q = q.copy(v = -q.v)
        entry = null
        cur = q
        return q
    }

    /** commit + finish a pending operation (for function keys) */
    fun value(): Q {
        val f = isFresh()
        commit()
        val p = pend
        if (p != null) {
            val b = if (f) cur else p.a
            cur = calc(p.a, p.op, b)
            pend = null
        }
        return cur
    }

    fun set(q: Q, l: String = "") {
        cur = q; label = l; fresh = false
    }

    // ================= ARITHMETIC =================
    private fun norm(q: Q) = if (q.t == 'p') Q('n', q.v) else q

    private fun resU(a: Q, d: Int): String {
        if (a.d == d) return a.u
        if (a.u == "acre") return "m"
        val imp = a.u in listOf("ftin", "ft", "in", "yd")
        if (!imp) return a.u
        return if (d == 1) "ftin" else if (d == 2) "ft" else "yd"
    }

    fun calc(a: Q, op: String, b: Q): Q {
        if (a.t == 'e' || b.t == 'e') return Q.ERR
        var A = norm(a)
        var B = norm(b)
        if (A.t == 'W' || B.t == 'W') return calcW(A, op, B)
        if (A.t != 'L' && B.t != 'L') {
            val x = A.v
            val y = B.v
            val r = when (op) {
                "+" -> x + y
                "−" -> x - y
                "×" -> x * y
                else -> if (y == 0.0) Double.NaN else x / y
            }
            if (!r.isFinite()) return Q.ERR
            return Q(if (A.t == 'a' && (op != "÷" || B.t != 'a')) 'a' else 'n', r)
        }
        if (op == "+" || op == "−") {
            if (A.t != 'L') A = B.copy(v = A.v * UF.getValue(baseU(B.u)).pow(B.d))
            if (B.t != 'L') B = A.copy(v = B.v * UF.getValue(baseU(A.u)).pow(A.d))
            if (A.d != B.d) return Q.ERR
            return A.copy(v = if (op == "+") A.v + B.v else A.v - B.v)
        }
        if (op == "×") {
            if (A.t != 'L') return B.copy(v = B.v * A.v)
            if (B.t != 'L') return A.copy(v = A.v * B.v)
            val d = A.d + B.d
            if (d > 3) return Q.ERR
            return Q('L', A.v * B.v, d, resU(A, d))
        }
        if (B.t != 'L') return if (B.v != 0.0) A.copy(v = A.v / B.v) else Q.ERR
        if (A.t != 'L' || B.v == 0.0) return Q.ERR
        val d = A.d - B.d
        if (d < 0) return Q.ERR
        if (d == 0) return Q('n', A.v / B.v)
        return Q('L', A.v / B.v, d, resU(A, d))
    }

    private fun calcW(A0: Q, op: String, B0: Q): Q {
        var A = A0
        var B = B0
        if (A.t == 'L' || B.t == 'L') return Q.ERR
        when (op) {
            "+", "−" -> {
                if (A.t != 'W') A = B.copy(v = A.v * (WF[B.u] ?: 1.0))
                if (B.t != 'W') B = A.copy(v = B.v * (WF[A.u] ?: 1.0))
                return A.copy(v = if (op == "+") A.v + B.v else A.v - B.v)
            }
            "×" -> {
                if (A.t == 'W' && B.t == 'W') return Q.ERR
                return if (A.t == 'W') A.copy(v = A.v * B.v) else B.copy(v = B.v * A.v)
            }
            else -> {
                if (B.v == 0.0) return Q.ERR
                if (A.t == 'W' && B.t == 'W') return Q('n', A.v / B.v)
                if (A.t == 'W') return A.copy(v = A.v / B.v)
                return Q.ERR
            }
        }
    }

    private fun weightKey(u: String) {
        commit()
        val q = cur
        cur = when (q.t) {
            'n' -> Q('W', q.v * WF.getValue(u), u = u)
            'W' -> q.copy(u = u)
            else -> return ui.toast("Pehle number daaliye, phir kg / lbs / tons")
        }
        fresh = true; label = "Weight"
    }

    // ================= KEY ACTIONS =================
    private fun digit(ch: String) {
        val e = entry ?: Entry().also { entry = it }
        if (ch == ".") {
            if (e.num != null) return
            val dots = e.dig.count { it == '.' }
            if (dots >= 2 || (e.d > 0 && dots >= 1)) return
            if (e.dig.isEmpty()) e.dig = "0"
        }
        if (e.dig.length > 15) return
        e.dig += ch
        fresh = true
    }

    private fun solidus() {
        val e = entry ?: return
        if (e.dig.isNotEmpty() && e.num == null && !e.dig.contains('.')) {
            e.num = e.dig; e.dig = ""
        }
    }

    private fun backsp() {
        val e = entry ?: return
        if (e.dig.isNotEmpty()) e.dig = e.dig.dropLast(1)
        else if (e.num != null) { e.dig = e.num!!; e.num = null }
    }

    private fun unitKey(u: String) {
        val e = entry
        if (e != null) {
            val a = pendAmt(e)
            if (a != null) {
                if (e.d > 1) return ui.toast("Pehle area/volume poora karein")
                val hadFt = e.lu == "ft"
                e.tot += a * UF.getValue(u); e.d = 1; e.dig = ""; e.num = null
                e.u = if (u == "ft" || (u == "in" && hadFt)) "ftin" else u
                e.lu = u
                return
            }
            if (e.d >= 1 && e.lu == u && e.dig.isEmpty() && e.num == null) {
                if (e.d >= 3) return
                e.tot *= UF.getValue(u); e.d++
                if (e.u == "ftin") e.u = "ft"
                return
            }
            if (e.d == 0) return
            commit()
        }
        val q = cur
        if (q.t == 'n') cur = Q('L', q.v * UF.getValue(u), 1, if (u == "ft") "ftin" else u)
        else if (q.t == 'L') {
            val tu = if (u == "ft" && q.d == 1) "ftin" else u
            cur = if (q.u == tu) q.copy(f = if (q.f == "dec") "frac" else "dec")
            else q.copy(u = tu, f = if (tu == "ftin" || tu == "in") "frac" else "dec")
        } else return
        fresh = true; label = ""
    }

    private fun opKey(op: String) {
        val f = isFresh()
        commit()
        val p = pend
        if (p != null && f) cur = calc(p.a, p.op, cur)
        if (cur.t == 'e') { pend = null; label = ""; return }
        pend = Pend(cur, op); fresh = false; label = fmt(cur) + " " + op
    }

    private fun equals() {
        val f = isFresh()
        commit()
        val p = pend ?: return
        val b = if (f) cur else p.a
        val r = calc(p.a, p.op, b)
        tape.add(fmt(p.a) + " " + p.op + " " + fmt(b) + " = " + fmt(r))
        while (tape.size > 100) tape.removeAt(0)
        cur = r; pend = null; fresh = true; label = ""
    }

    private fun percent() {
        commit()
        val p = pend
        if (p != null && cur.t == 'n') {
            cur = if (p.op == "+" || p.op == "−") {
                val a = p.a.copy(v = p.a.v * cur.v / 100)
                if (a.t == 'p') a.copy(t = 'n') else a
            } else Q('n', cur.v / 100)
        } else if (cur.t == 'n') cur = Q('p', cur.v)
        fresh = true
    }

    private fun unary(k: String) {
        commit()
        val q = cur
        var r = Q.ERR
        when (k) {
            "neg" -> r = q.copy(v = -q.v)
            "sqrt" -> {
                if ((q.t == 'n' || q.t == 'p') && q.v >= 0) r = Q('n', sqrt(q.v))
                else if (q.t == 'L' && q.d == 2 && q.v >= 0) {
                    val u = when (q.u) { "acre" -> "m"; "ft" -> "ftin"; else -> q.u }
                    r = Q('L', sqrt(q.v), 1, u)
                }
            }
            "sq" -> {
                if (q.t == 'n') r = Q('n', q.v * q.v)
                else if (q.t == 'L' && q.d == 1) r = Q('L', q.v * q.v, 2, if (q.u == "ftin") "ft" else q.u, "dec")
            }
            "inv" -> if (q.t == 'n' && q.v != 0.0) r = Q('n', 1 / q.v)
        }
        if (r.t == 'e') ui.toast("Is value par ye kaam nahi hota")
        else { cur = r; fresh = true; label = "" }
    }

    private fun trigKey(k: String) {
        val q = value()
        if (q.t != 'n' && q.t != 'a' && q.t != 'p') return ui.toast("Sirf number/angle par")
        val x = if (q.t == 'p') q.v / 100 else q.v
        val r = when (k) {
            "SIN" -> Q('n', sin(x * D2R))
            "COS" -> Q('n', cos(x * D2R))
            "TAN" -> Q('n', tan(x * D2R))
            "ASIN" -> Q('a', asin(x) / D2R)
            "ACOS" -> Q('a', acos(x) / D2R)
            else -> Q('a', atan(x) / D2R)
        }
        if (!r.v.isFinite()) return set(Q.ERR, k)
        cur = r; label = k; fresh = true
    }

    private fun dmsKey() {
        commit()
        if (cur.t == 'a' || cur.t == 'n') {
            cur = cur.copy(t = 'a', dms = !cur.dms)
            label = if (cur.dms) "DMS" else "Degrees"
            fresh = true
        } else ui.toast("Angle chahiye")
    }

    private fun acreKey() {
        commit()
        cur = when {
            cur.t == 'n' -> Q('L', cur.v * ACRE, 2, "acre")
            cur.t == 'L' && cur.d == 2 -> cur.copy(u = "acre")
            else -> return ui.toast("Area chahiye (jaise ft²)")
        }
        fresh = true; label = "Acre"
    }

    // ================= MEMORY =================
    private fun memKey(k: String, md: String) {
        val nm = mapOf("m1" to "M1", "m2" to "M2", "m3" to "M3", "oc" to "On-Center", "rails" to "Rails").getValue(k)
        if (md == "store") {
            val q = value()
            when (k) {
                "oc" -> M["oc"] = when {
                    q.t == 'n' -> lq(q.v * (if (metric) 0.001 else IN), 1, if (metric) "mm" else "in")
                    q.t == 'L' && q.d == 1 -> q
                    else -> return ui.toast("Length chahiye")
                }
                "rails" -> {
                    if (q.t != 'n') return ui.toast("Number chahiye")
                    M["rails"] = Q('n', Math.round(q.v).toDouble())
                }
                else -> M[k] = q
            }
            set(M.getValue(k), "$nm saved")
        } else {
            val s = M[k] ?: return ui.toast("$nm khaali hai")
            entry = null; cur = s; label = nm; fresh = true
        }
    }

    private fun mPlus(neg: Boolean) {
        if (!neg && (lastKey == "cumT" || lastKey == "cumA")) {
            val c = cumQ ?: return
            if (lastKey == "cumT") { set(calc(c, "÷", Q('n', cumN.toDouble())), "M+ Avg"); lastKey = "cumA" }
            else { set(Q('n', cumN.toDouble()), "M+ Count"); lastKey = "cumC" }
            return
        }
        val q = value()
        if (q.t == 'e') return
        val add = if (neg) q.copy(v = -q.v) else q
        val c = cumQ
        val r = if (c != null) calc(c, "+", add) else add
        if (r.t == 'e') return ui.toast("Units match nahi karte")
        cumQ = r; cumN++
        set(q, if (neg) "M−" else "M+")
    }

    private fun mRecall() {
        val c = cumQ ?: return ui.toast("M+ khaali hai")
        set(c, "M+ Total"); lastKey = "cumT"
    }

    private fun mClear() {
        cumQ = null; cumN = 0; ui.toast("M+ memory saaf")
    }

    // ================= TRIANGLE =================
    private val TRI = listOf("rise", "run", "diag", "pitch")
    private val TN = mapOf(
        "rise" to "Rise", "run" to "Run", "diag" to "Diagonal", "pitch" to "Pitch",
        "len" to "Length", "wid" to "Width", "hei" to "Height"
    )

    fun toLen(q: Q): Double? {
        if (q.t == 'L' && q.d == 1) return q.v
        if (q.t == 'n') return q.v * UF.getValue(baseU(defU()))
        return null
    }

    private fun toAng(q: Q, slope: Boolean): Double? = when {
        q.t == 'a' -> q.v
        q.t == 'n' -> if (slope) atan(q.v) / D2R else q.v
        q.t == 'p' -> atan(q.v / 100) / D2R
        q.t == 'L' && q.d == 1 -> atan(q.v / IN / 12) / D2R
        else -> null
    }

    class Tri(val rise: Double, val run: Double, val diag: Double, val th: Double)

    fun solveTri(): Tri? {
        val ks = TRI.filter { T[it] != null }.sortedByDescending { T.getValue(it).ts }
        if (ks.size < 2) return null
        val h = mapOf(ks[0] to T.getValue(ks[0]).v, ks[1] to T.getValue(ks[1]).v)
        val r: Double
        val n: Double
        val d: Double
        val t: Double
        if ("pitch" in h) {
            t = h.getValue("pitch") * D2R
            if (t <= 0 || t >= PI / 2) return null
            when {
                "rise" in h -> { r = h.getValue("rise"); n = r / tan(t); d = r / sin(t) }
                "run" in h -> { n = h.getValue("run"); r = n * tan(t); d = n / cos(t) }
                else -> { d = h.getValue("diag"); r = d * sin(t); n = d * cos(t) }
            }
        } else if ("rise" in h && "run" in h) {
            r = h.getValue("rise"); n = h.getValue("run"); d = hypot(r, n); t = atan2(r, n)
        } else if ("rise" in h) {
            r = h.getValue("rise"); d = h.getValue("diag")
            if (d <= r) return null
            n = sqrt(d * d - r * r); t = asin(r / d)
        } else {
            n = h.getValue("run"); d = h.getValue("diag")
            if (d <= n) return null
            r = sqrt(d * d - n * n); t = acos(n / d)
        }
        return Tri(r, n, d, t / D2R)
    }

    private fun pitchQ(th: Double): Q =
        if (T["pitch"]?.f == "deg" || metric) Q('a', th)
        else Q('L', 12 * tan(th * D2R) * IN, 1, "in")

    private fun triKey(k: String, md: String?, slope: Boolean = false) {
        if (md == "recall") {
            val t = T[k] ?: return ui.toast(TN[k] + " khaali hai")
            showTri(k, t.v); return
        }
        if (md == "store" || isFresh()) {
            val q = value()
            val v: Double
            if (k == "pitch") {
                v = toAng(q, slope) ?: return ui.toast("Pitch galat hai")
                if (v <= 0 || v >= 90) return ui.toast("Pitch galat hai")
            } else {
                v = toLen(q) ?: return ui.toast("Length daaliye (jaise 12 Feet)")
                if (v <= 0) return ui.toast("Length daaliye (jaise 12 Feet)")
                if (q.t == 'L') triU = if (q.u in listOf("ftin", "in", "ft")) (if (q.u == "in") "in" else "ftin") else q.u
            }
            T[k] = Mem(v, ++TS, f = if (k == "pitch") (if (q.t == 'L') "in" else "deg") else "")
            set(cur, if (slope) "Slope" else TN.getValue(k))
            if (k == "pitch") cur = pitchQ(v)
            lastKey = k; return
        }
        if (lastKey == k || lastKey == k + "2") { triPanel(); lastKey = k + "2"; return }
        val s = solveTri()
        if (s == null) {
            val t = T[k]
            if (t != null) showTri(k, t.v) else ui.toast("Do values chahiye (jaise Rise aur Run)")
            lastKey = k; return
        }
        showTri(k, when (k) { "pitch" -> s.th; "rise" -> s.rise; "run" -> s.run; else -> s.diag })
        lastKey = k
    }

    private fun showTri(k: String, v: Double) {
        if (k == "pitch") set(pitchQ(v), "Pitch") else set(lq(v, 1, triU), TN.getValue(k))
    }

    private fun triPanel() {
        val s = solveTri() ?: return ui.toast("Do values chahiye")
        val t = s.th
        val tn = tan(t * D2R)
        val u = triU
        ui.panel(
            "Common Rafter", listOf(
                sec("Triangle"), Row("Rise", fL(s.rise, 1, u)), Row("Run", fL(s.run, 1, u)),
                Row("Diagonal (Rafter)", fL(s.diag, 1, u)),
                sec("Pitch"), Row("Pitch (angle)", f2(t) + "°"), Row("Pitch (inch per 12\")", fracStr(12 * tn) + "in"),
                Row("% Grade", num(rnd(tn * 100, 4))), Row("Slope (rise ÷ run)", num(rnd(tn, 6))),
                sec("Cuts"), Row("Plumb Cut", f2(t) + "°"), Row("Level Cut", f2(90 - t) + "°")
            )
        )
    }

    // ================= LENGTH / WIDTH / HEIGHT =================
    private fun lwhKey(k: String, md: String?) {
        val nm = TN.getValue(k)
        if (md == "recall") {
            val g = G[k] ?: return ui.toast("$nm khaali hai")
            set(lq(g.v, 1, g.u), nm); return
        }
        if (md == "store" || isFresh()) {
            val q = value()
            val v = toLen(q)
            if (v == null || v <= 0) return ui.toast("Length daaliye")
            val u = if (q.t == 'L') (if (q.u == "in") "in" else if (q.u == "ft" || q.u == "ftin") "ftin" else q.u) else defU()
            G[k] = Mem(v, ++TS, u)
            set(cur, nm); lastKey = k; return
        }
        lastKey = k
        if (k == "wid" && G["len"] != null && G["wid"] != null) return widthPanel()
        if (k == "hei" && G["len"] != null && G["wid"] != null && G["hei"] != null) return heightPanel()
        val g = G[k] ?: return ui.toast("$nm khaali hai")
        set(lq(g.v, 1, g.u), nm)
    }

    fun areaStr(a: Double) = if (metric) fL(a, 2, "m") else fL(a, 2, "ft")
    fun volStr(v: Double) = if (metric) fL(v, 3, "m") else fL(v, 3, "ft") + "  |  " + fL(v, 3, "yd")

    private fun widthPanel() {
        val L = G.getValue("len").v
        val W = G.getValue("wid").v
        val u = G.getValue("len").u
        ui.panel(
            "Width Function", listOf(
                sec("Inputs"), Row("Length", fL(L, 1, u)), Row("Width", fL(W, 1, u)),
                sec("Results"), Row("Area", areaStr(L * W)), Row("Perimeter", fL(2 * (L + W), 1, u)),
                Row("Square-up (Diagonal)", fL(hypot(L, W), 1, u))
            )
        )
    }

    private fun heightPanel() {
        val L = G.getValue("len").v
        val W = G.getValue("wid").v
        val H = G.getValue("hei").v
        val u = G.getValue("len").u
        val wall = 2 * (L + W) * H
        ui.panel(
            "Height Function", listOf(
                sec("Inputs"), Row("Length", fL(L, 1, u)), Row("Width", fL(W, 1, u)), Row("Height", fL(H, 1, u)),
                sec("Results"), Row("Volume", volStr(L * W * H)), Row("Floor Area", areaStr(L * W)),
                Row("Wall Area", areaStr(wall)), Row("Room Area (walls + ceiling)", areaStr(wall + L * W))
            )
        )
    }

    // ================= CIRCLE / ARC / POLYGON / COLUMN =================
    class Arc(val R: Double, val th: Double, val chord: Double, val rise: Double, val len: Double)

    fun arcSolve(): Arc? {
        val c = mutableListOf<Pair<Char, Int>>()
        G["circle"]?.let { c.add('D' to it.ts) }
        G["arc"]?.let { c.add('A' to it.ts) }
        T["run"]?.let { c.add('C' to it.ts) }
        T["rise"]?.let { c.add('H' to it.ts) }
        if (c.size < 2) return null
        c.sortByDescending { it.second }
        val h = setOf(c[0].first, c[1].first)
        val D = G["circle"]?.v ?: 0.0
        val A = G["arc"]
        val C = T["run"]?.v ?: 0.0
        val H = T["rise"]?.v ?: 0.0
        val R: Double
        var th: Double
        when {
            'D' in h && 'A' in h -> {
                R = D / 2
                th = A!!.ang ?: ((A.len ?: 0.0) / R / D2R)
            }
            'C' in h && 'H' in h -> {
                R = (C * C / 4 + H * H) / (2 * H)
                th = 2 * asin(min(1.0, C / (2 * R))) / D2R
                if (H > R) th = 360 - th
            }
            'A' in h && 'C' in h -> {
                val ang = A!!.ang ?: return null
                R = C / (2 * sin(ang * D2R / 2)); th = ang
            }
            'A' in h && 'H' in h -> {
                val ang = A!!.ang ?: return null
                R = H / (1 - cos(ang * D2R / 2)); th = ang
            }
            'D' in h && 'C' in h -> {
                R = D / 2
                if (C > D) return null
                th = 2 * asin(C / D) / D2R
            }
            'D' in h && 'H' in h -> {
                R = D / 2
                if (H > D) return null
                th = 2 * acos((R - H) / R) / D2R
            }
            else -> return null
        }
        if (!R.isFinite() || !th.isFinite() || R <= 0) return null
        val hr = th * D2R / 2
        return Arc(R, th, 2 * R * sin(hr), R * (1 - cos(hr)), R * th * D2R)
    }

    private fun circU(): String = G["circle"]?.u ?: triU ?: defU()

    private fun circleKey(md: String?, isRad: Boolean) {
        val nm = if (isRad) "Radius" else "Diameter"
        if (md == "recall") {
            val g = G["circle"] ?: return ui.toast("$nm khaali hai")
            set(lq(if (isRad) g.v / 2 else g.v, 1, circU()), nm); return
        }
        if (md == "store" || isFresh()) {
            val q = value()
            val v = toLen(q)
            if (v == null || v <= 0) return ui.toast("Length daaliye")
            G["circle"] = Mem(if (isRad) 2 * v else v, ++TS, if (q.t == 'L') (if (q.u == "ft") "ftin" else q.u) else defU())
            set(cur, nm); lastKey = if (isRad) "rad" else "circ"; return
        }
        val lk = if (isRad) "rad" else "circ"
        val g = G["circle"]
        if (lastKey == lk && g != null) { circlePanel(); return }
        lastKey = lk
        val a = arcSolve()
        val useArc = a != null && (g == null ||
            maxOf(T["run"]?.ts ?: 0, T["rise"]?.ts ?: 0, G["arc"]?.ts ?: 0) > g.ts)
        if (useArc) return set(lq(if (isRad) a!!.R else 2 * a!!.R, 1, circU()), nm)
        if (g != null) return set(lq(if (isRad) g.v / 2 else g.v, 1, circU()), nm)
        ui.toast("$nm khaali hai")
    }

    private fun circlePanel() {
        val D = G.getValue("circle").v
        val R = D / 2
        val u = circU()
        ui.panel(
            "Circle Function", listOf(
                sec("Results"), Row("Diameter", fL(D, 1, u)), Row("Radius", fL(R, 1, u)),
                Row("Circumference", fL(PI * D, 1, u)), Row("Area", areaStr(PI * R * R))
            )
        )
    }

    private fun arcKey(md: String?) {
        if (md == "recall") {
            val g = G["arc"] ?: return ui.toast("Arc khaali hai")
            set(if (g.ang != null) Q('a', g.ang) else lq(g.len ?: 0.0, 1, circU()), "Arc"); return
        }
        if (md == "store" || isFresh()) {
            val q = value()
            if (q.t == 'n' || q.t == 'a') {
                if (q.v <= 0 || q.v >= 360) return ui.toast("Angle 0 se 360 ke beech")
                G["arc"] = Mem(ts = ++TS, ang = q.v); set(Q('a', q.v), "Arc Angle")
            } else if (q.t == 'L' && q.d == 1) {
                G["arc"] = Mem(ts = ++TS, len = q.v); set(q, "Arc Length")
            } else return ui.toast("Angle ya length daaliye")
            lastKey = "arc"; return
        }
        val a = arcSolve() ?: return ui.toast("Do values chahiye: Run, Rise, Diameter ya Arc angle")
        if (lastKey == "arc" || lastKey == "arc2") { arcPanel(a); lastKey = "arc2"; return }
        set(Q('a', a.th), "Arch Angle"); lastKey = "arc"
    }

    private fun arcPanel(a: Arc) {
        val u = circU()
        ui.panel(
            "Arc Function", listOf(
                sec("Results"), Row("Arc Angle", f2(a.th) + "°"), Row("Arc Length", fL(a.len, 1, u)),
                Row("Chord Length (Run)", fL(a.chord, 1, u)), Row("Segment Height (Rise)", fL(a.rise, 1, u)),
                Row("Radius", fL(a.R, 1, u)), Row("Diameter", fL(2 * a.R, 1, u))
            )
        )
    }

    private fun polygonKey() {
        val f = isFresh()
        val q = value()
        if (!f || q.t != 'n' || q.v < 3 || q.v % 1 != 0.0) return ui.toast("Pehle sides ka number daaliye (3 ya zyada)")
        val g = G["circle"] ?: return ui.toast("Pehle Diameter (Circle) ya Radius daaliye")
        val n = q.v
        val R = g.v / 2
        val u = circU()
        val side = 2 * R * sin(PI / n)
        val full = 180 * (n - 2) / n
        ui.panel(
            "Polygon Function", listOf(
                sec("Inputs"), Row("Sides", n.toInt().toString()), Row("Diameter (corner to corner)", fL(2 * R, 1, u)),
                sec("Results"), Row("Full Corner Angle", f2(full) + "°"), Row("Half Corner Angle", f2(full / 2) + "°"),
                Row("Side Length", fL(side, 1, u)), Row("Perimeter", fL(side * n, 1, u)),
                Row("Area", areaStr(n / 2 * R * R * sin(2 * PI / n))), Row("Radius (center to corner)", fL(R, 1, u))
            )
        )
        fresh = false
    }

    private fun colConKey() {
        val g = G["circle"]
        val hh = G["hei"]
        if (g == null || hh == null) return ui.toast("Pehle Height aur Diameter/Radius daaliye")
        val R = g.v / 2
        val H = hh.v
        val u = circU()
        val sl = hypot(R, H)
        ui.panel(
            "Column / Cone", listOf(
                sec("Inputs"), Row("Radius", fL(R, 1, u)), Row("Height", fL(H, 1, u)),
                sec("Column"), Row("Volume", volStr(PI * R * R * H)), Row("Side Area", areaStr(2 * PI * R * H)),
                sec("Cone"), Row("Volume", volStr(PI * R * R * H / 3)), Row("Side Area", areaStr(PI * R * sl)),
                Row("Slant Length", fL(sl, 1, u)), Row("Cone Angle", f2(atan(H / R) / D2R) + "°")
            )
        )
    }

    // ================= QTY @ ON-CENTER =================
    private fun qtyKey() {
        val f = isFresh()
        val q = value()
        val sp = (if (metric) mutableListOf(0.3, 0.4, 0.6) else mutableListOf(12 * IN, 16 * IN, 19.2 * IN, 24 * IN))
        M["oc"]?.let { oc -> if (sp.none { abs(it - oc.v) < 1e-6 }) sp.add(oc.v) }
        sp.sort()
        val lab = { s: Double -> if (metric) num(rnd(s * 1000, 1)) + "mm" else fracStr(s / IN) + "in" }
        if (f && q.t == 'n' && q.v >= 2 && q.v % 1 == 0.0) {
            val n = q.v
            val rows = mutableListOf(sec("${n.toInt()} members kitni length cover karenge"))
            sp.forEach { rows.add(Row("@ " + lab(it) + " on-center", fL((n - 1) * it))) }
            return ui.panel("Qty @ On-Center", rows)
        }
        val L = if (f && q.t == 'L' && q.d == 1) q.v else G["len"]?.v
            ?: return ui.toast("Length daaliye (jaise 22 Feet 8 Inches)")
        val rows = mutableListOf(sec("Length: " + fL(L)))
        sp.forEach { rows.add(Row("@ " + lab(it) + " on-center", (ceil(L / it - 1e-9).toInt() + 1).toString() + " pieces")) }
        ui.panel("Qty @ On-Center", rows)
        fresh = false
    }

    // ================= FORMS (Stair, Compound Miter) =================
    fun parseLen(s0: String): Double {
        var s = s0.trim().lowercase()
        if (s.isEmpty()) return Double.NaN
        if (metric) return (s.toDoubleOrNull() ?: Double.NaN) / 100
        s = s.replace(Regex("\"|in$|inch(es)?$"), "").trim()
        var ft = 0.0
        val m = Regex("^(-?\\d*\\.?\\d+)\\s*(?:'|ft|feet)\\s*(.*)$").find(s)
        if (m != null) { ft = m.groupValues[1].toDouble(); s = m.groupValues[2].trim() }
        var inch = 0.0
        if (s.isNotEmpty()) {
            val f = Regex("^(\\d*\\.?\\d+)?[\\s-]*(?:(\\d+)/(\\d+))?$").find(s) ?: return Double.NaN
            val a = f.groupValues[1]
            val nu = f.groupValues[2]
            val de = f.groupValues[3]
            if (a.isEmpty() && nu.isEmpty()) return Double.NaN
            inch = (if (a.isNotEmpty()) a.toDouble() else 0.0) +
                (if (nu.isNotEmpty() && de.toDouble() != 0.0) nu.toDouble() / de.toDouble() else 0.0)
        }
        return (ft * 12 + inch) * IN
    }

    fun lenTxt(v: Double?): String {
        if (v == null || !v.isFinite()) return ""
        if (metric) return num(rnd(v * 100, 2))
        val n = Math.round(v / IN * res).toDouble() / res
        val f = floor(n / 12 + 1e-9).toLong()
        val r = n - f * 12
        return (if (f != 0L) "$f' " else "") + fracStr(r).replace("- ", "-") + "\""
    }

    private fun stairKey() {
        if (isFresh()) {
            val q = value()
            toLen(q)?.let { if (it > 0) T["rise"] = Mem(it, ++TS) }
            fresh = false
        }
        val m = metric
        val sm = if (m) "cm" else "in"
        ui.form(
            FormSpec(
                "Stairs Function", listOf(
                    Field("rise", "Rise (floor to floor)", true, lenTxt(T["rise"]?.v), if (m) "cm mein" else "jaise 9' 10\""),
                    Field("run", "Run (khaali chhod sakte hain)", true, lenTxt(T["run"]?.v), if (m) "cm mein" else "jaise 12'"),
                    Field("dr", "Desired Riser Height", true, if (m) "19" else "7-1/2\""),
                    Field("dt", "Desired Tread Width", true, if (m) "25" else "10\""),
                    Field("hr", "Headroom", true, if (m) "203" else "6' 8\""),
                    Field("th", "Floor thickness (upar wala)", true, if (m) "25" else "10\""),
                    Field("bw", "Stringer board width (patiya)", true, if (m) "28" else "11-1/4\"", if (m) "cm mein" else "2x12 = 11-1/4\"")
                ), null
            ) { v, _ ->
                val rise = v["rise"] ?: Double.NaN
                val run = v["run"] ?: Double.NaN
                val dr = v["dr"] ?: Double.NaN
                val dt = v["dt"] ?: Double.NaN
                if (!(rise > 0) || !(dr > 0) || !(dt > 0)) return@FormSpec listOf(Row("Rise, Riser aur Tread daaliye", "—"))
                val n = ceil(rise / dr - 1e-9).toInt()
                if (n > 60) return@FormSpec listOf(Row("Bahut zyada steps — values check karein", "—", true))
                val ur = rise / n
                val tr = n - 1
                val ut = if (run > 0 && tr > 0) run / tr else dt
                val tot = ut * tr
                val ang = atan(ur / ut) / D2R
                val diag = hypot(ur, ut)
                val bw = v["bw"] ?: Double.NaN
                val notch = ur * ut / diag
                val throat = if (bw > 0) bw - notch else Double.NaN
                val hr = v["hr"] ?: 0.0
                val th = v["th"] ?: 0.0
                val open = (if (hr > 0) hr else 0.0) + (if (th > 0) th else 0.0)
                val plan = StairPlan(
                    n, ur, ut, if (bw > 0) bw else notch * 2,
                    (1..n).map { k -> fL(k * diag) },
                    listOf(
                        "Rise: " + fL(rise), "Run: " + fL(tot),
                        "Riser: " + fL(ur, 1, sm) + " × " + n, "Tread: " + fL(ut, 1, sm) + " × " + tr,
                        "Angle: " + f2(ang) + "°", "Throat: " + (if (throat.isFinite()) fL(throat, 1, sm) else "—")
                    )
                )
                val rows = mutableListOf(
                    sec("Stringer Layout (drawing)"), Row("", stair = plan),
                    sec("Steps"), Row("Number of Risers", n.toString()), Row("Number of Treads", tr.toString()),
                    Row("Actual Riser Height", fL(ur, 1, sm), ur > dr + 1e-6),
                    Row("Actual Tread Width", fL(ut, 1, sm), ut < dt - 1e-6),
                    sec("Staircase"), Row("Total Rise", fL(rise)), Row("Total Run", fL(tot)),
                    Row("Angle of Incline", f2(ang) + "°"),
                    Row("Step diagonal (har step)", fL(diag, 1, sm)),
                    Row("Stringer board length (kharidne ke liye)", fL(n * diag)),
                    Row("Notch depth", fL(notch, 1, sm)),
                    Row("Throat (bachi lakdi)", if (throat.isFinite()) fL(throat, 1, sm) else "—", throat.isFinite() && throat < 3.5 * IN),
                    Row("Stairwell Opening (lagbhag)", fL(open / tan(ang * D2R) + ut)),
                    Row("2R + T (60-65 cm theek)", fL(2 * ur + ut, 1, sm))
                )
                if (ang > 42) rows.add(Row("Seedhi bahut steep hai", "⚠", true))
                if (throat.isFinite() && throat < 3.5 * IN) rows.add(Row("Throat kam hai — chaudi patiya lein", "⚠", true))
                rows
            }
        )
    }

    private fun miterKey() {
        var corner = 90.0
        if (isFresh()) {
            val q = value()
            if (q.t == 'n' || q.t == 'a') {
                if (q.t == 'n' && q.v >= 3 && q.v < 25 && q.v % 1 == 0.0) corner = 180 * (q.v - 2) / q.v
                else if (q.v >= 25 && q.v < 180) corner = q.v
            }
            fresh = false
        }
        ui.form(
            FormSpec(
                "Compound + Simple Miters", listOf(
                    Field("c", "Corner Angle (°)", false, num(rnd(corner, 2))),
                    Field("s", "Spring Angle (°)", false, "38", "Crown molding ki deewar se angle")
                ), listOf("Miter Saw", "Protractor")
            ) { v, mode ->
                val c = v["c"] ?: Double.NaN
                val s = v["s"] ?: Double.NaN
                if (!(c > 0 && c < 180) || !(s >= 0 && s < 90)) return@FormSpec listOf(Row("Sahi angle daaliye", "—"))
                val hc = c / 2 * D2R
                val S = s * D2R
                var mi = atan(sin(S) / tan(hc)) / D2R
                var be = asin(cos(S) * cos(hc)) / D2R
                var smp = 90 - c / 2
                if (mode == "Protractor") { mi = 90 - mi; be = 90 - be; smp = 90 - smp }
                listOf(
                    sec("Crown (compound)"), Row("Compound Miter Angle", f2(mi) + "°"),
                    Row("Compound Bevel Angle", f2(be) + "°"),
                    sec("Flat trim (simple)"), Row("Simple Miter Angle", f2(smp) + "°")
                )
            }
        )
    }

    private fun tapePanel() {
        val rows = if (tape.isEmpty()) listOf(Row("Abhi koi hisaab nahi"))
        else tape.reversed().map {
            val i = it.lastIndexOf(" = ")
            Row(it.substring(0, i), "= " + it.substring(i + 3))
        }
        ui.panel("Tape (History)", rows)
    }

    fun clearTemp() {
        entry = null; cur = Q('n', 0.0); pend = null; label = ""; mode = null; conv = false; fresh = false; lastKey = ""
    }

    fun clrAll() {
        clearTemp(); T.clear(); G.clear(); M.clear(); cumQ = null; cumN = 0; TS = 0; triU = null
    }

    // ================= KEYPAD =================
    /** [primary, conv (yellow), class, store-label (blue)] */
    private val ROWS = arrayOf(
        arrayOf(k("Rise", "R/Wall", "fn"), k("Run", "Roof", "fn"), k("Pitch", "Slope", "fn"), k("Diag", "Polygon", "fn"), k("Stair", "Baluster", "fn")),
        arrayOf(k("Hip/V", "IrPitch", "fn"), k("Jack", "IrJack", "fn"), k("Arc", "Radius", "fn"), k("Circle", "ColCon", "fn"), k("CmpMtr", "Fence", "fn")),
        arrayOf(k("m", "", "unit"), k("Length", "Masonry", "green"), k("Width", "Footing", "green"), k("Height", "Drywall", "green"), k("⌫", "√x", "red")),
        arrayOf(k("Yards", "", "unit"), k("Feet", "", "unit"), k("Inches", "", "unit"), k("/", "", "unit"), k("%", "x²", "op")),
        arrayOf(k("Conv", "", "conv"), k("7", "cm", "num", "Rails"), k("8", "BdFt", "num"), k("9", "mm", "num"), k("÷", "1/x", "op")),
        arrayOf(k("Store", "Prefs", "st"), k("4", "lbs", "num"), k("5", "qty@oc", "num", "o.c."), k("6", "Tons", "num"), k("×", "ClrAll", "op")),
        arrayOf(k("Recall", "M-R/C", "st"), k("1", "kg", "num", "M1"), k("2", "Acre", "num", "M2"), k("3", "met tons", "num", "M3"), k("−", "+/-", "op")),
        arrayOf(k("M+", "M-", "op"), k("0", "Cost", "num", "wt/vol"), k(".", "dms⇄deg", "num"), k("=", "Tape", "op"), k("+", "π", "op"))
    )

    private fun k(a: String, b: String, c: String, d: String = "") = KeyDef(a, b, c, d)

    fun keyDef(r: Int, c: Int): KeyDef {
        val o = ROWS[r][c]
        val d = KeyDef(o.main, o.conv, o.cls, o.blue)
        if (trig && r == 2 && c in 1..3) { d.main = listOf("SIN", "COS", "TAN")[c - 1]; d.conv = listOf("ASIN", "ACOS", "ATAN")[c - 1] }
        if (metric && r == 3 && c <= 2) d.main = listOf("m", "cm", "mm")[c]
        if (metric && r == 2 && c == 0) d.main = "Feet"
        return d
    }

    /** text shown on the key face */
    fun keyText(r: Int, c: Int): String {
        val d = keyDef(r, c)
        return if (conv && d.conv.isNotEmpty()) d.conv else d.main
    }

    /** small yellow label above the key */
    fun keyTop(r: Int, c: Int): String {
        val d = keyDef(r, c)
        return if (conv) (if (d.conv.isNotEmpty()) d.main else "") else d.conv
    }

    fun keyLit(r: Int, c: Int): Boolean {
        val m = keyDef(r, c).main
        return (m == "Conv" && conv) || (m == "Store" && mode == "store") || (m == "Recall" && mode == "recall")
    }

    fun display(): String = if (entry != null) entryText() else fmt(cur)
    fun displayLabel(): String = if (entry != null) (pend?.let { fmt(it.a) + " " + it.op } ?: "") else label
    fun tag(): String = when {
        mode == "store" -> "STO"
        mode == "recall" -> "RCL"
        conv -> "CONV"
        else -> ""
    }

    private val MEMABLE = setOf("Rise", "Run", "Diag", "Pitch", "Slope", "Length", "Width", "Height", "Circle", "Radius", "Arc", "M+")

    fun press(r: Int, c: Int) {
        val d = keyDef(r, c)
        if (d.main == "Conv") { conv = !conv; return }
        val wasConv = conv
        conv = false
        val n = if (wasConv && d.conv.isNotEmpty()) d.conv else d.main
        val md0 = mode
        if (md0 != null && !wasConv && d.blue.isNotEmpty()) {
            val k = mapOf("M1" to "m1", "M2" to "m2", "M3" to "m3", "o.c." to "oc", "Rails" to "rails")[d.blue]
            mode = null
            if (k != null) memKey(k, md0) else wtVolForm()
            after(n); return
        }
        if (n == "Store") { mode = if (mode == "store") null else "store"; return }
        if (n == "Recall") {
            if (mode == "recall") { mode = null; mClear() } else mode = "recall"
            return
        }
        val md = if (n in MEMABLE) mode else null
        mode = null
        try {
            handle(n, md)
        } catch (ex: Exception) {
            set(Q.ERR)
        }
        after(n)
    }

    private fun after(n: String) {
        if (n !in MEMABLE) lastKey = ""
    }

    private fun handle(n: String, md: String?) {
        if (n.length == 1 && (n[0].isDigit() || n == ".")) return digit(n)
        val units = mapOf("Yards" to "yd", "Feet" to "ft", "Inches" to "in", "m" to "m", "cm" to "cm", "mm" to "mm")
        units[n]?.let { return unitKey(it) }
        when (n) {
            "/" -> solidus()
            "⌫" -> backsp()
            "+", "−", "×", "÷" -> opKey(n)
            "=" -> equals()
            "%" -> percent()
            "√x" -> unary("sqrt")
            "x²" -> unary("sq")
            "1/x" -> unary("inv")
            "+/-" -> { val e = entry; if (e != null) e.neg = !e.neg else unary("neg") }
            "π" -> { commit(); cur = Q('n', PI); fresh = true; label = "π" }
            "ClrAll" -> { clrAll(); ui.toast("Saari memory saaf") }
            "Prefs" -> ui.prefs()
            "Tape" -> tapePanel()
            "M-R/C" -> mClear()
            "M+" -> if (md == "recall") mRecall() else mPlus(false)
            "M-" -> mPlus(true)
            "SIN", "COS", "TAN", "ASIN", "ACOS", "ATAN" -> trigKey(n)
            "dms⇄deg" -> dmsKey()
            "Acre" -> acreKey()
            "Pitch" -> triKey("pitch", md)
            "Slope" -> triKey("pitch", md, true)
            "Rise" -> triKey("rise", md)
            "Run" -> triKey("run", md)
            "Diag" -> triKey("diag", md)
            "Length" -> lwhKey("len", md)
            "Width" -> lwhKey("wid", md)
            "Height" -> lwhKey("hei", md)
            "Circle" -> circleKey(md, false)
            "Radius" -> circleKey(md, true)
            "Arc" -> arcKey(md)
            "Polygon" -> polygonKey()
            "ColCon" -> colConKey()
            "qty@oc" -> qtyKey()
            "Stair" -> stairKey()
            "CmpMtr" -> miterKey()
            "Hip/V" -> hipForm(false)
            "IrPitch" -> hipForm(true)
            "Jack" -> jackForm(false)
            "IrJack" -> jackForm(true)
            "R/Wall" -> rakeWallForm()
            "Roof" -> roofForm()
            "Masonry" -> masonryForm()
            "Footing" -> footingForm()
            "Drywall" -> drywallForm()
            "BdFt" -> boardFeetForm()
            "Fence" -> fenceForm()
            "Baluster" -> balusterForm()
            "Cost" -> costForm()
            "kg" -> weightKey("kg")
            "lbs" -> weightKey("lbs")
            "Tons" -> weightKey("tons")
            "met tons" -> weightKey("mt")
            else -> ui.toast(SOON)
        }
    }
}
