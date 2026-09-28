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
        val r = formRows(mapOf("ridge" to "0", "oh" to "0", "seat" to "0"))
        assertEquals("38.66°  (9-5/8/12)", r.v("Pitch"))
        assertEquals("19ft 2- 1/2in", r.v("Diagonal (line length)"))
    }

    @Test fun pitchFromRiseRun() = assertEquals("16in", k("8 Feet Rise 6 Feet Run Pitch"))

    @Test fun riseFromPitchDiag() {
        assertEquals("12ft 2- 7/16in", k("8 Inches Pitch 2 2 Feet Diag Rise"))
        assertEquals("18ft 3- 11/16in", k("Run"))
    }

    @Test fun arcAngle() = assertEquals("141.78°", k("9 Feet 1 0 Inches Run 3 Feet 6 Inches Rise Arc"))
    @Test fun arcRadiusMetric() = assertEquals("3.25m", k("6 ^m Run 2 ^m Rise ^Radius"))

    @Test fun qtyOnCenter() {
        k("2 2 Feet 8 Inches 3 / 4 ^qty@oc")
        assertEquals("19 pieces", formRows().v("@ 16in on-center"))
        assertEquals("15 pieces", formRows(mapOf("sp" to "19-1/2\"")).v("@ 19- 1/2in on-center"))
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

    private fun formRows(vals: Map<String, String>? = null, seg: String? = null): List<Row> {
        val sp = ui.spec!!
        val v = HashMap<String, Double>()
        sp.fields.forEach { f ->
            val t = vals?.get(f.key) ?: f.value
            if (f.isSize) e.parseSize(t)?.let { v[f.key + "_a"] = it.first; v[f.key + "_b"] = it.second }
            else v[f.key] = if (f.isPitch) e.parsePitch(t) else if (f.isLen) e.parseLen(t) else (t.toDoubleOrNull() ?: Double.NaN)
        }
        return sp.compute(v, seg ?: sp.seg?.getOrNull(sp.segSel))
    }

    private fun List<Row>.v(label: String) = first { !it.section && it.label == label }.value

    @Test fun hipRegular() {
        k("^Conv") // no-op safety
        e.clearTemp()
        pressLabel("Hip/V")
        val r = formRows(mapOf("pa" to "47", "run" to "9' 3-7/8\""))
        assertEquals("37.17°", r.v("Plumb Cut"))
        assertEquals("52.83°", r.v("Level Cut"))
        assertEquals("45.00°", r.v("Cheek Cut – saw bevel"))
        assertEquals("31.14°", r.v("Hip Backing Angle"))
        assertEquals("117.72°", r.v("Dihedral Angle"))
        assertEquals("45.00°", r.v("Plan Angle (deewar A se)"))
    }

    @Test fun hipIrregularSymmetric() {
        k("^IrPitch")
        val r = formRows(mapOf("pa" to "47", "pb" to "47", "run" to "10'"))
        assertEquals("31.14°", r.v("Hip Backing Angle B"))
    }

    @Test fun pitchParse() {
        assertEquals(33.69, e.parsePitch("8/12"), 0.01)
        assertEquals(30.0, e.parsePitch("30"), 1e-9)
    }

    @Test fun jacks() {
        k("Jack")
        val r = formRows(mapOf("pa" to "45", "run" to "4'", "sp" to "16\""))
        assertEquals("22- 5/8in", r.v("Common Difference (har jack ka farak)"))
        assertTrue(r.any { it.label.startsWith("Jack 2") })
    }

    @Test fun weights() {
        assertEquals("15kg", k("5 ^kg + 1 0 ^kg ="))
        assertEquals("33.0693lbs", k("^lbs"))
        pressLabel("Conv"); pressLabel("met tons"); assertEquals("0.015mt", e.display())
    }

    @Test fun masonry() {
        e.metric = true
        k("^Masonry")
        val r = formRows(mapOf("l" to "1000", "h" to "300", "t" to "23", "op" to "0"))
        assertEquals("3623 nag", r.v("Int (+5% waste)"))
    }

    @Test fun footing() {
        e.metric = true
        k("^Footing")
        val r = formRows(mapOf("l" to "100", "w" to "100", "d" to "100", "n" to "1"), "M20 1:1.5:3")
        assertEquals("8.1 bag", r.v("Cement (50 kg bag)"))
    }

    @Test fun boardFeet() {
        k("^BdFt")
        val r = formRows(mapOf("t" to "2\"", "w" to "6\"", "l" to "12'", "q" to "1", "r" to "0"))
        assertEquals("12 BF", r.v("Board feet"))
    }

    @Test fun baluster() {
        k("^Baluster")
        val r = formRows(mapOf("run" to "4'", "w" to "1-1/2\"", "g" to "4\"", "rk" to "0"))
        assertEquals("8", r.v("Balusters ki ginti"))
    }

    @Test fun stairDrawingData() {
        k("1 0 Feet Rise 1 2 Feet Run Stair")
        val r = formRows()
        val plan = r.first { it.stair != null }.stair!!
        assertEquals(16, plan.n)
        assertEquals("5- 5/16in", r.v("Throat (bachi lakdi)"))
    }

    @Test fun roof() {
        k("^Roof")
        val r = formRows(mapOf("l" to "40'", "w" to "24'", "p" to "6/12", "oh" to "0", "sp" to "24\""))
        assertEquals("1073.31ft²", r.v("Roof area (chhajje ke saath)"))
    }

    @Test fun costForm() {
        k("C ^Cost")
        val r = formRows(mapOf("q" to "10", "r" to "50", "g" to "18"))
        assertEquals("₹ 590.00", r.v("Kul"))
    }

    @Test fun costKeys() {
        assertEquals("₹ 1,107.00", k("9 Yards Yards Yards × 1 2 3 ^Cost"))
        assertEquals("₹ 23.07", k("C 5 6 ^BdFt × 4 1 2 ^Cost"))
        assertEquals("₹ 1,500.00", k("C 5 0 0 ^Cost × 3 ="))
    }

    @Test fun densityConversions() {
        assertEquals("14968.5482kg", k("1 1 Yards Yards Yards ^kg"))
        assertEquals("22.5ft³", k("C 1 . 2 5 ^Tons Feet"))
        assertEquals("16.5tons", k("C 1 1 Yards Yards Yards ^Tons"))
        assertEquals("50.1751bf", k("C 4 ^m × 3 7 ^cm × 8 ^cm = ^BdFt"))
    }

    @Test fun wtVolMemory() {
        assertEquals("1600T/yd³", k("1 6 0 0 Store 0"))
        k("Store 0"); k("Store 0"); k("Store 0")
        assertEquals("1600kg/m³", k("Store 0"))
        assertEquals("1.348444T/yd³", k("C Recall 0"))
        assertTrue(k("Recall 0").startsWith("2696.88"))
    }

    @Test fun dmsCycle() {
        assertEquals("23.28°", k("2 3 . 1 6 . 4 5 ^dms⇄deg"))
        assertEquals("5- 3/16in", k("^dms⇄deg"))
        assertTrue(k("^dms⇄deg").startsWith("43.02"))
        assertEquals("0.430237", k("^dms⇄deg"))
        assertEquals("0.406298", k("^dms⇄deg"))
    }

    @Test fun meterCycle() {
        assertEquals("1.4224m", k("5 6 Inches ^m"))
        assertEquals("142.24cm", k("^m"))
        assertEquals("1422.4mm", k("^m"))
    }

    @Test fun arcRiseRun() {
        k("2 ^m Circle 2 0 Arc")
        assertEquals("0.3473m", k("Run"))
        assertEquals("0.0152m", k("Rise"))
    }

    @Test fun arcDiameter() = assertEquals("24ft 8in", k("6 Feet 2 Inches Rise 1 2 0 Arc Circle"))

    @Test fun rafterForm() {
        k("1 2 Feet Run 7 Inches 1 / 2 Pitch Diag")
        k("Diag")
        assertEquals("Common Rafter", ui.title)
        val r = formRows(mapOf("ridge" to "0", "oh" to "0", "seat" to "0"))
        assertEquals("14ft 1- 13/16in", r.v("Kul rafter length"))
    }

    @Test fun jackMating() {
        k("^IrJack")
        val r = formRows(mapOf("pa" to "45", "pb" to "30", "run" to "10'", "sp" to "16\""), "Chhote se shuru|Mating (hip par milte)")
        assertTrue(r.any { it.section && it.label == "Side B jacks (mating)" })
    }

    @Test fun drywallSizes() {
        k("4 8 Feet Length 9 Feet 6 Inches Height ^Drywall")
        val r = formRows(mapOf("cs" to "100x200"), "Deewar L×H")
        e.metric = false
        assertTrue(r.any { it.label.startsWith("4'×12'") })
    }

    @Test fun moneyGroup() = assertEquals("₹ 12,34,567.50", e.money(1234567.5))

    @Test fun fracKey() {
        k("2 5 9 Inches 3 / 1 6")
        pressLabel("Conv"); pressLabel("Frac")
        assertEquals(32, e.res)
    }


    @Test fun purlinRegular45() {
        val (face, edge) = purlinCuts(45.0, 45.0)
        assertEquals(35.26, face, 0.01)
        assertEquals(35.26, edge, 0.01)
    }

    @Test fun hipAreas() {
        k("Hip/V")
        val r = formRows(mapOf("pa" to "30", "run" to "12'", "bl" to "40'"))
        assertEquals("16ft", r.v("Ridge length"))
    }

    @Test fun hipSwitchIrregular() {
        k("Hip/V")
        val r = formRows(mapOf("pa" to "45", "pb" to "30", "run" to "12'"), "Miter Saw|Irregular")
        assertTrue(r.any { it.label == "Side B: face cut (upar ki satah)" })
    }

    @Test fun editableSizes() {
        e.metric = true
        e.sizes["oc"] = mutableListOf(0.5 to 0.0)
        k("1 0 m ^qty@oc") // metric: m is the main key
        val r = formRows()
        assertTrue(r.any { it.label.contains("500mm") })
    }

    @Test fun legacyMemory() {
        e.advanced = false
        assertEquals("7in", k("7 Inches Store 9"))
        k("C 1 0 Feet Rise Stair")
        assertEquals("7in", ui.spec!!.fields.first { it.key == "dr" }.value.replace("\"", "in"))
    }

    @Test fun stairInstall() {
        k("1 0 Feet Rise 1 2 Feet Run Stair")
        val r = formRows()
        assertTrue(r.any { it.stair?.install == true })
        assertEquals("4 nag", r.v("Stringers ki ginti (max 16\" doori)"))
    }

    // ---------------- Machinist calculator (examples from the Machinist Calc Pro user's guide) ----------------
    private fun mk(vararg keys: String): String {
        e.mach = true
        for (s in keys) {
            if (s.startsWith("^") && s.length > 1) { pressLabel("Conv"); pressLabel(s.substring(1)) }
            else if (s.length > 1 && s.all { it.isDigit() || it == '.' }) s.forEach { pressLabel(it.toString()) }
            else pressLabel(s)
        }
        return e.display()
    }

    @Test fun machRpmFeed() {
        mk(".375", "Diam")
        mk("300", "Cut Speed")
        assertEquals("3056", mk("RPM"))
        mk("Clear")
        mk("4", "#Teeth")
        mk(".005", "Feed/Tooth")
        mk("1000", "RPM")
        assertEquals("20", mk("Feed Rate"))
        assertEquals("0.02in", mk("Cut Feed"))
    }

    @Test fun machTriangle() {
        mk("3", "Adj (x)")
        mk("4", "Opp (y)")
        assertEquals("5in", mk("Hyp (r)"))
        assertEquals("53.13°", mk("Angle (Ø)"))
        assertEquals("36.87°", mk("Angle (Ø)"))
    }

    @Test fun machDrills() {
        mk("36", "Drill Size")
        assertEquals("0.1065in", e.display())
        assertTrue(e.displayLabel().startsWith("#36"))
        mk("^Alpha", "8", "8", "8", "8", "Drill Size")
        assertTrue(e.displayLabel().startsWith("E "))
        assertEquals("0.25in", e.display())
        mk(".5", "Drill Size")
        assertEquals("0.1502in", mk("^Drill Point"))
    }

    private fun cycleLabels(n: Int): List<String> = (0 until n).map { mk("Thread Size"); e.displayLabel() + " = " + e.display() }

    @Test fun machThread832() {
        mk("8", "Thread Size", "32", "Thread Size")
        val c = cycleLabels(10)
        assertTrue(c[1], c[1].contains("#29"))
        assertTrue(c[2], c[2].contains("3.75 mm"))
        assertTrue(c[3], c[3].contains("#18"))
        assertTrue(c[4], c[4].contains("#16"))
        assertTrue(c[5], c[5].endsWith("0.1437in"))
        assertTrue(c[6], c[6].endsWith("0.1475in"))
        assertTrue(c[7], c[7].endsWith("0.13in"))
        assertTrue(c[8], c[8].endsWith("0.139in"))
        assertTrue(c[9], c[9].endsWith("0.164in"))
        mk("^Thread Class", "^Thread Class")          // INT 2B -> EXT 2A
        assertTrue(e.displayLabel(), e.displayLabel().startsWith("EXT 2A"))
        val x = cycleLabels(8)
        assertTrue(x[2], x[2].endsWith("0.1412in"))
        assertTrue(x[3], x[3].endsWith("0.1428in"))
        assertTrue(x[4], x[4].endsWith("0.1399in"))
        assertTrue(x[5], x[5].endsWith("0.1631in"))
        assertTrue(x[7], x[7].endsWith("0.1259in"))
    }

    @Test fun machThreadMetric() {
        e.metric = true
        mk("5", "Thread Size", "0.75", "Thread Size")
        mk("4", "^Thread Class")                      // INT MM 4H
        val c = cycleLabels(9)
        assertTrue(c[1], c[1].contains("4.25 mm"))
        assertTrue(c[2], c[2].contains("#14"))
        assertTrue(c[3], c[3].contains("5.30 mm"))
        assertTrue(c[4], c[4].contains("5.80 mm"))
        assertTrue(c[5], c[5].endsWith("4.513mm"))
        assertTrue(c[7], c[7].endsWith("4.188mm"))
        assertTrue(c[8], c[8].endsWith("4.306mm"))
        mk("^Thread Class", "^Thread Class")          // show, then INT 4H -> EXT 4g
        val x = cycleLabels(8)
        assertTrue(x[2], x[2].endsWith("4.452mm"))
        assertTrue(x[3], x[3].endsWith("4.491mm"))
        assertTrue(x[4], x[4].endsWith("4.435mm"))
        assertTrue(x[5], x[5].endsWith("4.978mm"))
        assertTrue(x[6], x[6].endsWith("4.888mm"))
        assertTrue(x[7], x[7].endsWith("4.166mm"))
    }

    @Test fun machWireAndBolts() {
        mk(".375", "Thread Size", "16", "Thread Size")
        assertEquals("0.0361in", mk("^Wire Size"))
        assertEquals("0.0563in", mk("^Wire Size"))
        assertEquals("0.035in", mk("^Wire Size"))
        mk("10", "Adj (x)", "15", "Opp (y)", "20", "Angle (Ø)", "3.5", "Diam", "3", "Bolt Pattern")
        assertEquals("3.0311in", mk("Bolt Pattern"))
        assertEquals("11.6445in", mk("Bolt Pattern"))
        assertEquals("15.5985in", mk("Bolt Pattern"))
    }

    @Test fun machSwitchKeepsNirmaanKeys() {
        e.mach = true
        assertEquals("Cut Speed", e.keyText(0, 0))
        e.mach = false
        assertEquals("Rise", e.keyText(0, 0))
    }

    // ---------------- Self-test: every key, both calculators, many situations ----------------
    private fun typeSeq(seq: String) {
        for (t in seq.split(' ')) {
            if (t.isEmpty()) continue
            if (t.startsWith("^")) { pressLabel("Conv"); pressLabel(t.substring(1).replace('_', ' ')) }
            else if (t.length > 1 && t.all { it.isDigit() || it == '.' }) t.forEach { pressLabel(it.toString()) }
            else pressLabel(t.replace('_', ' '))
        }
    }

    @Test fun stairPlannerStraight() {
        e.stairPlanner()
        val sp = ui.spec!!
        assertEquals("Stair Planner", sp.title)
        assertTrue(sp.links.isNotEmpty())
        val vals = mapOf("L" to "20'", "W" to "8'", "H" to "10'", "sw" to "3'", "dr" to "7\"", "dt" to "10\"")
        val r = formRows(vals, "Ghar|1 Straight")
        assertTrue(r.v("Riser (oonchai) × ginti").contains("× 18"))
        // straight: 18 risers in one flight -> fits but code warning
        assertTrue(r.first { it.label.endsWith("1 Straight") }.value.startsWith("✓"))
        assertTrue(r.first { it.label.endsWith("1 Straight") }.value.contains("⚠"))
        // straight + landing: 9 + 9, fits; for a house the L-shape is suggested first
        val two = r.first { it.label.endsWith("2 Straight + landing") }
        assertTrue(two.value.startsWith("✓"))
        assertTrue(r.first { it.label.endsWith("5 Dog-legged") }.label.startsWith("⭐"))
        assertEquals(2, r.count { it.plan != null })
        val r2 = formRows(vals, "Ghar|2 Straight + landing")
        assertTrue(r2.v("Flight 1").startsWith("9 riser"))
        assertTrue(r2.v("Flight 2").startsWith("9 riser"))
        val need = r2.v("Jagya chahiye (lambai × pohlai)")
        assertTrue("need=$need", need.startsWith("19") && need.contains("4"))
        // short room: straight + landing does not fit in 15'
        val r3 = formRows(vals + ("L" to "15'"), "Ghar|1 Straight")
        assertTrue(r3.first { it.label.endsWith("2 Straight + landing") }.value.startsWith("✗"))
        // metric and all options run without error
        val errs = mutableListOf<String>()
        checkForm("planner", errs)
        e.metric = true; e.stairPlanner(); checkForm("planner metric", errs)
        assertTrue(errs.joinToString("\n"), errs.isEmpty())
    }

    @Test fun stairPlannerLShapes() {
        e.stairPlanner()
        val vals = mapOf("L" to "20'", "W" to "8'", "H" to "10'", "sw" to "3'", "dr" to "7\"", "dt" to "10\"")
        // L in a 20' x 8' room: most even split that fits is 7 + 11 (turned: short arm across the 8')
        val l = formRows(vals, "Ghar|3 L-shape")
        assertTrue(l.v("Flight 1"), l.v("Flight 1").startsWith("7 riser"))
        assertTrue(l.v("Flight 2"), l.v("Flight 2").startsWith("11 riser"))
        assertEquals("8ft × 14ft 4in", l.v("Jagya chahiye (lambai × pohlai)"))
        assertTrue(l.first { it.label.endsWith("3 L-shape") }.value.startsWith("✓ bethegi (ghuma kar)"))
        // Double-L: three flights adding to 18, fits
        val dl = formRows(vals, "Ghar|4 Double-L")
        val f = (1..3).map { dl.v("Flight $it").substringBefore(' ').toInt() }
        assertEquals(18, f.sum())
        assertTrue(dl.first { it.label.endsWith("4 Double-L") }.value.startsWith("✓"))
        assertEquals(2, dl.count { it.plan != null })
        // square 12' x 12' room: straight + landing does not fit, the L does
        val sq = formRows(vals + ("L" to "12'") + ("W" to "12'"), "Ghar|3 L-shape")
        assertTrue(sq.first { it.label.endsWith("2 Straight + landing") }.value.startsWith("✗"))
        assertTrue(sq.first { it.label.endsWith("3 L-shape") }.value.startsWith("✓"))
        // tiny room: nothing fits, reason is given
        val tiny = formRows(vals + ("L" to "6'") + ("W" to "6'"), "Ghar|4 Double-L")
        assertTrue(tiny.first { it.label.endsWith("4 Double-L") }.value.contains("kam"))
    }

    @Test fun stairPlannerHalfTurn() {
        e.stairPlanner()
        val vals = mapOf("L" to "20'", "W" to "8'", "H" to "10'", "sw" to "3'", "dr" to "7\"", "dt" to "10\"", "wg" to "1'")
        // dog-legged: 9 + 9, flights side by side -> 6' wide, length = going + 2 landings
        val dg = formRows(vals, "Ghar|5 Dog-legged")
        assertTrue(dg.v("Flight 1"), dg.v("Flight 1").startsWith("9 riser"))
        assertTrue(dg.v("Flight 2"), dg.v("Flight 2").startsWith("9 riser"))
        assertEquals("12ft 8in × 6ft", dg.v("Jagya chahiye (lambai × pohlai)"))
        assertEquals("6ft × 3ft", dg.v("Landing (1)"))
        assertTrue(dg.first { it.label.endsWith("5 Dog-legged") }.value.startsWith("✓"))
        // open-well: 1' gap -> 7' wide
        val ow = formRows(vals, "Ghar|6 Open-well")
        assertEquals("12ft 8in × 7ft", ow.v("Jagya chahiye (lambai × pohlai)"))
        assertEquals("7ft × 3ft", ow.v("Landing (1)"))
        // 6'6" wide room: dog-legged fits, open-well does not
        val nr = formRows(vals + ("W" to "6'6\""), "Ghar|6 Open-well")
        assertTrue(nr.first { it.label.endsWith("5 Dog-legged") }.value.startsWith("✓"))
        assertTrue(nr.first { it.label.endsWith("6 Open-well") }.value.startsWith("✗"))
        // high-rise: the scissor stair (two separate exits) is suggested first when it fits
        val hr = formRows(vals + ("L" to "30'") + ("W" to "12'"), "High-rise|6 Open-well")
        assertTrue(hr.first { it.label.endsWith("14 Scissor") }.label.startsWith("⭐"))
        assertTrue(hr.first { it.label.endsWith("6 Open-well") }.value.startsWith("✓"))
    }

    private fun flightSum(r: List<Row>) = r.filter { it.label.startsWith("Flight ") && !it.label.contains("stringer") }
        .sumOf { it.value.substringBefore(' ').toInt() }

    @Test fun stairPlannerAllTypes() {
        e.stairPlanner()
        val vals = mapOf("L" to "12'", "W" to "12'", "H" to "10'", "sw" to "3'", "dr" to "7\"", "dt" to "10\"", "wg" to "1'")
        // three-quarter turn: 4 flights, 3 landings, fits a 12' x 12' room
        val tq = formRows(vals, "Ghar|7 Three-quarter")
        assertEquals(18, flightSum(tq))
        assertEquals(3, tq.v("Landing (3)").let { 3 })
        assertTrue(tq.first { it.label.endsWith("7 Three-quarter") }.value.startsWith("✓"))
        // L-winder: 3 winders of 30° in the corner, flights carry the other 15 risers
        val lw = formRows(vals, "Ghar|8 L-Winder")
        assertEquals("3 nag × 30.00°", lw.v("Winder pagthiya"))
        assertEquals(15, flightSum(lw))
        assertTrue(lw.first { it.label.endsWith("8 L-Winder") }.value.startsWith("✓"))
        assertTrue(lw.any { it.label.startsWith("Andar") } && lw.any { it.label.startsWith("Bahar") } && lw.any { it.label.startsWith("Chalvani line") })
        // two-quarter winder: two corners of 3 winders
        val w2 = formRows(vals, "Ghar|9 Two-quarter winder")
        assertEquals(2, w2.count { it.label == "Winder pagthiya" })
        assertEquals(12, flightSum(w2))
        // U-winder: 6 winders of 30° in the half turn
        val uw = formRows(vals, "Ghar|10 U-Winder")
        assertEquals("6 nag × 30.00°", uw.v("Winder pagthiya"))
        assertEquals(12, flightSum(uw))
        // winders are not allowed for commercial exits
        val cw = formRows(vals, "Commercial|8 L-Winder")
        assertTrue(cw.any { it.warn && it.label.startsWith("Winder: Commercial") })
        // spiral: 30" wide by default -> 5'6" circle, fits a 6' x 6' room
        val sp = formRows(vals + ("sw" to "") + ("L" to "6'") + ("W" to "6'"), "Ghar|11 Spiral")
        assertEquals("5ft 6in", sp.v("Gol (diameter)"))
        assertTrue(sp.first { it.label.endsWith("11 Spiral") }.value.startsWith("✓"))
        assertTrue(sp.any { it.label.startsWith("Pag ni pohlai — pole paase") })
        // helical: inner / walk line / outer going shown
        val hl = formRows(vals + ("L" to "16'") + ("W" to "16'"), "Ghar|12 Helical (gol)")
        assertTrue(hl.any { it.label == "Kul ghumav" })
        assertTrue(hl.any { it.label == "Pag ni pohlai — andar ni kinaar" })
        // bifurcated: wide middle flight + two side flights
        val bf = formRows(vals + ("L" to "20'"), "Ghar|13 Bifurcated")
        assertTrue(bf.v("Flight 2"), bf.v("Flight 2").contains("× 2"))
        assertEquals("12ft 8in × 12ft", bf.v("Jagya chahiye (lambai × pohlai)"))
        // scissor: two stairs of 9 + 9 with mid landings, 6'8" wide
        val sc = formRows(vals + ("L" to "30'"), "High-rise|14 Scissor")
        assertEquals("22ft 4in × 6ft 8in", sc.v("Jagya chahiye (lambai × pohlai)"))
        assertTrue(sc.first { it.label.endsWith("14 Scissor") }.label.startsWith("⭐"))
        // all types on one screen (for the PDF): 14 plan drawings
        val all = formRows(vals, "Ghar|★ Badhi sidi (PDF)")
        assertEquals(14, all.count { it.plan != null })
        // no room given: nothing crashes, fit shows —
        val nr = formRows(vals + ("L" to "") + ("W" to ""), "Ghar|7 Three-quarter")
        assertTrue(nr.first { it.label.endsWith("7 Three-quarter") }.value.startsWith("—"))
    }

    @Test fun stairPlannerUsesOwnRiserTread() {
        // user's own 8" riser / 8" tread: used as given, not shown as an error
        e.stairPlanner()
        val r = formRows(mapOf("L" to "20'", "W" to "8'", "H" to "10'", "sw" to "", "dr" to "8\"", "dt" to "8\""), "Ghar|1 Straight")
        val riser = r.first { it.label == "Riser (oonchai) × ginti" }
        assertTrue(riser.value, riser.value.startsWith("8in × 15"))
        assertTrue(!riser.warn && !r.first { it.label == "Tread (pag ki jagah)" }.warn)
        assertTrue(r.any { it.value == "salah" && it.label.contains("riser") })
        // code width rounded to a whole inch: 1.0 m -> 3ft 3in
        assertTrue(r.v("Seedhi ki pohlai"), r.v("Seedhi ki pohlai").startsWith("3ft 3in"))
        // flight uses 8" treads: 15 risers -> 14 treads -> 9ft 4in
        assertTrue(r.v("Flight 1"), r.v("Flight 1").contains("9ft 4in"))
    }

    @Test fun stairKeyLinksToPlanner() {
        k("1 0 Feet Rise 1 2 Feet Run Stair")
        // the stair result is a panel; the planner is opened from the stair form link
        e.stairKey()
        val link = ui.spec!!.links.first()
        link.second()
        assertEquals("Stair Planner", ui.spec!!.title)
    }

    /** runs a form's calculation with its default values and with every option */
    private fun checkForm(where: String, errors: MutableList<String>) {
        val sp = ui.spec ?: return
        val segs = sp.seg ?: listOf(null)
        val segs2 = sp.seg2 ?: listOf(null)
        for (a in segs) for (b in segs2) {
            val sel = if (sp.seg2 != null) (a ?: "") + "|" + (b ?: "") else a
            try {
                val rows = formRows(null, sel)
                if (rows.isEmpty()) errors.add("$where [form ${sp.title} / $sel]: khali result")
            } catch (t: Throwable) {
                errors.add("$where [form ${sp.title} / $sel]: " + t)
            }
        }
    }

    @Test fun selfTestEveryKey() {
        val errors = mutableListOf<String>()
        val nirmaanStarts = listOf("", "5", "3 Feet 6 Inches", "1 2 Feet Rise 1 5 Feet Run 1 0 Feet Length 8 Feet Width 9 Feet Height 7",
            "2 ^m Circle 4 5 Arc 3", "1 0 Yards Yards Yards", "2 5 %", "1 0 +")
        val machStarts = listOf("", "5", "3 Inch", ".5 Diam 300 Cut_Speed 4 #Teeth .002 Feed/Tooth",
            "8 Thread_Size 32 Thread_Size", "3 Adj_(x) 4 Opp_(y) 20 Angle_(Ø) 3.5 Diam 6 Bolt_Pattern", "1 / 4 Inch Thread_Size 20 Thread_Size ^Thread_Class ^Thread_Class",
            "10 + ")
        var checked = 0
        for (mach in listOf(false, true)) for (metric in listOf(false, true)) {
            val starts = if (mach) machStarts else nirmaanStarts
            for (start in starts) for (conv in listOf(false, true)) for (r in 0 until 8) for (c in 0 until 5) {
                ui = FakeUi(); e = Engine(ui)
                e.mach = mach; e.metric = metric
                val where = (if (mach) "Machinist" else "Nirmaan") + (if (metric) "/Metric" else "/US") + " start='" + start + "'"
                try {
                    if (!mach && metric) { /* metric keypad shows m/cm/mm on the unit row */ }
                    typeSeq(if (mach || !metric) start else start.replace("^m", "m").replace("Feet", "m").replace("Inches", "cm").replace("Yards", "m"))
                } catch (t: Throwable) {
                    errors.add("$where: start failed: $t"); continue
                }
                val label = e.keyDef(r, c).let { if (conv && it.conv.isNotEmpty()) it.conv else it.main }
                if (conv && e.keyDef(r, c).conv.isEmpty()) continue
                if (label == "Conv") continue
                val w = "$where key=" + (if (conv) "Conv+" else "") + label
                try {
                    repeat(3) { i ->
                        ui.spec = null
                        e.lastError = null
                        if (conv) e.conv = true
                        e.press(r, c)
                        e.lastError?.let { errors.add("$w (press ${i + 1}): $it") }
                        e.display(); e.displayLabel(); e.tag()
                        for (rr in 0 until 8) for (cc in 0 until 5) { e.keyText(rr, cc); e.keyTop(rr, cc) }
                        checkForm(w, errors)
                    }
                    // then use the result in a sum
                    e.lastError = null
                    typeSeq("+ 2 =")
                    e.lastError?.let { errors.add("$w (+2=): $it") }
                    checked++
                } catch (t: Throwable) {
                    errors.add("$w CRASH: $t")
                }
            }
        }
        println("SELFTEST checked=$checked errors=${errors.size}")
        errors.take(60).forEach { println("SELFTEST ERR: $it") }
        assertTrue("Self-test found ${errors.size} problems:\n" + errors.take(40).joinToString("\n"), errors.isEmpty())
        assertTrue("Self-test ne sirf $checked keys check kiye", checked > 1500)
    }

    @Test
    fun concreteStraightStair() {
        assertEquals(3048.0, LenUnit.FOOT.mm(10.0), 1e-9)
        assertEquals(254.0, LenUnit.INCH.mm(10.0), 1e-9)
        assertEquals(3000.0, LenUnit.METER.mm(3.0), 1e-9)
        assertEquals(300.0, LenUnit.CM.mm(30.0), 1e-9)
        val c = concreteStair(3000.0, 1000.0)!!
        assertEquals(20, c.risers); assertEquals(150.0, c.riserMm, 1e-9); assertEquals(19, c.treads)
        assertEquals(5130.0, c.runMm, 1e-9)
        assertEquals(5942.8, c.slabLenMm, 0.05)
        assertEquals(0.89142, c.slabM3, 1e-4); assertEquals(0.38475, c.stepsM3, 1e-5)
        assertEquals(1.9653, c.dryM3, 1e-4)
        assertEquals(11, c.cementBags)
        assertEquals(18.93, c.sandCft, 0.01); assertEquals(37.86, c.aggCft, 0.01)
        // 10 ft high, 3 ft wide
        val f = concreteStair(LenUnit.FOOT.mm(10.0), LenUnit.FOOT.mm(3.0))!!
        assertEquals(20, f.risers); assertEquals(152.4, f.riserMm, 1e-9); assertEquals(10, f.cementBags)
        assertTrue(concreteStair(0.0, 1000.0) == null)
    }
}
