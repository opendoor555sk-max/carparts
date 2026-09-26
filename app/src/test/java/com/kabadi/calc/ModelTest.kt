package com.kabadi.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test fun expr() {
        assertEquals(780.0, evalExpr("400+380"), 1e-9)
        assertEquals(110.0, evalExpr("2*55"), 1e-9)
        assertEquals(1200.0, evalExpr("1,200"), 1e-9)
        assertEquals(45.0, evalExpr("(10+5)×3"), 1e-9)
        assertEquals(0.0, evalExpr(""), 1e-9)
        assertTrue(evalExpr("12a").isNaN())
    }

    @Test fun moneyFormat() {
        assertEquals("₹ 1,03,500", money(103500.0))
        assertEquals("₹ 28,000", money(28000.0))
        assertEquals("-₹ 2,500.50", money(-2500.5))
    }

    @Test fun hisabTotals() {
        val h = Hisab(1, 1, priceText = "90000")
        h.kharch.add(Line("crane", "Crane", true, amountText = "2500"))
        h.kharch.add(Line("majuri", "Majuri", true, amountText = "1500"))
        h.maal.add(Line("body", "Body", false, kgText = "800", rateText = "35"))
        h.maal.add(Line("alu", "Aluminium", false, kgText = "100", rateText = "225"))
        h.maal.add(Line("engine", "Engine + Gear", true, amountText = "55000"))
        assertEquals(4000.0, h.kharchTotal(), 1e-9)
        assertEquals(94000.0, h.lagat(), 1e-9)
        assertEquals(105500.0, h.maalTotal(), 1e-9)
        assertEquals(900.0, h.kg(), 1e-9)
        assertEquals(11500.0, h.munafa(), 1e-9)
    }
}
