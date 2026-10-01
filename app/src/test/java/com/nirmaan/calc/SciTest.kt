package com.nirmaan.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SciTest {
    private val env = SciMath.Env(mapOf("X" to SQ(5.0)), 0, SQ(10.0))
    private fun v(s: String): Double { val r = SciMath.run(s, env); return r.unit?.fromBase(r.q.re) ?: r.q.re }
    private fun u(s: String) = SciMath.run(s, env).unit?.sym
    private fun err(s: String) = try { SciMath.run(s, env); "" } catch (e: SciErr) { e.code }

    @Test fun basics() {
        assertEquals(14.0, v("2+3×4"), 1e-12)
        assertEquals(1024.0, v("2^10"), 1e-12)
        assertEquals(0.5, v("sin(30)"), 1e-12)
        assertEquals(0.0, v("sin(180)"), 0.0)
        assertEquals(120.0, v("5!"), 0.0)
        assertEquals(220.0, v("200 + 10%"), 1e-12)
        assertEquals(180.0, v("200 − 10%"), 1e-12)
        assertEquals(5.0, v("(2+3"), 0.0)
        assertEquals(-2.0, v("−8^(1/3)"), 1e-12)
        assertEquals(20.0, v("Ans×2"), 0.0)
        assertEquals(2001.0, v("2E3+1"), 0.0)
    }

    @Test fun complex() {
        val r = SciMath.run("(1+2i)(3−i)", env).q
        assertEquals(5.0, r.re, 1e-12); assertEquals(5.0, r.im, 1e-12)
        val s = SciMath.run("√(−4)", env).q
        assertEquals(0.0, s.re, 0.0); assertEquals(2.0, s.im, 1e-12)
        assertEquals(-1.0, SciMath.run("e^(iπ)", env).q.re, 1e-12)
        assertEquals(0.0, SciMath.run("e^(iπ)", env).q.im, 0.0)
    }

    @Test fun units() {
        assertEquals(120.0, v("10 ft × 12 ft"), 1e-9); assertEquals("ft²", u("10 ft × 12 ft"))
        assertEquals(160.02, v("5 ft 3 in → cm"), 1e-9)
        assertEquals(50.0, v("100 km ÷ 2 h"), 1e-9); assertEquals("km/h", u("100 km ÷ 2 h"))
        assertEquals(5000.0, v("5 kN → N"), 1e-9)
        assertEquals(24.0, v("3 m × 4 m × 2 m"), 1e-12)
        assertEquals(1000.0, v("1 m³ → L"), 1e-9)
        assertEquals(98.1, v("10 kg × 9.81 m/s²"), 1e-9); assertEquals("N", u("10 kg × 9.81 m/s²"))
        assertEquals(77.0, v("25 °C → °F"), 1e-9)
        assertEquals(1618.7425856, v("1 [vigha (Gujarat)] → m²"), 1e-6)
        assertEquals("mismatch", err("5 m + 2 kg"))
    }

    @Test fun calculus() {
        assertEquals(9.0, v("∫(X^2, 0, 3)"), 1e-9)
        assertEquals(1.0, v("∫(e^(−X), 0, ∞)"), 1e-7)
        assertEquals(Math.sqrt(Math.PI), v("∫(e^(−X^2), −∞, ∞)"), 1e-7)
        assertEquals(5050.0, v("Σ(X, 1, 100)"), 0.0)
        assertEquals(120.0, v("Π(X, 1, 5)"), 0.0)
        assertEquals(12.0, v("d/dx(X^3, 2)"), 1e-9)
    }

    @Test fun solving() {
        val r = SciMath.run("2X + 3 = 11", env)
        assertEquals("X", r.assign); assertEquals(4.0, r.q.re, 1e-9)
        val q = SciMath.run("X^2 = 9", env)
        assertEquals(listOf(-3.0, 3.0), q.roots.map { it.re })
        assertEquals(true, SciMath.run("2+2=4", env).truth)
        assertEquals(7.0, SciMath.run("X=7", env).q.re, 0.0)
    }

    @Test fun newKeys() {
        assertEquals(128.0, v("2⁷"), 0.0)
        assertEquals(1e-3, v("10⁻³"), 1e-15)
        assertEquals(20.0, v("[2+3]×4"), 0.0)
        assertEquals(1618.7425856, v("1 [vigha (Gujarat)] → m²"), 1e-6)
        val p = SciMath.run("2∠90", env).q
        assertEquals(0.0, p.re, 0.0); assertEquals(2.0, p.im, 1e-12)
        assertEquals("x", SciMath.run("3x + 1 = 7", env).assign)
        assertEquals(2.0, SciMath.run("3x + 1 = 7", env).q.re, 1e-9)
        assertEquals(15.0, v("X³ ÷ 25 × 3"), 1e-9)
    }

    @Test fun formats() {
        val o = SciOpts()
        assertEquals("3/4", SciFmt.exact(0.75))
        assertEquals("√2/2", SciFmt.exact(Math.sqrt(2.0) / 2))
        assertEquals("π/4", SciFmt.exact(Math.PI / 4))
        assertEquals(null, SciFmt.exact(160.02))
        assertEquals("12,34,567.891", SciFmt.real(1234567.891, o))
        o.mode = 1; assertEquals("1.23456×10⁵", SciFmt.real(123456.0, o))
        o.mode = 2; assertEquals("123.456×10³", SciFmt.real(123456.0, o))
        o.mode = 0; o.dec = 2; assertEquals("0.67", SciFmt.real(2.0 / 3, o))
        o.dec = -1; o.group = 1; o.comma = true; assertEquals("1.234.567,5", SciFmt.real(1234567.5, o))
        assertTrue(SciFmt.dimText(Dim.of(Dim.L, 1, Dim.T, -1), false) == "m/s")
    }
}
