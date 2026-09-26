package com.kabadimarket.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kabadimarket.app.data.Codes.esc
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Store details printed at the top of every bill / receipt / report (same as old app). */
data class Branding(
    val name: String,
    val gst: String = "",
    val phone: String = "",
    val address: String = "",
    val bank: String = "",
    val logoUrl: String? = null,
) {
    companion object {
        fun from(u: User) = Branding(
            name = u.storeName.ifBlank { "Auto Parts Store" },
            gst = u.storeGst,
            phone = u.storePhone,
            address = u.storeAddress,
            bank = u.storeBank,
            logoUrl = if (u.storeLogo.isNotBlank()) Api.fileUrl(u.storeLogo) else null,
        )
    }
}

/**
 * Printing: builds the same HTML documents as the old app and sends them to
 * Android's print screen (any printer, or "Save as PDF").
 */
object Printer {
    private val live = mutableListOf<WebView>() // keep WebViews alive while printing

    private fun nowText(): String = SimpleDateFormat("dd/MM/yyyy, HH:mm:ss", Locale.ENGLISH).format(Date())

    fun serverDate(iso: String, withTime: Boolean = true): String {
        if (iso.length < 19) return iso
        return try {
            val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val d = p.parse(iso.substring(0, 19)) ?: return iso
            SimpleDateFormat(if (withTime) "dd/MM/yyyy, HH:mm:ss" else "dd/MM/yyyy", Locale.ENGLISH).format(d)
        } catch (e: Exception) {
            iso
        }
    }

    private fun header(b: Branding): String {
        val contact = listOfNotNull(
            b.gst.takeIf { it.isNotBlank() }?.let { "GST: ${esc(it)}" },
            b.phone.takeIf { it.isNotBlank() }?.let { "Phone: ${esc(it)}" },
        ).joinToString(" &nbsp;|&nbsp; ")
        val logo = b.logoUrl?.let { "<img src=\"$it\" style=\"height:56px;max-width:120px;object-fit:contain;margin-right:14px\"/>" } ?: ""
        return """<div style="display:flex;align-items:center;border-bottom:2px solid #222;padding-bottom:10px;margin-bottom:14px">
    $logo
    <div>
      <div style="font-size:22px;font-weight:800">${esc(b.name)}</div>
      ${if (contact.isNotEmpty()) "<div style=\"font-size:12px;color:#555;margin-top:2px\">$contact</div>" else ""}
      ${if (b.address.isNotBlank()) "<div style=\"font-size:12px;color:#555\">${esc(b.address)}</div>" else ""}
      ${if (b.bank.isNotBlank()) "<div style=\"font-size:12px;color:#555\">Bank: ${esc(b.bank)}</div>" else ""}
    </div>
  </div>"""
    }

    fun wrap(title: String, b: Branding, body: String): String = """<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>
    body{font-family:-apple-system,Roboto,Arial,sans-serif;padding:24px;color:#111}
    h2{font-size:15px;color:#333;margin:0 0 4px}
    .meta{font-size:12px;color:#777;margin-bottom:14px}
    table{width:100%;border-collapse:collapse;font-size:12px}
    th,td{border:1px solid #ccc;padding:6px 8px;text-align:left}
    th{background:#f2f2f2}
    .tot{margin-top:16px;font-size:14px;font-weight:bold}
  </style></head><body>
    ${header(b)}
    <h2>${esc(title)}</h2>
    <div class="meta">${esc(nowText())}</div>
    $body
  </body></html>"""

    private fun oldLoc(l: JSONObject?): String =
        if (l == null) "" else listOf("rack", "shelf", "box", "position").map { l.str(it) }.filter { it.isNotBlank() }.joinToString(" -> ")

    fun money(v: Double?): String = if (v == null) "-" else "Rs. " + String.format(Locale.US, "%.2f", v)

    // ---------------- Documents ----------------

    fun barcodeLabelHtml(b: Branding, partNumber: String, company: String?, qrMm: Int): String {
        val body = """<div style="text-align:center;padding:10px">
    ${if (!company.isNullOrBlank()) "<div style=\"font-size:13px;font-weight:700;margin-bottom:6px\">${esc(company)}</div>" else ""}
    ${Codes.code128Svg(partNumber, height = 90, moduleWidth = 2)}
    <div style="display:flex;align-items:center;justify-content:center;margin-top:12px">
      <div style="width:${qrMm}mm;height:${qrMm}mm">${Codes.qrSvg(partNumber, margin = 1)}</div>
    </div>
  </div>"""
        return wrap("Barcode Label", b, body)
    }

    data class Receipt(
        val partNumber: String,
        val name: String? = null,
        val condition: String? = null,
        val price: String? = null,
        val buyer: String? = null,
        val location: JSONObject? = null,
        val by: String? = null,
        val qty: Int? = null,
    )

    fun receiptHtml(b: Branding, kind: String, d: Receipt): String {
        val loc = oldLoc(d.location)
        val lines = listOfNotNull(
            "Type" to (if (kind == "BUY") "Purchase" else "Sale"),
            "Part Number" to d.partNumber,
            "Name" to (d.name?.ifBlank { null } ?: "-"),
            "Condition" to (d.condition?.ifBlank { null } ?: "-"),
            if (d.qty != null && d.qty != 1) "Quantity" to d.qty.toString() else null,
            if (loc.isNotBlank()) "Location" to loc else null,
            if (!d.buyer.isNullOrBlank()) "Buyer" to d.buyer else null,
            if (!d.price.isNullOrBlank()) "Price" to "Rs. ${d.price}" else null,
            if (!d.by.isNullOrBlank()) "By" to d.by else null,
        )
        val rows = lines.joinToString("") { (k, v) -> "<tr><th style=\"width:35%\">${esc(k)}</th><td>${esc(v)}</td></tr>" }
        val barcode = "<div style=\"text-align:center;margin-top:16px\">${Codes.code128Svg(d.partNumber, height = 60)}</div>"
        return wrap(if (kind == "BUY") "Purchase Receipt" else "Sale Receipt", b, "<table>$rows</table>$barcode")
    }

    fun inventoryHtml(b: Branding, units: List<JSONObject>): String {
        val rows = units.mapIndexed { i, u ->
            "<tr><td>${i + 1}</td><td>${esc(u.str("part_number"))}</td><td>${esc(u.str("part_name"))}</td><td>${esc(u.str("condition"))}</td><td>${esc(oldLoc(u.obj("location")))}</td></tr>"
        }.joinToString("")
        val body = "<table><thead><tr><th>#</th><th>Part Number</th><th>Name</th><th>Condition</th><th>Location</th></tr></thead><tbody>$rows</tbody></table><div class=\"tot\">Total units: ${units.size}</div>"
        return wrap("Inventory Report", b, body)
    }

    fun requirementsHtml(b: Branding, reqs: List<JSONObject>): String {
        val rows = reqs.mapIndexed { i, r ->
            "<tr><td>${i + 1}</td><td>${esc(r.str("part_number"))}</td><td>${esc(r.str("name"))}</td><td>${esc(r.str("priority"))}</td><td>${esc(r.opt("quantity") ?: "")}</td><td>${esc(r.int("stock_count"))}</td><td>${esc(r.str("status"))}</td></tr>"
        }.joinToString("")
        val body = "<table><thead><tr><th>#</th><th>Part Number</th><th>Name</th><th>Priority</th><th>Qty</th><th>In Stock</th><th>Status</th></tr></thead><tbody>$rows</tbody></table><div class=\"tot\">Total: ${reqs.size}</div>"
        return wrap("Requirements / Inquiry List", b, body)
    }

    /** Report grouped by company → category (same as old printReport). */
    fun reportHtml(b: Branding, title: String, items: List<JSONObject>, showPrice: Boolean): String {
        val groups = sortedMapOf<String, java.util.SortedMap<String, MutableList<JSONObject>>>()
        items.forEach { it0 ->
            val co = it0.str("company").ifBlank { "All" }
            val cat = it0.str("category").ifBlank { "Uncategorized" }
            groups.getOrPut(co) { sortedMapOf() }.getOrPut(cat) { mutableListOf() }.add(it0)
        }
        val body = StringBuilder()
        var grand = 0.0
        groups.forEach { (co, cats) ->
            body.append("<h3 style=\"margin:16px 0 4px;font-size:15px;border-bottom:2px solid #333\">${esc(co)}</h3>")
            cats.forEach { (cat, list) ->
                val rows = list.joinToString("") { x ->
                    val price = x.num("price")
                    grand += price ?: 0.0
                    val date = x.str("at").ifBlank { x.str("created_at") }
                    "<tr><td>${esc(x.str("part_number"))}</td><td>${esc(x.str("part_name"))}</td><td>${esc(x.str("condition"))}</td>" +
                        (if (showPrice) "<td>${if (price != null) "Rs. " + fmtNum(price) else ""}</td>" else "") +
                        "<td>${esc(if (date.isNotBlank()) serverDate(date, false) else "")}</td></tr>"
                }
                body.append("<div style=\"font-weight:bold;color:#555;margin:8px 0 2px\">${esc(cat)} (${list.size})</div>")
                body.append("<table><thead><tr><th>Part Number</th><th>Name</th><th>Condition</th>${if (showPrice) "<th>Price</th>" else ""}<th>Date</th></tr></thead><tbody>$rows</tbody></table>")
            }
        }
        body.append("<div class=\"tot\">Total items: ${items.size}${if (showPrice) " &nbsp;|&nbsp; Total Rs. ${fmtNum(grand)}" else ""}</div>")
        return wrap(title, b, body.toString())
    }

    /** 1250.0 → "1250", 99.5 → "99.5" (like JavaScript number printing). */
    fun fmtNum(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun invoiceHtml(b: Branding, inv: JSONObject): String {
        val meta = mutableListOf("Invoice No." to inv.str("invoice_number"), "Date" to serverDate(inv.str("at")))
        if (inv.str("customer_name").isNotBlank()) meta.add("Customer" to inv.str("customer_name"))
        val metaRows = meta.joinToString("") { (k, v) -> "<tr><th style=\"width:35%\">${esc(k)}</th><td>${esc(v)}</td></tr>" }
        val rate = ((inv.num("gst_rate") ?: 0.18) * 100).toInt()
        val body = """<table>$metaRows</table>
    <table style="margin-top:14px"><thead><tr><th>Part Number</th><th>Description</th><th>Amount</th></tr></thead>
      <tbody><tr><td>${esc(inv.str("part_number"))}</td><td>${esc(inv.str("description").ifBlank { "-" })}</td><td>${money(inv.num("price"))}</td></tr></tbody>
    </table>
    <table style="margin-top:12px"><tbody>
      <tr><th style="width:70%">Taxable Amount</th><td>${money(inv.num("price"))}</td></tr>
      <tr><th>GST ($rate%)</th><td>${money(inv.num("gst_amount"))}</td></tr>
      <tr><th>Total</th><td><b>${money(inv.num("total"))}</b></td></tr>
    </tbody></table>"""
        return wrap("Tax Invoice", b, body)
    }

    fun invoiceWhatsAppText(b: Branding, inv: JSONObject): String {
        val lines = mutableListOf("*${b.name}*", "Invoice: ${inv.str("invoice_number")}", "Date: ${serverDate(inv.str("at"), false)}")
        if (inv.str("customer_name").isNotBlank()) lines.add("Customer: ${inv.str("customer_name")}")
        val desc = inv.str("description")
        val rate = ((inv.num("gst_rate") ?: 0.18) * 100).toInt()
        lines.add("Part: ${inv.str("part_number")}${if (desc.isNotBlank()) " - $desc" else ""}")
        lines.add("Amount: ${money(inv.num("price"))}")
        lines.add("GST ($rate%): ${money(inv.num("gst_amount"))}")
        lines.add("*Total: ${money(inv.num("total"))}*")
        lines.add("")
        lines.add("Thank you for your business!")
        return lines.joinToString("\n")
    }

    // ---------------- Output ----------------

    /** Opens Android's print screen for this HTML (printer or Save as PDF). */
    @SuppressLint("SetJavaScriptEnabled")
    fun print(context: Context, html: String, jobName: String = "Auto Parts Store") {
        val web = WebView(context)
        live.add(web)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                pm?.print(jobName, view.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
                Handler(Looper.getMainLooper()).postDelayed({ live.remove(view) }, 60_000)
            }
        }
        web.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }

    /** Renders the HTML to a PDF file and opens the share menu (WhatsApp, email …). */
    fun sharePdf(context: Context, html: String, fileName: String, onDone: (Boolean) -> Unit = {}) {
        WebView.enableSlowWholeDocumentDraw()
        val web = WebView(context)
        live.add(web)
        val widthPx = 794 // A4 width at 96 dpi
        web.layout(0, 0, widthPx, 1123)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val heightPx = (view.contentHeight * view.scale).toInt().coerceAtLeast(1123)
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
                        )
                        view.layout(0, 0, widthPx, heightPx)
                        val pageW = 595
                        val pageH = 842
                        val scale = pageW.toFloat() / widthPx
                        val pageHeightPx = (pageH / scale).toInt()
                        val pdf = PdfDocument()
                        var top = 0
                        var n = 1
                        while (top < heightPx) {
                            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, n).create())
                            val c = page.canvas
                            c.scale(scale, scale)
                            c.translate(0f, -top.toFloat())
                            view.draw(c)
                            pdf.finishPage(page)
                            top += pageHeightPx
                            n++
                        }
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        val file = File(dir, fileName)
                        file.outputStream().use { pdf.writeTo(it) }
                        pdf.close()
                        Share.file(context, file.readBytes(), fileName, "application/pdf")
                        onDone(true)
                    } catch (e: Exception) {
                        onDone(false)
                    } finally {
                        live.remove(view)
                    }
                }, 400)
            }
        }
        web.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
    }
}
