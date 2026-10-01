package com.nirmaan.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
    private fun ans(q: String): Double {
        val r = Units.parse(q)
        assertNotNull("not understood: $q", r)
        val to = r!!.to ?: r.from
        return to.fromBase(r.base)
    }

    @Test fun indianLand() {
        assertEquals(0.8, ans("2 vigha acre me"), 0.001)
        assertEquals(2.5, ans("1 acre me kitna vigha"), 0.001)
        assertEquals(0.8, ans("2 વીઘા એકર માં"), 0.001)
        assertEquals(17424.0, ans("1 vigha sq ft"), 0.5)
        assertEquals(16.0, ans("1 vigha guntha"), 0.001)
    }

    @Test fun weightsAndVolume() {
        assertEquals(100.0, ans("5 मन किलो में"), 1e-9)
        assertEquals(116.638, ans("10 tola gram"), 0.001)
        assertEquals(100.0, ans("1 brass cft"), 1e-6)
        assertEquals(35.3147, ans("1 cubic meter cft"), 0.0001)
    }

    @Test fun sumsAndSeparators() {
        assertEquals(160.02, ans("5 ft 3 in to cm"), 1e-6)
        assertEquals(25.4, ans("10 in in cm"), 1e-9)
        assertEquals(182.4, ans("5 ft + 30 cm = cm"), 1e-6)
        assertEquals(19.05, ans("3/4 inch mm"), 1e-9)
    }

    @Test fun temperatureAndOthers() {
        assertEquals(212.0, ans("100 c in f"), 1e-9)
        assertEquals(-40.0, ans("-40 c f"), 1e-9)
        assertEquals(62.1371, ans("100 km/h mph"), 0.0001)
        assertEquals(10.0, ans("10 l/100km km/l"), 1e-9)
        assertEquals(1000.0, ans("1 gb mb"), 1e-9)
    }

    @Test fun formatting() {
        assertEquals("12,34,567", Units.fmt(1234567.0))
        assertTrue(Units.fmt(1.616255e-35).contains("10⁻³⁵"))
        assertEquals(0, Units.UNITS.groupBy { it.id }.count { it.value.size > 1 })
    }
}
