package com.kabadi.calc

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** the calculator screen is built and driven here like on the phone (no microphone: the spoken text is fed in) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TapeViewTest {
    private fun walk(v: View, out: MutableList<View> = mutableListOf()): List<View> {
        out.add(v); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), out); return out
    }
    private fun texts(t: TapeView) = walk(t.root).filterIsInstance<TextView>().map { it.text.toString() }
    private fun make(): TapeView {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        a.getSharedPreferences("kabadi_tape", 0).edit().clear().commit()
        return TapeView(a)
    }

    @Test fun opensOnABlankPaper() {
        val t = make()
        assertEquals(0, t.calc.lines.size)
        assertTrue(texts(t).any { it == "0" })                     // total strip
        assertTrue(texts(t).any { it.contains("Blank paper") })
    }

    @Test fun spokenCalculationIsWrittenAndAddedUp() {
        val t = make()
        t.feed("labour 500 plus diesel 1200 minus advance 300 total")
        assertEquals(3, t.calc.lines.size)
        val x = texts(t)
        assertTrue(x.contains("labour")); assertTrue(x.contains("diesel")); assertTrue(x.contains("advance"))
        assertTrue(x.any { it == "= 1,400.00" || it == "= 1,400" })
        assertTrue(x.any { it.startsWith("🗣") })                  // what the phone wrote is shown
    }

    @Test fun symbolsAndMinusAfterTheAmount() {
        val t = make()
        t.feed("ક્રેન ભાડું 5000 વાલીયા પેડલ વાળાને 500")
        t.feed("500 - કરોડ રફિકભાઈ ના")
        assertEquals(3, t.calc.lines.size)
        assertEquals("5000", t.calc.total()!!.toPlainString())
        assertTrue(texts(t).any { it.contains("-500") })
    }

    @Test fun deleteByVoice() {
        val t = make()
        t.feed("labour 500 plus diesel 1200 plus advance 300")
        assertEquals(3, t.calc.lines.size)
        t.feed("ઉપરનું રદ્દ કરો")
        assertEquals(2, t.calc.lines.size)
        t.feed("ખોટું લખેલું હટાવો")
        assertEquals(1, t.calc.lines.size)
        t.feed("cancel")
        assertEquals(0, t.calc.lines.size)
        t.feed("cancel")                                           // nothing left: no crash
        assertEquals(0, t.calc.lines.size)
    }

    @Test fun clearAllByVoiceAndDeleteThenWriteInOneSentence() {
        val t = make()
        t.feed("labour 500 plus diesel 1200")
        t.feed("બધું રદ્દ કરો")
        assertEquals(0, t.calc.lines.size)
        t.feed("labour 500 plus diesel 1200")
        t.feed("diesel 1200 रद्द करो crane 800")
        assertEquals(listOf("labour", "diesel", "crane"), t.calc.lines.map { it.note })
        assertEquals(listOf("500", "1200", "800"), t.calc.lines.map { it.value.toPlainString() })
    }

    @Test fun keyPadWorks() {
        val t = make()
        fun tap(label: String) { walk(t.root).filterIsInstance<TextView>().first { it.text.toString() == label && it.hasOnClickListeners() }.performClick() }
        tap("7"); tap("+"); tap("8"); tap("=")
        assertEquals("15", t.calc.total()!!.toPlainString())
        assertTrue(t.calc.closed)
    }

    @Test fun wrongTextNeverCrashes() {
        val t = make()
        for (s in listOf("", "   ", "hello", "₹", "% %", "- - -", "x", "total", "plus", "500 500 500", "0", "00", "1/0", "÷", "999999999999999999999999 times 999999999999",
            "બધું", "૫૦૦", "દોઢ", "એક કરોડ", "500 ÷ 0 =")) { t.feed(s) }
        assertTrue(texts(t).isNotEmpty())
    }
}
