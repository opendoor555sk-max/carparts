package com.kabadimarket.app.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** A4 sticker-sheet layouts (same chart as the old app). */
data class SheetLayout(val code: String, val label: String, val total: Int, val w: Double, val h: Double, val rows: Int, val cols: Int)

object Stickers {
    val SHEET_LAYOUTS = listOf(
        SheetLayout("01P", "1 Label (Full A4)", 1, 210.0, 297.0, 1, 1),
        SheetLayout("02L", "2 Labels", 2, 200.0, 146.0, 2, 1),
        SheetLayout("04P", "4 Labels", 4, 100.0, 145.0, 2, 2),
        SheetLayout("06L", "6 Labels", 6, 99.0, 93.0, 3, 2),
        SheetLayout("08L", "8 Labels", 8, 100.0, 72.0, 4, 2),
        SheetLayout("08LA", "8 Labels (90x55)", 8, 90.0, 55.0, 4, 2),
        SheetLayout("12L", "12 Labels", 12, 100.0, 44.0, 6, 2),
        SheetLayout("15L", "15 Labels", 15, 61.0, 21.0, 5, 3),
        SheetLayout("16L", "16 Labels", 16, 99.0, 34.0, 8, 2),
        SheetLayout("18L", "18 Labels", 18, 63.5, 46.6, 6, 3),
        SheetLayout("21L", "21 Labels", 21, 63.5, 38.0, 7, 3),
        SheetLayout("22L", "22 Labels", 22, 100.0, 24.0, 11, 2),
        SheetLayout("24L", "24 Labels", 24, 64.0, 34.0, 8, 3),
        SheetLayout("30L", "30 Labels (67x27.5)", 30, 67.0, 27.5, 10, 3),
        SheetLayout("30P", "30 Labels (39x47.5)", 30, 39.0, 47.5, 6, 5),
        SheetLayout("32P", "32 Labels", 32, 25.0, 70.0, 4, 8),
        SheetLayout("40L", "40 Labels", 40, 39.0, 35.0, 8, 5),
        SheetLayout("40P", "40 Labels (18x73)", 40, 18.0, 73.0, 20, 2),
        SheetLayout("48L", "48 Labels", 48, 48.0, 24.0, 12, 4),
        SheetLayout("56L", "56 Labels", 56, 48.0, 20.0, 14, 4),
        SheetLayout("65L", "65 Labels", 65, 38.0, 21.0, 13, 5),
        SheetLayout("84L", "84 Labels", 84, 46.0, 11.0, 21, 4),
        SheetLayout("110L", "110 Labels", 110, 35.0, 10.0, 22, 5),
    )

    fun layout(code: String) = SHEET_LAYOUTS.firstOrNull { it.code == code } ?: SHEET_LAYOUTS.first { it.code == "24L" }

    /** Code types that encode part numbers reliably (the ones the phone can draw). */
    val CODE_TYPES = listOf(
        "qr" to "QR Code",
        "datamatrix" to "DataMatrix",
        "azteccode" to "Aztec",
        "azteccodecompact" to "Aztec Compact",
        "pdf417" to "PDF417",
        "pdf417compact" to "PDF417 Compact",
        "barcode" to "Barcode 128",
    )

    // ---------------- Code drawing ----------------

    /** Dark/light grid of a code. rowScale stretches rows (for 1-D barcodes). */
    class CodeMatrix(val w: Int, val h: Int, val bits: BooleanArray, val rowScale: Float = 1f) {
        operator fun get(r: Int, c: Int) = bits[r * w + c]
        val ratio: Float get() = w / (h * rowScale)
    }

    private fun fromBitMatrix(m: BitMatrix, margin: Int = 1): CodeMatrix {
        // Trim ZXing's own white border, then add our own small quiet zone (like the old app).
        val rect = m.enclosingRectangle ?: intArrayOf(0, 0, m.width, m.height)
        val (x0, y0, rw, rh) = rect.toList()
        val w = rw + margin * 2
        val h = rh + margin * 2
        val bits = BooleanArray(w * h)
        for (r in 0 until rh) for (c in 0 until rw) {
            if (m.get(x0 + c, y0 + r)) bits[(r + margin) * w + (c + margin)] = true
        }
        return CodeMatrix(w, h, bits)
    }

    private fun qr(v: String): CodeMatrix {
        val q = Codes.qrMatrix(v)
        val n = q.size
        val w = n + 2
        val bits = BooleanArray(w * w)
        for (r in 0 until n) for (c in 0 until n) if (q[r][c]) bits[(r + 1) * w + c + 1] = true
        return CodeMatrix(w, w, bits)
    }

    private fun barcode(v: String): CodeMatrix {
        val widths = Codes.code128Widths(v)
        val quiet = 5
        val total = widths.sum() + quiet * 2
        val bits = BooleanArray(total)
        var x = quiet
        var bar = true
        for (wd in widths) {
            if (bar) for (i in 0 until wd) bits[x + i] = true
            x += wd
            bar = !bar
        }
        // Old app: 2px modules, 60px tall → one row = 30 modules tall.
        return CodeMatrix(total, 1, bits, rowScale = 30f)
    }

    fun codeMatrix(type: String, value: String): CodeMatrix {
        val v = value.ifEmpty { " " }
        // Plain part numbers get no UTF-8 marker (ECI), like the old app — some scanners print it.
        val hints = mutableMapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 0)
        if (v.any { it.code > 127 }) hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        try {
            when (type) {
                "qr" -> return qr(v)
                "barcode" -> return barcode(v)
                "datamatrix" -> return fromBitMatrix(MultiFormatWriter().encode(v, BarcodeFormat.DATA_MATRIX, 0, 0, mapOf(EncodeHintType.DATA_MATRIX_SHAPE to com.google.zxing.datamatrix.encoder.SymbolShapeHint.FORCE_SQUARE)))
                "azteccode" -> return fromBitMatrix(MultiFormatWriter().encode(v, BarcodeFormat.AZTEC, 0, 0, hints))
                "azteccodecompact" -> {
                    for (layers in -1 downTo -4) {
                        try {
                            return fromBitMatrix(MultiFormatWriter().encode(v, BarcodeFormat.AZTEC, 0, 0, hints + (EncodeHintType.AZTEC_LAYERS to layers)))
                        } catch (_: Exception) {
                        }
                    }
                    return fromBitMatrix(MultiFormatWriter().encode(v, BarcodeFormat.AZTEC, 0, 0, hints))
                }
                "pdf417", "micropdf417" -> return fromBitMatrix(MultiFormatWriter().encode(v, BarcodeFormat.PDF_417, 0, 0, hints))
                "pdf417compact" -> return fromBitMatrix(MultiFormatWriter().encode(v, BarcodeFormat.PDF_417, 0, 0, hints + (EncodeHintType.PDF417_COMPACT to true)))
            }
        } catch (_: Exception) {
        }
        // Unsupported type (e.g. Han Xin from an old template) → QR, same as the old app.
        return try { qr(v) } catch (_: Exception) { qr(v.take(200)) }
    }

    fun codeSvg(type: String, value: String): String {
        val m = codeMatrix(type, value)
        val vh = m.h * m.rowScale
        val sb = StringBuilder()
        for (r in 0 until m.h) {
            var c = 0
            while (c < m.w) {
                if (m[r, c]) {
                    var e = c
                    while (e < m.w && m[r, e]) e++
                    sb.append("<rect x=\"$c\" y=\"${f(r * m.rowScale)}\" width=\"${e - c}.02\" height=\"${f(m.rowScale + 0.02f)}\" fill=\"#000\"/>")
                    c = e
                } else c++
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ${m.w} ${f(vh)}\" shape-rendering=\"crispEdges\"><rect width=\"${m.w}\" height=\"${f(vh)}\" fill=\"#fff\"/>$sb</svg>"
    }

    private fun f(x: Float) = String.format(Locale.US, "%.2f", x)
    private fun f2(x: Double) = String.format(Locale.US, "%.2f", x)

    // ---------------- Sticker template ----------------

    data class Box(val x: Double, val y: Double, val w: Double, val h: Double) {
        fun json() = JSONObject().put("x", x).put("y", y).put("w", w).put("h", h)
        companion object {
            fun from(o: JSONObject?, fb: Box) = if (o == null) fb else Box(o.optDouble("x", fb.x), o.optDouble("y", fb.y), o.optDouble("w", fb.w), o.optDouble("h", fb.h))
        }
    }

    data class Line(val text: String, val x: Double, val y: Double, val size: Double, val bold: Boolean = false, val zone: String? = null) {
        fun json(): JSONObject = JSONObject().put("text", text).put("x", x).put("y", y).put("size", size).put("bold", bold).also { if (zone != null) it.put("zone", zone) }
    }

    data class Code(val type: String, val value: String, val box: Box, val sizeMm: Double = 10.0)
    data class Logo(val dataUrl: String, val box: Box)

    data class Tpl(val aspect: Double, val lines: List<Line>, val code: Code?, val logo: Logo?, val company: String?) {
        fun json(): JSONObject = JSONObject()
            .put("aspect", aspect)
            .put("lines", JSONArray().also { a -> lines.forEach { a.put(it.json()) } })
            .put("code", code?.let { JSONObject().put("type", it.type).put("value", it.value).put("box", it.box.json()).put("sizeMm", it.sizeMm) } ?: JSONObject.NULL)
            .put("logo", logo?.let { JSONObject().put("dataUrl", it.dataUrl).put("box", it.box.json()) } ?: JSONObject.NULL)
            .put("company", company ?: JSONObject.NULL)

        companion object {
            fun parse(s: String): Tpl? = try {
                val o = JSONObject(s)
                val lines = o.arr("lines").objects().map { lineFrom(it) }
                val c = o.obj("code")
                val lg = o.obj("logo")
                Tpl(
                    o.optDouble("aspect", 1.6).takeIf { !it.isNaN() && it > 0 } ?: 1.6,
                    lines,
                    c?.let { Code(it.str("type").ifEmpty { "qr" }, it.str("value"), Box.from(it.obj("box"), Box(60.0, 33.0, 30.0, 34.0)), it.optDouble("sizeMm", 10.0).takeIf { d -> !d.isNaN() } ?: 10.0) },
                    lg?.let { l -> l.str("dataUrl").takeIf { it.isNotEmpty() }?.let { Logo(it, Box.from(l.obj("box"), LOGO_START)) } },
                    o.str("company").ifEmpty { null },
                )
            } catch (_: Exception) {
                null
            }

            fun lineFrom(o: JSONObject) = Line(
                o.str("text"), o.optDouble("x", 3.0), o.optDouble("y", 4.0), o.optDouble("size", 5.0),
                o.optBoolean("bold", false), o.str("zone").ifEmpty { null },
            )
        }
    }

    val LOGO_START = Box(3.0, 2.0, 34.0, 15.0)

    // ---------------- Layout helpers (ported from the old app) ----------------

    data class Raw(val text: String, val bold: Boolean = false)

    val COMPANIES = listOf("Hyundai / Kia", "Maruti Suzuki", "Tata", "Mahindra", "Toyota", "Honda", "Nissan", "Renault", "Ford", "Volkswagen", "Skoda", "MG", "Datsun", "Chevrolet", "Fiat", "Jeep", "Citroen", "Isuzu", "Other")
    val FORMATTED = listOf("Hyundai / Kia")

    fun layoutLines(raw: List<Raw>, aspect: Double, hasCode: Boolean, topPad: Double): List<Line> {
        val lines = raw.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val frac = if (hasCode) 0.66 else 0.96
        val widthUnits = aspect * 100 * frac
        val slot = maxOf(4.0, (100 - topPad - 4) / lines.size)
        return lines.mapIndexed { i, ln ->
            val len = maxOf(1, ln.text.length)
            val fs = maxOf(2.75, minOf(slot * 0.92, widthUnits / (len * 0.46), 11.25))
            Line(ln.text, 3.0, topPad + i * slot + (slot - fs) / 2, fs, ln.bold)
        }
    }

    private class Zone(val key: String, val x: Double, val y0: Double, val dy: Double, val w: Double, val base: Double, val center: Boolean = false)

    private val ZONES = listOf(
        Zone("leftTop", 2.0, 19.0, 6.5, 40.0, 6.0),
        Zone("rightTop", 44.0, 3.0, 5.2, 54.0, 5.4),
        Zone("leftMid", 2.0, 44.0, 6.5, 54.0, 6.0),
        Zone("rightMid", 44.0, 46.0, 6.0, 40.0, 5.4),
        Zone("botCenter", 0.0, 82.0, 5.0, 96.0, 5.5, center = true),
        Zone("bottom", 2.0, 90.0, 5.0, 96.0, 4.6),
    )

    private fun positionLines(lines: List<Triple<String, Boolean, String>>, aspect: Double): List<Line> {
        val count = mutableMapOf<String, Int>()
        return lines.map { (text, bold, zoneKey) ->
            val z = ZONES.firstOrNull { it.key == zoneKey } ?: ZONES[2]
            val i = count[z.key] ?: 0
            count[z.key] = i + 1
            val size = maxOf(2.0, minOf(z.base, (z.w * aspect) / (maxOf(1, text.length) * 0.5)))
            var x = z.x
            if (z.center) {
                val wpct = (text.length * 0.5 * size) / aspect
                x = maxOf(1.0, 50 - wpct / 2)
            }
            Line(text, x, z.y0 + i * z.dy, size, bold, z.key)
        }
    }

    private fun autoZonesHK(raw: List<Raw>): List<Triple<String, Boolean, String>> {
        val lines = raw.map { Raw(it.text.trim(), it.bold) }.filter { it.text.isNotEmpty() }
        fun isBottom(u: String) = Regex("(MADE IN|ELECTRONIC|SEOYON|PVT|LTD|//)").containsMatchIn(u)
        fun isBrand(u: String) = u.contains("MOTORS") || Regex("^HYUNDAI\\s*KIA").containsMatchIn(u)
        fun isRight(u: String) = Regex("(UNIT ASSY|ASSY|P/N|LOT|H/W|S/W|VER|HKMC|SYEC)").containsMatchIn(u)
        fun isLeft(u: String) = Regex("(MODEL|IFT|^TA[ -])").containsMatchIn(u)
        fun isCenter(t: String) = t.length <= 6 && Regex("^[A-Za-z]+$").matches(t)
        var last = "leftMid"
        return lines.map { ln ->
            val u = ln.text.uppercase()
            val zone = when {
                isBottom(u) -> "bottom"
                isBrand(u) -> "leftTop"
                isCenter(ln.text) -> "botCenter"
                isLeft(u) -> "leftMid"
                isRight(u) -> "rightTop"
                last == "rightTop" -> "rightTop"
                last == "bottom" -> "bottom"
                else -> "leftMid"
            }
            last = zone
            Triple(ln.text, ln.bold, zone)
        }
    }

    fun buildLines(raw: List<Raw>, aspect: Double, hasCode: Boolean, hasLogo: Boolean, company: String?): List<Line> =
        if (company != null && company in FORMATTED) positionLines(autoZonesHK(raw), aspect)
        else layoutLines(raw, aspect, hasCode, if (hasLogo) 20.0 else 4.0)

    fun codeBox(aspect: Double, company: String?): Box {
        val h = 34.0
        val w = minOf(40.0, h / aspect)
        val y = if (company != null && company in FORMATTED) 46.0 else (100 - h) / 2
        return Box(100 - w - 3, y, w, h)
    }

    /** Old app's field key: text before ':' without digits — so recurring fields keep their place. */
    fun fieldKey(t: String): String {
        val u = t.uppercase().trim()
        val c = u.indexOf(':')
        val k = (if (c >= 0) u.substring(0, c) else u).replace(Regex("[0-9]"), "").replace(Regex("\\s+"), " ").trim()
        return k.ifEmpty { u.take(4) }
    }

    fun applyCompanyFormat(tpl: Tpl, fmt: JSONObject?): Tpl {
        if (fmt == null || fmt.optJSONArray("lines") == null) return tpl
        val map = LinkedHashMap<String, JSONObject>()
        for (s in fmt.arr("lines").objects()) {
            val k = fieldKey(s.str("text"))
            if (!map.containsKey(k)) map[k] = s
        }
        val lines = tpl.lines.map { l ->
            val s = map[fieldKey(l.text)]
            if (s != null) l.copy(x = s.optDouble("x", l.x), y = s.optDouble("y", l.y), size = s.optDouble("size", l.size), bold = s.optBoolean("bold", l.bold)) else l
        }
        val fc = fmt.obj("code")
        val code = if (tpl.code != null && fc != null) {
            tpl.code.copy(
                type = fc.str("type").ifEmpty { tpl.code.type },
                sizeMm = if (fc.has("sizeMm") && !fc.isNull("sizeMm")) fc.optDouble("sizeMm", tpl.code.sizeMm) else tpl.code.sizeMm,
                box = tpl.code.box.copy(y = fc.obj("box")?.optDouble("y", tpl.code.box.y) ?: tpl.code.box.y),
            )
        } else tpl.code
        return tpl.copy(lines = lines, code = code)
    }

    // ---------------- Print HTML ----------------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** A clean white sticker: typed text lines + a regenerated code. No photo pixels. */
    fun templateInner(tpl: Tpl, wMm: Double, hMm: Double): String {
        val out = StringBuilder()
        tpl.logo?.let { lg ->
            val b = lg.box
            out.append("<img src=\"${lg.dataUrl}\" style=\"position:absolute;left:${b.x}%;top:${b.y}%;width:${b.w}%;height:${b.h}%;object-fit:contain\"/>")
        }
        for (ln in tpl.lines) {
            val fs = maxOf(0.8, (ln.size / 100) * hMm)
            out.append("<div style=\"position:absolute;left:${ln.x}%;top:${ln.y}%;font-size:${f2(fs)}mm;font-weight:${if (ln.bold) 800 else 500};white-space:nowrap;line-height:1;color:#000;font-family:Arial,Helvetica,sans-serif\">${esc(ln.text)}</div>")
        }
        val c = tpl.code
        if (c != null && c.value.isNotEmpty()) {
            val m = codeMatrix(c.type, c.value)
            val svg = codeSvg(c.type, c.value)
            val ratio = m.ratio.toDouble()
            val sizeMm = minOf(c.sizeMm, hMm - 1)
            val codeWmm = if (ratio > 1.3) minOf(sizeMm * ratio, wMm * 0.6) else sizeMm
            val topMm = maxOf(0.0, minOf((c.box.y / 100) * hMm, hMm - sizeMm))
            val leftMm = maxOf(0.0, minOf((c.box.x / 100) * wMm, maxOf(0.0, wMm - codeWmm)))
            out.append("<div style=\"position:absolute;left:${f2(leftMm)}mm;top:${f2(topMm)}mm;width:${f2(codeWmm)}mm;height:${f2(sizeMm)}mm;display:flex;align-items:center;justify-content:center\">")
            out.append(svg.replaceFirst("<svg ", "<svg preserveAspectRatio=\"xMidYMid meet\" style=\"width:100%;height:100%\" "))
            out.append("</div>")
        }
        return out.toString()
    }

    /** Composed A4 sheet: each cell (1-based) can hold a different sticker. */
    fun composedSheetHtml(cells: Map<Int, Tpl>, layout: SheetLayout, marginTop: Double?, marginLeft: Double?, pageMargin: Double, showBorder: Boolean = false): String {
        val a4w = 210.0
        val a4h = 297.0
        val autoLeft = maxOf(0.0, (a4w - layout.cols * layout.w) / 2)
        val autoTop = minOf(maxOf(0.0, (a4h - layout.rows * layout.h) / 2), autoLeft)
        val leftM = marginLeft?.let { maxOf(0.0, it) } ?: autoLeft
        val topM = marginTop?.let { maxOf(0.0, it) } ?: autoTop
        val out = StringBuilder()
        for (i in 0 until layout.total) {
            val r = i / layout.cols
            val col = i % layout.cols
            val x = leftM + col * layout.w
            val y = topM + r * layout.h
            val border = if (showBorder) "border:0.2mm dashed #bbb;" else ""
            val inner = cells[i + 1]?.let { templateInner(it, layout.w, layout.h) } ?: ""
            out.append("<div style=\"position:absolute;left:${f2(x)}mm;top:${f2(y)}mm;width:${layout.w}mm;height:${layout.h}mm;box-sizing:border-box;overflow:hidden;background:#fff;$border\"><div style=\"position:relative;width:100%;height:100%\">$inner</div></div>")
        }
        return """<html><head><meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    @page { size: A4; margin: ${maxOf(0.0, pageMargin)}mm; }
    html,body { margin:0; padding:0; }
    .sheet { position:relative; width:${a4w}mm; height:${a4h}mm; }
    * { -webkit-print-color-adjust:exact; print-color-adjust:exact; }
  </style></head>
  <body><div class="sheet">$out</div></body></html>"""
    }
}
