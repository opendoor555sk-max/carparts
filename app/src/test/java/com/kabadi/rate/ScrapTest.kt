package com.kabadi.rate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScrapTest {
    private fun at(h: Int, m: Int = 0, dayShift: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 5, h, m, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, dayShift)
    }.timeInMillis

    @Test fun ratesDisappearAtSevenPm() {
        assertTrue(Scrap.alive(at(10), at(18, 59)))
        assertFalse(Scrap.alive(at(10), at(19, 0)))
        assertFalse(Scrap.alive(at(10), at(8, 0, 1)))
        // sent after 7 PM: counts until 7 PM of the next day
        assertTrue(Scrap.alive(at(20), at(9, 0, 1)))
        assertFalse(Scrap.alive(at(20), at(19, 30, 1)))
    }

    @Test fun highestFirstOnePricePerTrader() {
        val e = listOf(
            Entry("a", "A", "9000000001", at(9), mapOf("cabin" to 40.0, "copper" to 700.0)),
            Entry("b", "B", "9000000002", at(10), mapOf("cabin" to 45.0)),
            Entry("a", "A", "9000000001", at(11), mapOf("cabin" to 47.0)),            // A changes cabin, copper stays from before
            Entry("c", "C", "9000000003", at(8, 0, -1), mapOf("cabin" to 99.0))       // yesterday: gone
        )
        val now = at(12)
        val cabin = Scrap.rank("cabin", e, now)
        assertEquals(listOf("A", "B"), cabin.map { it.name })
        assertEquals(47.0, cabin[0].price, 1e-9)
        assertEquals(700.0, Scrap.rank("copper", e, now)[0].price, 1e-9)
        assertTrue(Scrap.rank("copper", e, at(19, 5)).isEmpty())
    }

    @Test fun traderOtpIsOwnAndPerPhone() {
        val o = Scrap.otp("9773041676", "ABC234")
        assertEquals(6, o.length)
        assertTrue(Scrap.otpOk("+91 97730 41676", "abc234", o))
        assertFalse(Scrap.otpOk("9773041676", "ABC235", o))
        assertFalse(Scrap.otpOk("9773041677", "ABC234", o))
        assertNotEquals(o, Otp.code("9773041676", "ABC234"))    // not the Kabadi OTP
    }

    @Test fun formatHasNoChassisEngineGear() {
        val all = Scrap.DEFAULT.joinToString(" ") { it.gu + it.hi + it.en + it.id }.lowercase()
        assertFalse(all.contains("chassis") || all.contains("engine") || all.contains("gear"))
        assertTrue(Scrap.DEFAULT.size >= 8)
        assertEquals("Cabin", Scrap.DEFAULT[0].name(0)); assertEquals("કેબિન", Scrap.DEFAULT[0].name(2))
        assertEquals("₹ 245", Scrap.price(245.0)); assertEquals("₹ 245.5", Scrap.price(245.5))
    }
}
