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

    @Test fun auctionCompany() {
        val h = Hisab(1, 1, priceText = "100000", type = "haraji", saleText = "120000", commText = "2", commPct = true)
        h.kharch.add(Line("crane", "Crane", true, amountText = "3000"))
        h.partners.add(Partner("Salambhai", "1"))
        h.partners.add(Partner("Asifbhai", "3"))
        assertEquals(103000.0, h.lagat(), 1e-9)
        assertEquals(2400.0, h.commission(), 1e-9)
        assertEquals(14600.0, h.munafa(), 1e-9)
        assertEquals(146.0, h.partnerMunafa(h.partners[0]), 1e-9)
        assertEquals(3090.0, h.partnerLagat(h.partners[1]), 1e-9)
        assertEquals(96.0, h.ownerShare(), 1e-9)
        h.commPct = false; h.commText = "5000"
        assertEquals(12000.0, h.munafa(), 1e-9)
    }

    @Test fun duplicateCheck() {
        val a = Line("body", "Body", false)
        assertTrue(sameItem(a, "body", "x"))
        assertTrue(sameItem(Line("body", "बॉडी", false), "", "Body"))
        assertEquals(listOf("Tamba"), duplicates(listOf(Line("", "Tamba", false), Line("", " tamba ", false))))
    }

    @Test fun litreTotals() {
        val h = Hisab(1, 1, priceText = "1000")
        h.maal.add(Line("body", "Body", false, kgText = "100", rateText = "30"))
        h.maal.add(Line("oil", "Engine oil", false, kgText = "20", rateText = "25", litre = true))
        assertEquals(100.0, h.kg(), 1e-9)
        assertEquals(20.0, h.litre(), 1e-9)
        assertEquals(3500.0, h.maalTotal(), 1e-9)
    }
}
