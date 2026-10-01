package com.nirmaan.calc

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** "Sab Unit" — every unit of the world: type anything, or pick a category and see all units at once. */
class UnitsActivity : Activity() {

    private var lang = 0                       // 0 English, 1 हिन्दी, 2 ગુજરાતી
    private val pins = LinkedHashSet<String>() // "Mera dabba"
    private var country: String? = null        // flag filter
    private var curCat: String? = null         // null = home
    private var from: UDef? = null
    private var baseVal = 1.0

    private lateinit var search: EditText
    private lateinit var answer: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var langBtn: TextView
    private lateinit var titleTv: TextView

    // converter widgets
    private var amountEt: EditText? = null
    private var fromBtn: TextView? = null
    private var feelTv: TextView? = null
    private val rowVals = ArrayList<Pair<UDef, TextView>>()
    private var settingAmount = false

    private fun dp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, resources.displayMetrics)
    private fun dpi(x: Float) = dp(x).toInt()
    private fun c(hex: Long) = hex.toInt()
    private fun llp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun t(en: String, hi: String, gu: String) = when (lang) { 1 -> hi; 2 -> gu; else -> en }

    private val BG = 0xFF0D0D0D
    private val CARD = 0xFF1C2226
    private val ACC = 0xFFF7A832
    private val TXT = 0xFFEDEDED
    private val SUB = 0xFF9AA7AE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("units", Context.MODE_PRIVATE)
        lang = sp.getInt("lang", 1)
        sp.getString("pins", "")!!.split(',').filter { it.isNotEmpty() && Units.byId(it) != null }.forEach { pins.add(it) }
        if (pins.isEmpty()) listOf("ft", "m", "vigha_gj", "acre", "kg", "man_gj").forEach { pins.add(it) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c(BG)); fitsSystemWindows = true
            setPadding(dpi(10f), dpi(6f), dpi(10f), 0)
        }
        // header
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(chip("←", 0xFF2A3238) { onBackPressed() }, llp(dpi(44f), dpi(40f)))
        titleTv = TextView(this).apply {
            textSize = 22f; setTextColor(c(ACC)); typeface = Typeface.DEFAULT_BOLD
            setPadding(dpi(10f), 0, 0, 0)
        }
        head.addView(titleTv, llp(0, WRAP_CONTENT, 1f))
        langBtn = chip("", 0xFF2A3238) {
            lang = (lang + 1) % 3
            sp.edit().putInt("lang", lang).apply()
            refreshAll()
        }
        head.addView(langBtn, llp(WRAP_CONTENT, dpi(40f)))
        root.addView(head)

        // search box
        search = EditText(this).apply {
            textSize = 18f; setTextColor(c(TXT)); setHintTextColor(c(0xFF6B7780))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = GradientDrawable().apply { setColor(c(CARD)); cornerRadius = dp(24f); setStroke(dpi(2f), c(ACC)) }
            setPadding(dpi(18f), dpi(12f), dpi(18f), dpi(12f))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onSearch() }
        })
        root.addView(search, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })

        answer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(answer, llp(MATCH_PARENT, WRAP_CONTENT))

        scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(6f), 0, dpi(24f)) }
        scroll.addView(body)
        root.addView(scroll, llp(MATCH_PARENT, 0, 1f))
        setContentView(root)
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("units", Context.MODE_PRIVATE).edit().putString("pins", pins.joinToString(",")).apply()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            curCat != null -> { curCat = null; refreshAll() }
            search.text.isNotEmpty() -> search.setText("")
            country != null -> { country = null; refreshAll() }
            else -> super.onBackPressed()
        }
    }

    // ================= small builders =================
    private fun chip(text: String, color: Long, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 16f; setTextColor(c(TXT)); gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { setColor(c(color)); cornerRadius = dp(20f) }
        setPadding(dpi(12f), dpi(4f), dpi(12f), dpi(4f))
        setOnClickListener { onClick() }
    }

    private fun label(text: String, size: Float = 15f, color: Long = SUB, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(c(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(c(CARD)); cornerRadius = dp(14f) }
        setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
    }

    private fun refreshAll() {
        titleTv.text = "🌍 " + t("All Units", "सब यूनिट", "બધા યુનિટ")
        langBtn.text = when (lang) { 1 -> "हिं"; 2 -> "ગુ"; else -> "EN" }
        search.hint = t("Type: 2 vigha in acre, 5 ft + 30 cm", "लिखो: 2 बीघा एकड़ में, 5 ft + 30 cm", "લખો: 2 વીઘા એકર માં, 5 ft + 30 cm")
        onSearch()
    }

    // ================= search / typed answer =================
    private fun onSearch() {
        answer.removeAllViews()
        val q = search.text.toString().trim()
        if (q.isEmpty()) { if (curCat == null) showHome() else showConverter(); return }
        val r = Units.parse(q)
        if (r != null) {
            val box = card()
            val main = r.to ?: r.from
            val v = main.fromBase(r.base)
            box.addView(label("= " + Units.fmt(v) + " " + main.name(lang) + sym(main), 24f, ACC, true))
            if (r.to == null || r.terms > 1) {
                // a few handy equivalents
                val more = equivalents(r.cat, r.base, main).take(4)
                for (d in more) box.addView(label(Units.fmt(d.fromBase(r.base)) + "  " + d.name(lang) + sym(d), 16f, TXT))
            }
            Units.feel(r.cat, r.base, lang)?.let { box.addView(label(it, 14f, SUB)) }
            val open = chip("📋 " + t("All units", "सब यूनिट देखो", "બધા યુનિટ જુઓ"), 0xFF2E7D32) {
                curCat = r.cat; from = main; baseVal = r.base; hideKb(); search.setText("")
            }
            box.addView(open, llp(WRAP_CONTENT, dpi(38f)).apply { topMargin = dpi(6f) })
            answer.addView(box, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
            body.removeAllViews()
            return
        }
        // not a sum: show units whose name matches
        val hits = Units.search(q, null).take(40)
        body.removeAllViews()
        if (hits.isEmpty()) {
            body.addView(label(t("Not understood. Try: 10 tola in gram", "समझ नहीं आया. ऐसे लिखो: 10 तोला ग्राम में", "સમજાયું નહીં. આમ લખો: 10 તોલા ગ્રામ માં"), 16f))
            return
        }
        for (d in hits) body.addView(unitLine(d) { curCat = d.cat; from = d; baseVal = d.toBase(1.0); hideKb(); search.setText("") })
    }

    private fun sym(d: UDef) = if (d.sym.isNotEmpty() && d.sym != d.name(lang)) " (${d.sym})" else ""

    /** pinned units of that category first, then the most used ones */
    private fun equivalents(cat: String, base: Double, skip: UDef?): List<UDef> {
        val all = Units.inCat(cat).filter { it != skip && it.fromBase(base).isFinite() }
        return all.sortedWith(compareByDescending<UDef> { it.id in pins }.thenByDescending { it.pop })
    }

    private fun unitLine(d: UDef, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(4f), dpi(10f), dpi(4f), dpi(10f))
        addView(label(d.flag, 20f, TXT), llp(dpi(36f), WRAP_CONTENT))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(d.name(lang) + sym(d), 17f, TXT))
        col.addView(label(Units.cat(d.cat).icon + " " + Units.cat(d.cat).name(lang) + if (d.note.isNotEmpty()) " · " + d.note else "", 13f))
        addView(col, llp(0, WRAP_CONTENT, 1f))
        setOnClickListener { onClick() }
    }

    // ================= home =================
    private fun showHome() {
        rowVals.clear(); amountEt = null
        body.removeAllViews()

        // flags
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val fr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun flagChip(cc: String?, text: String) {
            val sel = country == cc
            fr.addView(chip(text, if (sel) 0xFF8A5A00 else 0xFF2A3238) { country = cc; refreshAll() },
                llp(WRAP_CONTENT, dpi(40f)).apply { rightMargin = dpi(6f) })
        }
        flagChip(null, "🌐 " + t("All", "सब", "બધા"))
        for ((cc, n) in Units.COUNTRIES) flagChip(cc, Units.flag(cc) + " " + n)
        hs.addView(fr)
        body.addView(hs, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(4f) })

        val cc = country
        if (cc != null) {
            // units of that country, grouped by category
            val list = Units.UNITS.filter { it.cc == cc }
            body.addView(label(Units.flag(cc) + " " + (Units.COUNTRIES.firstOrNull { it.first == cc }?.second ?: "") +
                " — " + list.size + " " + t("units", "यूनिट", "યુનિટ"), 18f, ACC, true).apply { setPadding(0, dpi(12f), 0, dpi(4f)) })
            for (cat in Units.CATS) {
                val inCat = list.filter { it.cat == cat.id }
                if (inCat.isEmpty()) continue
                body.addView(label(cat.icon + " " + cat.name(lang), 15f, SUB, true).apply { setPadding(0, dpi(10f), 0, 0) })
                for (d in inCat) body.addView(unitLine(d) { curCat = d.cat; from = d; baseVal = d.toBase(1.0); refreshAll() })
            }
            return
        }

        // Mera dabba (pinned)
        if (pins.isNotEmpty()) {
            body.addView(label("⭐ " + t("My box (long-press a unit to add/remove)", "मेरा डब्बा (यूनिट पर देर तक दबाओ)", "મારો ડબ્બો (યુનિટ પર લાંબું દબાવો)"), 14f, SUB, true)
                .apply { setPadding(0, dpi(12f), 0, dpi(4f)) })
            val ps = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val pr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (id in pins) {
                val d = Units.byId(id) ?: continue
                pr.addView(chip(d.flag + " " + d.name(lang), 0xFF2A3238) { curCat = d.cat; from = d; baseVal = d.toBase(1.0); refreshAll() },
                    llp(WRAP_CONTENT, dpi(40f)).apply { rightMargin = dpi(6f) })
            }
            ps.addView(pr)
            body.addView(ps)
        }

        // category circles, 4 per row
        body.addView(label(t("Pick a kind", "किस चीज़ की यूनिट?", "શેનો યુનિટ?"), 14f, SUB, true).apply { setPadding(0, dpi(14f), 0, dpi(4f)) })
        var row: LinearLayout? = null
        Units.CATS.forEachIndexed { i, cat ->
            if (i % 4 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                body.addView(row, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
            }
            row!!.addView(catCircle(cat), llp(0, WRAP_CONTENT, 1f))
        }
        val rest = Units.CATS.size % 4
        if (rest != 0) repeat(4 - rest) { row!!.addView(View(this), llp(0, 1, 1f)) }
        body.addView(label("${Units.UNITS.size} " + t("units · ", "यूनिट · ", "યુનિટ · ") + "${Units.CATS.size} " + t("kinds", "प्रकार", "પ્રકાર"), 13f)
            .apply { gravity = Gravity.CENTER; setPadding(0, dpi(16f), 0, 0) }, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun catCircle(cat: UCat) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        val size = dpi(62f)
        addView(TextView(context).apply {
            text = cat.icon; textSize = 26f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(cat.color)) }
        }, llp(size, size))
        addView(TextView(context).apply {
            text = cat.name(lang); textSize = 12f; setTextColor(c(TXT)); gravity = Gravity.CENTER; maxLines = 2
        }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(3f) })
        setOnClickListener {
            curCat = cat.id
            val list = Units.inCat(cat.id)
            from = list.firstOrNull { it.id in pins } ?: list.maxByOrNull { it.pop }
            baseVal = from?.toBase(1.0) ?: 1.0
            refreshAll()
        }
    }

    // ================= converter =================
    private fun showConverter() {
        val cid = curCat ?: return showHome()
        val cat = Units.cat(cid)
        val list = Units.inCat(cid)
        val f = from?.takeIf { it.cat == cid } ?: list.maxByOrNull { it.pop }!!
        from = f
        body.removeAllViews(); rowVals.clear()

        body.addView(label(cat.icon + " " + cat.name(lang) + "  ·  " + list.size + " " + t("units", "यूनिट", "યુનિટ"), 18f, ACC, true)
            .apply { setPadding(0, dpi(6f), 0, dpi(6f)) })

        // amount + from unit
        val box = card()
        val inRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val et = EditText(this).apply {
            textSize = 26f; setTextColor(c(ACC)); typeface = Typeface.DEFAULT_BOLD
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true); background = null
        }
        inRow.addView(et, llp(0, WRAP_CONTENT, 1f))
        val fb = chip("", 0xFF2A3238) { pickFrom(list) }
        inRow.addView(fb, llp(WRAP_CONTENT, dpi(44f)))
        box.addView(inRow)
        val ft = label("", 14f)
        box.addView(ft)
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(chip("📤 " + t("Share", "भेजो", "મોકલો"), 0xFF1565C0) { share() }, llp(WRAP_CONTENT, dpi(36f)).apply { rightMargin = dpi(6f) })
        btns.addView(chip("⬅ " + t("Home", "वापस", "પાછા"), 0xFF2A3238) { curCat = null; refreshAll() }, llp(WRAP_CONTENT, dpi(36f)))
        box.addView(btns, llp(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
        body.addView(box, llp(MATCH_PARENT, WRAP_CONTENT))
        amountEt = et; fromBtn = fb; feelTv = ft

        body.addView(label(t("Tap a unit to type in it · long-press to ⭐ / copy", "यूनिट दबाओ = उसमें लिखो · देर तक दबाओ = ⭐ / कॉपी",
            "યુનિટ દબાવો = તેમાં લખો · લાંબું દબાવો = ⭐ / કૉપી"), 13f).apply { setPadding(dpi(4f), dpi(8f), 0, dpi(2f)) })

        val shown = list.filter { country == null || it.cc == country || it.cc == "" }
            .sortedWith(compareByDescending<UDef> { it.id in pins }.thenByDescending { it.pop })
        for (d in shown) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(6f), dpi(9f), dpi(6f), dpi(9f))
            }
            r.addView(label(d.flag, 19f, TXT), llp(dpi(34f), WRAP_CONTENT))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val v = label("", 19f, TXT, true)
            col.addView(v)
            col.addView(label((if (d.id in pins) "⭐ " else "") + d.name(lang) + sym(d) + if (d.note.isNotEmpty()) "  · " + d.note else "", 13f))
            r.addView(col, llp(0, WRAP_CONTENT, 1f))
            r.setOnClickListener { from = d; setAmountFromBase(); updateRows() }
            r.setOnLongClickListener { rowMenu(d, v.text.toString()); true }
            body.addView(r)
            body.addView(View(this).apply { setBackgroundColor(c(0xFF22292E)) }, llp(MATCH_PARENT, 1))
            rowVals.add(d to v)
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (settingAmount) return
                val x = s.toString().replace(",", "").toDoubleOrNull() ?: return
                baseVal = from!!.toBase(x); updateRows()
            }
        })
        setAmountFromBase()
        updateRows()
        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun setAmountFromBase() {
        val f = from ?: return
        settingAmount = true
        val v = f.fromBase(baseVal)
        amountEt?.setText(if (v.isFinite()) Units.fmt(v).replace(",", "").let { if (it.contains("×")) v.toString() else it } else "")
        amountEt?.setSelection(amountEt?.text?.length ?: 0)
        settingAmount = false
    }

    private fun updateRows() {
        val f = from ?: return
        fromBtn?.text = f.flag + " " + f.name(lang) + " ▾"
        for ((d, tv) in rowVals) {
            tv.text = Units.fmt(d.fromBase(baseVal))
            tv.setTextColor(c(if (d == f) ACC else TXT))
        }
        feelTv?.text = Units.feel(f.cat, baseVal, lang) ?: ""
    }

    private fun pickFrom(list: List<UDef>) {
        val sorted = list.sortedWith(compareByDescending<UDef> { it.id in pins }.thenByDescending { it.pop })
        AlertDialog.Builder(this)
            .setItems(sorted.map { it.flag + "  " + it.name(lang) + sym(it) }.toTypedArray()) { _, i ->
                val keep = amountEt?.text.toString().replace(",", "").toDoubleOrNull()
                from = sorted[i]
                if (keep != null) baseVal = from!!.toBase(keep)
                updateRows()
            }.show()
    }

    private fun rowMenu(d: UDef, value: String) {
        val pinned = d.id in pins
        val items = arrayOf(
            if (pinned) "⭐ " + t("Remove from my box", "मेरे डब्बे से हटाओ", "મારા ડબ્બામાંથી કાઢો") else "⭐ " + t("Add to my box", "मेरे डब्बे में रखो", "મારા ડબ્બામાં મૂકો"),
            "📋 " + t("Copy", "कॉपी", "કૉપી") + "  $value ${d.sym.ifEmpty { d.name(lang) }}"
        )
        AlertDialog.Builder(this).setTitle(d.flag + " " + d.name(lang)).setItems(items) { _, i ->
            if (i == 0) { if (pinned) pins.remove(d.id) else pins.add(d.id); showConverter() }
            else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("unit", value.replace(",", "")))
                Toast.makeText(this, t("Copied", "कॉपी हो गया", "કૉપી થઈ ગયું"), Toast.LENGTH_SHORT).show()
            }
        }.show()
    }

    private fun share() {
        val f = from ?: return
        val sb = StringBuilder()
        sb.append(Units.fmt(f.fromBase(baseVal))).append(" ").append(f.name(lang)).append(" =\n")
        for (d in equivalents(f.cat, baseVal, f).take(10)) sb.append("• ").append(Units.fmt(d.fromBase(baseVal))).append(" ").append(d.name(lang)).append(sym(d)).append("\n")
        Units.feel(f.cat, baseVal, lang)?.let { sb.append(it).append("\n") }
        sb.append("\n— Nirmaan Calc 🌍")
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString())
        try { startActivity(Intent.createChooser(i, "Share")) } catch (_: Exception) {}
    }

    private fun hideKb() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }
}
