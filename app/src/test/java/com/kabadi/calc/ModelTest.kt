package com.kabadi.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test fun otp() {
        val dev = Otp.deviceCode("a1b2c3d4e5f60708")
        assertEquals(6, dev.length)
        assertEquals(dev, Otp.deviceCode("a1b2c3d4e5f60708"))
        val o = Otp.code("9876543210", dev)
        assertEquals(6, o.length)
        assertTrue(Otp.check("+91 98765 43210".replace(" ", ""), dev, o))
        assertFalse(Otp.check("9876543211", dev, o))
        assertFalse(Otp.check("9876543210", Otp.deviceCode("other"), o))
        val msg = "OTP request\nName: Rafik\nMobile: 9876543210\nCode: #" + dev
        assertEquals("9876543210" to dev, Otp.parse(msg))
        assertEquals(null, Otp.parse("hello 12345"))
        assertEquals(20, Otp.topic("requests").length); assertTrue(Otp.topic("a") != Otp.topic("b"))
        assertTrue(Otp.isAdminCode("219977")); assertFalse(Otp.isAdminCode("123456"))
    }

    @Test fun lotHisab() {
        val h = Hisab(1, 0, type = "lot")
        h.vehicles.add(Veh("Tata", priceText = "200000")); h.vehicles.add(Veh("Mahindra", priceText = "150000"))
        assertEquals(350000.0, h.price, 1e-9)           // no total written = sum of vehicles
        h.kharch.add(Line("crane", "Crane", true, amountText = "10000"))
        h.maal.add(Line("", "Lokhand", false, kgText = "10000", rateText = "30"))
        h.saleText = "100000"                            // one vehicle sold whole
        assertEquals(40000.0, h.munafa(), 1e-9)          // 300000+100000 − 360000
        assertTrue(h.verify().isEmpty())
        h.priceText = "340000"
        assertEquals(340000.0, h.price, 1e-9)
        assertTrue(RFilter(brand = "mahindra").matches(h))
    }

    @Test fun companyRitB() {
        val h = Hisab(1, 0, type = "haraji", coMode = true, priceText = "300000", saleText = "350000", mudiName = "Salam", mudiPctText = "60", khedName = "Rafik", khedPctText = "40")
        h.kharch.add(Line("crane", "Crane", true, amountText = "10000"))
        h.maal.add(Line("body", "Body", true, amountText = "400000"))
        h.commText = "1"                                 // office: 1% of 350000 = 3500
        h.partners.add(Partner("Salam", "50")); h.partners.add(Partner("Asif", "50"))
        // mudi malik pays the commission
        assertEquals(3500.0, h.commission(), 1e-9)
        assertEquals(36500.0, h.munafa(), 1e-9)          // 350000 − 310000 − 3500
        assertEquals(50000.0, h.companyResult(), 1e-9)   // 400000 − 350000
        assertEquals(21900.0, h.mudiShare(), 1e-9)
        assertEquals(25000.0, h.coShareOf("salam"), 1e-9)
        assertEquals(175000.0, h.partnerLagat(h.partners[1]), 1e-9)
        assertTrue(h.verify().isEmpty())
        val book = personBook(listOf(h), "Salam")
        assertEquals(listOf("mudi", "co"), book.map { it.role })
        assertEquals(46900.0, book.sumOf { it.amount }, 1e-9)
        assertTrue(RFilter(mudi = "Asif").matches(h))
        // company adds the commission to its purchase
        h.commByCo = true
        assertEquals(40000.0, h.munafa(), 1e-9)
        assertEquals(46500.0, h.companyResult(), 1e-9)
        assertEquals(176750.0, h.partnerLagat(h.partners[1]), 1e-9)
        assertTrue(h.verify().isEmpty())
    }

    @Test fun finalLock() {
        val now = 10L * 86400000L
        val h = Hisab(1, now - 86400000L)
        assertEquals(0, lockState(h, now))
        h.finalAt = now - 1000
        assertEquals(1, lockState(h, now))
        h.maal.add(Line("body", "Body", true, amountText = "100", pay = "udhaar", daysText = "0"))
        assertEquals(2, lockState(h, now))               // credit time over
        val a = Hisab(2, now, type = "haraji"); a.finalAt = now
        assertEquals(1, lockState(a, now + 47L * 3600000L))
        assertEquals(2, lockState(a, now + 49L * 3600000L))
    }

    @Test fun manyLots() {
        val h = Hisab(1, 0, type = "lot")
        val a = Lot("GSRTC Lot 1"); a.vehicles.add(Veh("Tata", priceText = "100000")); a.items.add(Line("", "Lokhand scrap", false, kgText = "1000", rateText = "30"))
        val b = Lot("Bus lot", priceText = "250000"); b.vehicles.add(Veh("Ashok Leyland")); b.items.add(Line("", "Tyre", true, amountText = "5000"))
        h.lots.add(a); h.lots.add(b)
        assertEquals(130000.0, a.price, 1e-9)            // no price = vehicles + items
        assertEquals(380000.0, h.price, 1e-9)
        h.saleText = "400000"
        assertEquals(20000.0, h.munafa(), 1e-9)
        assertTrue(h.verify().isEmpty())
        assertTrue(RFilter(brand = "Ashok Leyland").matches(h))
        assertTrue(RFilter(who = "lokhand").matches(h))
        assertEquals(2, h.allVehicles().size)
    }

    @Test fun harajiMuddat() {
        val day = java.util.Calendar.getInstance().apply { set(2026, 8, 28, 10, 0, 0) }.timeInMillis
        val h = Hisab(1, day, type = "haraji", priceText = "300000", saleText = "350000", muddatText = "2m")
        val body = Line("body", "Body", true, amountText = "50000", pay = "udhaar")
        val oil = Line("oil", "Oil", true, amountText = "1000", pay = "udhaar", daysText = "15")
        h.maal.add(body); h.maal.add(oil)
        val c = java.util.Calendar.getInstance().apply { timeInMillis = dueTime(h, body)!! }
        assertEquals(10, c.get(java.util.Calendar.MONTH))             // 28 Sep + 2 months = 28 Nov
        assertEquals(28, c.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(day + 15L * 86400000L, dueTime(h, oil))           // own time wins
        assertEquals(day + 30L * 86400000L, muddatEnd(day, "30"))
        // vehicle bought on credit, auction sale on credit
        h.buyLine.pay = "udhaar"; h.saleLine.pay = "udhaar"; h.saleLine.cName = "Asif"
        assertEquals(300000.0, h.buyLine.value(), 1e-9)
        h.priceText = "310000"
        assertEquals(310000.0, h.buyLine.remaining(), 1e-9)          // follows the price
        h.saleLine.pays.add(Pay(day, "100000"))
        val d = openDues(listOf(h))
        assertEquals(250000.0, d.first { it.l.key == "sale" }.left, 1e-9)
        assertTrue(d.first { it.l.key == "sale" }.lena)
        assertFalse(d.first { it.l.key == "veh" }.lena)
        h.finalAt = day
        assertEquals(1, lockState(h, day + 3600000L))
    }

    @Test fun buyers() {
        val a = Hisab(1, 1000)
        a.maal.add(Line("body", "Body", true, amountText = "30000", cName = "Asif", cMobile = "98765 43210", pay = "udhaar"))
        a.maal.add(Line("alu", "Alu", true, amountText = "5000", cName = "asif bhai", cMobile = "+919876543210"))
        a.maal.add(Line("tyre", "Tyre", true, amountText = "2000", cName = "Rafik"))
        val b = Hisab(2, 2000, type = "haraji", saleText = "100000")
        b.saleLine.cName = "Asif"; b.saleLine.cMobile = "9876543210"; b.saleLine.pay = "udhaar"; b.saleLine.pays.add(Pay(2000, "40000"))
        val p = parties(listOf(a, b))
        assertEquals(2, p.size)
        val asif = p.first()
        assertEquals("9876543210", asif.key)
        assertEquals(135000.0, asif.total(), 1e-9)
        assertEquals(90000.0, asif.left(), 1e-9)              // 30000 + 60000
        assertEquals(2, buyerLines(a, asif.key).size)
        assertEquals("n:rafik", p[1].key)
        val buyer = Hisab(3, 3000, role = "buyer"); buyer.maal.add(Line("body", "Body", true, amountText = "1", cName = "X"))
        assertEquals(2, parties(listOf(a, b, buyer)).size)     // buyer hisab: not our sales
        val v = Hisab(4, 4000, priceText = "200000"); v.buyLine.cName = "Rafik"; v.buyLine.pay = "udhaar"
        val r = parties(listOf(a, v)).first { it.key == "n:rafik" }
        assertEquals(200000.0, r.dena(), 1e-9)                    // we owe him for the vehicle
        assertEquals(1, r.items.size); assertEquals(1, r.buys.size)
    }

    @Test fun harajiMobilesAndReminders() {
        val day = 100L * 86400000L
        val h = Hisab(1, day, type = "haraji", priceText = "300000", saleText = "250000", muddatText = "10")
        h.maal.add(Line("body", "Body", true, amountText = "80000", cName = "Asif", cMobile = "9876543210", pay = "udhaar"))
        h.maal.add(Line("tyre", "Tyre", true, amountText = "5000", cName = "Rafik"))
        h.partners.add(Partner("Salam", "50", "9000000001")); h.partners.add(Partner("Imran", "50"))
        assertEquals(3, missingMobile(h).size)                 // tyre buyer, vehicle buyer, Imran
        assertTrue(missingMobile(Hisab(2, day)).isEmpty())     // only haraji
        // day 5: nothing yet; day 7 (3 days before): body buyer + partners
        assertTrue(reminders(listOf(h), day + 5 * 86400000L).isEmpty())
        val r = reminders(listOf(h), day + 7 * 86400000L)
        assertEquals(listOf("Asif", "Salam", "Imran"), r.map { it.name })
        assertEquals(17500.0, r[1].amount, 1e-9)               // profit 35000 → 50%
        h.partners[0].done = true
        h.maal[0].pays.add(Pay(day, "80000"))
        assertEquals(listOf("Imran"), reminders(listOf(h), day + 20 * 86400000L).map { it.name })   // overdue: every day until done
    }

    @Test fun shareCodec() {
        val k = Otp.shareKey("98765 43210")
        val text = "{\"n\":\"સલામભાઈ\",\"x\":\"" + "body 9000 kg ".repeat(200) + "\"}"
        val sealed = Codec.seal(text, k)
        assertTrue(sealed.length < 2000)                         // gzip keeps it small
        assertEquals(text, Codec.open(sealed, Otp.shareKey("9876543210")))
        assertEquals(null, Codec.open(sealed, Otp.shareKey("9876543211")))   // other number cannot read
        for (n in 0..5) { val b = ByteArray(n) { (it * 37).toByte() }; assertTrue(b.contentEquals(Codec.unb64(Codec.b64(b)))) }
    }

    @Test fun writerNeedsBothMobiles() {
        val h = Hisab(9, 9000, writer = "mudi")
        assertEquals(2, missingMobile(h).size)
        h.mudiMobile = "98765 43210"
        assertEquals(1, missingMobile(h).size)
        h.khedMobile = "9123456789"
        assertEquals(0, missingMobile(h).size)
        // old hisab (no writer chosen) is not forced
        assertEquals(0, missingMobile(Hisab(10, 9000)).size)
    }

    @Test fun purchaseVehicleAndOtherGoods() {
        // vehicle 50,000 + 200 kg scrap × 30 = 56,000 bought
        val h = Hisab(11, 1000, priceText = "50000")
        h.buyItems.add(Line("", "Scrap", false, kgText = "200", rateText = "30"))
        assertEquals(56000.0, h.price, 0.001)
        assertEquals(50000.0, h.vehPrice(), 0.001)
        // only other goods, no vehicle
        val g = Hisab(12, 1000, type = "haraji")
        g.buyItems.add(Line("", "Battery", true, amountText = "4000"))
        assertEquals(4000.0, g.price, 0.001)
        g.maal.add(Line("", "Battery", true, amountText = "5000"))
        assertEquals(1000.0, g.munafa(), 0.001)
        assertTrue(g.verify().isEmpty())
    }
}
