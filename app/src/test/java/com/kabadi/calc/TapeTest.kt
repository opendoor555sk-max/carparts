package com.kabadi.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TapeTest {
    private fun bd(s: String) = BigDecimal(s)
    private fun l(v: String, op: Char, pct: Boolean = false, open: Int = 0, close: Int = 0) = TLine(bd(v), op, pct, open, close)
    private fun tot(smart: Boolean = true, vararg x: TLine) = TapeMath.total(x.toList(), smart)

    @Test fun rentsAddUp() {
        assertEquals(bd("9400"), tot(true, l("1500", '+'), l("1700", '+'), l("1700", '+'), l("4500", '=')))
    }

    @Test fun chainGoesLeftToRight() {
        val t = tot(true, l("124", 'x'), l("555", '+'), l("12", '+'), l("25", '+'), l("500", '+'), l("25000", '-'), l("775", '+'), l("895", '='))!!
        assertEquals(0, t.compareTo(bd("94477")))
    }

    @Test fun noFloatingErrors() {
        assertEquals(0, tot(true, l("0.1", '+'), l("0.2", '='))!!.compareTo(bd("0.3")))
        assertEquals(0, tot(true, l("1", '/'), l("3", 'x'), l("3", '='))!!.compareTo(bd("1")))
    }

    @Test fun smartPercent() {
        assertEquals(0, tot(true, l("100", '+'), l("10", '=', true))!!.compareTo(bd("110")))
        assertEquals(0, tot(true, l("200", '-'), l("10", '=', true))!!.compareTo(bd("180")))
        assertEquals(0, tot(false, l("100", '+'), l("10", '=', true))!!.compareTo(bd("100.1")))
        assertEquals(0, tot(true, l("200", 'x'), l("10", '=', true))!!.compareTo(bd("20")))
    }

    @Test fun divisionByZeroIsAnError() {
        assertNull(tot(true, l("5", '/'), l("0", '=')))
    }

    @Test fun brackets() {
        assertEquals(0, tot(true, l("2", 'x'), l("3", '+', open = 1), l("4", '=', close = 1))!!.compareTo(bd("14")))
    }

    @Test fun indianAndInternationalFormat() {
        assertEquals("12,34,567.89", TapeMath.fmt(bd("1234567.891"), 2, 3))
        assertEquals("1,000.00", TapeMath.fmt(bd("1000"), 2, 3))
        assertEquals("-9,400", TapeMath.fmt(bd("-9400"), 0, 3))
        assertEquals("1,234,567.89", TapeMath.fmt(bd("1234567.891"), 2, 0))
        assertEquals("1.234.567,89", TapeMath.fmt(bd("1234567.891"), 2, 1))
        assertEquals("0.3", TapeMath.fmt(bd("0.30"), -1, 3))
    }

    @Test fun keyEntryBuildsTheTape() {
        val c = TapeCalc(TapeSettings(places = 0))
        fun num(s: String) = s.forEach { c.digit(it.toString()) }
        num("1500"); c.op('+'); num("1700"); c.op('+'); num("1700"); c.op('+'); num("4500"); c.equals()
        assertEquals(4, c.lines.size)
        assertEquals(bd("9400"), c.total())
        assertTrue(c.closed)
        c.digit("7")                    // a digit after "=" starts a new paper
        assertEquals(0, c.lines.size)
    }

    @Test fun backBringsLastLineBack() {
        val c = TapeCalc(TapeSettings())
        c.digit("5"); c.digit("0"); c.op('+')
        c.back()
        assertEquals("50", c.cur)
        assertEquals(0, c.lines.size)
    }

    @Test fun spokenLinesAreWritten() {
        val c = TapeCalc(TapeSettings(places = 0))
        c.addSpoken(TapeSpeech.parse("labour 500 plus diesel 1200 minus advance 300 total"))
        assertEquals(3, c.lines.size)
        assertEquals("labour", c.lines[0].note)
        assertEquals(bd("1400"), c.total())
        assertTrue(c.closed)
    }

    // ---- speech parsing ----
    private fun p(s: String) = TapeSpeech.parse(s)

    @Test fun englishWholeCalculation() {
        val r = p("labour 500 plus diesel 1200 minus advance 300 total")
        assertEquals(listOf("labour", "diesel", "advance"), r.map { it.note })
        assertEquals(listOf(bd("500"), bd("1200"), bd("300")), r.map { it.amount })
        assertEquals(listOf('+', '-', '='), r.map { it.op })
    }

    @Test fun timesAndTotal() {
        val r = p("gas cutting 800 times 2 total")
        assertEquals(listOf(bd("800"), bd("2")), r.map { it.amount })
        assertEquals(listOf('x', '='), r.map { it.op })
        assertEquals("gas cutting", r[0].note)
    }

    @Test fun gujaratiDigitsAndWords() {
        val r = p("મજૂરી ૫૦૦ પ્લસ ડીઝલ ૧૨૦૦ માઇનસ એડવાન્સ ૩૦૦ કુલ")
        assertEquals(listOf(bd("500"), bd("1200"), bd("300")), r.map { it.amount })
        assertEquals(listOf('+', '-', '='), r.map { it.op })
        assertEquals("મજૂરી", r[0].note)
        assertEquals(bd("500"), p("ક્રેન ભાડું પાંચસો")[0].amount)
        assertEquals(bd("2"), p("ગેસ કટિંગ 800 ગુણ્યા 2 કુલ")[1].amount)
    }

    @Test fun hindi() {
        val r = p("मजदूरी पांच सौ जोड़ डीजल 1200 घटा एडवांस 300 कुल")
        assertEquals(listOf(bd("500"), bd("1200"), bd("300")), r.map { it.amount })
        assertEquals(listOf('+', '-', '='), r.map { it.op })
    }

    @Test fun numberWords() {
        assertEquals(bd("1525"), p("one thousand five hundred twenty five")[0].amount)
        assertEquals(bd("25"), p("twenty five")[0].amount)
        assertEquals(bd("150000"), p("1 lakh 50 thousand")[0].amount)
        assertEquals(bd("2500"), p("twenty five hundred")[0].amount)
    }

    @Test fun percentWords() {
        val r = p("labour 500 plus 10 percent total")
        assertEquals(2, r.size)
        assertTrue(r[1].pct)
        assertFalse(r[0].pct)
        assertTrue(p("labour 500 plus 10% total")[1].pct)
    }

    @Test fun amountBeforeItem() {
        val r = p("500 labour 200 diesel")
        assertEquals(listOf("labour", "diesel"), r.map { it.note })
        assertEquals(listOf(bd("500"), bd("200")), r.map { it.amount })
    }

    @Test fun rupeeSignsAndCommas() {
        assertEquals(bd("1500"), p("rent ₹1,500")[0].amount)
        assertEquals(bd("1500"), p("rent 1500 rupees")[0].amount)
        assertEquals(bd("2.5"), p("weight 2.5")[0].amount)
    }

    @Test fun noAmountNoLine() {
        assertEquals(0, p("hello how are you").size)
        assertEquals(0, p("").size)
    }

    @Test fun wordsCutBetweenTwoSentencesAreCarried() {
        val a = TapeSpeech.parseFull("labour 500 plus diesel")
        assertEquals(1, a.items.size)
        assertEquals("diesel", a.rest)
        val b = TapeSpeech.parseFull(a.rest + " 1200 total")
        assertEquals("diesel", b.items[0].note)
        assertEquals(bd("1200"), b.items[0].amount)
        assertEquals("", TapeSpeech.parseFull("labour 500 total").rest)
    }
}
