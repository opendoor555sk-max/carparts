package com.nirmaan.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/*
 * Scientific calculator engine (pure Kotlin, unit tested).
 * Numbers can be complex (i) and can carry units (5 ft × 4 ft = 20 ft²).
 * Also: ∫ integral, d/dx derivative, Σ / Π, n!, %, X Y Z memories, solving "2X+3=11".
 */

class SciErr(val code: String, val arg: String = "") : Exception(code)

/** exponents of the base dimensions */
class Dim private constructor(val e: IntArray) {
    companion object {
        const val L = 0; const val M = 1; const val T = 2; const val K = 3; const val I = 4
        const val N = 5; const val J = 6; const val B = 7; const val A = 8; const val F = 9
        const val SIZE = 10
        val NONE = Dim(IntArray(SIZE))
        val SYM = arrayOf("m", "kg", "s", "K", "A", "mol", "cd", "B", "rad", "km/L")
        /** of(L,1, T,-1) */
        fun of(vararg p: Int): Dim {
            val a = IntArray(SIZE); var i = 0
            while (i + 1 < p.size) { a[p[i]] += p[i + 1]; i += 2 }
            return Dim(a)
        }
        private val CAT: Map<String, Dim> = mapOf(
            "len" to of(L, 1), "area" to of(L, 2), "vol" to of(L, 3), "mass" to of(M, 1), "temp" to of(K, 1),
            "time" to of(T, 1), "speed" to of(L, 1, T, -1), "press" to of(M, 1, L, -1, T, -2),
            "energy" to of(M, 1, L, 2, T, -2), "power" to of(M, 1, L, 2, T, -3), "count" to NONE, "angle" to of(A, 1),
            "data" to of(B, 1), "force" to of(M, 1, L, 1, T, -2), "torque" to of(M, 1, L, 2, T, -2),
            "flow" to of(L, 3, T, -1), "dens" to of(M, 1, L, -3), "fuel" to of(F, 1), "acc" to of(L, 1, T, -2),
            "freq" to of(T, -1), "volt" to of(M, 1, L, 2, T, -3, I, -1), "curr" to of(I, 1),
            "res" to of(M, 1, L, 2, T, -3, I, -2), "charge" to of(I, 1, T, 1), "cap" to of(M, -1, L, -2, T, 4, I, 2),
            "ind" to of(M, 1, L, 2, T, -2, I, -2), "light" to of(J, 1, L, -2), "rad" to of(T, -1),
            "dose" to of(L, 2, T, -2), "mag" to of(M, 1, T, -2, I, -1), "conc" to NONE,
            "visc" to of(M, 1, L, -1, T, -1), "amount" to of(N, 1)
        )
        fun ofCat(cat: String) = CAT[cat] ?: NONE
        fun parse(s: String): Dim {
            val p = s.split(',').mapNotNull { it.trim().toIntOrNull() }
            return if (p.size == SIZE) Dim(p.toIntArray()) else NONE
        }
    }
    val none get() = e.all { it == 0 }
    operator fun plus(o: Dim) = Dim(IntArray(SIZE) { e[it] + o.e[it] })
    operator fun minus(o: Dim) = Dim(IntArray(SIZE) { e[it] - o.e[it] })
    fun times(k: Int) = Dim(IntArray(SIZE) { e[it] * k })
    override fun equals(other: Any?) = other is Dim && other.e.contentEquals(e)
    override fun hashCode() = e.contentHashCode()
    override fun toString() = e.joinToString(",")
}

/** a value: complex number + dimension (stored in SI base units) */
class SQ(val re: Double, val im: Double = 0.0, val d: Dim = Dim.NONE) {
    val isReal get() = im == 0.0
    fun dim(nd: Dim) = SQ(re, im, nd)
    fun clean(): SQ {
        var r = re; var i = im
        if (i != 0.0 && abs(i) < 1e-13 * maxOf(1.0, abs(r))) i = 0.0
        if (r != 0.0 && i != 0.0 && abs(r) < 1e-13 * abs(i)) r = 0.0
        return if (r == re && i == im) this else SQ(r, i, d)
    }
    override fun toString() = "$re,$im,$d"
    companion object {
        fun parse(s: String?): SQ? {
            if (s.isNullOrEmpty()) return null
            val p = s.split(',')
            if (p.size < 2) return null
            val r = p[0].toDoubleOrNull() ?: return null
            val i = p[1].toDoubleOrNull() ?: return null
            return SQ(r, i, Dim.parse(p.drop(2).joinToString(",")))
        }
    }
}

object SciMath {
    // ================= complex helpers =================
    private fun c(r: Double, i: Double = 0.0) = SQ(r, i)
    fun add(a: SQ, b: SQ): SQ { same(a, b); return SQ(a.re + b.re, a.im + b.im, a.d).clean() }
    fun sub(a: SQ, b: SQ): SQ { same(a, b); return SQ(a.re - b.re, a.im - b.im, a.d).clean() }
    fun mul(a: SQ, b: SQ) = SQ(a.re * b.re - a.im * b.im, a.re * b.im + a.im * b.re, a.d + b.d).clean()
    fun div(a: SQ, b: SQ): SQ {
        val d = a.d - b.d
        if (b.im == 0.0) return SQ(a.re / b.re, if (a.im == 0.0) 0.0 else a.im / b.re, d).clean()
        val den = b.re * b.re + b.im * b.im
        return SQ((a.re * b.re + a.im * b.im) / den, (a.im * b.re - a.re * b.im) / den, d).clean()
    }
    private fun same(a: SQ, b: SQ) { if (a.d != b.d) throw SciErr("mismatch", SciFmt.dimText(a.d) + " ≠ " + SciFmt.dimText(b.d)) }
    private fun plain(a: SQ, what: String = "") { if (!a.d.none) throw SciErr("needplain", what) }

    fun cexp(z: SQ): SQ { val m = exp(z.re); return if (z.im == 0.0) c(m) else c(m * cos(z.im), m * sin(z.im)).clean() }
    fun cln(z: SQ): SQ = if (z.im == 0.0 && z.re >= 0) c(ln(z.re)) else c(ln(hypot(z.re, z.im)), atan2(z.im, z.re)).clean()
    fun cpow(a: SQ, b: SQ): SQ {
        if (a.im == 0.0 && b.im == 0.0) {
            val x = a.re; val y = b.re
            if (x >= 0 || y == floor(y)) return c(Math.pow(x, y))
            // negative ^ fraction: odd root of a negative stays real (−8^(1/3) = −2)
            val inv = 1 / y
            if (abs(inv - round(inv)) < 1e-9 && round(inv).toLong() % 2L != 0L) return c(-Math.pow(-x, y))
        }
        if (a.re == 0.0 && a.im == 0.0) return if (b.re > 0) c(0.0) else c(Double.POSITIVE_INFINITY)
        return cexp(mul(b, cln(a)))
    }
    fun csqrt(z: SQ): SQ {
        if (z.im == 0.0) return if (z.re >= 0) c(sqrt(z.re)) else c(0.0, sqrt(-z.re))
        val m = hypot(z.re, z.im)
        val r = sqrt((m + z.re) / 2); val i = sqrt((m - z.re) / 2) * (if (z.im < 0) -1 else 1)
        return c(r, i)
    }
    fun csin(z: SQ) = if (z.im == 0.0) c(snap(sin(z.re))) else c(sin(z.re) * cosh(z.im), cos(z.re) * sinh(z.im)).clean()
    fun ccos(z: SQ) = if (z.im == 0.0) c(snap(cos(z.re))) else c(cos(z.re) * cosh(z.im), -sin(z.re) * sinh(z.im)).clean()
    fun ctan(z: SQ): SQ {
        if (z.im == 0.0) {
            val cs = snap(cos(z.re))
            return if (cs == 0.0) c(Double.NaN) else c(snap(sin(z.re)) / cs)
        }
        return div(csin(z), ccos(z))
    }
    private fun snap(v: Double) = if (abs(v) < 1e-15) 0.0 else v
    private fun snapC(v: Double) = if (abs(v) < 1e-14) 0.0 else v
    private val I1 = c(0.0, 1.0)
    fun casin(z: SQ): SQ {
        if (z.im == 0.0 && abs(z.re) <= 1) return c(Math.asin(z.re))
        // −i ln(iz + √(1−z²))
        val w = add(mul(I1, z), csqrt(sub(c(1.0), mul(z, z))))
        return mul(c(0.0, -1.0), cln(w))
    }
    fun cacos(z: SQ): SQ = if (z.im == 0.0 && abs(z.re) <= 1) c(Math.acos(z.re)) else sub(c(PI / 2), casin(z))
    fun catan(z: SQ): SQ {
        if (z.im == 0.0) return c(Math.atan(z.re))
        // i/2 (ln(1−iz) − ln(1+iz))
        val iz = mul(I1, z)
        return mul(c(0.0, 0.5), sub(cln(sub(c(1.0), iz)), cln(add(c(1.0), iz))))
    }

    fun gamma(x: Double): Double {
        if (x < 0.5) return PI / (sin(PI * x) * gamma(1 - x))
        val g = 7.0
        val p = doubleArrayOf(0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7)
        val xx = x - 1
        var a = p[0]
        val t = xx + g + 0.5
        for (i in 1 until 9) a += p[i] / (xx + i)
        return sqrt(2 * PI) * Math.pow(t, xx + 0.5) * exp(-t) * a
    }

    // ================= units in the text =================
    /** exact symbols (case sensitive) → unit; the most used unit wins a shared symbol */
    val SYM: Map<String, UDef> by lazy {
        val m = HashMap<String, UDef>()
        for (d in Units.UNITS.sortedByDescending { it.pop }) if (d.sym.isNotEmpty() && !m.containsKey(d.sym)) m[d.sym] = d
        m
    }
    private val SYM_SORTED: List<String> by lazy { SYM.keys.sortedByDescending { it.length } }
    val PREFIX = listOf(
        Triple("da", 1e1, "deca"), Triple("Y", 1e24, "yotta"), Triple("Z", 1e21, "zetta"), Triple("P", 1e15, "peta"),
        Triple("T", 1e12, "tera"), Triple("G", 1e9, "giga"), Triple("M", 1e6, "mega"), Triple("k", 1e3, "kilo"),
        Triple("h", 1e2, "hecto"), Triple("d", 1e-1, "deci"), Triple("c", 1e-2, "centi"), Triple("m", 1e-3, "milli"),
        Triple("µ", 1e-6, "micro"), Triple("u", 1e-6, "micro"), Triple("n", 1e-9, "nano"), Triple("p", 1e-12, "pico"),
        Triple("f", 1e-15, "femto"), Triple("a", 1e-18, "atto")
    )

    /** the text to put in an expression for a unit: its symbol, or [name] */
    fun unitText(d: UDef): String = if (d.sym.isNotEmpty() && SYM[d.sym] === d && !d.sym.contains(' ')) d.sym else "[" + d.en + "]"

    private fun symAt(s: String, i: Int): String? {
        for (k in SYM_SORTED) {
            if (s.startsWith(k, i)) {
                val nx = s.getOrNull(i + k.length)
                if (nx == null || !(nx.isLetter() || nx == '_')) return k
            }
        }
        return null
    }

    private fun unitByName(n: String): UDef? {
        val t = n.trim()
        return Units.UNITS.firstOrNull { it.en == t || it.hi == t || it.gu == t || it.id == t } ?: SYM[t] ?: Units.find(t)
    }

    // ================= lexer =================
    private const val NUM = 0; private const val OP = 1; private const val FUNC = 2; private const val ID = 3
    private const val UNIT = 4; private const val END = 5

    private class Tok(val k: Int, val s: String, val v: Double = 0.0, val u: UDef? = null)

    val FUNCS = setOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "ln", "log", "exp",
        "sqrt", "cbrt", "abs", "Re", "Im", "arg", "conj", "floor", "ceil", "round", "int", "der", "sum", "prod")
    private val NAMES = setOf("e", "i", "pi", "Ans", "X", "Y", "Z", "x", "y", "z")
    private const val SUPD = "⁻⁰¹²³⁴⁵⁶⁷⁸⁹"
    val VARS = listOf("X", "Y", "Z", "x", "y", "z")

    private fun lex(src: String, comma: Boolean): List<Tok> {
        val out = ArrayList<Tok>()
        val dsep = if (comma) ',' else '.'
        var i = 0
        val s = src
        while (i < s.length) {
            val ch = s[i]
            if (ch.isWhitespace() || ch == ' ' || ch == ' ') { i++; continue }
            // numbers
            if (ch.isDigit() || (ch == dsep && s.getOrNull(i + 1)?.isDigit() == true)) {
                val st = i
                while (i < s.length && s[i].isDigit()) i++
                if (i < s.length && s[i] == dsep) { i++; while (i < s.length && s[i].isDigit()) i++ }
                if (i < s.length && s[i] == 'E') {
                    var j = i + 1
                    if (j < s.length && (s[j] == '+' || s[j] == '-' || s[j] == '−')) j++
                    if (j < s.length && s[j].isDigit()) { while (j < s.length && s[j].isDigit()) j++; i = j }
                }
                val txt = s.substring(st, i).replace(dsep, '.').replace('−', '-')
                out.add(Tok(NUM, txt, txt.toDoubleOrNull() ?: throw SciErr("syntax", txt)))
                continue
            }
            if (s.startsWith("d/dx(", i)) { out.add(Tok(FUNC, "der")); i += 4; continue }
            when (ch) {
                '+' -> { out.add(Tok(OP, "+")); i++; continue }
                '-', '−' -> { out.add(Tok(OP, "-")); i++; continue }
                '×', '*', '·' -> { out.add(Tok(OP, "*")); i++; continue }
                '÷', '/' -> { out.add(Tok(OP, "/")); i++; continue }
                '^' -> { out.add(Tok(OP, "^")); i++; continue }
                '⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹', '⁻' -> {
                    // x⁷, 10⁻³ : superscript power
                    var j = i; val sb = StringBuilder()
                    while (j < s.length && SUPD.indexOf(s[j]) >= 0) { sb.append("-0123456789"[SUPD.indexOf(s[j])]); j++ }
                    val v = sb.toString().toDoubleOrNull() ?: throw SciErr("syntax", s.substring(i, j))
                    out.add(Tok(OP, "^")); out.add(Tok(NUM, sb.toString(), v)); i = j; continue
                }
                '∠' -> { out.add(Tok(OP, "∠")); i++; continue }
                ']' -> { out.add(Tok(OP, ")")); i++; continue }
                '(', ')', '%', '!', '=' -> { out.add(Tok(OP, ch.toString())); i++; continue }
                ',', ';' -> { out.add(Tok(OP, ",")); i++; continue }
                '→' -> { out.add(Tok(OP, "→")); i++; continue }
                '√' -> { out.add(Tok(OP, "√")); i++; continue }
                '∛' -> { out.add(Tok(OP, "∛")); i++; continue }
                'π' -> { out.add(Tok(ID, "pi")); i++; continue }
                '∞' -> { out.add(Tok(NUM, "∞", Double.POSITIVE_INFINITY)); i++; continue }
                '∫' -> { out.add(Tok(FUNC, "int")); i++; continue }
                'Σ' -> { out.add(Tok(FUNC, "sum")); i++; continue }
                'Π' -> { out.add(Tok(FUNC, "prod")); i++; continue }
                '[' -> {
                    // [vigha (Gujarat)] = a unit by name; otherwise [ ] work like ( )
                    val e = s.indexOf(']', i)
                    val name = if (e > i) s.substring(i + 1, e) else ""
                    val u = if (name.isNotBlank() && name.none { it.isDigit() }) unitByName(name) else null
                    if (u != null) { out.add(Tok(UNIT, name, u = u)); i = e + 1 } else { out.add(Tok(OP, "(")); i++ }
                    continue
                }
            }
            if (ch.isLetter()) {
                var j = i
                while (j < s.length && s[j].isLetter() && s[j].code < 128) j++
                val w = s.substring(i, j)
                if (w.isNotEmpty() && w in FUNCS && s.getOrNull(j) == '(') { out.add(Tok(FUNC, w)); i = j; continue }
                if (w.isNotEmpty() && w in NAMES) { out.add(Tok(ID, w)); i = j; continue }
            }
            // a unit symbol, maybe with a prefix (kN, MPa …)
            val k = symAt(s, i)
            if (k != null) { out.add(Tok(UNIT, k, u = SYM.getValue(k))); i += k.length; continue }
            var done = false
            for ((p, f, pn) in PREFIX) {
                if (!s.startsWith(p, i)) continue
                val k2 = symAt(s, i + p.length) ?: continue
                val base = SYM.getValue(k2)
                if (base.off != 0.0 || base.inv) continue
                val syn = UDef("pre_" + p + k2, base.cat, base.k * f, pn + base.en, "", "", p + k2, base.cc, emptyList(), "", 0.0, false, 0)
                out.add(Tok(UNIT, p + k2, u = syn)); i += p.length + k2.length; done = true; break
            }
            if (done) continue
            var j = i
            while (j < s.length && s[j].isLetter()) j++
            throw SciErr("unknown", s.substring(i, maxOf(j, i + 1)))
        }
        out.add(Tok(END, ""))
        return out
    }

    // ================= syntax tree =================
    sealed class Nd
    class NNum(val q: SQ, val unit: UDef? = null, val withNum: Boolean = false) : Nd()
    class NVar(val n: String) : Nd()
    class NNeg(val a: Nd) : Nd()
    class NBin(val op: Char, val a: Nd, val b: Nd) : Nd()
    class NPct(val a: Nd) : Nd()
    class NFact(val a: Nd) : Nd()
    class NCall(val f: String, val args: List<Nd>) : Nd()

    class Parsed(val lhs: Nd, val rhs: Nd?, val target: UDef?, val hints: List<UDef>)

    private class P(val t: List<Tok>) {
        var p = 0
        val hints = ArrayList<UDef>()
        fun peek() = t[p]
        fun isOp(s: String) = t[p].k == OP && t[p].s == s
        fun take() = t[p++]
        fun expect(s: String) {
            if (isOp(s)) { p++; return }
            if (s == ")" && t[p].k == END) return // brackets close by themselves at the end
            throw SciErr(if (s == ")") "paren" else "syntax", t[p].s)
        }

        fun top(): Parsed {
            if (t[0].k == END) throw SciErr("empty")
            val lhs = sum()
            var rhs: Nd? = null
            if (isOp("=")) { p++; rhs = sum() }
            var target: UDef? = null
            if (isOp("→")) {
                p++
                val u = take()
                if (u.k != UNIT) throw SciErr("nounit")
                target = u.u
            }
            if (isOp(")")) throw SciErr("paren", ")")
            if (t[p].k != END) throw SciErr("syntax", t[p].s)
            return Parsed(lhs, rhs, target, hints)
        }

        fun sum(): Nd {
            var a = term()
            while (isOp("+") || isOp("-")) {
                val op = take().s[0]
                a = NBin(op, a, term())
            }
            return a
        }

        private fun startsFactor(k: Tok) = k.k == NUM || k.k == UNIT || k.k == ID || k.k == FUNC ||
            (k.k == OP && (k.s == "(" || k.s == "√" || k.s == "∛"))

        fun term(): Nd {
            var a = unary()
            while (true) {
                if (isOp("*") || isOp("/") || isOp("∠")) {
                    val op = take().s[0]
                    a = NBin(op, a, unary())
                } else if (startsFactor(peek())) {
                    val b = power()
                    // "5 ft 3 in" → 5 ft + 3 in
                    a = if (a is NNum && b is NNum && a.withNum && b.withNum && a.unit != null && b.unit != null &&
                        a.q.d == b.q.d && !a.q.d.none) NBin('+', a, b) else NBin('*', a, b)
                } else break
            }
            return a
        }

        fun unary(): Nd {
            if (isOp("-")) { p++; return NNeg(unary()) }
            if (isOp("+")) { p++; return unary() }
            return power()
        }

        fun power(): Nd {
            val a = postfix()
            if (isOp("^")) { p++; return NBin('^', a, unary()) }
            return a
        }

        fun postfix(): Nd {
            var a = primary()
            while (true) {
                a = when {
                    isOp("!") -> { p++; NFact(a) }
                    isOp("%") -> { p++; NPct(a) }
                    else -> return a
                }
            }
        }

        /** a unit, with an optional power on the unit alone: m², s^-1 */
        private fun unitQ(u: UDef, v: Double?): NNum {
            hints.add(u)
            var e = 1.0
            if (isOp("^") && (t[p + 1].k == NUM || (t[p + 1].k == OP && t[p + 1].s == "-" && t[p + 2].k == NUM))) {
                p++
                var sg = 1.0
                if (isOp("-")) { p++; sg = -1.0 }
                e = sg * take().v
            }
            if (e != 1.0) {
                if (u.off != 0.0 || u.inv) throw SciErr("syntax", u.sym)
                val ei = round(e).toInt()
                if (abs(e - ei) > 1e-9) throw SciErr("syntax", "^$e")
                val q = SQ((v ?: 1.0) * Math.pow(u.k, e), 0.0, Dim.ofCat(u.cat).times(ei))
                return NNum(q, null, v != null)
            }
            return NNum(SQ(u.toBase(v ?: 1.0), 0.0, Dim.ofCat(u.cat)), u, v != null)
        }

        fun primary(): Nd {
            val k = take()
            when (k.k) {
                NUM -> {
                    if (peek().k == UNIT) return unitQ(take().u!!, k.v)
                    return NNum(SQ(k.v))
                }
                UNIT -> return unitQ(k.u!!, null)
                ID -> return when (k.s) {
                    "pi" -> NNum(SQ(PI))
                    "e" -> NNum(SQ(Math.E))
                    "i" -> NNum(SQ(0.0, 1.0))
                    else -> NVar(k.s)
                }
                FUNC -> {
                    expect("(")
                    val args = ArrayList<Nd>()
                    if (!isOp(")")) {
                        args.add(sum())
                        while (isOp(",")) { p++; args.add(sum()) }
                    }
                    expect(")")
                    return NCall(k.s, args)
                }
                OP -> when (k.s) {
                    "(" -> { val a = sum(); expect(")"); return a }
                    "√" -> return NCall("sqrt", listOf(power()))
                    "∛" -> return NCall("cbrt", listOf(power()))
                }
                END -> throw SciErr("incomplete")
            }
            throw SciErr("syntax", k.s)
        }
    }

    fun parse(text: String, comma: Boolean = false): Parsed = P(lex(text, comma)).top()

    // ================= evaluating =================
    class Env(val vars: Map<String, SQ>, val angle: Int = 0, val ans: SQ? = null)

    private fun toRad(a: SQ, angle: Int): SQ {
        if (a.d == Dim.of(Dim.A, 1)) return a.dim(Dim.NONE)
        plain(a, "sin")
        if (!a.isReal) return a
        return when (angle) {
            0 -> SQ((a.re % 360.0) * PI / 180)
            2 -> SQ((a.re % 400.0) * PI / 200)
            else -> a
        }
    }

    private fun fromRad(a: SQ, angle: Int): SQ {
        if (!a.isReal) return a
        return when (angle) { 0 -> SQ(a.re * 180 / PI); 2 -> SQ(a.re * 200 / PI); else -> a }
    }

    fun ev(n: Nd, env: Env, loc: Map<String, SQ> = emptyMap()): SQ = when (n) {
        is NNum -> n.q
        is NVar -> loc[n.n] ?: if (n.n == "Ans") env.ans ?: SQ(0.0) else env.vars[n.n] ?: SQ(0.0)
        is NNeg -> ev(n.a, env, loc).let { SQ(-it.re, -it.im, it.d) }
        is NPct -> ev(n.a, env, loc).let { SQ(it.re / 100, it.im / 100, it.d) }
        is NFact -> {
            val a = ev(n.a, env, loc)
            plain(a, "!")
            if (!a.isReal) throw SciErr("domain", "!")
            val x = a.re
            if (x < 0 && x == floor(x)) throw SciErr("domain", "!")
            if (x == floor(x) && x <= 170) { var r = 1.0; var k = 2.0; while (k <= x) { r *= k; k++ }; SQ(r) } else SQ(gamma(x + 1))
        }
        is NBin -> {
            val a = ev(n.a, env, loc)
            when (n.op) {
                '+', '-' -> if (n.b is NPct) {
                    // 200 + 10% = 220
                    val pc = ev(n.b.a, env, loc)
                    plain(pc, "%")
                    val part = mul(a, SQ(pc.re / 100, pc.im / 100))
                    if (n.op == '+') add(a, part) else sub(a, part)
                } else {
                    val b = ev(n.b, env, loc)
                    if (n.op == '+') add(a, b) else sub(a, b)
                }
                '*' -> mul(a, ev(n.b, env, loc))
                '∠' -> {
                    // r∠θ = r·(cos θ + i sin θ), θ in the angle mode
                    val th = toRad(ev(n.b, env, loc), env.angle)
                    mul(a, SQ(snapC(cos(th.re)), snapC(sin(th.re))))
                }
                '/' -> div(a, ev(n.b, env, loc))
                '^' -> powQ(a, ev(n.b, env, loc))
                else -> throw SciErr("syntax")
            }
        }
        is NCall -> call(n, env, loc)
    }

    private fun powQ(a: SQ, b: SQ): SQ {
        plain(b, "^")
        if (a.d.none) return cpow(a, b).clean()
        if (!b.isReal) throw SciErr("domain", "^")
        val ne = IntArray(Dim.SIZE)
        for (k in 0 until Dim.SIZE) {
            val x = a.d.e[k] * b.re
            if (abs(x - round(x)) > 1e-9) throw SciErr("domain", "^")
            ne[k] = round(x).toInt()
        }
        var nd = Dim.NONE
        for (k in 0 until Dim.SIZE) if (ne[k] != 0) nd = nd + Dim.of(k, ne[k])
        return cpow(a.dim(Dim.NONE), b).dim(nd).clean()
    }

    private fun nargs(n: NCall, k: Int) { if (n.args.size != k) throw SciErr("args", n.f) }

    private fun call(n: NCall, env: Env, loc: Map<String, SQ>): SQ {
        val f = n.f
        when (f) {
            "int" -> { nargs(n, 3); return integral(n, env, loc) }
            "der" -> { nargs(n, 2); return derivative(n, env, loc) }
            "sum", "prod" -> { nargs(n, 3); return series(n, env, loc) }
            "log" -> if (n.args.size == 2) {
                val a = ev(n.args[0], env, loc); val b = ev(n.args[1], env, loc)
                plain(a, "log"); plain(b, "log")
                return div(cln(a), cln(b))
            }
        }
        nargs(n, 1)
        val a = ev(n.args[0], env, loc)
        return when (f) {
            "sin" -> csin(toRad(a, env.angle))
            "cos" -> ccos(toRad(a, env.angle))
            "tan" -> ctan(toRad(a, env.angle))
            "asin" -> { plain(a, f); fromRad(casin(a), env.angle) }
            "acos" -> { plain(a, f); fromRad(cacos(a), env.angle) }
            "atan" -> { plain(a, f); fromRad(catan(a), env.angle) }
            "sinh" -> { plain(a, f); val e1 = cexp(a); val e2 = cexp(SQ(-a.re, -a.im)); SQ((e1.re - e2.re) / 2, (e1.im - e2.im) / 2).clean() }
            "cosh" -> { plain(a, f); val e1 = cexp(a); val e2 = cexp(SQ(-a.re, -a.im)); SQ((e1.re + e2.re) / 2, (e1.im + e2.im) / 2).clean() }
            "tanh" -> { plain(a, f); val e1 = cexp(a); val e2 = cexp(SQ(-a.re, -a.im))
                div(SQ(e1.re - e2.re, e1.im - e2.im), SQ(e1.re + e2.re, e1.im + e2.im)) }
            "ln" -> { plain(a, f); cln(a) }
            "log" -> { plain(a, f); val l = cln(a); SQ(l.re / ln(10.0), l.im / ln(10.0)).clean() }
            "exp" -> { plain(a, f); cexp(a) }
            "sqrt" -> if (a.d.none) csqrt(a) else powQ(a, SQ(0.5))
            "cbrt" -> if (a.d.none && a.isReal) SQ(Math.cbrt(a.re)) else powQ(a, SQ(1.0 / 3))
            "abs" -> SQ(hypot(a.re, a.im), 0.0, a.d)
            "Re" -> SQ(a.re, 0.0, a.d)
            "Im" -> SQ(a.im, 0.0, a.d)
            "conj" -> SQ(a.re, -a.im, a.d)
            "arg" -> fromRad(SQ(atan2(a.im, a.re)), env.angle)
            "floor" -> SQ(floor(a.re), 0.0, a.d)
            "ceil" -> SQ(Math.ceil(a.re), 0.0, a.d)
            "round" -> SQ(Math.rint(a.re), 0.0, a.d)
            else -> throw SciErr("unknown", f)
        }
    }

    // ---- ∫(f(X), a, b): Gauss–Legendre on 128 panels; ∞ limits by change of variable ----
    private val GX = doubleArrayOf(0.1834346424956498, 0.5255324099163290, 0.7966664774136267, 0.9602898564975363)
    private val GW = doubleArrayOf(0.3626837833783620, 0.3137066458778873, 0.2223810344533745, 0.1012285362903763)

    private fun gl(g: (Double) -> SQ, lo: Double, hi: Double, panels: Int): SQ {
        var sr = 0.0; var si = 0.0; var d: Dim? = null
        val h = (hi - lo) / panels
        for (pn in 0 until panels) {
            val mid = lo + (pn + 0.5) * h
            for (k in 0 until 4) for (sg in intArrayOf(-1, 1)) {
                val v = g(mid + sg * GX[k] * h / 2)
                if (d == null) d = v.d else if (d != v.d) throw SciErr("mismatch")
                sr += GW[k] * v.re * h / 2; si += GW[k] * v.im * h / 2
            }
        }
        return SQ(sr, si, d ?: Dim.NONE).clean()
    }

    private fun integral(n: NCall, env: Env, loc: Map<String, SQ>): SQ {
        val qa = ev(n.args[1], env, loc); val qb = ev(n.args[2], env, loc)
        if (!qa.isReal || !qb.isReal) throw SciErr("domain", "∫")
        val xd = if (qa.re.isInfinite()) qb.d else qa.d
        if (!qa.re.isInfinite() && !qb.re.isInfinite() && qa.d != qb.d) throw SciErr("mismatch")
        val a = qa.re; val b = qb.re
        if (a == b) return SQ(0.0, 0.0, Dim.NONE)
        val sign = if (a > b) -1.0 else 1.0
        val lo = minOf(a, b); val hi = maxOf(a, b)
        fun f(x: Double): SQ = ev(n.args[0], env, loc + ("X" to SQ(x, 0.0, xd)))
        val r: SQ = when {
            lo.isInfinite() && hi.isInfinite() -> gl({ t -> val x = t / (1 - t * t); val w = (1 + t * t) / ((1 - t * t) * (1 - t * t)); f(x).let { SQ(it.re * w, it.im * w, it.d) } }, -1.0, 1.0, 256)
            hi.isInfinite() -> gl({ t -> val x = lo + t / (1 - t); val w = 1 / ((1 - t) * (1 - t)); f(x).let { SQ(it.re * w, it.im * w, it.d) } }, 0.0, 1.0, 256)
            lo.isInfinite() -> gl({ t -> val x = hi - t / (1 - t); val w = 1 / ((1 - t) * (1 - t)); f(x).let { SQ(it.re * w, it.im * w, it.d) } }, 0.0, 1.0, 256)
            else -> gl(::f, lo, hi, 128)
        }
        return SQ(sign * r.re, sign * r.im, r.d + xd).clean()
    }

    // ---- d/dx(f(X), a): central difference + Richardson ----
    private fun derivative(n: NCall, env: Env, loc: Map<String, SQ>): SQ {
        val qa = ev(n.args[1], env, loc)
        if (!qa.isReal) throw SciErr("domain", "d/dx")
        val a = qa.re
        fun f(x: Double) = ev(n.args[0], env, loc + ("X" to SQ(x, 0.0, qa.d)))
        fun dd(h: Double): SQ { val p = f(a + h); val m = f(a - h); return SQ((p.re - m.re) / (2 * h), (p.im - m.im) / (2 * h), p.d) }
        val h = 1e-3 * maxOf(1.0, abs(a))
        val d1 = dd(h); val d2 = dd(h / 2)
        val re = (4 * d2.re - d1.re) / 3; val im = (4 * d2.im - d1.im) / 3
        return SQ(roundTiny(re), roundTiny(im), d1.d - qa.d).clean()
    }
    private fun roundTiny(v: Double): Double {
        // 11.999999998 → 12 (difference error), keep real decimals
        val r = Math.rint(v * 1e7) / 1e7
        return if (abs(r - v) < 1e-7 * maxOf(1.0, abs(v))) r else v
    }

    // ---- Σ(f(X), a, b) and Π ----
    private fun series(n: NCall, env: Env, loc: Map<String, SQ>): SQ {
        val qa = ev(n.args[1], env, loc); val qb = ev(n.args[2], env, loc)
        plain(qa, "Σ"); plain(qb, "Σ")
        val a = Math.rint(qa.re).toLong(); val b = Math.rint(qb.re).toLong()
        if (b - a > 1_000_000) throw SciErr("big")
        var acc: SQ? = null
        var k = a
        while (k <= b) {
            val v = ev(n.args[0], env, loc + ("X" to SQ(k.toDouble())))
            acc = if (acc == null) v else if (n.f == "sum") add(acc, v) else mul(acc, v)
            k++
        }
        return acc ?: SQ(if (n.f == "sum") 0.0 else 1.0)
    }

    // ================= whole line =================
    class Out(
        val q: SQ,                  // the answer (SI)
        val unit: UDef?,           // show it in this unit (null = plain / composite)
        val assign: String? = null, // "X" when the line stored or solved a memory
        val roots: List<SQ> = emptyList(),
        val truth: Boolean? = null  // "2+2=4" → true
    )

    fun has(n: Nd, v: String): Boolean = when (n) {
        is NVar -> n.n == v
        is NNum -> false
        is NNeg -> has(n.a, v)
        is NPct -> has(n.a, v)
        is NFact -> has(n.a, v)
        is NBin -> has(n.a, v) || has(n.b, v)
        is NCall -> if (n.f in setOf("int", "der", "sum", "prod") && v == "X") n.args.drop(1).any { has(it, v) } else n.args.any { has(it, v) }
    }

    fun run(text: String, env: Env, comma: Boolean = false): Out {
        val p = parse(text, comma)
        val rhs = p.rhs
        if (rhs == null) {
            val q = ev(p.lhs, env)
            return Out(q, pick(q.d, p.hints, p.target))
        }
        val lv = p.lhs
        if (lv is NVar && lv.n in VARS && !has(rhs, lv.n)) {
            val q = ev(rhs, env)
            return Out(q, pick(q.d, p.hints, p.target), lv.n)
        }
        val v = VARS.firstOrNull { has(p.lhs, it) || has(rhs, it) }
        if (v == null) {
            val a = ev(p.lhs, env); val b = ev(rhs, env)
            if (a.d != b.d) return Out(a, null, truth = false)
            val ok = abs(a.re - b.re) <= 1e-9 * maxOf(1.0, abs(a.re), abs(b.re)) && abs(a.im - b.im) <= 1e-9 * maxOf(1.0, abs(a.im), abs(b.im))
            return Out(a, null, truth = ok)
        }
        val roots = solve(p.lhs, rhs, v, env)
        if (roots.isEmpty()) throw SciErr("noroot", v)
        return Out(roots[0], pick(roots[0].d, p.hints, p.target), v, roots)
    }

    /** solve lhs = rhs for the memory v (real roots, up to 3) */
    private fun solve(lhs: Nd, rhs: Nd, v: String, env: Env): List<SQ> {
        // which dimension must v have?  try none, then what balances the two sides
        fun dims(xd: Dim): Pair<Dim, Dim>? = try {
            val one = mapOf(v to SQ(1.0, 0.0, xd))
            ev(lhs, env, one).d to ev(rhs, env, one).d
        } catch (e: SciErr) { if (e.code == "mismatch") null else throw e }
        var xd = Dim.NONE
        val d0 = dims(xd)
        if (d0 == null || d0.first != d0.second) {
            val tries = if (d0 != null) listOf(d0.second - d0.first, d0.first - d0.second) else emptyList()
            xd = tries.firstOrNull { val d = dims(it); d != null && d.first == d.second } ?: throw SciErr("mismatch")
        }
        fun g(x: Double): Double {
            val m = mapOf(v to SQ(x, 0.0, xd))
            return try { val a = ev(lhs, env, m); val b = ev(rhs, env, m); if (abs(a.im - b.im) > 1e-9 * maxOf(1.0, abs(a.re))) Double.NaN else a.re - b.re } catch (e: SciErr) { Double.NaN }
        }
        val pts = ArrayList<Double>()
        pts.add(0.0)
        for (ex in -4..8) for (m in 1..9) { val x = m * Math.pow(10.0, ex.toDouble()); pts.add(x); pts.add(-x) }
        pts.sort()
        val roots = ArrayList<Double>()
        fun addRoot(r: Double) { if (roots.none { abs(it - r) <= 1e-9 * maxOf(1.0, abs(r)) }) roots.add(r) }
        var px = pts[0]; var py = g(px)
        if (py == 0.0) addRoot(px)
        for (k in 1 until pts.size) {
            val x = pts[k]; val y = g(x)
            if (y == 0.0) addRoot(x)
            else if (py.isFinite() && y.isFinite() && py != 0.0 && (py < 0) != (y < 0)) {
                var lo = px; var hi = x; var flo = py
                repeat(200) {
                    val mid = (lo + hi) / 2
                    val fm = g(mid)
                    if (!fm.isFinite()) return@repeat
                    if ((fm < 0) == (flo < 0)) { lo = mid; flo = fm } else hi = mid
                }
                val r = (lo + hi) / 2
                // a real root, not a jump (like 1/X at 0)
                val fr = g(r)
                if (fr.isFinite() && abs(fr) < 1e-6 * maxOf(1.0, abs(py), abs(y))) addRoot(clean(r))
            }
            px = x; py = y
        }
        if (roots.isEmpty()) {
            // Newton from a few starts (touching roots like (X−2)² = 0)
            for (s in doubleArrayOf(1.0, -1.0, 10.0, 0.5)) {
                var x = s
                repeat(100) {
                    val fx = g(x); val h = 1e-6 * maxOf(1.0, abs(x))
                    val d = (g(x + h) - g(x - h)) / (2 * h)
                    if (!fx.isFinite() || !d.isFinite() || d == 0.0) return@repeat
                    x -= fx / d
                }
                if (g(x).let { it.isFinite() && abs(it) < 1e-9 }) { addRoot(clean(x)); break }
            }
        }
        roots.sortBy { abs(it) }
        return roots.take(3).sorted().map { SQ(it, 0.0, xd) }
    }
    private fun clean(r: Double): Double { val k = Math.rint(r * 1e9) / 1e9; return if (abs(k - r) < 1e-10 * maxOf(1.0, abs(r))) k else r }

    // ================= which unit to show =================
    fun pick(d: Dim, hints: List<UDef>, target: UDef?): UDef? {
        if (target != null) {
            if (Dim.ofCat(target.cat) != d) throw SciErr("mismatch", SciFmt.dimText(d) + " → " + (target.sym.ifEmpty { target.en }))
            return target
        }
        if (d.none) return null
        hints.firstOrNull { Dim.ofCat(it.cat) == d }?.let { return it }
        val cats = Units.CATS.filter { it.id != "count" && it.id != "conc" && Dim.ofCat(it.id) == d }
        if (cats.isEmpty()) return null
        // build the scale from the units used: ft × ft → ft², km ÷ h → km/h
        var scale = 1.0; var ok = true
        for (k in 0 until Dim.SIZE) {
            val ex = d.e[k]
            if (ex == 0) continue
            val h = hints.firstOrNull { it.off == 0.0 && !it.inv && Dim.ofCat(it.cat) == Dim.of(k, 1) }
            if (h == null) { if (k == Dim.L || k == Dim.M || k == Dim.T) ok = false; continue }
            scale *= Math.pow(h.k, ex.toDouble())
        }
        if (ok && hints.isNotEmpty()) {
            for (c in cats) {
                val u = Units.inCat(c.id).filter { it.off == 0.0 && !it.inv && abs(it.k - scale) <= 1e-6 * scale }.maxByOrNull { it.pop }
                if (u != null) return u
            }
        }
        for (c in cats) {
            val u = Units.inCat(c.id).filter { it.k == 1.0 && it.off == 0.0 && !it.inv }.maxByOrNull { it.pop }
            if (u != null) return u
        }
        return null
    }
}
