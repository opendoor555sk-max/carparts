package com.kabadi.calc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Tape calculator (adding-machine style): every number you enter is a line on the tape with the operator
 * that follows it. Everything is calculated with BigDecimal, so 0.1 + 0.2 is exactly 0.3 – no floating errors.
 */
class TLine(var value: BigDecimal, var op: Char, var pct: Boolean = false, var open: Int = 0, var close: Int = 0, var note: String = "") {
    fun copy() = TLine(value, op, pct, open, close, note)
}

class TapeSettings(
    /** decimals shown: 0..6, -1 = floating (as many as needed) */
    var places: Int = 2,
    /** 0 = 1,234,567.89   1 = 1.234.567,89   2 = 1 234 567,89   3 = 12,34,567.89 (Indian) */
    var style: Int = 3,
    /** Smart mode: "100 + 10%" = 110 (percent of the running total); off: % is just ÷100 */
    var smart: Boolean = true,
    /** cash-register entry: digits come in from the right (1 2 5 = 1.25) */
    var cash: Boolean = false,
    var vibrate: Boolean = true,
    var showMem: Boolean = true,
    var showNotes: Boolean = true
)

object TapeMath {
    val MC: MathContext = MathContext.DECIMAL128
    private val HUNDRED = BigDecimal(100)

    /** acc <op> v (v may be a percent); null = error (division by zero) */
    fun apply(acc: BigDecimal, op: Char, v: BigDecimal, pct: Boolean, smart: Boolean): BigDecimal? {
        val p = v.divide(HUNDRED, MC)
        return when (op) {
            '+' -> if (!pct) acc.add(v, MC) else if (smart) acc.add(acc.multiply(p, MC), MC) else acc.add(p, MC)
            '-' -> if (!pct) acc.subtract(v, MC) else if (smart) acc.subtract(acc.multiply(p, MC), MC) else acc.subtract(p, MC)
            'x' -> acc.multiply(if (pct) p else v, MC)
            '/' -> { val d = if (pct) p else v; if (d.signum() == 0) null else acc.divide(d, MC) }
            else -> if (pct) p else v
        }
    }

    private class Frame(var acc: BigDecimal? = null, var pend: Char = ' ')

    /** running total of the tape, left to right like a pocket calculator; open brackets are closed at the end. null = error */
    fun total(lines: List<TLine>, smart: Boolean): BigDecimal? {
        if (lines.isEmpty()) return BigDecimal.ZERO
        val st = ArrayList<Frame>(); st.add(Frame())
        fun put(f: Frame, v: BigDecimal, pct: Boolean): Boolean {
            val a = f.acc
            f.acc = if (a == null) (if (pct) v.divide(HUNDRED, MC) else v) else (apply(a, f.pend, v, pct, smart) ?: return false)
            return true
        }
        fun pop(): Boolean {
            val f = st.removeAt(st.size - 1)
            return put(st.last(), f.acc ?: BigDecimal.ZERO, false)
        }
        for (l in lines) {
            repeat(l.open) { st.add(Frame()) }
            if (!put(st.last(), l.value, l.pct)) return null
            repeat(l.close) { if (st.size > 1 && !pop()) return null }
            st.last().pend = if (l.op == '=') ' ' else l.op
        }
        while (st.size > 1) if (!pop()) return null
        // 1 ÷ 3 × 3 shows 1, not 0.99999…: the answer is rounded to 20 significant digits
        return st[0].acc?.round(java.math.MathContext(20))
    }

    /** a value as text: [places] decimals (-1 = floating) and the chosen grouping style */
    fun fmt(v: BigDecimal, places: Int, style: Int): String {
        var x = if (places >= 0) v.setScale(places, RoundingMode.HALF_UP) else {
            var y = v.stripTrailingZeros(); if (y.scale() > 10) y = y.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
            if (y.scale() < 0) y = y.setScale(0); y
        }
        val neg = x.signum() < 0
        val s = x.abs().toPlainString()
        val frac = s.substringAfter('.', "")
        return (if (neg) "-" else "") + group(s.substringBefore('.'), style) + (if (frac.isEmpty()) "" else decSep(style) + frac)
    }

    fun decSep(style: Int) = if (style == 1 || style == 2) "," else "."

    fun group(int: String, style: Int): String {
        if (int.length <= 3) return int
        val sep = when (style) { 1 -> "."; 2 -> " "; else -> "," }
        if (style == 3) {
            val last = int.takeLast(3); var rest = int.dropLast(3); val parts = ArrayList<String>()
            while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
            if (rest.isNotEmpty()) parts.add(0, rest)
            return parts.joinToString(sep) + sep + last
        }
        return int.reversed().chunked(3).joinToString(sep).reversed()
    }

    /** the number being typed (keeps a trailing '.'), with grouping */
    fun fmtEntry(raw: String, style: Int): String {
        val ip = raw.substringBefore('.')
        return group(ip, style) + if (raw.contains('.')) decSep(style) + raw.substringAfter('.') else ""
    }
}

class TapeCalc(val set: TapeSettings) {
    val lines = mutableListOf<TLine>()
    /** typed number: digits and one '.' (cash entry: only digits, the decimal point is implied) */
    var cur = ""
    var neg = false
    var curPct = false
    var pendOpen = 0
    var pendClose = 0
    /** "=" was pressed: the next digit starts a new tape, an operator continues with the total */
    var closed = false
    /** called with the finished tape just before it is cleared */
    var archive: (List<TLine>) -> Unit = {}

    private val cashPlaces get() = if (set.places >= 0) set.places else 2

    fun total(): BigDecimal? = TapeMath.total(lines, set.smart)
    fun hasEntry() = cur.isNotEmpty() || pendOpen > 0
    fun unclosed() = lines.sumOf { it.open - it.close } + pendOpen - pendClose

    private fun fresh() {
        if (closed) { if (lines.isNotEmpty()) archive(lines.map { it.copy() }); lines.clear(); closed = false }
    }

    fun curValue(): BigDecimal? {
        if (cur.isEmpty()) return null
        var v = if (set.cash) BigDecimal(cur).movePointLeft(cashPlaces) else BigDecimal(cur.trimEnd('.').ifEmpty { "0" }.let { if (it.startsWith(".")) "0$it" else it })
        if (neg) v = v.negate()
        return v
    }

    fun digit(d: String) {
        fresh()
        if (set.cash) {
            if (cur.length + d.length > 15) return
            cur = (cur + d).trimStart('0').ifEmpty { "0" }
            return
        }
        if (cur.replace(".", "").length + d.length > 15) return
        cur = if (cur.isEmpty() || cur == "0") (if (d.all { it == '0' }) "0" else d.trimStart('0').ifEmpty { "0" }) else cur + d
    }

    fun dot() {
        if (set.cash) return
        fresh()
        if (cur.contains('.')) return
        cur = if (cur.isEmpty()) "0." else "$cur."
    }

    /** + − x / and = */
    fun op(c: Char) {
        if (c == '=') return equals()
        if (cur.isNotEmpty()) {
            lines.add(TLine(curValue()!!, c, curPct, pendOpen, pendClose)); clearEntry(); return
        }
        if (lines.isEmpty()) { if (c == '-') neg = !neg; return }
        if (closed) {
            val t = total() ?: return
            archive(lines.map { it.copy() }); lines.clear(); closed = false
            lines.add(TLine(t, c)); return
        }
        lines.last().op = c
    }

    fun equals() {
        if (cur.isNotEmpty()) { lines.add(TLine(curValue()!!, '=', curPct, pendOpen, pendClose)); clearEntry(); closed = true }
        else if (lines.isNotEmpty() && !closed) { lines.last().op = '='; closed = true }
    }

    fun percent() { if (cur.isNotEmpty()) curPct = !curPct }

    fun paren(open: Boolean) {
        if (open) {
            fresh()
            if (cur.isEmpty() && pendOpen < 6) pendOpen++
        } else if (unclosed() > 0) {
            if (cur.isNotEmpty()) pendClose++ else if (lines.isNotEmpty() && lines.last().close < lines.sumOf { it.open }) lines.last().close++
        }
    }

    fun negate() {
        if (cur.isNotEmpty()) neg = !neg else if (lines.isNotEmpty()) lines.last().value = lines.last().value.negate() else neg = !neg
    }

    /** ⌫: last typed digit; nothing typed: the last line comes back into the entry for correction */
    fun back() {
        if (cur.isNotEmpty()) { cur = cur.dropLast(1); if (cur.isEmpty()) { neg = false; curPct = false }; return }
        if (pendOpen > 0) { pendOpen--; return }
        if (lines.isEmpty()) return
        val l = lines.removeAt(lines.size - 1); closed = false
        pendOpen = l.open; pendClose = l.close; curPct = l.pct; neg = l.value.signum() < 0
        val a = l.value.abs()
        cur = if (set.cash) a.movePointRight(cashPlaces).toBigInteger().toString() else a.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()
    }

    /** CE: clear the entry; nothing typed: delete the last line */
    fun ce() {
        if (cur.isNotEmpty() || pendOpen > 0 || pendClose > 0) { clearEntry(); return }
        if (lines.isNotEmpty()) { lines.removeAt(lines.size - 1); closed = false }
    }

    fun ac() {
        if (lines.isNotEmpty()) archive(lines.map { it.copy() })
        lines.clear(); clearEntry(); closed = false
    }

    /** spoken lines: written under the tape (a finished tape starts a new one) */
    fun addSpoken(items: List<TapeSpeech.Item>) {
        if (items.isEmpty()) return
        fresh(); clearEntry()
        items.forEach { lines.add(TLine(it.amount, it.op, it.pct, 0, 0, it.note)) }
        closed = items.last().op == '='
    }

    private fun clearEntry() { cur = ""; neg = false; curPct = false; pendOpen = 0; pendClose = 0 }

    fun loadLines(l: List<TLine>) { lines.clear(); lines.addAll(l.map { it.copy() }); clearEntry(); closed = l.isNotEmpty() && l.last().op == '=' }

    /** the line text of the entry being typed ("" when nothing) */
    fun entryText(): String {
        if (cur.isEmpty() && pendOpen == 0 && !neg) return ""
        val body = if (cur.isEmpty()) (if (neg) "-" else "") else {
            if (set.cash) TapeMath.fmt((curValue() ?: BigDecimal.ZERO).abs(), cashPlaces, set.style).let { if (neg) "-$it" else it }
            else (if (neg) "-" else "") + TapeMath.fmtEntry(cur, set.style)
        }
        return "(".repeat(pendOpen) + body + (if (curPct && cur.isNotEmpty()) "%" else "") + ")".repeat(pendClose)
    }

    fun lineText(l: TLine): String =
        "(".repeat(l.open) + TapeMath.fmt(l.value, if (l.pct) -1 else set.places, set.style) + (if (l.pct) "%" else "") + ")".repeat(l.close)

    fun opChar(l: TLine) = when (l.op) { 'x' -> "x"; '/' -> "÷"; '-' -> "−"; else -> l.op.toString() }

    /** plain text of the whole tape (share / print) */
    fun text(title: String = ""): String {
        val sb = StringBuilder()
        if (title.isNotBlank()) sb.append(title).append('\n')
        val w = lines.maxOfOrNull { lineText(it).length } ?: 0
        lines.forEachIndexed { i, l ->
            sb.append(String.format("%2d. ", i + 1)).append(lineText(l).padStart(w)).append(' ').append(opChar(l))
            if (l.note.isNotBlank()) sb.append("   ").append(l.note)
            sb.append('\n')
        }
        sb.append("= ").append(total()?.let { TapeMath.fmt(it, set.places, set.style) } ?: "Error")
        return sb.toString()
    }

    // ---- save / load ----
    fun toJson(): JSONObject = JSONObject().put("l", linesJson(lines)).put("cur", cur).put("neg", neg).put("cp", curPct)
        .put("po", pendOpen).put("pc", pendClose).put("cl", closed)

    fun fromJson(o: JSONObject) {
        lines.clear(); lines.addAll(linesFrom(o.optJSONArray("l")))
        cur = o.optString("cur"); neg = o.optBoolean("neg"); curPct = o.optBoolean("cp")
        pendOpen = o.optInt("po"); pendClose = o.optInt("pc"); closed = o.optBoolean("cl")
        if (cur.isNotEmpty() && !Regex("[0-9]*\\.?[0-9]*").matches(cur)) clearEntry()
    }

    companion object {
        fun linesJson(l: List<TLine>): JSONArray = JSONArray().also { a -> l.forEach {
            a.put(JSONObject().put("v", it.value.toPlainString()).put("o", it.op.toString()).put("p", it.pct).put("a", it.open).put("c", it.close).put("n", it.note)) } }
        fun linesFrom(a: JSONArray?): List<TLine> = (0 until (a?.length() ?: 0)).mapNotNull { i ->
            val o = a!!.getJSONObject(i)
            try { TLine(BigDecimal(o.optString("v", "0")), o.optString("o", "+").firstOrNull() ?: '+', o.optBoolean("p"), o.optInt("a"), o.optInt("c"), o.optString("n")) } catch (_: Exception) { null }
        }
    }
}

/** a finished tape kept in the history */
class SavedTape(val at: Long, val lines: List<TLine>, val total: String)

/** settings, current tape, memory and history on the phone */
object TapeStore {
    private fun sp(c: Context) = c.getSharedPreferences("kabadi_tape", Context.MODE_PRIVATE)

    fun settings(c: Context): TapeSettings = sp(c).let { p ->
        TapeSettings(p.getInt("places", 2), p.getInt("style", 3), p.getBoolean("smart", true), p.getBoolean("cash", false),
            p.getBoolean("vib", true), p.getBoolean("mem", true), p.getBoolean("notes", true))
    }
    fun saveSettings(c: Context, s: TapeSettings) = sp(c).edit().putInt("places", s.places).putInt("style", s.style).putBoolean("smart", s.smart)
        .putBoolean("cash", s.cash).putBoolean("vib", s.vibrate).putBoolean("mem", s.showMem).putBoolean("notes", s.showNotes).apply()

    fun loadState(c: Context, t: TapeCalc) { try { t.fromJson(JSONObject(sp(c).getString("state", "{}") ?: "{}")) } catch (_: Exception) {} }
    fun saveState(c: Context, t: TapeCalc) = sp(c).edit().putString("state", t.toJson().toString()).apply()

    fun voiceLang(c: Context, def: Int): Int = sp(c).getInt("vlang", def)
    fun saveVoiceLang(c: Context, i: Int) = sp(c).edit().putInt("vlang", i).apply()

    fun memory(c: Context): BigDecimal = try { BigDecimal(sp(c).getString("memv", "0") ?: "0") } catch (_: Exception) { BigDecimal.ZERO }
    fun saveMemory(c: Context, m: BigDecimal) = sp(c).edit().putString("memv", m.toPlainString()).apply()

    fun history(c: Context): MutableList<SavedTape> {
        val a = try { JSONArray(sp(c).getString("hist", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).map { a.getJSONObject(it) }.map { SavedTape(it.optLong("t"), TapeCalc.linesFrom(it.optJSONArray("l")), it.optString("tot")) }.toMutableList()
    }
    private fun saveHistory(c: Context, h: List<SavedTape>) = sp(c).edit().putString("hist",
        JSONArray().also { a -> h.take(50).forEach { a.put(JSONObject().put("t", it.at).put("l", TapeCalc.linesJson(it.lines)).put("tot", it.total)) } }.toString()).apply()

    /** newest first, at most 50 */
    fun addHistory(c: Context, lines: List<TLine>, total: String) {
        if (lines.isEmpty()) return
        val h = history(c); h.add(0, SavedTape(System.currentTimeMillis(), lines.map { it.copy() }, total)); saveHistory(c, h)
    }
    fun removeHistory(c: Context, at: Long) = saveHistory(c, history(c).filter { it.at != at })
    fun clearHistory(c: Context) = sp(c).edit().remove("hist").apply()
}
