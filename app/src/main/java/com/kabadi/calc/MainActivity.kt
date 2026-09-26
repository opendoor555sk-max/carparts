package com.kabadi.calc

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    // colours
    private val BG = 0xFFF2F4F5.toInt()
    private val INK = 0xFF1C2328.toInt()
    private val MUTED = 0xFF607D8B.toInt()
    private val GREEN = 0xFF2E7D32.toInt()
    private val RED = 0xFFC62828.toInt()
    private val ORANGE = 0xFFEF6C00.toInt()
    private val BLUE = 0xFF1565C0.toInt()

    private lateinit var root: LinearLayout
    private lateinit var titleTv: TextView
    private lateinit var backBtn: TextView
    private lateinit var content: FrameLayout
    private lateinit var bottom: LinearLayout
    private var screen = "home"
    private var editing: Hisab? = null
    private var onBack: (() -> Unit)? = null

    private fun dp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, resources.displayMetrics)
    private fun dpi(x: Float) = dp(x).toInt()
    private fun llp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun round(color: Int, r: Float = 10f, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r); if (stroke != 0) setStroke(dpi(1f), stroke)
    }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.load(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); fitsSystemWindows = true }

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF263238.toInt())
            setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f))
        }
        backBtn = barBtn("‹") { goBack() }
        bar.addView(backBtn)
        titleTv = TextView(this).apply { textSize = 20f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setPadding(dpi(8f), 0, 0, 0); maxLines = 1 }
        bar.addView(titleTv, llp(0, WRAP_CONTENT, 1f))
        bar.addView(barBtn("🧮") { showCalc() })
        bar.addView(barBtn("⚙") { showSettings() })
        root.addView(bar, llp(MATCH_PARENT, WRAP_CONTENT))

        content = FrameLayout(this)
        root.addView(content, llp(MATCH_PARENT, 0, 1f))
        bottom = LinearLayout(this).apply { setBackgroundColor(Color.WHITE); setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f)) }
        root.addView(bottom, llp(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
        showHome()
        Updater.autoCheck(this)
    }

    override fun onPause() {
        super.onPause()
        autoSave()
        Store.save(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (screen == "home") @Suppress("DEPRECATION") super.onBackPressed() else goBack()
    }

    private fun goBack() {
        hideKeyboard()
        val b = onBack
        if (b != null) b() else showHome()
    }

    private fun hideKeyboard() {
        currentFocus?.let { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun barBtn(t: String, act: () -> Unit) = TextView(this).apply {
        text = t; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setPadding(dpi(12f), dpi(2f), dpi(12f), dpi(2f)); setOnClickListener { act() }
    }

    private fun setScreen(name: String, title: String, back: (() -> Unit)?): LinearLayout {
        screen = name
        onBack = back
        titleTv.text = title
        backBtn.visibility = if (name == "home") View.GONE else View.VISIBLE
        content.removeAllViews()
        bottom.removeAllViews()
        bottom.visibility = View.GONE
        val sv = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(24f)) }
        sv.addView(body)
        content.addView(sv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return body
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(Color.WHITE, 12f)
        setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(12f))
        elevation = dp(1.5f)
    }

    private fun cardLp() = llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(10f) }

    private fun heading(t: String, color: Int = INK) = TextView(this).apply {
        text = t; textSize = 17f; setTextColor(color); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dpi(6f))
    }

    private fun small(t: String, color: Int = MUTED) = TextView(this).apply { text = t; textSize = 13f; setTextColor(color) }

    private fun bigButton(t: String, color: Int, act: () -> Unit) = TextView(this).apply {
        text = t; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        background = round(color, 10f); setPadding(dpi(8f), dpi(13f), dpi(8f), dpi(13f)); setOnClickListener { act() }
    }

    private fun input(hint: String, value: String, number: Boolean, onChange: (String) -> Unit) = EditText(this).apply {
        this.hint = hint
        setText(value)
        textSize = 17f
        setTextColor(INK)
        setSingleLine()
        inputType = if (number) InputType.TYPE_CLASS_PHONE else (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        background = round(0xFFF7F9FA.toInt(), 8f, 0xFFCFD8DC.toInt())
        setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
        setSelectAllOnFocus(number)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = onChange(s?.toString() ?: "")
        })
    }

    private fun labeled(label: String, v: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dpi(4f), 0, dpi(4f))
        addView(small(label))
        addView(v, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    /** grid of quick-add chips */
    private fun chips(list: List<String>, color: Int, onPick: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var row: LinearLayout? = null
        list.forEachIndexed { i, t ->
            if (i % 3 == 0) { row = LinearLayout(this); box.addView(row, llp(MATCH_PARENT, WRAP_CONTENT)) }
            row!!.addView(TextView(this).apply {
                text = t; textSize = 13.5f; gravity = Gravity.CENTER; maxLines = 2
                setTextColor(color); background = round(Color.WHITE, 18f, color)
                setPadding(dpi(4f), dpi(8f), dpi(4f), dpi(8f))
                setOnClickListener { onPick(i) }
            }, llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(3f), dpi(3f), dpi(3f), dpi(3f)) })
        }
        val rest = list.size % 3
        if (rest != 0) repeat(3 - rest) { row!!.addView(View(this), llp(0, 1, 1f).apply { setMargins(dpi(3f), 0, dpi(3f), 0) }) }
        return box
    }

    // ================= HOME =================
    private fun showHome() {
        editing = null
        val body = setScreen("home", L.t("app"), null)
        body.addView(bigButton(L.t("new"), GREEN) { newHisab() }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(10f) })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun fill(q: String) {
            list.removeAllViews()
            val items = Store.hisabs.filter {
                q.isBlank() || it.party.contains(q, true) || it.vehicle.contains(q, true) || it.note.contains(q, true)
            }.sortedByDescending { it.time }
            if (Store.hisabs.isNotEmpty()) {
                val all = items.sumOf { it.munafa() }
                list.addView(small(L.t("all_total") + " (" + items.size + "): " + money(all), if (all >= 0) GREEN else RED).apply {
                    textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setPadding(dpi(4f), dpi(4f), 0, dpi(8f))
                })
            }
            if (items.isEmpty()) list.addView(small(L.t("none")).apply { setPadding(dpi(6f), dpi(20f), dpi(6f), 0); gravity = Gravity.CENTER })
            items.forEach { h ->
                val c = card()
                val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                top.addView(TextView(this).apply {
                    text = h.party.ifBlank { h.vehicle.ifBlank { "—" } }; textSize = 17f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; maxLines = 1
                }, llp(0, WRAP_CONTENT, 1f))
                val m = h.munafa()
                top.addView(TextView(this).apply { text = money(m); textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(if (m >= 0) GREEN else RED) })
                c.addView(top)
                c.addView(small(listOf(h.vehicle, Bill.dateText(h.time)).filter { it.isNotBlank() }.joinToString("  •  ")))
                c.setOnClickListener { showEditor(h) }
                c.setOnLongClickListener { askDelete(h) { fill(q) }; true }
                list.addView(c, cardLp())
            }
        }
        if (Store.hisabs.size > 3) body.addView(input(L.t("search"), "", false) { fill(it) }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
        body.addView(heading(L.t("saved")))
        body.addView(list)
        fill("")
    }

    private fun askDelete(h: Hisab, after: () -> Unit) {
        AlertDialog.Builder(this).setMessage(L.t("del_q") + "\n" + h.party + "  " + money(h.munafa()))
            .setPositiveButton(L.t("yes")) { _, _ -> Store.hisabs.remove(h); Store.save(this); after() }
            .setNegativeButton(L.t("no"), null).show()
    }

    private fun newHisab() {
        val now = System.currentTimeMillis()
        showEditor(Hisab(now, now))
    }

    // ================= EDITOR =================
    private lateinit var sumPrice: TextView
    private lateinit var sumKharch: TextView
    private lateinit var sumLagat: TextView
    private lateinit var sumMaal: TextView
    private lateinit var sumKg: TextView
    private lateinit var sumResult: TextView
    private lateinit var resultBox: LinearLayout
    private var scrollTo: View? = null

    private fun hasContent(h: Hisab) = h.party.isNotBlank() || h.vehicle.isNotBlank() || h.priceText.isNotBlank() || h.kharch.isNotEmpty() || h.maal.isNotEmpty()

    private fun autoSave() {
        val h = editing ?: return
        if (!hasContent(h)) return
        if (Store.hisabs.none { it === h }) Store.hisabs.add(h)
        h.maal.forEach { if (!it.fixed && it.rateText.isNotBlank()) Store.lastRate[it.name] = it.rateText }
    }

    private fun refreshTotals() {
        val h = editing ?: return
        sumPrice.text = money(h.price)
        sumKharch.text = money(h.kharchTotal())
        sumLagat.text = money(h.lagat())
        sumMaal.text = money(h.maalTotal())
        sumKg.text = plain(h.kg()) + " " + L.t("kg")
        val m = h.munafa()
        sumResult.text = (if (m >= 0) L.t("profit") else L.t("loss")) + "   " + money(Math.abs(m))
        resultBox.background = round(if (m >= 0) GREEN else RED, 12f)
    }

    private fun showEditor(h: Hisab) {
        editing = h
        val body = setScreen("edit", h.party.ifBlank { L.t("hisab") }, { autoSave(); Store.save(this); showHome() })

        // header
        val head = card()
        head.addView(labeled(L.t("party"), input(L.t("party"), h.party, false) { h.party = it; titleTv.text = it.ifBlank { L.t("hisab") } }))
        head.addView(labeled(L.t("vehicle"), input("GJ-23-XX-0000", h.vehicle, false) { h.vehicle = it }))
        head.addView(labeled(L.t("note"), input(L.t("note"), h.note, false) { h.note = it }))
        val dt = TextView(this).apply {
            text = "🕒  " + Bill.dateText(h.time); textSize = 16f; setTextColor(BLUE); setPadding(0, dpi(8f), 0, dpi(2f))
        }
        dt.setOnClickListener { pickDate(h) { dt.text = "🕒  " + Bill.dateText(h.time) } }
        head.addView(labeled(L.t("date"), dt))
        body.addView(head, cardLp())

        // 1. vehicle price
        val pc = card()
        pc.addView(heading(L.t("price"), BLUE))
        pc.addView(input("₹", h.priceText, true) { h.priceText = it; refreshTotals() }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(pc, cardLp())

        // 2. expenses
        val kc = card()
        kc.addView(heading(L.t("kharch"), ORANGE))
        val kLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawKharch() {
            kLines.removeAllViews()
            h.kharch.forEach { l -> kLines.addView(kharchRow(h, l) { drawKharch(); refreshTotals() }) }
        }
        kc.addView(kLines)
        kc.addView(chips(Store.expenses.map { it.label() } + L.t("other"), ORANGE) { i ->
            val b = Store.expenses.getOrNull(i)
            h.kharch.add(Line(b?.key ?: "", b?.label() ?: "", true))
            drawKharch(); refreshTotals()
            focusLast(kLines)
        }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
        drawKharch()
        body.addView(kc, cardLp())

        // 3. parts
        val mc = card()
        mc.addView(heading(L.t("maal"), GREEN))
        val mLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawMaal() {
            mLines.removeAllViews()
            h.maal.forEach { l -> mLines.addView(maalRow(h, l) { drawMaal(); refreshTotals() }) }
        }
        mc.addView(mLines)
        mc.addView(chips(Store.parts.map { it.label() } + L.t("other"), GREEN) { i ->
            val b = Store.parts.getOrNull(i)
            val name = b?.label() ?: ""
            h.maal.add(Line(b?.key ?: "", name, b?.fixed ?: false, rateText = Store.lastRate[name] ?: ""))
            drawMaal(); refreshTotals()
            focusLast(mLines)
        }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
        drawMaal()
        body.addView(mc, cardLp())

        // totals
        val tc = card()
        fun sumRow(label: String, bold: Boolean = false, color: Int = INK): TextView {
            val r = LinearLayout(this).apply { setPadding(0, dpi(3f), 0, dpi(3f)) }
            r.addView(TextView(this).apply { text = label; textSize = 15f; setTextColor(MUTED) }, llp(0, WRAP_CONTENT, 1f))
            val v = TextView(this).apply { textSize = if (bold) 17f else 15f; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD }
            r.addView(v)
            tc.addView(r)
            return v
        }
        tc.addView(heading(L.t("total")))
        sumPrice = sumRow(L.t("sum_price"))
        sumKharch = sumRow(L.t("sum_kharch"))
        sumLagat = sumRow(L.t("sum_lagat"), true, RED)
        sumKg = sumRow(L.t("sum_kg"))
        sumMaal = sumRow(L.t("sum_maal"), true, GREEN)
        resultBox = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dpi(8f), dpi(14f), dpi(8f), dpi(14f)) }
        sumResult = TextView(this).apply { textSize = 22f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        resultBox.addView(sumResult)
        tc.addView(resultBox, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
        body.addView(tc, cardLp())
        body.addView(small(L.t("tip")).apply { gravity = Gravity.CENTER })

        refreshTotals()

        // action bar
        bottom.visibility = View.VISIBLE
        fun act(t: String, color: Int, a: () -> Unit) = bottom.addView(bigButton(t, color, a).apply { textSize = 15f },
            llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(3f), 0, dpi(3f), 0) })
        act(L.t("save"), BLUE) { autoSave(); Store.save(this); toast(L.t("saved_ok")) }
        act(L.t("jpg"), ORANGE) { exportJpg(h) }
        act(L.t("pdf"), RED) { exportPdf(h) }
        act(L.t("share"), GREEN) { shareText(h) }
        if (Store.hisabs.any { it === h }) act("🗑", 0xFF78909C.toInt()) { askDelete(h) { editing = null; showHome() } }
    }

    private fun focusLast(lines: LinearLayout) {
        val v = lines.getChildAt(lines.childCount - 1) ?: return
        v.post {
            (v.findViewWithTag<View>("focus") ?: v).requestFocus()
            (content.getChildAt(0) as? ScrollView)?.let { sv ->
                val loc = IntArray(2); v.getLocationInWindow(loc)
                val sl = IntArray(2); sv.getLocationInWindow(sl)
                sv.smoothScrollBy(0, loc[1] - sl[1] - dpi(120f))
            }
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(v.findViewWithTag<View>("focus") ?: v, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun xBtn(act: () -> Unit) = TextView(this).apply {
        text = "✕"; textSize = 18f; setTextColor(RED); gravity = Gravity.CENTER
        setPadding(dpi(10f), dpi(4f), dpi(4f), dpi(4f)); setOnClickListener { act() }
    }

    private fun kharchRow(h: Hisab, l: Line, redraw: () -> Unit): View {
        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(3f), 0, dpi(3f)) }
        r.addView(input(L.t("name_q"), l.name, false) { l.name = it }.apply { textSize = 15f }, llp(0, WRAP_CONTENT, 1.3f))
        r.addView(input("₹", l.amountText, true) { l.amountText = it; refreshTotals() }.apply { tag = "focus"; gravity = Gravity.END },
            llp(0, WRAP_CONTENT, 1f).apply { leftMargin = dpi(6f) })
        r.addView(xBtn { h.kharch.remove(l); redraw() })
        return r
    }

    private fun maalRow(h: Hisab, l: Line, redraw: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(0xFFF7FAF7.toInt(), 10f, 0xFFC8E6C9.toInt())
            setPadding(dpi(8f), dpi(6f), dpi(6f), dpi(8f))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(input(L.t("name_q"), l.name, false) { l.name = it }.apply { textSize = 15f }, llp(0, WRAP_CONTENT, 1f))
        // Kg × Rate / Fix switch
        fun modeBtn(t: String, on: Boolean, a: () -> Unit) = TextView(this).apply {
            text = t; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(if (on) Color.WHITE else GREEN)
            background = round(if (on) GREEN else Color.WHITE, 14f, GREEN)
            setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f)); setOnClickListener { a() }
        }
        top.addView(modeBtn(L.t("kgrate"), !l.fixed) { if (l.fixed) { l.fixed = false; redraw() } }, llp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(6f) })
        top.addView(modeBtn(L.t("fix"), l.fixed) { if (!l.fixed) { l.fixed = true; redraw() } }, llp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(4f) })
        top.addView(xBtn { h.maal.remove(l); redraw() })
        box.addView(top)

        val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(6f), 0, 0) }
        if (l.fixed) {
            r.addView(input(L.t("amount"), l.amountText, true) { l.amountText = it; refreshTotals() }.apply { tag = "focus"; gravity = Gravity.END }, llp(0, WRAP_CONTENT, 1f))
        } else {
            val amt = TextView(this).apply { textSize = 16f; setTextColor(GREEN); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END }
            fun upd() { amt.text = "= " + money(l.value()) }
            r.addView(input(L.t("kg"), l.kgText, true) { l.kgText = it; upd(); refreshTotals() }.apply { tag = "focus"; gravity = Gravity.END }, llp(0, WRAP_CONTENT, 1f))
            r.addView(TextView(this).apply { text = "×"; textSize = 18f; setTextColor(MUTED); setPadding(dpi(6f), 0, dpi(6f), 0) })
            r.addView(input(L.t("rate"), l.rateText, true) { l.rateText = it; upd(); refreshTotals() }.apply { gravity = Gravity.END }, llp(0, WRAP_CONTENT, 1f))
            r.addView(amt, llp(0, WRAP_CONTENT, 1.2f).apply { leftMargin = dpi(6f) })
            upd()
            val hint = LinearLayout(this)
            hint.addView(small(L.t("kg")), llp(0, WRAP_CONTENT, 1f))
            hint.addView(small(L.t("rate")), llp(0, WRAP_CONTENT, 1f).apply { leftMargin = dpi(24f) })
            hint.addView(View(this), llp(0, 1, 1.2f))
            box.addView(r)
            box.addView(hint)
            return wrap(box)
        }
        box.addView(r)
        return wrap(box)
    }

    private fun wrap(v: View): View = LinearLayout(this).apply {
        setPadding(0, dpi(4f), 0, dpi(4f)); addView(v, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun pickDate(h: Hisab, done: () -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = h.time }
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            TimePickerDialog(this, { _, hh, mm ->
                cal.set(Calendar.HOUR_OF_DAY, hh); cal.set(Calendar.MINUTE, mm)
                h.time = cal.timeInMillis; done()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ================= EXPORT =================
    private fun needsStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) return false
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 7)
        toast(L.t("perm"))
        return true
    }

    private fun shareUri(uri: Uri, type: String) {
        val i = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(Intent.createChooser(i, L.t("share"))) } catch (_: Exception) {}
    }

    private fun exportJpg(h: Hisab) {
        autoSave(); Store.save(this)
        if (needsStoragePermission()) return
        try {
            val uri = Bill.saveJpg(this, h) ?: return toast("JPG ✕")
            toast(L.t("img_ok"))
            shareUri(uri, "image/jpeg")
        } catch (e: Exception) { toast("JPG ✕ " + e.message) }
    }

    private fun exportPdf(h: Hisab) {
        autoSave(); Store.save(this)
        if (needsStoragePermission()) return
        try {
            val uri = Bill.savePdf(this, h) ?: return toast("PDF ✕")
            toast(L.t("pdf_ok"))
            if (Build.VERSION.SDK_INT >= 29) shareUri(uri, "application/pdf")
        } catch (e: Exception) { toast("PDF ✕ " + e.message) }
    }

    private fun shareText(h: Hisab) {
        autoSave(); Store.save(this)
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Bill.text(h))
        try { startActivity(Intent.createChooser(i, L.t("share"))) } catch (_: Exception) {}
    }

    // ================= SETTINGS =================
    private fun showSettings() {
        val from = editing
        autoSave()
        val body = setScreen("settings", L.t("settings"), { if (from != null) showEditor(from) else showHome() })
        val c = card()
        c.addView(heading(L.t("lang")))
        val langs = LinearLayout(this)
        listOf("English", "हिंदी", "ગુજરાતી").forEachIndexed { i, t ->
            langs.addView(TextView(this).apply {
                text = t; textSize = 16f; gravity = Gravity.CENTER
                setTextColor(if (L.lang == i) Color.WHITE else BLUE)
                background = round(if (L.lang == i) BLUE else Color.WHITE, 10f, BLUE)
                setPadding(0, dpi(10f), 0, dpi(10f))
                setOnClickListener { L.lang = i; Store.save(this@MainActivity); showSettings() }
            }, llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(3f), 0, dpi(3f), 0) })
        }
        c.addView(langs)
        body.addView(c, cardLp())

        val o = card()
        o.addView(labeled(L.t("owner"), input(L.t("owner"), Store.owner, false) { Store.owner = it; Store.save(this) }))
        o.addView(labeled(L.t("mobile"), input("98xxxxxxxx", Store.mobile, true) { Store.mobile = it; Store.save(this) }))
        o.addView(labeled(L.t("address"), input(L.t("address"), Store.address, false) { Store.address = it; Store.save(this) }))
        body.addView(o, cardLp())

        val lc = card()
        lc.addView(heading(L.t("lists")))
        lc.addView(listEditor(L.t("exp_list"), { Store.expenses }, { Store.expenses = it }, { Store.defaultExpenses() }, true))
        lc.addView(listEditor(L.t("parts_list"), { Store.parts }, { Store.parts = it }, { Store.defaultParts() }, false))
        body.addView(lc, cardLp())

        val u = card()
        u.addView(bigButton(L.t("update") + "  (v" + Updater.myVersionName(this) + ")", BLUE) { Updater.check(this, manual = true) })
        body.addView(u, cardLp())
    }

    private fun listEditor(title: String, get: () -> MutableList<Btn>, set: (MutableList<Btn>) -> Unit, def: () -> MutableList<Btn>, allFixed: Boolean): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(6f), 0, dpi(10f)) }
        fun draw() {
            box.removeAllViews()
            box.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD })
            val l = get()
            l.forEachIndexed { i, b ->
                val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                r.addView(TextView(this).apply {
                    text = b.label() + (if (!allFixed) "   (" + (if (b.fixed) L.t("fix") else L.t("kgrate")) + ")" else "")
                    textSize = 15f; setTextColor(INK); setPadding(0, dpi(6f), 0, dpi(6f))
                    if (!allFixed) setOnClickListener { l[i] = Btn(b.key, b.name, !b.fixed); Store.save(this@MainActivity); draw() }
                }, llp(0, WRAP_CONTENT, 1f))
                if (i > 0) r.addView(TextView(this).apply {
                    text = "▲"; textSize = 16f; setTextColor(BLUE); setPadding(dpi(10f), 0, dpi(10f), 0)
                    setOnClickListener { val x = l.removeAt(i); l.add(i - 1, x); Store.save(this@MainActivity); draw() }
                })
                r.addView(xBtn { l.removeAt(i); Store.save(this@MainActivity); draw() })
                box.addView(r)
            }
            val add = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val et = input(L.t("name_q"), "", false) {}
            add.addView(et, llp(0, WRAP_CONTENT, 1f))
            add.addView(bigButton(L.t("add"), GREEN) {
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) { l.add(Btn("", n, allFixed)); Store.save(this@MainActivity); draw() }
            }.apply { textSize = 14f; setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f)) }, llp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(6f) })
            box.addView(add)
            box.addView(TextView(this).apply {
                text = L.t("reset"); textSize = 13f; setTextColor(MUTED); setPadding(0, dpi(6f), 0, 0)
                setOnClickListener { set(def()); Store.save(this@MainActivity); draw() }
            })
        }
        draw()
        return box
    }

    // ================= SIMPLE CALCULATOR =================
    private fun showCalc() {
        val from = editing
        autoSave()
        val body = setScreen("calc", L.t("calc"), { if (from != null) showEditor(from) else showHome() })
        var expr = ""
        val exprTv = TextView(this).apply { textSize = 22f; setTextColor(MUTED); gravity = Gravity.END; minHeight = dpi(36f) }
        val resTv = TextView(this).apply { textSize = 40f; setTextColor(INK); gravity = Gravity.END; typeface = Typeface.DEFAULT_BOLD }
        val disp = card()
        disp.addView(exprTv, llp(MATCH_PARENT, WRAP_CONTENT))
        disp.addView(resTv, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(disp, cardLp())
        fun show() {
            exprTv.text = expr
            val v = evalExpr(expr)
            resTv.text = if (expr.isEmpty()) "0" else if (v.isFinite()) group(plain(v).substringBefore('.')) + (plain(v).substringAfter('.', "").let { if (it.isEmpty()) "" else ".$it" }) else "…"
        }
        val keys = listOf(
            listOf("C", "⌫", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"), listOf("(", "0", ".", "=")
        )
        keys.forEach { row ->
            val r = LinearLayout(this)
            row.forEach { k ->
                val op = k in listOf("÷", "×", "−", "+", "=", "%")
                r.addView(TextView(this).apply {
                    text = k; textSize = 26f; gravity = Gravity.CENTER
                    setTextColor(if (op || k == "C" || k == "⌫") Color.WHITE else INK)
                    background = round(when { k == "=" -> GREEN; op -> BLUE; k == "C" || k == "⌫" -> RED; else -> Color.WHITE }, 14f)
                    elevation = dp(1f)
                    setPadding(0, dpi(16f), 0, dpi(16f))
                    setOnClickListener {
                        when (k) {
                            "C" -> expr = ""
                            "⌫" -> expr = expr.dropLast(1)
                            "=" -> { val v = evalExpr(expr); if (v.isFinite()) expr = plain(v) }
                            "(" -> expr += if (expr.count { it == '(' } > expr.count { it == ')' } && expr.isNotEmpty() && (expr.last().isDigit() || expr.last() == ')')) ")" else "("
                            else -> expr += k
                        }
                        show()
                    }
                }, llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(4f), dpi(4f), dpi(4f), dpi(4f)) })
            }
            body.addView(r, llp(MATCH_PARENT, WRAP_CONTENT))
        }
        body.addView(small("( ) : " + L.t("tip")).apply { setPadding(dpi(6f), dpi(8f), 0, 0) })
        show()
    }
}
