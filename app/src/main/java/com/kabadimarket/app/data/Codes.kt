package com.kabadimarket.app.data

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** Barcode (Code 128-B) and QR code generation — same output as the old app. */
object Codes {
    private val PATTERNS = listOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112",
    )
    private const val START_B = 104
    private const val STOP = 106

    fun code128Text(value: String): String = value.uppercase().replace(Regex("[^ -~]"), "")

    private fun encode(value: String): List<Int> {
        val codes = mutableListOf(START_B)
        var sum = START_B
        value.forEachIndexed { i, ch ->
            var v = ch.code - 32
            if (v < 0 || v > 94) v = 0
            codes.add(v)
            sum += v * (i + 1)
        }
        codes.add(sum % 103)
        codes.add(STOP)
        return codes
    }

    /** Bar/space widths in modules, starting with a bar. */
    fun code128Widths(value: String): List<Int> =
        encode(code128Text(value)).flatMap { c -> PATTERNS[c].map { it.digitToInt() } }

    fun code128Svg(value: String, height: Int = 70, moduleWidth: Int = 2, showText: Boolean = true): String {
        val raw = code128Text(value)
        val textH = if (showText) 20 else 0
        var x = 10
        val bars = StringBuilder()
        var bar = true
        for (w0 in code128Widths(raw)) {
            val w = w0 * moduleWidth
            if (bar) bars.append("<rect x=\"$x\" y=\"0\" width=\"$w\" height=\"$height\" fill=\"#000\"/>")
            x += w
            bar = !bar
        }
        val width = x + 10
        val text = if (showText) {
            "<text x=\"${width / 2.0}\" y=\"${height + 15}\" font-family=\"monospace\" font-size=\"14\" text-anchor=\"middle\" fill=\"#000\">${esc(raw)}</text>"
        } else ""
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"${height + textH}\" viewBox=\"0 0 $width ${height + textH}\"><rect width=\"$width\" height=\"${height + textH}\" fill=\"#fff\"/>$bars$text</svg>"
    }

    /** QR code modules (true = dark), error correction M like the old app. */
    fun qrMatrix(value: String): Array<BooleanArray> {
        val v = value.ifEmpty { " " }
        val qr = Encoder.encode(v, ErrorCorrectionLevel.M, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        val m = qr.matrix
        return Array(m.height) { r -> BooleanArray(m.width) { c -> m.get(c, r).toInt() == 1 } }
    }

    fun qrSvg(value: String, margin: Int = 2): String {
        val m = qrMatrix(value)
        val count = m.size
        val size = count + margin * 2
        val rects = StringBuilder()
        for (r in 0 until count) for (c in 0 until count) {
            if (m[r][c]) rects.append("<rect x=\"${c + margin}\" y=\"${r + margin}\" width=\"1.02\" height=\"1.02\" fill=\"#000\"/>")
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $size $size\" width=\"100%\" height=\"100%\" shape-rendering=\"crispEdges\"><rect width=\"$size\" height=\"$size\" fill=\"#fff\"/>$rects</svg>"
    }

    fun esc(s: Any?): String = (s?.toString() ?: "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
