package com.nirmaan.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sqrt

/** every setting of the scientific calculator */
class SciOpts {
    var lang = 1          // 0 English, 1 हिन्दी, 2 ગુજરાતી
    var angle = 0         // 0 DEG, 1 RAD, 2 GRA
    var mode = 0          // 0 normal, 1 scientific, 2 engineering
    var dec = -1          // decimal places, -1 = auto
    var exact = true      // 3/4, √2/2, π/4 instead of decimals
    var expStyle = 0      // 0: 2×10³   1: 2E3   2: 2e3
    var comma = false     // decimal comma
    var group = 0         // 0 Indian 12,34,567   1 International 1,234,567   2 none
    var mul = 0           // 0 ×  1 ·  2 *
    var div = 0           // 0 ÷  1 /
    var keep = true       // keep the sum on screen after Enter (preserve structure)
    var negExp = true     // m·s⁻¹ (on) or m/s (off)
    var abbrev = true     // m (on) or metre (off)
    var spacious = true   // 2 + 3 (on) or 2+3 (off)
    var isolate = true    // unit in its own colour
    var vibrate = false
    var prefixDen = false // "Allow prefix in denominator": km/ms style labels allowed
    var interval = 0      // 0 concise, 1 plus-minus

    val mulSign get() = when (mul) { 1 -> "·"; 2 -> "*"; else -> "×" }
    val divSign get() = if (div == 1) "/" else "÷"
    val angleName get() = when (angle) { 1 -> "RAD"; 2 -> "GRA"; else -> "DEG" }
    val modeName get() = when (mode) { 1 -> "SCI"; 2 -> "ENG"; else -> "NORM" }
    val decName get() = if (dec < 0) "0.###" else if (dec == 0) "0" else "0." + "0".repeat(dec)
}

object SciFmt {
    private val SUP = mapOf('-' to '⁻', '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵',
        '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹')
    fun sup(n: Int) = n.toString().map { SUP[it] ?: it }.joinToString("")

    // ================= numbers =================
    fun real(x: Double, o: SciOpts): String {
        if (x.isNaN()) return "?"
        if (x.isInfinite()) return if (x > 0) "∞" else "−∞"
        if (x == 0.0) return "0"
        val a = abs(x)
        val s = when {
            o.mode == 1 -> sci(x, o, false)
            o.mode == 2 -> sci(x, o, true)
            a >= 1e15 || (o.dec < 0 && a < 1e-9) -> sci(x, o, false)
            o.dec >= 0 -> fixed(BigDecimal(x).setScale(o.dec, RoundingMode.HALF_UP).toPlainString(), o)
            else -> fixed(BigDecimal(x).round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString(), o)
        }
        return s
    }

    private fun sci(x: Double, o: SciOpts, eng: Boolean): String {
        var e = floor(log10(abs(x))).toInt()
        if (eng) e = Math.floorDiv(e, 3) * 3
        var m = BigDecimal(x).movePointLeft(e)
        m = if (o.dec >= 0) m.setScale(o.dec, RoundingMode.HALF_UP) else m.round(MathContext(10, RoundingMode.HALF_EVEN))
        // 9.9999 → 10.0 after rounding
        val lim = if (eng) BigDecimal(1000) else BigDecimal.TEN
        if (m.abs() >= lim) {
            val step = if (eng) 3 else 1
            e += step
            m = m.movePointLeft(step)
            m = if (o.dec >= 0) m.setScale(o.dec, RoundingMode.HALF_UP) else m.round(MathContext(10))
        }
        var ms = m.toPlainString()
        if (o.dec < 0 && ms.contains('.')) ms = ms.trimEnd('0').trimEnd('.')
        ms = fixed(ms, o)
        if (e == 0 && o.mode == 0) return ms
        return ms + when (o.expStyle) {
            1 -> "E$e"
            2 -> "e$e"
            else -> "×10" + sup(e)
        }
    }

    /** "1234567.5" → "12,34,567.5" with the chosen separators */
    private fun fixed(plain: String, o: SciOpts): String {
        val neg = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val ip = body.substringBefore('.')
        val fp = if (body.contains('.')) body.substringAfter('.') else ""
        val gs = if (o.comma) "." else ","
        val g = when {
            o.group == 2 || ip.length <= 3 -> ip
            o.group == 1 -> ip.reversed().chunked(3).joinToString(gs).reversed()
            else -> ip.dropLast(3).reversed().chunked(2).joinToString(gs).reversed() + gs + ip.takeLast(3)
        }
        val r = g + if (fp.isNotEmpty()) (if (o.comma) "," else ".") + fp else ""
        return if (neg && r.any { it in '1'..'9' }) "−$r" else r
    }

    /** plain text that can be typed back (no grouping) */
    fun raw(x: Double, o: SciOpts): String {
        if (x.isInfinite()) return if (x > 0) "∞" else "−∞"
        if (x.isNaN()) return "0"
        val bd = BigDecimal(x).round(MathContext(15, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        var s = if (abs(x) >= 1e15 || abs(x) < 1e-9) bd.toString().replace("E+", "E") else bd.toPlainString()
        if (o.comma) s = s.replace('.', ',')
        return s.replace("-", "−")
    }

    // ================= exact forms =================
    private fun gcd(a: Long, b: Long): Long { var x = abs(a); var y = abs(b); while (y != 0L) { val t = x % y; x = y; y = t }; return x }

    /** p/q with q ≤ maxQ, or null */
    fun fraction(x: Double, maxQ: Long = 10000): Pair<Long, Long>? {
        if (!x.isFinite() || abs(x) > 1e12) return null
        var h0 = 0L; var h1 = 1L; var k0 = 1L; var k1 = 0L
        var v = x
        for (n in 0 until 40) {
            val a = floor(v)
            if (abs(a) > 1e13) break
            val al = a.toLong()
            val h2 = al * h1 + h0; val k2 = al * k1 + k0
            if (k2 > maxQ || k2 <= 0) break
            h0 = h1; h1 = h2; k0 = k1; k1 = k2
            if (abs(x - h1.toDouble() / k1) <= 1e-12 * maxOf(1.0, abs(x))) return h1 to k1
            val f = v - a
            if (f < 1e-15) break
            v = 1 / f
        }
        return null
    }

    /** 3/4, −5/2, 3π/4, √2/2, 2√3 … null when the plain decimal is the best form */
    fun exact(x: Double): String? {
        if (!x.isFinite() || x == 0.0 || x == Math.rint(x) || abs(x) > 1e9 || abs(x) < 1e-6) return null
        val sg = if (x < 0) "−" else ""
        val a = abs(x)
        fraction(a)?.let { (p, q) ->
            if (q == 1L) return null
            // 0.75 → 3/4 but 160.02 stays a decimal: fractions only when the decimal never ends, or for small halves/quarters/eighths
            var r = q; while (r % 2 == 0L) r /= 2; while (r % 5 == 0L) r /= 5
            return if (r != 1L || q <= 8) "$sg$p/$q" else null
        }
        fraction(a / PI, 100)?.let { (p, q) -> if (p <= 1000) return sg + (if (p == 1L) "" else "$p") + "π" + (if (q == 1L) "" else "/$q") }
        fraction(a * a, 1000)?.let { (p, q) ->
            if (p > 100000) return null
            var n = p * q          // √(p/q) = √(p·q)/q
            var k = 1L
            var f = 2L
            while (f * f <= n) { while (n % (f * f) == 0L) { n /= f * f; k *= f }; f++ }
            if (n == 1L) return null
            val g = gcd(k, q)
            val kk = k / g; val qq = q / g
            return sg + (if (kk == 1L) "" else "$kk") + "√$n" + (if (qq == 1L) "" else "/$qq")
        }
        return null
    }

    // ================= units =================
    fun unitLabel(u: UDef, o: SciOpts): String = if (o.abbrev && u.sym.isNotEmpty()) u.sym else u.name(o.lang)

    fun dimText(d: Dim, negExp: Boolean = true): String {
        if (d.none) return "1"
        val order = intArrayOf(Dim.M, Dim.L, Dim.T, Dim.K, Dim.I, Dim.N, Dim.J, Dim.B, Dim.A, Dim.F)
        val pos = ArrayList<String>(); val neg = ArrayList<String>()
        for (k in order) {
            val e = d.e[k]
            if (e == 0) continue
            val s = Dim.SYM[k]
            when {
                e > 0 -> pos.add(s + if (e == 1) "" else sup(e))
                negExp -> pos.add(s + sup(e))
                else -> neg.add(s + if (e == -1) "" else sup(-e))
            }
        }
        val top = if (pos.isEmpty()) "1" else pos.joinToString("·")
        return if (neg.isEmpty()) top else top + "/" + (if (neg.size == 1) neg[0] else "(" + neg.joinToString("·") + ")")
    }

    class Shown(val num: String, val unit: String, val approx: String?)

    /** the answer as text: number part, unit part, and "≈ 0.75" when the number is shown as 3/4 */
    fun show(q: SQ, unit: UDef?, o: SciOpts): Shown {
        var re = q.re; var im = q.im
        var ul = ""
        if (unit != null) {
            re = unit.fromBase(q.re)
            im = if (unit.off == 0.0 && !unit.inv) q.im / unit.k else 0.0
            ul = unitLabel(unit, o)
        } else if (!q.d.none) ul = dimText(q.d, o.negExp)
        if (im != 0.0) return Shown(complex(re, im, o), ul, null)
        if (o.exact && o.mode == 0 && o.dec < 0) {
            val ex = exact(re)
            if (ex != null) return Shown(ex, ul, "≈ " + real(re, o))
        }
        return Shown(real(re, o), ul, null)
    }

    fun complex(re: Double, im: Double, o: SciOpts): String {
        val sp = if (o.spacious) " " else ""
        val ims = when {
            abs(im) == 1.0 -> "i"
            else -> real(abs(im), o) + "i"
        }
        if (re == 0.0) return (if (im < 0) "−" else "") + ims
        return real(re, o) + sp + (if (im < 0) "−" else "+") + sp + ims
    }

    /** the answer as text that can be typed back: "3+4i", "12.5 ft" */
    fun rawOf(q: SQ, unit: UDef?, o: SciOpts): String {
        val re = unit?.fromBase(q.re) ?: q.re
        val im = if (unit != null && unit.off == 0.0 && !unit.inv) q.im / unit.k else if (unit == null) q.im else 0.0
        var s = raw(re, o)
        if (im != 0.0) s = (if (re == 0.0) "" else s) + (if (im < 0) "−" else if (re == 0.0) "" else "+") + (if (abs(im) == 1.0) "" else raw(abs(im), o)) + "i"
        if (unit != null) s += " " + SciMath.unitText(unit)
        else if (!q.d.none) return "Ans"
        return s
    }
}
