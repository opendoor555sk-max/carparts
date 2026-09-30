package com.kabadi.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodTest {
    private fun tod(h: Hisab, name: String, amt: String) = h.kharch.add(Line("", name, true, amountText = amt, post = true))

    @Test fun ritA_breakingCostsReduceTheDeal() {
        val h = Hisab(1, 1, priceText = "100000", type = "haraji", saleText = "120000")
        h.kharch.add(Line("", "Crane", true, amountText = "2000"))
        val before = h.munafa()
        tod(h, "Majuri", "3000"); tod(h, "Gas cutting", "1500")
        assertEquals(4500.0, h.kharchPost(), 1e-9)
        assertEquals(2000.0, h.kharchPre(), 1e-9)
        assertEquals(before - 4500.0, h.munafa(), 1e-9)
        assertTrue(h.verify().isEmpty())
    }

    @Test fun ritB_breakingCostsAreTheCompanys() {
        val h = Hisab(1, 1, priceText = "300000", type = "haraji", coMode = true, saleText = "350000")
        h.kharch.add(Line("", "Crane", true, amountText = "5000"))
        h.maal.add(Line("", "Body", true, amountText = "500000"))
        h.partners.add(Partner("A", "50")); h.partners.add(Partner("B", "50"))
        val owner = h.munafa(); val co = h.companyResult()
        tod(h, "Majuri", "10000"); tod(h, "Gas cutting", "6000")
        assertEquals(owner, h.munafa(), 1e-9)                   // owner side does not change
        assertEquals(co - 16000.0, h.companyResult(), 1e-9)     // company profit is less
        assertEquals(350000.0 + 16000.0, h.companyLagat(), 1e-9)
        assertEquals((co - 16000.0) / 2, h.partnerMunafa(h.partners[0]), 1e-9)
        assertTrue(h.verify().isEmpty())
    }
}
