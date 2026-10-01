package com.kabadi.calc

import java.math.BigDecimal

/**
 * Spoken calculation -> tape lines.
 *   "majuri 500 plus diesel 1200 minus advance 300 total"  ->  [majuri 500 +] [diesel 1200 −] [advance 300 =]
 * An item (words) and an amount make one line. The operator word after it is that line's sign, and "total" closes the tape.
 * Works for Gujarati, Hindi and English words, and for digits in any of the three scripts.
 */
object TapeSpeech {
    class Item(val note: String, val amount: BigDecimal, var op: Char, var pct: Boolean)

    private val digMap = HashMap<Char, Char>().also { m ->
        "૦૧૨૩૪૫૬૭૮૯".forEachIndexed { i, c -> m[c] = '0' + i }
        "०१२३४५६७८९".forEachIndexed { i, c -> m[c] = '0' + i }
    }

    private val unitW: Map<String, Int> = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "छह" to 6, "छः" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10,
        "बीस" to 20, "तीस" to 30, "चालीस" to 40, "पचास" to 50, "साठ" to 60, "सत्तर" to 70, "अस्सी" to 80, "नब्बे" to 90,
        "એક" to 1, "બે" to 2, "ત્રણ" to 3, "ચાર" to 4, "પાંચ" to 5, "છ" to 6, "સાત" to 7, "આઠ" to 8, "નવ" to 9, "દસ" to 10,
        "વીસ" to 20, "ત્રીસ" to 30, "ચાલીસ" to 40, "પચાસ" to 50, "સાઠ" to 60, "સિત્તેર" to 70, "એંસી" to 80, "નેવું" to 90
    )
    private val multW: Map<String, Int> = mapOf(
        "hundred" to 100, "thousand" to 1000, "lakh" to 100000, "lac" to 100000,
        "सौ" to 100, "हज़ार" to 1000, "हजार" to 1000, "लाख" to 100000,
        "સો" to 100, "હજાર" to 1000, "લાખ" to 100000
    )
    private val fillers = hashSetOf("rupees", "rupee", "rs", "rupaiya", "રૂપિયા", "રૂપિયાનું", "રૂ", "रुपये", "रुपया", "रुपए", "₹",
        "pachi", "પછી", "फिर", "then", "and", "ane", "અને", "और", "of", "na", "ના", "નો", "ની", "નું")
    private val opWords: Map<Char, Set<String>> = mapOf(
        '+' to setOf("plus", "add", "પ્લસ", "જમા", "ઉમેરો", "ઉમેર", "ઉમેરવા", "प्लस", "जोड़", "जोड़ो", "जमा", "जोड"),
        '-' to setOf("minus", "less", "માઇનસ", "માઈનસ", "ઓછા", "ઓછું", "બાદ", "બાદબાકી", "घटा", "माइनस", "कम", "घटाओ", "घटाव"),
        'x' to setOf("times", "into", "multiply", "multiplied", "x", "ગુણ", "ગુણ્યા", "ગુણી", "ગુણાકાર", "गुना", "गुणा", "गुणे"),
        '/' to setOf("divide", "divided", "by", "ભાગ", "ભાગ્યા", "ભાગાકાર", "भाग", "बटा", "भागा"),
        '=' to setOf("total", "equals", "equal", "kul", "કુલ", "બરાબર", "ટોટલ", "टोटल", "कुल", "बराबर", "योग")
    )
    private val pctWords = hashSetOf("percent", "percentage", "ટકા", "ટકાવારી", "प्रतिशत", "फीसदी", "%")

    private fun opOf(w: String): Char? = opWords.entries.firstOrNull { w in it.value }?.key

    private enum class K { D, U, T, M }
    private class Part(val k: K, val v: BigDecimal)

    /** a spoken/typed token as number parts (digits, one unit, or unit+multiplier like "પાંચસો"); null = not a number */
    private fun parts(tok0: String): List<Part>? {
        val tok = tok0.map { digMap[it] ?: it }.joinToString("").replace(",", "")
        if (tok.isNotEmpty() && tok.all { it in '0'..'9' || it == '.' } && Regex("[0-9]+(\\.[0-9]+)?").matches(tok)) return listOf(Part(K.D, BigDecimal(tok)))
        val w = tok.lowercase()
        unitW[w]?.let { return listOf(Part(if (it >= 20 && it % 10 == 0) K.T else K.U, BigDecimal(it))) }
        multW[w]?.let { return listOf(Part(K.M, BigDecimal(it))) }
        for ((m, mv) in multW) if (w.length > m.length && w.endsWith(m)) {
            val u = unitW[w.dropLast(m.length)]
            if (u != null) return listOf(Part(K.U, BigDecimal(u)), Part(K.M, BigDecimal(mv)))
        }
        return null
    }

    /** one number being built from words/digits: "one thousand five hundred twenty five" = 1525 */
    private class Acc {
        var total: BigDecimal = BigDecimal.ZERO
        var cur: BigDecimal = BigDecimal.ZERO
        var last: K? = null
        fun active() = last != null
        fun value(): BigDecimal = total.add(cur)
        fun copy() = Acc().also { it.total = total; it.cur = cur; it.last = last }
        fun take(p: Part): Boolean {
            when (p.k) {
                K.D -> {
                    if (last == K.M && p.v.signum() > 0 && p.v < BigDecimal(100) && p.v.scale() <= 0) { cur = cur.add(p.v); last = K.U; return true }
                    if (active()) return false
                    cur = p.v
                }
                K.U, K.T -> {
                    val l = last
                    val ok = l == null || l == K.M || (l == K.T && p.k == K.U && p.v.toInt() < 10)
                    if (!ok) return false
                    cur = cur.add(p.v)
                }
                K.M -> {
                    if (last == K.M) return false
                    val one = if (cur.signum() == 0) BigDecimal.ONE else cur
                    if (p.v.toInt() == 100) cur = one.multiply(p.v) else { total = total.add(one.multiply(p.v)); cur = BigDecimal.ZERO }
                }
            }
            last = p.k
            return true
        }
        fun takeAll(ps: List<Part>): Boolean { val t = copy(); for (p in ps) if (!t.take(p)) return false; total = t.total; cur = t.cur; last = t.last; return true }
    }

    class Result(val items: List<Item>, /** item words heard after the last amount (the amount comes in the next sentence) */ val rest: String)

    fun parse(text0: String): List<Item> = parseFull(text0).items

    fun parseFull(text0: String): Result {
        val text = text0.replace("₹", " ").replace("%", " % ").replace("/-", " ").replace("=", " total ")
        val toks = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ArrayList<Item>()
        var words = ArrayList<String>()
        var acc = Acc()
        var pct = false

        fun flush(op: Char?): Boolean {
            if (!acc.active()) return false
            out.add(Item(words.joinToString(" "), acc.value(), op ?: '+', pct))
            words = ArrayList(); acc = Acc(); pct = false
            return true
        }

        val numberFirst = toks.isNotEmpty() && parts(toks[0]) != null && toks.drop(1).any { t ->
            val lw = t.lowercase(); parts(t) == null && lw !in fillers && opOf(lw) == null && lw !in pctWords
        }

        for (t in toks) {
            val lw = t.lowercase()
            if (lw in fillers) continue
            if (lw in pctWords) { if (acc.active()) pct = true else if (out.isNotEmpty()) out.last().pct = true; continue }
            val o = opOf(lw)
            if (o != null && !(lw == "x" && !acc.active() && out.isEmpty())) {
                if (!flush(o) && out.isNotEmpty()) out.last().op = o
                continue
            }
            val ps = parts(t)
            if (ps != null) {
                if (!acc.takeAll(ps)) { flush('+'); acc = Acc(); acc.takeAll(ps) }
            } else {
                if (acc.active() && !numberFirst) flush('+')
                words.add(t)
            }
        }
        flush('+')
        return Result(out.filter { it.amount.signum() > 0 }, if (acc.active()) "" else words.joinToString(" "))
    }
}
