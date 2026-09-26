package com.nirmaan.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Checks the engine against the examples from the BuildCalc manual. */
class EngineTest {
    private class FakeUi : Ui {
        var title = ""
        var rows: List<Row> = emptyList()
        var spec: FormSpec? = null
        val toasts = mutableListOf<String>()
        override fun toast(m: String) { toasts.add(m) }
        override fun panel(title: String, rows: List<Row>) { this.title = title; this.rows = rows }
        override fun form(spec: FormSpec) { this.spec = spec; title = spec.title }
        override fun prefs() {}
        override fun help() {}
        fun value(label: String) = rows.first { !it.section && it.label == label }.value
    }

    private lateinit var ui: FakeUi
    private lateinit var e: Engine

    @Before
    fun setUp() {
        ui = FakeUi()
        e = Engine(ui)
    }

    private fun pressLabel(l: String) {
        for (r in 0 until 8) for (c in 0 until 5) {
            if (e.keyText(r, c) == l) { e.press(r, c); return }
        }
        throw AssertionError("No key: $l")
    }

    private fun k(seq: String): String {
        for (s in seq.split(' ')) {
            if (s.isEmpty()) continue
            if (s == "C") { e.clearTemp(); continue }
            if (s.startsWith("^") && s.length > 1) { pressLabel("Conv"); pressLabel(s.substring(1)) } else pressLabel(s)
        }
        return e.display()
    }

    @Test fun fractionDivide() = assertEquals("1ft 8- 3/8in", k("1 3 Feet 6 Inches 3 / 4 ÷ 8 ="))
    @Test fun areaKey() = assertEquals("6ft²", k("6 Feet Feet"))
    @Test fun convertInchToFeet() = assertEquals("1.5ft", k("1 8 Inches Feet Feet"))
    @Test fun backspace() = assertEquals("7/8", k("7 / 1 6 ⌫ ⌫ 8"))
    @Test fun volumeDivide() = assertEquals("259- 3/16in", k("1 Yards Yards Yards ÷ 5 Inches ÷ 3 Feet = Inches"))
    @Test fun percentAdd() = assertEquals("220", k("2 0 0 + 1 0 % ="))
    @Test fun dms() = assertEquals("23.28°", k("2 3 . 1 6 . 4 5 ^dms⇄deg"))
    @Test fun acre() = assertEquals("0.367309acre", k("2 0 0 Feet × 8 0 Feet = ^Acre"))

    @Test fun rafter() {
        assertEquals("19ft 2- 1/2in", k("1 2 Feet Rise 1 5 Feet Run Diag"))
        k("Diag")
        assertEquals("Common Rafter", ui.title)
        assertEquals("38.66°", ui.value("Pitch (angle)"))
        assertEquals("9- 5/8in", ui.value("Pitch (inch per 12\")"))
    }

    @Test fun pitchFromRiseRun() = assertEquals("16in", k("8 Feet Rise 6 Feet Run Pitch"))

    @Test fun riseFromPitchDiag() {
        assertEquals("12ft 2- 7/16in", k("8 Inches Pitch 2 2 Feet Diag Rise"))
        assertEquals("18ft 3- 11/16in", k("Run"))
    }

    @Test fun arcAngle() = assertEquals("141.78°", k("9 Feet 1 0 Inches Run 3 Feet 6 Inches Rise Arc"))
    @Test fun arcRadiusMetric() = assertEquals("3.25m", k("6 m Run 2 m Rise ^Radius"))

    @Test fun qtyOnCenter() {
        k("2 2 Feet 8 Inches 3 / 4 ^qty@oc")
        assertEquals("19 pieces", ui.value("@ 16in on-center"))
    }

    @Test fun room() {
        k("2 2 Feet Length 1 8 Feet 8 Inches Width 9 Feet 6 Inches Height Height")
        assertEquals("3901.33ft³  |  144.49yd³", ui.value("Volume"))
        assertEquals("410.67ft²", ui.value("Floor Area"))
    }

    @Test fun memoryPlus() {
        k("5 M+ 1 0 M+ 1 5 M+ Recall M+")
        assertEquals("30", e.display())
        assertEquals("10", k("M+"))
        assertEquals("3", k("M+"))
    }

    @Test fun storeRecall() = assertEquals("11", k("1 1 Store 1 C Recall 1"))

    @Test fun polygon() {
        k("1 2 Feet Circle 6 ^Polygon")
        assertEquals("6ft", ui.value("Side Length"))
        assertEquals("120.00°", ui.value("Full Corner Angle"))
    }

    @Test fun compoundMiter() {
        k("CmpMtr")
        val rows = ui.spec!!.compute(mapOf("c" to 90.0, "s" to 38.0), "Miter Saw")
        assertEquals("31.62°", rows.first { it.label == "Compound Miter Angle" }.value)
        assertEquals("33.86°", rows.first { it.label == "Compound Bevel Angle" }.value)
    }

    @Test fun stairs() {
        k("1 0 Feet Rise 1 2 Feet Run Stair")
        val sp = ui.spec!!
        val v = sp.fields.associate { it.key to e.parseLen(it.value) }
        val rows = sp.compute(v, null)
        assertEquals("16", rows.first { it.label == "Number of Risers" }.value)
        assertEquals("7- 1/2in", rows.first { it.label == "Actual Riser Height" }.value)
        assertEquals("9- 5/8in", rows.first { it.label == "Actual Tread Width" }.value)
    }

    @Test fun parseLen() {
        assertEquals(118.0 * IN, e.parseLen("9' 10\""), 1e-9)
        assertEquals(7.5 * IN, e.parseLen("7-1/2\""), 1e-9)
    }

    @Test fun slope() = assertEquals("63.43°", k("2 ^Slope"))

    @Test fun trigKeys() {
        e.trig = true
        assertTrue(k("3 0 SIN").startsWith("0.5"))
    }
}
