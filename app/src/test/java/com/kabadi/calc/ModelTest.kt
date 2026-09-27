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

    @Test fun rokadUdhaar() {
        val h = Hisab(1, 1, priceText = "50000")
        h.kharch.add(Line("dalali", "Dalali", true, amountText = "2000", pay = "udhaar"))
        h.kharch.add(Line("crane", "Crane", true, amountText = "1500", pay = "udhaar", paid = true))
        h.kharch.add(Line("food", "Khana", true, amountText = "500"))
        h.maal.add(Line("body", "Body", false, kgText = "800", rateText = "35", pay = "udhaar", cName = "Rafiq", daysText = "30"))
        h.maal.add(Line("tamba", "Tamba", false, kgText = "20", rateText = "600"))
        assertEquals(2000.0, h.kharchBaaki(), 1e-9)
        assertEquals(4000.0, h.kharchTotal(), 1e-9)
        assertEquals(28000.0, h.udhaarBikri(), 1e-9)
        assertEquals(12000.0, h.rokadBikri(), 1e-9)
        assertEquals(40000.0 - 54000.0, h.munafa(), 1e-9)
    }

    @Test fun kistAndSplit() {
        val h = Hisab(1, 0, priceText = "50000", mudiName = "Salambhai", mudiPctText = "55", khedName = "Asif", khedPctText = "45")
        val body = Line("body", "Body", false, kgText = "800", rateText = "100", pay = "udhaar", cName = "Rafiq", daysText = "30")
        body.pays.add(Pay(1, "20000"))
        body.pays.add(Pay(2, "10000"))
        h.maal.add(body)
        h.kharch.add(Line("crane", "Crane", true, amountText = "5000", pay = "udhaar"))
        assertEquals(30000.0, body.received(), 1e-9)
        assertEquals(50000.0, h.lenaBaaki(), 1e-9)
        assertEquals(5000.0, h.denaBaaki(), 1e-9)
        assertEquals(25000.0, h.munafa(), 1e-9)
        assertEquals(13750.0, h.mudiShare(), 1e-9)
        assertEquals(11250.0, h.khedShare(), 1e-9)
        assertTrue(h.verify().isEmpty())
        val d = openDues(listOf(h))
        assertEquals(2, d.size)
        assertEquals(30L * 86400000L, d.first().due)
    }

    @Test fun monthlyReport() {
        val jan = java.util.Calendar.getInstance().apply { set(2026, 0, 10) }.timeInMillis
        val feb = java.util.Calendar.getInstance().apply { set(2026, 1, 5) }.timeInMillis
        val a = Hisab(1, jan, priceText = "1000", mudiName = "Salam")
        a.maal.add(Line("body", "Body", true, amountText = "1500"))
        val b = Hisab(2, feb, priceText = "2000", mudiName = "Asif")
        b.maal.add(Line("body", "Body", true, amountText = "1800"))
        val m = monthly(listOf(a, b))
        assertEquals("2026-02", m[0].key)
        assertEquals(-200.0, m[0].munafa, 1e-9)
        assertEquals(500.0, m[1].munafa, 1e-9)
        assertEquals(1, monthly(listOf(a, b), "sal").size)
    }

    @Test fun reportFilter() {
        val jan = java.util.Calendar.getInstance().apply { set(2025, 0, 10) }.timeInMillis
        val feb = java.util.Calendar.getInstance().apply { set(2026, 1, 5) }.timeInMillis
        val a = Hisab(1, jan, priceText = "1000", mudiName = "Salam", brand = "Tata", tyres = "6")
        val b = Hisab(2, feb, priceText = "2000", mudiName = "Asif", khedName = "Salam", brand = "Mahindra", tyres = "4")
        val c = Hisab(3, feb, priceText = "3000", mudiName = "Rafik", brand = "Tata", tyres = "10", type = "haraji")
        val all = listOf(a, b, c)
        assertEquals(2, all.count { RFilter(mudi = "salam").matches(it) })
        assertEquals(2, all.count { RFilter(year = 2026).matches(it) })
        assertEquals(1, all.count { RFilter(year = 2025, month = 1).matches(it) })
        assertEquals(2, all.count { RFilter(brand = "TATA").matches(it) })
        assertEquals(1, all.count { RFilter(brand = "Tata", tyres = "10").matches(it) })
        assertEquals(1, all.count { RFilter(type = "haraji").matches(it) })
        assertEquals(listOf("Asif", "Rafik", "Salam"), peopleNames(all))
        assertEquals(listOf(2026, 2025), years(all))
        assertEquals(1, monthly(all, RFilter(brand = "Mahindra")).size)
    }
}
