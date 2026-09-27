package com.kabadi.calc

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Draws the hisab bill. Same drawing is used for the JPG and the PDF. */
object Bill {

    fun dateText(t: Long): String = SimpleDateFormat("dd-MM-yyyy  hh:mm a", Locale.US).format(Date(t))

    /** sale date + credit days */
    fun dueDate(t: Long, days: String): String {
        val d = evalExpr(days).let { if (it.isFinite()) it else 0.0 }
        return SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date(t + (d * 86400000L).toLong()))
    }

    private fun tp(size: Float, bold: Boolean = false, color: Int = Color.rgb(0x21, 0x21, 0x21), align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; textAlign = align
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    /** Draws on [c] (or only measures when c == null). Returns the height used. */
    fun draw(c: Canvas?, W: Float, h: Hisab): Float {
        val u = W / 360f
        val pad = 14 * u
        var y = 0f
        val green = Color.rgb(0x1B, 0x5E, 0x20)
        val red = Color.rgb(0xC6, 0x28, 0x28)
        val band = Paint().apply { color = Color.rgb(0x26, 0x32, 0x38) }
        val light = Paint().apply { color = Color.rgb(0xEC, 0xEF, 0xF1) }
        val lineP = Paint().apply { color = Color.rgb(0xCF, 0xD8, 0xDC); strokeWidth = u }
        c?.drawColor(Color.WHITE)

        // header band
        val headH = 74 * u
        c?.drawRect(0f, 0f, W, headH, band)
        val white = Color.WHITE
        c?.drawText(TextUtils.ellipsize(Store.owner.ifBlank { L.t("app") }, tp(19 * u, true, white), W * 0.62f, TextUtils.TruncateAt.END).toString(), pad, 28 * u, tp(19 * u, true, white))
        val sub = listOf(Store.mobile, Store.address).filter { it.isNotBlank() }.joinToString("  •  ")
        if (sub.isNotEmpty()) c?.drawText(TextUtils.ellipsize(sub, tp(11 * u, false, white), W * 0.62f, TextUtils.TruncateAt.END).toString(), pad, 47 * u, tp(11 * u, false, Color.rgb(0xCF, 0xD8, 0xDC)))
        c?.drawText(if (h.type == "haraji") L.t("haraji") else L.t("hisab"), W - pad, 28 * u, tp(16 * u, true, Color.rgb(0xFF, 0xB7, 0x4D), Paint.Align.RIGHT))
        c?.drawText(dateText(h.time), W - pad, 47 * u, tp(11 * u, false, white, Paint.Align.RIGHT))
        c?.drawText("#" + (h.id % 100000), W - pad, 64 * u, tp(10 * u, false, Color.rgb(0xB0, 0xBE, 0xC5), Paint.Align.RIGHT))
        y = headH + 8 * u

        fun info(k: String, v: String) {
            if (v.isBlank()) return
            c?.drawText(L.t(k) + ":", pad, y + 13 * u, tp(11.5f * u, false, Color.rgb(0x60, 0x7D, 0x8B)))
            c?.drawText(TextUtils.ellipsize(v, tp(13 * u, true), W - 2 * pad - 110 * u, TextUtils.TruncateAt.END).toString(), pad + 110 * u, y + 13 * u, tp(13 * u, true))
            y += 19 * u
        }
        info("party", h.party + "  (" + (if (h.role == "buyer") L.t("buyer_s") else L.t("seller_s")) + ")")
        info("vinfo", h.vehicleInfo())
        info("vehicle", h.vehicle)
        info("place", h.place)
        info("note", h.note)
        y += 4 * u

        fun section(t: String) {
            c?.drawRect(0f, y, W, y + 22 * u, light)
            c?.drawText(t, pad, y + 15.5f * u, tp(12.5f * u, true, Color.rgb(0x37, 0x47, 0x4F)))
            y += 26 * u
        }
        fun row(label: String, value: String, bold: Boolean = false, color: Int = Color.rgb(0x21, 0x21, 0x21), size: Float = 12.5f) {
            c?.drawText(TextUtils.ellipsize(label, tp(size * u, bold), W * 0.6f, TextUtils.TruncateAt.END).toString(), pad, y + 13 * u, tp(size * u, bold, color))
            c?.drawText(value, W - pad, y + 13 * u, tp(size * u, bold, color, Paint.Align.RIGHT))
            y += 19 * u
        }
        fun rule() { c?.drawLine(pad, y, W - pad, y, lineP); y += 5 * u }

        // 1. vehicle price
        section((if (h.type == "haraji") L.t("buy_price") else L.t("price")).removePrefix("1. "))
        row(L.t("sum_price"), money(h.price), true)
        y += 4 * u

        // 2. expenses
        if (h.kharch.isNotEmpty()) {
            section(L.t("kharch").substringBefore(" (").removePrefix("2. "))
            h.kharch.forEachIndexed { i, l ->
                row("${i + 1}. " + l.name + if (l.udhaar) "  (" + L.t("udhaar") + " • " + (if (l.remaining() < 0.005) L.t("chukaya") else L.t("left") + " " + money(l.remaining())) + ")" else "", money(l.value()),
                    color = if (l.udhaar && !l.paid) red else Color.rgb(0x21, 0x21, 0x21))
            }
            rule()
            row(L.t("sum_kharch"), money(h.kharchTotal()), true)
            y += 4 * u
        }
        if (h.kharchBaaki() > 0) row(L.t("dena_baaki"), money(h.kharchBaaki()), false, red)
        row(L.t("sum_lagat"), money(h.lagat()), true, red, 13.5f)
        y += 6 * u

        // 3. parts
        if (h.maal.isNotEmpty()) {
            section((if (h.role == "seller") L.t("sell") else L.t("maal")).substringBefore(" (").removePrefix("3. "))
            val cKg = W * 0.50f
            val cRate = W * 0.68f
            val hp = tp(10.5f * u, true, Color.rgb(0x60, 0x7D, 0x8B))
            c?.drawText(L.t("item"), pad, y + 11 * u, hp)
            val anyLtr = h.maal.any { !it.fixed && it.litre }
            c?.drawText(if (anyLtr) L.t("qty") else L.t("kg"), cKg, y + 11 * u, tp(10.5f * u, true, Color.rgb(0x60, 0x7D, 0x8B), Paint.Align.RIGHT))
            c?.drawText(if (anyLtr) L.t("rate").substringBefore(" ") + " ₹" else "₹/" + L.t("kg"), cRate, y + 11 * u, tp(10.5f * u, true, Color.rgb(0x60, 0x7D, 0x8B), Paint.Align.RIGHT))
            c?.drawText(L.t("amount").replace(" ₹", ""), W - pad, y + 11 * u, tp(10.5f * u, true, Color.rgb(0x60, 0x7D, 0x8B), Paint.Align.RIGHT))
            y += 16 * u
            rule()
            h.maal.forEachIndexed { i, l ->
                val name = TextUtils.ellipsize("${i + 1}. " + l.name, tp(12.5f * u), cKg - pad - 48 * u, TextUtils.TruncateAt.END).toString()
                c?.drawText(name, pad, y + 13 * u, tp(12.5f * u))
                if (l.fixed) {
                    c?.drawText(L.t("fix"), cRate, y + 13 * u, tp(11.5f * u, false, Color.rgb(0x78, 0x90, 0x9C), Paint.Align.RIGHT))
                } else {
                    c?.drawText(plain(l.kg) + if (anyLtr) (if (l.litre) " L" else " kg") else "", cKg, y + 13 * u, tp(12.5f * u, false, Color.rgb(0x21, 0x21, 0x21), Paint.Align.RIGHT))
                    c?.drawText(plain(l.rate), cRate, y + 13 * u, tp(12.5f * u, false, Color.rgb(0x21, 0x21, 0x21), Paint.Align.RIGHT))
                }
                c?.drawText(money(l.value()).removePrefix("₹ "), W - pad, y + 13 * u, tp(12.5f * u, false, Color.rgb(0x21, 0x21, 0x21), Paint.Align.RIGHT))
                y += 19 * u
                if (h.role == "seller") {
                    val who = listOf(l.cName, l.cMobile, if (l.udhaar) L.t("udhaar") + (if (l.daysText.isNotBlank()) " • " + L.t("due") + " " + dueDate(h.time, l.daysText) else "") +
                        (if (l.pays.isNotEmpty()) " • " + L.t("got") + " " + money(l.received()) + " • " + L.t("left") + " " + money(l.remaining()) else "") else L.t("rokad"),
                        if (l.gName.isNotBlank() || l.gMobile.isNotBlank()) L.t("gname").substringBefore(" (") + ": " + (l.gName + " " + l.gMobile).trim() else "",
                        if (l.shop.isNotBlank()) L.t("shop") + " " + l.shop else "").filter { it.isNotBlank() }.joinToString("  •  ")
                    if (who.isNotEmpty()) {
                        c?.drawText(TextUtils.ellipsize("    ↳ " + who, tp(10.5f * u), W - 2 * pad, TextUtils.TruncateAt.END).toString(), pad, y + 10 * u,
                            tp(10.5f * u, false, if (l.udhaar) Color.rgb(0xEF, 0x6C, 0x00) else Color.rgb(0x60, 0x7D, 0x8B)))
                        y += 15 * u
                    }
                }
            }
            rule()
            row(L.t("sum_kg"), plain(h.kg()) + " " + L.t("kg"))
            if (anyLtr) row(L.t("sum_ltr"), plain(h.litre()) + " " + L.t("ltr"))
            row(L.t("sum_maal"), money(h.maalTotal()), true, green, 13.5f)
            if (h.udhaarBikri() > 0) {
                row(L.t("rokad_bikri"), money(h.rokadBikri()))
                row(L.t("lena_baaki"), money(h.lenaBaaki()), false, Color.rgb(0xEF, 0x6C, 0x00))
            }
            y += 6 * u
        }

        if (h.type == "haraji") {
            section(L.t("sale").removePrefix("4. ").substringBefore(" ("))
            row(L.t("sale_s"), money(h.sale))
            if (h.maal.isNotEmpty()) row(L.t("sum_maal"), money(h.maalTotal()))
            row(L.t("bikri"), money(h.bikri()), true, green)
            row(L.t("comm_s") + (if (h.commPct) " (" + plain(evalExpr(h.commText).let { if (it.isFinite()) it else 0.0 }) + "%)" else ""), "- " + money(h.commission()), false, Color.rgb(0xEF, 0x6C, 0x00))
            y += 6 * u
        }

        // result box
        val m = h.munafa()
        val boxH = 52 * u
        val col = if (m >= 0) green else red
        c?.drawRoundRect(RectF(pad, y, W - pad, y + boxH), 8 * u, 8 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
        c?.drawText(if (m >= 0) L.t("profit") else L.t("loss"), pad + 12 * u, y + 32 * u, tp(16 * u, true, Color.WHITE))
        c?.drawText(money(Math.abs(m)), W - pad - 12 * u, y + 34 * u, tp(22 * u, true, Color.WHITE, Paint.Align.RIGHT))
        y += boxH + 6 * u
        if (h.hasSplit()) {
            listOf(h.mudiName.ifBlank { L.t("mudi") } to h.mudiPct, h.khedName.ifBlank { L.t("khed") } to h.khedPct).forEach { (n, p) ->
                val v = m * p / 100
                row(n + "  (" + plain(p) + "%)", (if (v >= 0) L.t("profit") else L.t("loss")) + "  " + money(Math.abs(v)), true, if (v >= 0) green else red)
            }
            y += 4 * u
        }
        c?.drawText(L.t("sum_maal") + " " + money(h.maalTotal()) + "  −  " + L.t("sum_lagat").substringBefore(" (") + " " + money(h.lagat()),
            W / 2, y + 11 * u, tp(9.5f * u, false, Color.rgb(0x78, 0x90, 0x9C), Paint.Align.CENTER))
        y += 18 * u
        if (h.type == "haraji" && h.partners.isNotEmpty()) {
            section(L.t("company").removePrefix("6. "))
            val m2 = h.munafa()
            val list = h.partners.map { Triple(it.name.ifBlank { "—" }, it.share, it) }
            val cSh = W * 0.42f
            val cIn = W * 0.64f
            val hp = Color.rgb(0x60, 0x7D, 0x8B)
            c?.drawText(L.t("partner").substringBefore(" "), pad, y + 11 * u, tp(10.5f * u, true, hp))
            c?.drawText("%", cSh, y + 11 * u, tp(10.5f * u, true, hp, Paint.Align.RIGHT))
            c?.drawText(L.t("invest"), cIn, y + 11 * u, tp(10.5f * u, true, hp, Paint.Align.RIGHT))
            c?.drawText(if (m2 >= 0) L.t("profit") else L.t("loss"), W - pad, y + 11 * u, tp(10.5f * u, true, hp, Paint.Align.RIGHT))
            y += 16 * u
            rule()
            val all = list.map { Triple(it.first, it.second, h.partnerLagat(it.third) to h.partnerMunafa(it.third)) } +
                (if (h.ownerShare() > 0) listOf(Triple(L.t("owner_share"), h.ownerShare(), h.lagat() * h.ownerShare() / 100 to m2 * h.ownerShare() / 100)) else emptyList())
            all.forEach { (n, sh, v) ->
                c?.drawText(TextUtils.ellipsize(n, tp(12.5f * u), cSh - pad - 30 * u, TextUtils.TruncateAt.END).toString(), pad, y + 13 * u, tp(12.5f * u))
                c?.drawText(plain(sh), cSh, y + 13 * u, tp(12.5f * u, false, Color.rgb(0x21, 0x21, 0x21), Paint.Align.RIGHT))
                c?.drawText(money(v.first).removePrefix("₹ "), cIn, y + 13 * u, tp(12.5f * u, false, Color.rgb(0x21, 0x21, 0x21), Paint.Align.RIGHT))
                c?.drawText(money(v.second).removePrefix("₹ ").replace("-₹ ", "-"), W - pad, y + 13 * u, tp(12.5f * u, true, if (v.second >= 0) green else red, Paint.Align.RIGHT))
                y += 19 * u
            }
            y += 8 * u
        }
        c?.drawText(L.t("made"), W / 2, y + 11 * u, tp(9 * u, false, Color.rgb(0xB0, 0xBE, 0xC5), Paint.Align.CENTER))
        y += 20 * u
        return y
    }

    private fun fileName(h: Hisab, ext: String): String {
        val p = h.party.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(20)
        return "Hisab_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(h.time)) + (if (p.isNotEmpty()) "_$p" else "") + "." + ext
    }

    fun bitmap(h: Hisab): Bitmap {
        val w = 1080f
        val hh = draw(null, w, h)
        val bmp = Bitmap.createBitmap(w.toInt(), hh.toInt() + 1, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), w, h)
        return bmp
    }

    /** Saves the JPG to Pictures/KabadiCalc. Returns a content Uri (null if permission is needed). */
    fun saveJpg(ctx: Context, h: Hisab): Uri? {
        val bmp = bitmap(h)
        val name = fileName(h, "jpg")
        return if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KabadiCalc")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            uri
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.insertImage(ctx.contentResolver, bmp, name, L.t("hisab"))?.let { Uri.parse(it) }
        }
    }

    /** A4 PDF (long bills are split over pages). */
    private fun pdf(h: Hisab): PdfDocument {
        val doc = PdfDocument()
        val pw = 595
        val ph = 842
        val w = pw.toFloat()
        val total = draw(null, w, h)
        var top = 0f
        var n = 1
        while (top < total) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            c.translate(0f, -top)
            draw(c, w, h)
            doc.finishPage(page)
            top += ph
            n++
        }
        return doc
    }

    /** Saves the PDF to Downloads/KabadiCalc (Android 10+) or the app folder. Returns a Uri to share. */
    fun savePdf(ctx: Context, h: Hisab): Uri? {
        val doc = pdf(h)
        val name = fileName(h, "pdf")
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KabadiCalc")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
                ctx.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                return uri
            }
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "KabadiCalc")
            dir.mkdirs()
            val f = File(dir, name)
            FileOutputStream(f).use { doc.writeTo(it) }
            return Uri.fromFile(f)
        } finally {
            doc.close()
        }
    }

    /** plain text for WhatsApp */
    fun text(h: Hisab): String {
        val sb = StringBuilder()
        sb.append("*").append(Store.owner.ifBlank { L.t("app") }).append("*\n")
        if (Store.mobile.isNotBlank()) sb.append(Store.mobile).append("\n")
        sb.append(L.t("hisab")).append("  ").append(dateText(h.time)).append("\n")
        if (h.party.isNotBlank()) sb.append(L.t("party")).append(": ").append(h.party).append("\n")
        if (h.vehicleInfo().isNotBlank()) sb.append(h.vehicleInfo()).append("\n")
        if (h.vehicle.isNotBlank()) sb.append(L.t("vehicle")).append(": ").append(h.vehicle).append("\n")
        if (h.place.isNotBlank()) sb.append(L.t("place").substringBefore(" (")).append(": ").append(h.place).append("\n")
        sb.append("\n").append(L.t("sum_price")).append(": ").append(money(h.price)).append("\n")
        if (h.kharch.isNotEmpty()) {
            sb.append("\n_").append(L.t("kharch").substringBefore(" (").removePrefix("2. ")).append("_\n")
            h.kharch.forEach {
                sb.append("• ").append(it.name).append(": ").append(money(it.value()))
                if (it.udhaar) sb.append(" (").append(L.t("udhaar")).append(" • ").append(if (it.paid) L.t("chukaya") else L.t("baaki")).append(")")
                sb.append("\n")
            }
            sb.append(L.t("sum_kharch")).append(": ").append(money(h.kharchTotal())).append("\n")
        }
        sb.append("*").append(L.t("sum_lagat")).append(": ").append(money(h.lagat())).append("*\n")
        if (h.maal.isNotEmpty()) {
            sb.append("\n_").append(L.t("maal").substringBefore(" (").removePrefix("3. ")).append("_\n")
            h.maal.forEach {
                sb.append("• ").append(it.name).append(": ")
                if (!it.fixed) sb.append(plain(it.kg)).append(if (it.litre) " litre × " else " kg × ").append(plain(it.rate)).append(" = ")
                sb.append(money(it.value()))
                if (h.role == "seller" && (it.cName.isNotBlank() || it.udhaar)) {
                    sb.append(" → ").append(it.cName)
                    if (it.udhaar) sb.append(" (").append(L.t("udhaar")).append(if (it.daysText.isNotBlank()) ", " + L.t("due") + " " + dueDate(h.time, it.daysText) else "").append(")")
                }
                sb.append("\n")
            }
            sb.append(L.t("sum_kg")).append(": ").append(plain(h.kg())).append(" kg\n")
            if (h.maal.any { !it.fixed && it.litre }) sb.append(L.t("sum_ltr")).append(": ").append(plain(h.litre())).append(" litre\n")
            sb.append("*").append(L.t("sum_maal")).append(": ").append(money(h.maalTotal())).append("*\n")
        }
        if (h.type == "haraji") {
            sb.append("\n").append(L.t("sale_s")).append(": ").append(money(h.sale)).append("\n")
            sb.append(L.t("bikri")).append(": ").append(money(h.bikri())).append("\n")
            sb.append(L.t("comm_s")).append(": -").append(money(h.commission())).append("\n")
        }
        val m = h.munafa()
        sb.append("\n*").append(if (m >= 0) L.t("profit") else L.t("loss")).append(": ").append(money(Math.abs(m))).append("*\n")
        if (h.hasSplit()) {
            listOf(h.mudiName.ifBlank { L.t("mudi") } to h.mudiPct, h.khedName.ifBlank { L.t("khed") } to h.khedPct).forEach { (n, p) ->
                val v = m * p / 100
                sb.append("• ").append(n).append(" (").append(plain(p)).append("%): ").append(if (v >= 0) L.t("profit") else L.t("loss")).append(" ").append(money(Math.abs(v))).append("\n")
            }
        }
        if (h.lenaBaaki() > 0) sb.append(L.t("lena_baaki")).append(": ").append(money(h.lenaBaaki())).append("\n")
        if (h.denaBaaki() > 0) sb.append(L.t("dena_baaki")).append(": ").append(money(h.denaBaaki())).append("\n")
        if (h.type == "haraji" && h.partners.isNotEmpty()) {
            sb.append("\n_").append(L.t("company").removePrefix("6. ")).append("_\n")
            h.partners.forEach { p ->
                sb.append("• ").append(p.name).append(" (").append(plain(p.share)).append("%): ")
                    .append(L.t("invest")).append(" ").append(money(h.partnerLagat(p))).append(", ")
                    .append(if (h.partnerMunafa(p) >= 0) L.t("profit") else L.t("loss")).append(" ").append(money(Math.abs(h.partnerMunafa(p)))).append("\n")
            }
        }
        return sb.toString()
    }
}
