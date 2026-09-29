package com.kabadi.calc

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** one person who must be told about a hisab (WhatsApp / SMS text, ready to send) */
/** [full] = mudi malik / khedut / mehta: gets the whole hisab. Everybody else (company partners, buyers, sellers, service) gets only what concerns him */
class Rcpt(val name: String, val mobile: String, val role: String, val text: String, val full: Boolean = false)

/**
 * Messages after each OK step. Everybody whose contact number is in the hisab gets the deal details:
 * mudi malik, khedut, mehta, company partners, the one we bought from, the ones we paid a service to,
 * and the buyers of the goods. Credit lines always carry the credit time (due date) and what is left.
 * phase 0 = names OK, 1 = buying OK, 2 = expenses OK, 3 = final.
 */
object Notify {
    private fun date(t: Long) = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date(t))

    const val BISM = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
    /** first lines of every message: Bismillah, then the greeting with the full name */
    fun opening(name: String): String = BISM + "\n🙏 " + L.t("salam") + (if (nm(name).isNotBlank()) " " + nm(name) else "") + ",\n"

    /** a name written properly: no double spaces, first letter capital for English words (riyan → Riyan) */
    fun nm(x: String): String = x.replace(Regex("\\s+"), " ").trim().trim('.', ',', '-', '_').split(" ").filter { it.isNotEmpty() }.joinToString(" ") { w ->
        if (w[0].isLowerCase() && w.all { it.code < 128 }) w.replaceFirstChar { it.uppercaseChar() } else w
    }

    /** header every message starts with: who sends, what deal, vehicle, place, who is in it */
    fun deal(h: Hisab, lite: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("*").append(nm(Store.owner).ifBlank { L.t("app") }).append("*")
        if (Store.mobile.isNotBlank()) sb.append("  ").append(Store.mobile)
        sb.append("\n").append(if (h.type == "haraji") "🔨 " + L.t("haraji") else "🚚 " + L.t("hisab")).append(" • ").append(date(h.time)).append("\n")
        val v = h.vehicleMsg()
        if (v.isNotBlank()) sb.append("🚚 ").append(v).append("\n")
        // buyers / partners / service people are not told where the vehicle was bought, nor who owns it (lite)
        if (!lite && h.place.isNotBlank()) sb.append("📍 ").append(h.place).append("\n")
        if (lite) return sb.toString()
        if (h.mudiName.isNotBlank()) sb.append("💰 ").append(L.t("mudi_h")).append(": ").append(nm(h.mudiName)).append(" (").append(plain(h.mudiPct)).append("%)\n")
        if (h.khedName.isNotBlank() && h.khedPct > 0) sb.append("🚚 ").append(L.t("khed_h")).append(": ").append(nm(h.khedName)).append(" (").append(plain(h.khedPct)).append("%)\n")
        if (h.type == "haraji" && h.mehtaName.isNotBlank()) sb.append("🔨 ").append(L.t("mehta_h")).append(": ").append(nm(h.mehtaName)).append("  ").append(h.mehtaMobile).append("\n")
        return sb.toString()
    }

    /** one line: what, how much, cash or credit; credit shows the due date and what is left */
    fun lineText(h: Hisab, l: Line): String {
        val sb = StringBuilder("• ").append(L.ln(l))
        if (!l.fixed && l.key != "veh" && l.key != "sale" && l.kg != 0.0) sb.append(" ").append(plain(l.kg)).append(if (l.litre) " L" else " kg").append(" × ").append(plain(l.rate))
        sb.append(": ").append(money(l.value()))
        if (l.udhaar) {
            sb.append("\n   ⏳ ").append(L.t("udhaar"))
            dueTime(h, l)?.let { sb.append(" • ").append(L.t("due")).append(" ").append(date(it)) }
            if (l.received() > 0.004) sb.append(" • ").append(L.t("got")).append(" ").append(money(l.received()))
            sb.append(" • ").append(L.t("left")).append(" ").append(money(l.remaining()))
        } else sb.append("  (").append(L.t("rokad_s")).append(")")
        return sb.toString()
    }

    /** message for the one buyer of one line, right after "OK – sold" */
    fun soldText(h: Hisab, l: Line): String =
        opening(l.cName) + deal(h, true) + "\n" + lineText(h, l) + "\n\n🙏 " + nm(Store.owner)

    /** one common text for all people of the deal (sent in one SMS to everybody): shares, invest, profit / loss */
    fun groupText(h: Hisab, phase: Int): String {
        if (phase >= 3) return BISM + "\n🙏 " + L.t("salam") + ",\n" + Bill.text(h) + "\n🙏 " + nm(Store.owner)
        val sb = StringBuilder(BISM + "\n🙏 " + L.t("salam") + ",\n" + deal(h))
        when (phase) {
            0 -> sb.append("✅ ").append(L.t("msg_started")).append("\n")
            1 -> sb.append("🛒 ").append(L.t("msg_buy")).append(": ").append(money(h.price)).append("\n")
            2 -> sb.append("💸 ").append(L.t("sum_kharch")).append(": ").append(money(h.kharchTotal())).append("\n")
            else -> {
                val m = h.munafa()
                sb.append("✅ ").append(L.t("final_s")).append("\n📊 ").append(if (m >= 0) L.t("profit") else L.t("loss")).append(" ").append(money(Math.abs(m))).append("\n")
            }
        }
        h.partners.filter { it.share > 0 }.forEach { p ->
            sb.append("🏢 ").append(nm(p.name).ifBlank { "?" }).append(" (").append(plain(p.share)).append("%): ")
            if (phase >= 3) { val pm = h.partnerMunafa(p); sb.append(L.t("invest_w")).append(" ").append(money(h.partnerLagat(p))).append(" ➡ ").append(if (pm >= 0) L.t("profit") else L.t("loss")).append(" ").append(money(Math.abs(pm))) }
            else sb.append(L.t("invest_w")).append(" ").append(money((if (phase == 1) h.price else h.lagat()) * p.share / 100))
            sb.append("\n")
        }
        sb.append("🙏 ").append(Store.owner.ifBlank { "" })
        return sb.toString()
    }

    private class Acc(var name: String) { val roles = mutableListOf<String>(); val parts = mutableListOf<String>(); var full = false }

    /** who to tell after [phase]; [me] = this phone's own number (never messaged) */
    fun recipients(h: Hisab, phase: Int, me: String): List<Rcpt> {
        val map = LinkedHashMap<String, Acc>()
        fun add(name: String, mob: String, role: String, piece: String, full: Boolean = false) {
            val m = digits10(mob)
            if (m.length != 10 || m == me) return
            val a = map.getOrPut(m) { Acc(name) }
            if (a.name.isBlank()) a.name = name
            if (full) a.full = true
            if (role !in a.roles) a.roles.add(role)
            if (piece.isNotBlank() && piece !in a.parts) a.parts.add(piece)
        }
        // the people of the deal
        val people = mutableListOf<Triple<String, String, String>>()
        if (h.mudiPct > 0 || h.mudiName.isNotBlank()) people.add(Triple(h.mudiName, h.mudiMobile, "💰 " + L.t("mudi_h")))
        if (h.khedPct > 0 || h.khedName.isNotBlank()) people.add(Triple(h.khedName, h.khedMobile, "🚚 " + L.t("khed_h")))
        if (h.type == "haraji" && h.mehtaName.isNotBlank()) people.add(Triple(h.mehtaName, h.mehtaMobile, "🔨 " + L.t("mehta_h")))
        val co = "🏢 " + L.t("role_co")
        val coPartners = h.partners.filter { it.share > 0 || it.name.isNotBlank() }

        // mudi malik, khedut, mehta, partners: after every OK they get the WHOLE hisab written so far (not only the last step)
        if (phase >= 1) people.forEach { (n, m, r) -> add(n, m, r, "", full = true) }
        when (phase) {
            0 -> { people.forEach { (n, m, r) -> add(n, m, r, "✅ " + L.t("msg_started")) }; coPartners.forEach { p -> add(p.name, p.mobile, co, "✅ " + L.t("msg_started")) } }
            1 -> {
                people.forEach { (n, m, r) -> add(n, m, r, "🛒 " + L.t("msg_buy") + ": " + money(h.price)) }
                coPartners.forEach { p -> if (p.share > 0) add(p.name, p.mobile, co, "💰 " + L.t("your_invest") + ": " + money(h.price * p.share / 100) + "\n📌 " + L.t("your_share") + ": " + plain(p.share) + "%") }
                if (digits10(h.buyLine.cMobile).length == 10) add(h.buyLine.cName, h.buyLine.cMobile, "🛒 " + L.t("due_veh"), "🛒 " + L.t("msg_buy") + "\n" + lineText(h, h.buyLine))
            }
            2 -> {
                people.forEach { (n, m, r) -> add(n, m, r, "💸 " + L.t("sum_kharch") + ": " + money(h.kharchTotal())) }
                coPartners.forEach { p -> if (p.share > 0) add(p.name, p.mobile, co, "💰 " + L.t("your_invest") + ": " + money(h.lagat() * p.share / 100) + "\n📌 " + L.t("your_share") + ": " + plain(p.share) + "%") }
                h.kharch.filter { digits10(it.cMobile).length == 10 }.forEach { l -> add(l.cName, l.cMobile, "🧾 " + L.t("kharch").substringAfter(". ").substringBefore(" ("), lineText(h, l)) }
            }
            else -> {
                val m = h.munafa()
                // mudi malik, khedut, mehta get the WHOLE hisab
                people.forEach { (n, mob, r) -> add(n, mob, r, "", full = true) }
                fun mine(pct: Double, amount: Double) = "📌 " + L.t("your_share") + ": " + plain(pct) + "%\n➡ " + (if (amount >= 0) L.t("your_profit") else L.t("your_loss")) + ": " + money(Math.abs(amount))
                val total = "📊 " + L.t("total_result") + ": " + (if (m >= 0) L.t("profit") else L.t("loss")) + " " + money(Math.abs(m))
                if (h.mudiPct > 0) add(h.mudiName, h.mudiMobile, "💰 " + L.t("mudi_h"), total + "\n" + mine(h.mudiPct, h.mudiShare()))
                if (h.khedPct > 0) add(h.khedName, h.khedMobile, "🚚 " + L.t("khed_h"), total + "\n" + mine(h.khedPct, h.khedShare()))
                if (h.type == "haraji") {
                    val cr = h.companyResult()
                    // company partners: only the vehicle (name, variant, year – in the header), what the auction paid, his invest, share and profit
                    // (no purchase price, no expenses, no place, no other people's lines)
                    h.partners.filter { it.share > 0 }.forEach { p ->
                        val pm = h.partnerMunafa(p)
                        add(p.name, p.mobile, co,
                            (if (h.sale != 0.0) "🔨 " + L.t(if (h.isCo) "co_give" else "sale_s") + ": " + money(h.sale) + "\n" else "") +
                            "🏢 " + L.t("co_result") + ": " + (if (cr >= 0) L.t("profit") else L.t("loss")) + " " + money(Math.abs(cr)) + "\n" +
                                "💰 " + L.t("your_invest") + ": " + money(h.partnerLagat(p)) + "\n" + mine(p.share, pm))
                    }
                }
                // buyers of the goods, one message each with all his items
                h.maal.filter { digits10(it.cMobile).length == 10 && it.value() != 0.0 }.groupBy { digits10(it.cMobile) }.forEach { (mob, ls) ->
                    add(ls.first().cName, mob, "🛍 " + L.t("buyer"), ls.joinToString("\n") { lineText(h, it) } + "\n" + L.t("total") + ": " + money(ls.sumOf { it.value() }))
                }
                if (h.type == "haraji" && h.sale != 0.0 && digits10(h.saleLine.cMobile).length == 10)
                    add(h.saleLine.cName, h.saleLine.cMobile, "🛍 " + L.t("buyer"), lineText(h, h.saleLine))
            }
        }
        return map.map { (m, a) ->
            val who = nm(a.name)
            Rcpt(who, m, a.roles.joinToString(" + "),
                opening(who) + (if (a.full) Bill.text(h) else deal(h, true)) + "\n" +
                    a.parts.joinToString("\n\n") + "\n\n🙏 " + nm(Store.owner), a.full)
        }
    }
}
