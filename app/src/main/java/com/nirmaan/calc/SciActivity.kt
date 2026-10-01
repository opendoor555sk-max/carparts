package com.nirmaan.calc

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Scientific calculator with units, ∫, d/dx, Σ, complex numbers and X Y Z memories. */
class SciActivity : Activity() {

    companion object {
        const val BG = 0xFF0F0F0F
        const val CARD = 0xFF1C1C1E
        const val KEY = 0xFF2C2C2E
        const val ACCENT = 0xFFFF9500
        const val GREEN = 0xFF30D158
        const val RED = 0xFFFF3B30
        const val TEXT = 0xFFFFFFFF
        const val SUB = 0xFF8E8E93
        const val ALT = 0xFFFFB74D
    }

    private lateinit var o: SciOpts
    private val vars = HashMap<String, Q>()
    private var ans: Q? = null
    private var ansUnit: UDef? = null
    private var justEval = false
    private var settingText = false
    private var afterPrefix = false

    private lateinit var histTv: TextView
    private lateinit var exprEt: EditText
    private lateinit var resTv: TextView
    private lateinit var subTv: TextView
    private val modeTv = arrayOfNulls<TextView>(4)
    private val keyMain = HashMap<String, TextView>()

    private fun dp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, resources.displayMetrics)
    private fun dpi(x: Float) = dp(x).toInt()
    private fun c(hex: Long) = hex.toInt()
    private fun llp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun t(en: String, hi: String, gu: String) = when (o.lang) { 1 -> hi; 2 -> gu; else -> en }

    // main label, long-press label, kind (0 function, 1 digit, 2 operator, 3 AC, 4 ⌫, 5 Enter)
    private val ROWS = listOf(
        listOf(Triple("X", "STO", 0), Triple("Y", "STO", 0), Triple("Z", "STO", 0), Triple("Kilo", "", 0), Triple("Meter", "🌍", 0), Triple("∫", "", 0)),
        listOf(Triple("dx", "", 0), Triple("Σ", "Π", 0), Triple("i", "Ans", 0), Triple("Real", "Im", 0), Triple("ln", "log", 0), Triple("∞", "n!", 0)),
        listOf(Triple("sin", "sin⁻¹", 0), Triple("cos", "cos⁻¹", 0), Triple("tan", "tan⁻¹", 0), Triple("√", "∛", 0), Triple("xʸ", "x²", 0), Triple("π", "e", 0)),
        listOf(Triple("(", "", 0), Triple(")", "", 0), Triple("±", "", 0), Triple("→", "", 0), Triple("%", "", 0), Triple("=", "", 2)),
        listOf(Triple("7", "", 1), Triple("8", "", 1), Triple("9", "", 1), Triple("⌫", "CLR", 4), Triple("AC", "", 3)),
        listOf(Triple("4", "", 1), Triple("5", "", 1), Triple("6", "", 1), Triple("−", "", 2), Triple("÷", "", 2)),
        listOf(Triple("1", "", 1), Triple("2", "", 1), Triple("3", "", 1), Triple("+", "", 2), Triple("×", "", 2)),
        listOf(Triple("0", "", 1), Triple(".", "", 1), Triple(",", "", 1), Triple("E", "", 1), Triple("Enter", "copy", 5))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        o = SciStore.load(this)
        loadState()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c(BG)); fitsSystemWindows = true
            setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f))
        }

        // ---- mode row: DEG · NORM · 0.### · EXACT · ⚙ ----
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (k in 0 until 4) {
            val tv = TextView(this).apply {
                gravity = Gravity.CENTER; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(c(TEXT))
                setOnClickListener { tap(this); onMode(k) }
            }
            modeTv[k] = tv
            top.addView(tv, llp(0, dpi(36f), 1f).apply { setMargins(dpi(3f), 0, dpi(3f), 0) })
        }
        top.addView(TextView(this).apply {
            text = "⚙"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(c(TEXT))
            background = keyBg(KEY)
            setOnClickListener { tap(this); startActivity(Intent(this@SciActivity, SciSettingsActivity::class.java)) }
        }, llp(dpi(44f), dpi(36f)).apply { setMargins(dpi(3f), 0, dpi(3f), 0) })
        root.addView(top)

        // ---- display ----
        val disp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(c(CARD)); cornerRadius = dp(14f) }
            setPadding(dpi(12f), dpi(6f), dpi(12f), dpi(8f))
        }
        histTv = TextView(this).apply {
            textSize = 13f; setTextColor(c(SUB)); maxLines = 1; ellipsize = TextUtils.TruncateAt.START; gravity = Gravity.END
            setOnClickListener { showHistory() }
        }
        disp.addView(histTv, llp(MATCH_PARENT, WRAP_CONTENT))
        exprEt = EditText(this).apply {
            showSoftInputOnFocus = false
            background = null; setTextColor(c(TEXT)); textSize = 28f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 3; minLines = 1
            setPadding(0, dpi(4f), 0, dpi(2f))
        }
        exprEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val n = s?.length ?: 0
                exprEt.textSize = when { n < 14 -> 28f; n < 24 -> 23f; n < 40 -> 19f; else -> 16f }
                if (!settingText) { justEval = false; preview() }
            }
        })
        disp.addView(exprEt, llp(MATCH_PARENT, WRAP_CONTENT))
        resTv = TextView(this).apply {
            gravity = Gravity.END; maxLines = 2; setTextColor(c(SUB)); textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setOnLongClickListener { copyResult(); true }
        }
        disp.addView(resTv, llp(MATCH_PARENT, WRAP_CONTENT))
        subTv = TextView(this).apply { gravity = Gravity.END; textSize = 13f; setTextColor(c(SUB)) }
        disp.addView(subTv, llp(MATCH_PARENT, WRAP_CONTENT))
        root.addView(disp, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f); bottomMargin = dpi(4f) })

        // ---- keypad ----
        val pad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((ri, row) in ROWS.withIndex()) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((main, alt, kind) in row) r.addView(key(main, alt, kind, ri), llp(0, MATCH_PARENT, 1f))
            pad.addView(r, llp(MATCH_PARENT, 0, if (ri < 4) 0.9f else 1.1f))
        }
        root.addView(pad, llp(MATCH_PARENT, 0, 1f))
        setContentView(root)

        settingText = true
        exprEt.setText(getSharedPreferences("sci", Context.MODE_PRIVATE).getString("expr", ""))
        exprEt.setSelection(exprEt.text.length)
        settingText = false
        exprEt.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        o = SciStore.load(this)
        refreshLabels()
        val sp = getSharedPreferences("sci", Context.MODE_PRIVATE)
        sp.getString("pick", null)?.let {
            sp.edit().remove("pick").apply()
            setExpr(it)
        }
        if (sp.getBoolean("histCleared", false)) { sp.edit().remove("histCleared").apply(); histTv.text = "" }
        preview()
    }

    override fun onPause() {
        super.onPause()
        saveState()
    }

    // ================= keys =================
    private fun keyBg(color: Long, radius: Float = 10f): RippleDrawable {
        val shape = GradientDrawable().apply { setColor(c(color)); cornerRadius = dp(radius) }
        return RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), shape, null)
    }

    private fun key(main: String, alt: String, kind: Int, row: Int): View {
        val color = when (kind) { 3 -> GREEN; 4 -> RED; 5 -> ACCENT; else -> KEY }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = keyBg(color)
        }
        if (alt.isNotEmpty()) box.addView(TextView(this).apply {
            text = alt; textSize = 9f; setTextColor(c(if (kind >= 3) TEXT else ALT)); gravity = Gravity.CENTER
            includeFontPadding = false
        })
        val tv = TextView(this).apply {
            text = main; gravity = Gravity.CENTER; includeFontPadding = false
            setTextColor(c(if (kind == 2) ACCENT else TEXT))
            textSize = when {
                kind == 1 || kind == 2 -> 22f
                main.length >= 4 -> 14f
                row < 4 && main.length >= 3 -> 16f
                else -> 19f
            }
            if (kind == 5 || kind == 3) typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(tv)
        keyMain[main] = tv
        box.setOnClickListener { tap(it); onKey(main) }
        box.setOnLongClickListener { tap(it); onLong(main) }
        // margins need the final layout params, so wrap
        val wrap = LinearLayout(this).apply { setPadding(dpi(2.5f), dpi(2.5f), dpi(2.5f), dpi(2.5f)) }
        wrap.addView(box, llp(MATCH_PARENT, MATCH_PARENT))
        return wrap
    }

    private fun tap(v: View) {
        if (o.vibrate) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun refreshLabels() {
        modeTv[0]!!.text = o.angleName
        modeTv[1]!!.text = o.modeName
        modeTv[2]!!.text = o.decName
        modeTv[3]!!.text = "EXACT"
        for (k in 0 until 4) {
            val on = k != 3 || o.exact
            modeTv[k]!!.background = keyBg(if (k == 3 && o.exact) ACCENT else KEY, 8f)
            modeTv[k]!!.setTextColor(c(if (on) TEXT else SUB))
        }
        keyMain["×"]?.text = o.mulSign
        keyMain["÷"]?.text = o.divSign
        keyMain["."]?.text = if (o.comma) "," else "."
        keyMain[","]?.text = if (o.comma) ";" else ","
    }

    private fun onMode(k: Int) {
        when (k) {
            0 -> o.angle = (o.angle + 1) % 3
            1 -> o.mode = (o.mode + 1) % 3
            2 -> {
                val items = arrayOf(t("Auto", "अपने आप", "આપમેળે")) + (0..10).map { if (it == 0) "0" else "0." + "0".repeat(it) }
                AlertDialog.Builder(this).setTitle(t("Decimal places", "दशमलव के बाद अंक", "દશાંશ પછી અંક"))
                    .setSingleChoiceItems(items, o.dec + 1) { d, w -> o.dec = w - 1; SciStore.save(this, o); refreshLabels(); reshow(); d.dismiss() }
                    .show()
                return
            }
            3 -> o.exact = !o.exact
        }
        SciStore.save(this, o)
        refreshLabels()
        reshow()
    }

    /** after a mode change: show the last answer again in the new format */
    private fun reshow() { if (justEval) evaluate(false) else preview() }

    private val OPS = setOf("+", "−", "×", "÷", "·", "*", "/", "^", "%", "!", "²", "=", "→")

    private fun onKey(k: String) {
        when (k) {
            "AC" -> { setExpr(""); resTv.text = ""; subTv.text = ""; justEval = false }
            "⌫" -> deleteBack()
            "Enter" -> evaluate(true)
            "Kilo" -> prefixDialog()
            "Meter" -> unitDialog(null)
            "→" -> { ins(" → "); unitDialog(dimsBeforeArrow()) }
            "±" -> toggleSign()
            "∫" -> ins("∫(")
            "dx" -> ins("d/dx(")
            "Σ" -> ins("Σ(")
            "Real" -> ins("Re(")
            "ln" -> ins("ln(")
            "sin", "cos", "tan" -> ins("$k(")
            "√" -> ins("√(")
            "xʸ" -> ins("^")
            "−", "+" -> ins(if (o.spacious) " $k " else k)
            "×" -> ins(if (o.spacious) " ${o.mulSign} " else o.mulSign)
            "÷" -> ins(if (o.spacious) " ${o.divSign} " else o.divSign)
            "=" -> ins(if (o.spacious) " = " else "=")
            "." -> ins(if (o.comma) "," else ".")
            "," -> ins(if (o.comma) "; " else ", ")
            else -> ins(k)
        }
    }

    private fun onLong(k: String): Boolean {
        when (k) {
            "X", "Y", "Z" -> {
                val a = ans
                if (a == null) { toast(t("Press Enter first", "पहले Enter दबाओ", "પહેલા Enter દબાવો")); return true }
                vars[k] = a
                val sh = SciFmt.show(a, ansUnit, o)
                toast("$k = ${sh.num} ${sh.unit}".trim())
                saveState()
            }
            "Σ" -> ins("Π(")
            "i" -> ins("Ans")
            "Real" -> ins("Im(")
            "ln" -> ins("log(")
            "∞" -> ins("!")
            "sin", "cos", "tan" -> ins("a$k(")
            "√" -> ins("∛(")
            "xʸ" -> ins("²")
            "π" -> ins("e")
            "⌫" -> { setExpr(""); resTv.text = ""; subTv.text = "" }
            "Enter" -> copyResult()
            "Meter" -> startActivity(Intent(this, UnitsActivity::class.java))
            else -> return false
        }
        return true
    }

    // ================= editing =================
    private fun setExpr(s: String) {
        settingText = true
        exprEt.setText(s)
        exprEt.setSelection(s.length)
        settingText = false
        preview()
    }

    private fun ins(s0: String) {
        var s = s0
        if (justEval) {
            val isOp = OPS.any { s.trim().startsWith(it) }
            if (isOp) {
                // continue from the answer
                if (o.keep) { setExpr("Ans") }
            } else setExpr("")
            justEval = false
        }
        val txt = exprEt.text
        var st = exprEt.selectionStart.coerceAtLeast(0)
        var en = exprEt.selectionEnd.coerceAtLeast(0)
        if (st > en) { val x = st; st = en; en = x }
        // no double spaces between tokens
        if (s.startsWith(" ") && st > 0 && txt[st - 1] == ' ') s = s.trimStart()
        afterPrefix = false
        txt.replace(st, en, s)
        exprEt.setSelection((st + s.length).coerceAtMost(exprEt.text.length))
    }

    private val TOKENS = listOf("d/dx(", "asin(", "acos(", "atan(", "sinh(", "cosh(", "tanh(", "sin(", "cos(", "tan(",
        "log(", "ln(", "Re(", "Im(", "∫(", "Σ(", "Π(", "√(", "∛(", "Ans", " → ")

    private fun deleteBack() {
        val txt = exprEt.text
        var st = exprEt.selectionStart.coerceAtLeast(0)
        var en = exprEt.selectionEnd.coerceAtLeast(0)
        if (st > en) { val x = st; st = en; en = x }
        justEval = false
        if (st != en) { txt.delete(st, en); return }
        if (st == 0) return
        val before = txt.substring(0, st)
        var n = 1
        val tok = TOKENS.firstOrNull { before.endsWith(it) }
        when {
            tok != null -> n = tok.length
            before.endsWith("]") && before.lastIndexOf('[') >= 0 -> n = st - before.lastIndexOf('[')
            before.length >= 3 && before[before.length - 1] == ' ' && before[before.length - 3] == ' ' -> n = 3 // " + "
            before.endsWith(" ") -> n = before.length - before.trimEnd().length + 1
            Character.isLowSurrogate(before.last()) && before.length >= 2 -> n = 2
        }
        n = n.coerceAtMost(st)
        txt.delete(st - n, st)
    }

    private fun toggleSign() {
        val txt = exprEt.text
        val cur = exprEt.selectionStart.coerceAtLeast(0)
        if (justEval) { justEval = false; if (o.keep) setExpr("−(Ans)") else setExpr("−(" + txt + ")"); return }
        var p = cur
        while (p > 0 && (txt[p - 1].isLetterOrDigit() || txt[p - 1] == '.' || txt[p - 1] == ',' || txt[p - 1] == 'π')) p--
        val prev = if (p > 0) txt[p - 1] else ' '
        val prevNonSpace = txt.substring(0, maxOf(p - 1, 0)).trimEnd().lastOrNull()
        if (prev == '−' && (prevNonSpace == null || prevNonSpace in "(+−×÷·*/^,;=→")) {
            txt.delete(p - 1, p)
            exprEt.setSelection((cur - 1).coerceAtLeast(0))
        } else {
            txt.insert(p, "−")
            exprEt.setSelection(cur + 1)
        }
    }

    // ================= unit pickers =================
    private fun prefixDialog() {
        val list = SciMath.PREFIX.filter { it.first != "u" && it.first != "da" && it.first != "Y" && it.first != "Z" }
        val items = list.map { (p, f, n) -> "$p   —   $n   (10" + SciFmt.sup(Math.round(Math.log10(f)).toInt()) + ")" }.toTypedArray()
        AlertDialog.Builder(this).setTitle(t("Prefix (then pick a unit)", "पहले का अक्षर (फिर यूनिट चुनो)", "આગળનો અક્ષર (પછી યુનિટ પસંદ કરો)"))
            .setItems(items) { _, w ->
                val txt = exprEt.text
                val st = exprEt.selectionStart.coerceAtLeast(0)
                val pre = if (st > 0 && (txt[st - 1].isDigit() || txt[st - 1] == ')')) " " else ""
                ins(pre + list[w].first)
                afterPrefix = true
                unitDialog(null, onlyPlain = true)
            }.show()
    }

    /** dimension of what is typed before "→" (to show only the units that fit) */
    private fun dimsBeforeArrow(): Dim? {
        val s = exprEt.text.toString()
        val head = s.substringBeforeLast("→").trim()
        if (head.isEmpty()) return ans?.d
        return try { SciMath.run(head, env(), o.comma).q.d } catch (e: Exception) { null }
    }

    private fun unitDialog(dims: Dim?, onlyPlain: Boolean = false) {
        val cats = Units.CATS.filter { cat ->
            if (dims != null) Dim.ofCat(cat.id) == dims && cat.id != "count" && cat.id != "conc" else true
        }.ifEmpty { Units.CATS }
        if (cats.size == 1) { unitList(cats[0], onlyPlain); return }
        val items = cats.map { it.icon + "  " + it.name(o.lang) }.toTypedArray()
        AlertDialog.Builder(this).setTitle(t("Which kind of unit?", "किस चीज़ की यूनिट?", "શેનો યુનિટ?"))
            .setItems(items) { _, w -> unitList(cats[w], onlyPlain) }
            .setOnCancelListener { afterPrefix = false }
            .show()
    }

    private fun unitList(cat: UCat, onlyPlain: Boolean) {
        val us = Units.inCat(cat.id).filter { !onlyPlain || (it.sym.isNotEmpty() && it.off == 0.0 && !it.inv && it.k == 1.0) }
            .ifEmpty { Units.inCat(cat.id) }
            .sortedByDescending { it.pop }
        val items = us.map { it.flag + "  " + it.name(o.lang) + (if (it.sym.isNotEmpty() && it.sym != it.name(o.lang)) "  (" + it.sym + ")" else "") }.toTypedArray()
        AlertDialog.Builder(this).setTitle(cat.icon + " " + cat.name(o.lang))
            .setItems(items) { _, w ->
                val u = us[w]
                val txt = exprEt.text
                val st = exprEt.selectionStart.coerceAtLeast(0)
                val prev = if (st > 0) txt[st - 1] else ' '
                val pre = if (!afterPrefix && (prev.isLetterOrDigit() || prev == ')' || prev == ']' || prev == 'π')) " " else ""
                val wasPrefix = afterPrefix
                ins(pre + (if (wasPrefix && u.sym.isNotEmpty()) u.sym else SciMath.unitText(u)))
            }
            .setOnCancelListener { afterPrefix = false }
            .show()
    }

    // ================= evaluating =================
    private fun env() = SciMath.Env(vars, o.angle, ans)

    private fun preview() {
        val s = exprEt.text.toString()
        if (s.isBlank() || s.contains('=')) { if (!justEval) { resTv.text = ""; subTv.text = "" }; return }
        try {
            val out = SciMath.run(s, env(), o.comma)
            val sh = SciFmt.show(out.q, out.unit, o)
            resTv.textSize = 22f
            subTv.setTextColor(c(SUB))
            resTv.text = styled(sh.num, sh.unit, SUB)
            subTv.text = sh.approx ?: ""
        } catch (e: Exception) {
            resTv.text = ""; subTv.text = ""
        }
    }

    private fun styled(num: String, unit: String, color: Long, prefix: String = ""): CharSequence {
        val b = SpannableStringBuilder(prefix + num)
        b.setSpan(ForegroundColorSpan(c(color)), 0, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (unit.isNotEmpty()) {
            val st = b.length
            b.append(" ").append(unit)
            if (o.isolate) {
                b.setSpan(ForegroundColorSpan(c(ACCENT)), st, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                b.setSpan(RelativeSizeSpan(0.72f), st, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else b.setSpan(ForegroundColorSpan(c(color)), st, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return b
    }

    /** Enter: work out the line, keep the answer, add it to the history */
    private fun evaluate(record: Boolean) {
        val s = exprEt.text.toString()
        if (s.isBlank()) return
        try {
            val out = SciMath.run(s, env(), o.comma)
            resTv.textSize = 32f
            if (out.truth != null) {
                resTv.text = if (out.truth) "✔ " + t("True", "सही", "સાચું") else "✘ " + t("False", "गलत", "ખોટું")
                resTv.setTextColor(c(if (out.truth) GREEN else RED))
                subTv.text = ""
                if (record) addHistory(s, resTv.text.toString())
                justEval = true
                return
            }
            if (record) { ans = out.q; ansUnit = out.unit }
            out.assign?.let { vars[it] = out.q }
            val sh = SciFmt.show(out.q, out.unit, o)
            val pre = if (out.assign != null) out.assign + " = " else "= "
            resTv.text = styled(sh.num, sh.unit, TEXT, pre)
            val more = ArrayList<String>()
            sh.approx?.let { more.add(it) }
            if (out.roots.size > 1) more.add(out.assign + ": " + out.roots.joinToString(",  ") { r ->
                val x = SciFmt.show(r, out.unit, o); (x.num + " " + x.unit).trim() })
            subTv.setTextColor(c(SUB))
            subTv.text = more.joinToString("\n")
            if (record) {
                val shown = (pre + sh.num + " " + sh.unit).trim()
                addHistory(s, shown)
                histTv.text = "$s  $shown"
                if (!o.keep) {
                    settingText = true
                    val raw = if (out.assign != null) out.assign else SciFmt.rawOf(out.q, out.unit, o)
                    exprEt.setText(raw); exprEt.setSelection(raw.length)
                    settingText = false
                }
                saveState()
            }
            justEval = true
        } catch (e: SciErr) {
            resTv.textSize = 26f
            resTv.text = t("Error", "गलती", "ભૂલ")
            resTv.setTextColor(c(RED))
            subTv.setTextColor(c(RED))
            subTv.text = errText(e)
        } catch (e: Exception) {
            resTv.textSize = 26f
            resTv.text = t("Error", "गलती", "ભૂલ"); resTv.setTextColor(c(RED))
            subTv.text = ""
        }
    }

    private fun errText(e: SciErr): String {
        val a = e.arg
        return when (e.code) {
            "syntax" -> t("Something is wrong near: $a", "यहाँ गलत लिखा है: $a", "અહીં ખોટું લખ્યું છે: $a")
            "paren" -> t("Brackets ( ) do not match", "ब्रैकेट ( ) ठीक नहीं", "કૌંસ ( ) બરાબર નથી")
            "unknown" -> t("Not understood: $a", "समझ नहीं आया: $a", "સમજાયું નહીં: $a")
            "mismatch" -> t("Units do not match: $a", "यूनिट मेल नहीं खाते: $a", "યુનિટ મેળ ખાતા નથી: $a")
            "needplain" -> t("No unit allowed in $a( )", "$a( ) के अंदर यूनिट नहीं चलेगा", "$a( ) અંદર યુનિટ નહીં ચાલે")
            "domain" -> t("Cannot be worked out: $a", "यह हिसाब नहीं हो सकता: $a", "આ હિસાબ થઈ શકે નહીં: $a")
            "args" -> t("Write it as ∫(f, a, b) · d/dx(f, a) · Σ(f, a, b)", "ऐसे लिखो: ∫(f, a, b) · d/dx(f, a) · Σ(f, a, b)", "આમ લખો: ∫(f, a, b) · d/dx(f, a) · Σ(f, a, b)")
            "noroot" -> t("No answer found for $a", "$a का जवाब नहीं मिला", "$a નો જવાબ મળ્યો નહીં")
            "nounit" -> t("Pick a unit after →", "→ के बाद यूनिट चुनो", "→ પછી યુનિટ પસંદ કરો")
            "incomplete" -> t("Incomplete", "अधूरा है", "અધૂરું છે")
            "big" -> t("Range too big", "रेंज बहुत बड़ी है", "રેન્જ બહુ મોટી છે")
            else -> e.code
        }
    }

    private fun copyResult() {
        val a = ans ?: return
        val txt = SciFmt.rawOf(a, ansUnit, o).let { if (it == "Ans") SciFmt.raw(a.re, o) else it }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("result", txt))
        toast(t("Copied: ", "कॉपी: ", "કૉપી: ") + txt)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ================= history & memory =================
    private fun addHistory(e: String, r: String) {
        val sp = getSharedPreferences("sci", Context.MODE_PRIVATE)
        val arr = try { JSONArray(sp.getString("hist", "[]")) } catch (x: Exception) { JSONArray() }
        val n = JSONArray()
        n.put(JSONObject().put("e", e).put("r", r))
        for (i in 0 until minOf(arr.length(), 99)) {
            val ob = arr.optJSONObject(i) ?: continue
            if (i == 0 && ob.optString("e") == e) continue
            n.put(ob)
        }
        sp.edit().putString("hist", n.toString()).apply()
    }

    private fun showHistory() {
        SciSettingsActivity.historyDialog(this, o) { picked -> setExpr(picked) }
    }

    private fun loadState() {
        val sp = getSharedPreferences("sci", Context.MODE_PRIVATE)
        ans = Q.parse(sp.getString("ans", null))
        ansUnit = sp.getString("ansUnit", null)?.let { Units.byId(it) }
        for (v in listOf("X", "Y", "Z")) Q.parse(sp.getString("var$v", null))?.let { vars[v] = it }
    }

    private fun saveState() {
        val e = getSharedPreferences("sci", Context.MODE_PRIVATE).edit()
        e.putString("expr", if (::exprEt.isInitialized) exprEt.text.toString() else "")
        ans?.let { e.putString("ans", it.toString()) }
        e.putString("ansUnit", ansUnit?.id)
        for ((k, v) in vars) e.putString("var$k", v.toString())
        e.apply()
    }
}
