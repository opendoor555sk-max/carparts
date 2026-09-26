package com.nirmaan.calc

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
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
import kotlin.math.abs

class MainActivity : Activity(), Ui {

    private lateinit var eng: Engine
    private lateinit var valTv: TextView
    private lateinit var lblTv: TextView
    private lateinit var tagTv: TextView
    private lateinit var panelRoot: LinearLayout
    private lateinit var panelTitle: TextView
    private lateinit var panelBody: LinearLayout
    private var panelOpen = false
    private var swiped = false
    private var toastObj: Toast? = null
    private lateinit var root: FrameLayout
    private var shareText = ""

    private class KeyView(val r: Int, val c: Int, val btn: TextView, val top: TextView, val blue: TextView)

    private val keys = ArrayList<KeyView>()
    private val bgCache = HashMap<String, Drawable.ConstantState>()

    private fun dp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, resources.displayMetrics)
    private fun dpi(x: Float) = dp(x).toInt()
    private fun c(hex: Long) = hex.toInt()

    private fun llp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eng = Engine(this)
        Store.load(this, eng)

        root = FrameLayout(this)
        root.fitsSystemWindows = true

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(3f), dpi(4f), dpi(3f), dpi(6f))
        }
        main.addView(buildLcd(), llp(MATCH_PARENT, WRAP_CONTENT))
        main.addView(buildPad(), llp(MATCH_PARENT, 0, 1f).apply { topMargin = dpi(4f) })
        root.addView(main, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        root.addView(buildPanel(), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        render()
        Updater.autoCheck(this)
    }

    override fun onPause() {
        super.onPause()
        Store.save(this, eng)
    }

    // ================= LCD =================
    private fun circleIcon(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        gravity = Gravity.CENTER
        setTextColor(c(0xFFBCCBB8))
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textSize = 16f
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(0xFF111111)) }
        layoutParams = llp(dpi(28f), dpi(28f))
        setOnClickListener { onClick() }
    }

    private fun buildLcd(): View {
        val lcd = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(c(0xFFBCCBB8), c(0xFF8EA28C))
            ).apply { cornerRadius = dp(10f); setStroke(dpi(3f), c(0xFF555555)) }
            setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(2f))
            minimumHeight = dpi(118f)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(circleIcon("i") { help() })
        lblTv = TextView(this).apply {
            textSize = 20f; setTextColor(c(0xFF111111)); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dpi(8f), 0, dpi(6f), 0)
        }
        top.addView(lblTv, llp(0, WRAP_CONTENT, 1f))
        tagTv = TextView(this).apply {
            textSize = 12f; setTextColor(c(0xFFBCCBB8)); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { setColor(c(0xFF111111)); cornerRadius = dp(4f) }
            setPadding(dpi(5f), dpi(1f), dpi(5f), dpi(1f))
        }
        top.addView(tagTv, llp(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dpi(8f) })
        top.addView(circleIcon("C") { eng.clearTemp(); render() })
        lcd.addView(top)
        valTv = TextView(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(c(0xFF111111)); maxLines = 1
            textSize = 52f
            includeFontPadding = false
        }
        lcd.addView(valTv, llp(MATCH_PARENT, dpi(72f)))
        return lcd
    }

    // ================= KEYPAD =================
    private fun colors(cls: String): IntArray = if (eng.light) lightColors(cls) else darkColors(cls)

    private fun lightColors(cls: String): IntArray = when (cls) {
        "fn" -> intArrayOf(c(0xFFDCE3E8), c(0xFFB0BEC5))
        "green" -> intArrayOf(c(0xFF66BB6A), c(0xFF388E3C))
        "red" -> intArrayOf(c(0xFFEF5350), c(0xFFC62828))
        "num" -> intArrayOf(c(0xFFFFFFFF), c(0xFFE3E3E3))
        "op" -> intArrayOf(c(0xFF42A5F5), c(0xFF1E88E5))
        "conv" -> intArrayOf(c(0xFFFFB74D), c(0xFFFB8C00))
        "st" -> intArrayOf(c(0xFF4DD0E1), c(0xFF0097A7))
        else -> intArrayOf(c(0xFFF5F5F5), c(0xFFD6DBDE))
    }

    private fun darkColors(cls: String): IntArray = when (cls) {
        "fn" -> intArrayOf(c(0xFF66737F), c(0xFF353F48))
        "green" -> intArrayOf(c(0xFF2C9A6B), c(0xFF156344))
        "red" -> intArrayOf(c(0xFFE0343D), c(0xFF9C141B))
        "num" -> intArrayOf(c(0xFF2A2A2A), c(0xFF050505))
        "op" -> intArrayOf(c(0xFF2A4F99), c(0xFF15295A))
        "conv" -> intArrayOf(c(0xFFF7A832), c(0xFFD97D05))
        "st" -> intArrayOf(c(0xFF3BB8DC), c(0xFF1C7EA0))
        else -> intArrayOf(c(0xFF3B3B3B), c(0xFF1B1B1B))
    }

    private fun lighten(col: Int): Int {
        val r = Color.red(col); val g = Color.green(col); val b = Color.blue(col)
        return Color.rgb(r + (255 - r) / 3, g + (255 - g) / 3, b + (255 - b) / 3)
    }

    private fun keyShape(cols: IntArray, lit: Boolean): Drawable {
        val face = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, cols).apply {
            cornerRadius = dp(9f)
            if (lit) setStroke(dpi(2f), if (eng.light) c(0xFF0D47A1) else Color.WHITE)
        }
        val shadow = GradientDrawable().apply { cornerRadius = dp(9f); setColor(if (eng.light) c(0xFF9AA7AE) else Color.BLACK) }
        return LayerDrawable(arrayOf(shadow, face)).apply { setLayerInset(1, 0, 0, 0, dpi(2f)) }
    }

    private fun keyBg(cls: String, lit: Boolean): Drawable {
        val id = "$cls/$lit/${eng.light}"
        bgCache[id]?.let { return it.newDrawable() }
        val cols = colors(cls)
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_pressed), keyShape(intArrayOf(lighten(cols[0]), lighten(cols[1])), lit))
        s.addState(intArrayOf(), keyShape(cols, lit))
        s.constantState?.let { bgCache[id] = it }
        return s
    }

    private fun buildPad(): View {
        val pad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 8) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until 5) {
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val tl = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val top = TextView(this).apply {
                    textSize = 10.5f; setTextColor(c(0xFFF4C430)); maxLines = 1
                    setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC)
                    setPadding(dpi(3f), 0, 0, 0)
                }
                val blue = TextView(this).apply {
                    textSize = 10.5f; setTextColor(c(0xFF4AA8FF)); maxLines = 1
                    setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC)
                    setPadding(0, 0, dpi(3f), 0)
                }
                tl.addView(top, llp(0, WRAP_CONTENT, 1f))
                tl.addView(blue, llp(WRAP_CONTENT, WRAP_CONTENT))
                cell.addView(tl, llp(MATCH_PARENT, dpi(15f)))
                val btn = TextView(this).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    isClickable = true
                    isHapticFeedbackEnabled = true
                }
                cell.addView(btn, llp(MATCH_PARENT, 0, 1f))
                row.addView(cell, llp(0, MATCH_PARENT, 1f).apply { leftMargin = dpi(3f); rightMargin = dpi(3f) })
                val kv = KeyView(r, col, btn, top, blue)
                keys.add(kv)
                btn.setOnClickListener { v ->
                    if (swiped) return@setOnClickListener
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(45)
                        .withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(90).start() }.start()
                    eng.press(r, col)
                    Store.save(this, eng)
                    render()
                }
                attachSwipe(btn, r)
            }
            pad.addView(row, llp(MATCH_PARENT, 0, 1f))
        }
        return pad
    }

    /** Swipe left/right on the green row -> SIN/COS/TAN, on the unit row -> metric. */
    private fun attachSwipe(v: View, r: Int) {
        if (r != 2 && r != 3) return
        var sx = 0f
        var sy = 0f
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = ev.rawX; sy = ev.rawY }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.rawX - sx
                    val dy = ev.rawY - sy
                    if (abs(dx) > dp(45f) && abs(dy) < dp(35f)) {
                        if (r == 2) {
                            eng.trig = !eng.trig
                            toast(if (eng.trig) "SIN / COS / TAN" else "Length / Width / Height")
                        } else {
                            eng.metric = !eng.metric
                            toast(if (eng.metric) "Metric (m / cm / mm)" else "Feet / Inch")
                        }
                        swiped = true
                        view.isPressed = false
                        view.postDelayed({ swiped = false }, 350)
                        Store.save(this, eng)
                        render()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun render() {
        for (k in keys) {
            val d = eng.keyDef(k.r, k.c)
            val showConv = eng.conv && d.conv.isNotEmpty()
            val txt = eng.keyText(k.r, k.c)
            k.btn.text = txt
            k.btn.background = keyBg(d.cls, eng.keyLit(k.r, k.c))
            when (d.cls) {
                "num" -> {
                    k.btn.setTypeface(Typeface.DEFAULT, if (showConv) Typeface.BOLD_ITALIC else Typeface.BOLD)
                    k.btn.textSize = if (txt.length > 1) (if (txt.length > 5) 13f else 16f) else 30f
                }
                "op" -> {
                    k.btn.setTypeface(Typeface.DEFAULT, if (showConv) Typeface.ITALIC else Typeface.NORMAL)
                    k.btn.textSize = if (txt.length > 2) 16f else 24f
                }
                else -> {
                    k.btn.setTypeface(Typeface.DEFAULT, Typeface.ITALIC)
                    k.btn.textSize = if (txt.length > 7) 13f else if (txt.length > 5) 15f else 18f
                }
            }
            val darkText = eng.light && d.cls in setOf("num", "fn", "unit")
            k.btn.setTextColor(
                when {
                    showConv -> if (eng.light) c(0xFFB25E00) else c(0xFFF4C430)
                    d.cls == "conv" -> c(0xFF222222)
                    darkText -> c(0xFF1C2328)
                    else -> Color.WHITE
                }
            )
            k.top.text = eng.keyTop(k.r, k.c)
            k.top.setTextColor(if (eng.light) c(0xFFB25E00) else c(0xFFF4C430))
            k.blue.text = d.blue
            k.blue.setTextColor(if (eng.light) c(0xFF1565C0) else c(0xFF4AA8FF))
        }
        root.setBackgroundColor(if (eng.light) c(0xFFF2F4F5) else c(0xFF0D0D0D))
        window.statusBarColor = if (eng.light) c(0xFF9AA7AE) else Color.BLACK
        window.navigationBarColor = window.statusBarColor
        val t = eng.display()
        valTv.text = t
        fitValue(t)
        lblTv.text = eng.displayLabel()
        val tg = eng.tag()
        tagTv.text = tg
        tagTv.visibility = if (tg.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Biggest text size (max 56sp) that still fits the full display width. */
    private fun fitValue(t: String) {
        val avail = valTv.width - valTv.paddingLeft - valTv.paddingRight
        if (avail <= 0) { valTv.post { if (valTv.width > 0) fitValue(valTv.text.toString()) }; return }
        val tp = TextPaint(valTv.paint)
        var size = 56f
        while (size > 16f) {
            tp.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, resources.displayMetrics)
            if (tp.measureText(t) <= avail) break
            size -= 2f
        }
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    }

    // ================= PANEL =================
    private fun buildPanel(): View {
        panelRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
            isClickable = true
        }
        panelTitle = TextView(this).apply {
            setBackgroundColor(Color.BLACK); setTextColor(c(0xFF4FB3FF))
            textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dpi(12f), dpi(14f), dpi(12f), dpi(14f))
        }
        panelRoot.addView(panelTitle, llp(MATCH_PARENT, WRAP_CONTENT))
        val sv = ScrollView(this)
        panelBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(panelBody)
        panelRoot.addView(sv, llp(MATCH_PARENT, 0, 1f))
        val foot = LinearLayout(this).apply {
            setBackgroundColor(c(0xFF111111)); gravity = Gravity.END
            setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
        }
        val done = TextView(this).apply {
            text = "Done"; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors("op")).apply { cornerRadius = dp(8f) }
            setPadding(dpi(28f), dpi(10f), dpi(28f), dpi(10f))
            setOnClickListener { closePanel() }
        }
        val share = TextView(this).apply {
            text = "Share"; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c(0xFF4CAF50), c(0xFF2E7D32))).apply { cornerRadius = dp(8f) }
            setPadding(dpi(24f), dpi(10f), dpi(24f), dpi(10f))
            setOnClickListener { shareResults() }
        }
        foot.addView(share)
        foot.addView(View(this), llp(0, 1, 1f))
        foot.addView(done)
        panelRoot.addView(foot, llp(MATCH_PARENT, WRAP_CONTENT))
        return panelRoot
    }

    private fun openPanel(title: String) {
        panelTitle.text = title
        panelBody.removeAllViews()
        shareText = ""
        if (!panelOpen) {
            panelRoot.visibility = View.VISIBLE
            panelRoot.alpha = 0f
            panelRoot.translationY = dp(60f)
            panelRoot.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
        panelOpen = true
    }

    private fun rowsText(title: String, rows: List<Row>): String {
        val sb = StringBuilder(title).append("\n")
        for (r in rows) {
            if (r.stair != null) continue
            if (r.section) sb.append("\n— ").append(r.label).append(" —\n")
            else if (r.value.isEmpty()) sb.append(r.label).append("\n")
            else sb.append(r.label).append(": ").append(r.value).append("\n")
        }
        return sb.append("\n— Nirmaan Calc").toString()
    }

    private fun shareResults() {
        val t = if (shareText.isNotEmpty()) shareText else panelTitle.text.toString()
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t)
        try { startActivity(Intent.createChooser(i, "Share")) } catch (_: Exception) { toast("Share nahi ho paya") }
    }

    private fun closePanel() {
        currentFocus?.let {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0)
        }
        panelRoot.visibility = View.GONE
        panelOpen = false
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (panelOpen) closePanel() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun secView(t: String) = TextView(this).apply {
        text = t; setBackgroundColor(c(0xFF474747)); setTextColor(Color.WHITE)
        setTypeface(Typeface.DEFAULT, Typeface.ITALIC); textSize = 16f
        setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(c(0xFFCCCCCC)); layoutParams = llp(MATCH_PARENT, 1)
    }

    private fun rowView(r: Row): View {
        if (r.section) return secView(r.label)
        if (r.stair != null) return StairView(this, r.stair).apply {
            layoutParams = llp(MATCH_PARENT, WRAP_CONTENT)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
            minimumHeight = dpi(56f)
        }
        box.addView(TextView(this).apply { text = r.label; textSize = 15f; setTextColor(c(0xFF111111)); setTextIsSelectable(true) })
        if (r.value.isNotEmpty()) box.addView(TextView(this).apply {
            text = r.value; textSize = 22f; gravity = Gravity.END
            setTextColor(if (r.warn) c(0xFFC0222A) else c(0xFF333333))
            setTextIsSelectable(true)
        }, llp(MATCH_PARENT, WRAP_CONTENT))
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(box)
        wrap.addView(divider())
        return wrap
    }

    override fun panel(title: String, rows: List<Row>) {
        openPanel(title)
        rows.forEach { panelBody.addView(rowView(it)) }
        shareText = rowsText(title, rows)
    }

    private fun seg(opts: List<String>, sel: Int, onPick: (Int) -> Unit): View {
        val s = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setStroke(1, c(0xFF888888)); cornerRadius = dp(8f); setColor(c(0xFFEEEEEE)) }
            setPadding(1, 1, 1, 1)
        }
        opts.forEachIndexed { i, o ->
            s.addView(TextView(this).apply {
                text = o; textSize = 15f; gravity = Gravity.CENTER; maxLines = 1
                setPadding(dpi(4f), dpi(10f), dpi(4f), dpi(10f))
                if (i == sel) {
                    background = GradientDrawable().apply { setColor(c(0xFF777777)); cornerRadius = dp(7f) }
                    setTextColor(Color.WHITE)
                } else setTextColor(c(0xFF222222))
                setOnClickListener { onPick(i) }
            }, llp(0, WRAP_CONTENT, 1f))
        }
        return s
    }

    override fun form(spec: FormSpec) {
        openPanel(spec.title)
        var segVal: String? = spec.seg?.firstOrNull()
        val out = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val inputs = LinkedHashMap<String, EditText>()

        fun recompute() {
            val vals = HashMap<String, Double>()
            spec.fields.forEach { f ->
                val s = inputs.getValue(f.key).text.toString()
                vals[f.key] = if (f.isPitch) eng.parsePitch(s) else if (f.isLen) eng.parseLen(s) else (s.trim().toDoubleOrNull() ?: Double.NaN)
            }
            out.removeAllViews()
            val rows = spec.compute(vals, segVal)
            rows.forEach { out.addView(rowView(it)) }
            val inp = spec.fields.map { Row(it.label, inputs.getValue(it.key).text.toString()) }
            shareText = rowsText(spec.title, listOf(sec("Inputs")) + inp + (segVal?.let { listOf(Row("Option", it)) } ?: emptyList()) + rows)
        }

        spec.seg?.let { opts ->
            val holder = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
            }
            fun draw() {
                holder.removeAllViews()
                holder.addView(seg(opts, opts.indexOf(segVal)) { i -> segVal = opts[i]; draw(); recompute() })
            }
            draw()
            panelBody.addView(holder)
        }
        panelBody.addView(secView("Input Parameters"))
        spec.fields.forEach { f ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(4f))
            }
            box.addView(TextView(this).apply { text = f.label; textSize = 15f; setTextColor(c(0xFF111111)) })
            val et = EditText(this).apply {
                setText(f.value)
                textSize = 22f
                gravity = Gravity.END
                setTextColor(c(0xFF1636D8))
                inputType = if (f.isLen || f.isPitch) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine()
            }
            box.addView(et, llp(MATCH_PARENT, WRAP_CONTENT))
            if (f.hint.isNotEmpty()) box.addView(TextView(this).apply { text = f.hint; textSize = 12f; setTextColor(c(0xFF777777)) })
            inputs[f.key] = et
            panelBody.addView(box)
            panelBody.addView(divider())
        }
        panelBody.addView(secView("Calculated Results"))
        panelBody.addView(out)
        inputs.values.forEach {
            it.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = recompute()
            })
        }
        recompute()
    }

    override fun prefs() {
        openPanel("Preferences")
        fun add(title: String, opts: List<String>, sel: Int, pick: (Int) -> Unit) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(10f))
            }
            box.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(c(0xFF111111)) })
            box.addView(seg(opts, sel) { i -> pick(i); Store.save(this, eng); prefs() },
                llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
            panelBody.addView(box)
            panelBody.addView(divider())
        }
        add("Theme", listOf("Dark", "Light"), if (eng.light) 1 else 0) { eng.light = it == 1; render() }
        add("Units System", listOf("Feet-Inch", "Metric"), if (eng.metric) 1 else 0) { eng.metric = it == 1 }
        val resList = listOf(2, 4, 8, 16, 32, 64)
        add("Fraction Resolution", resList.map { "1/$it" }, resList.indexOf(eng.res)) { eng.res = resList[it] }
        add("Green keys", listOf("Length/W/H", "SIN/COS/TAN"), if (eng.trig) 1 else 0) { eng.trig = it == 1 }

        val box = LinearLayout(this).apply { setPadding(dpi(12f), dpi(12f), dpi(12f), dpi(12f)) }
        box.addView(TextView(this).apply {
            text = "Sab memory saaf karein (Reset)"; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(c(0xFFC0222A))
            background = GradientDrawable().apply { setStroke(dpi(1f), c(0xFFC0222A)); cornerRadius = dp(8f); setColor(Color.WHITE) }
            setPadding(0, dpi(12f), 0, dpi(12f))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("Saari memory aur history saaf ho jayegi. Pakka?")
                    .setPositiveButton("Haan") { _, _ ->
                        eng.clrAll(); eng.tape.clear(); Store.save(this@MainActivity, eng); toast("Reset ho gaya")
                    }
                    .setNegativeButton("Nahi", null)
                    .show()
            }
        }, llp(MATCH_PARENT, WRAP_CONTENT))
        panelBody.addView(box)

        val upd = LinearLayout(this).apply { setPadding(dpi(12f), 0, dpi(12f), dpi(12f)) }
        upd.addView(TextView(this).apply {
            text = "Update check karein  (version " + Updater.myVersionName(this@MainActivity) + ")"
            textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors("op")).apply { cornerRadius = dp(8f) }
            setPadding(0, dpi(12f), 0, dpi(12f))
            setOnClickListener { Updater.check(this@MainActivity, manual = true) }
        }, llp(MATCH_PARENT, WRAP_CONTENT))
        panelBody.addView(upd)
        panelBody.addView(TextView(this).apply {
            text = "Tip: green keys par ungli left-right sarkayein to SIN/COS/TAN aa jayenge. " +
                "Yards/Feet/Inches wali line par sarkayein to metric (m/cm/mm) ho jayega."
            textSize = 15f; setTextColor(c(0xFF222222))
            setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
        })
    }

    override fun help() {
        openPanel("Madad (Help)")
        listOf(
            "<b>Length daalna:</b> 9 [Feet] 10 [Inches] 3 [/] 8 → 9ft 10-3/8in",
            "<b>Area/Volume:</b> unit key dobara dabayein: 6 [Feet] [Feet] → 6ft²",
            "<b>Unit badalna:</b> value ke baad doosri unit dabayein. Wahi unit dobara dabane par fraction/decimal badalta hai.",
            "<b>Conv (orange):</b> dabane ke baad keys ka peela (upar wala) kaam chalta hai.",
            "<b>Store / Recall:</b> Store dabakar M1, M2, M3 (1,2,3) ya o.c. (5) dabayein. Recall se wapas laayein.",
            "<b>Rafter:</b> 12 [Feet] [Rise], 15 [Feet] [Run], phir [Diag] → rafter length. [Diag] dobara → poori list.",
            "<b>Kamra:</b> [Length], [Width], [Height] mein values daalein. Value ke bina [Width] ya [Height] dabane par area/volume ki list aati hai.",
            "<b>Arc:</b> Run (chord) aur Rise daalkar [Arc] → angle, dobara [Arc] → poori list.",
            "<b>Seedhi:</b> Rise daalkar [Stair]. Values screen par badal sakte hain.",
            "<b>Chhat:</b> Hip/V, Jack, Conv+Hip/V = Irregular Pitch, Conv+Jack = Irregular Jack, Conv+Rise = Rake Wall, Conv+Run = Roof.",
            "<b>Material:</b> Conv + Length = Masonry (int/block), Conv + Width = Footing, Conv + Height = Drywall/Paint, Conv + 8 = Board Feet (cft), Conv + CmpMtr = Fence, Conv + Stair = Baluster.",
            "<b>Wazan:</b> 5 Conv+1 = 5 kg. Phir Conv+4 = lbs, Conv+6 = tons, Conv+3 = metric ton. Store/Recall + 0 = volume se wazan.",
            "<b>Kharcha:</b> value ke baad Conv + 0 = Cost (rate aur GST).",
            "<b>Share:</b> kisi bhi result screen par neeche Share dabayein (WhatsApp etc.).",
            "<b>Theme:</b> Conv + Store = Prefs, wahan Dark / Light.",
            "<b>C button:</b> screen saaf. Conv + × = ClrAll (saari memory saaf)."
        ).forEach {
            panelBody.addView(TextView(this).apply {
                text = Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
                textSize = 15f; setTextColor(c(0xFF222222)); setLineSpacing(0f, 1.2f)
                setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
            })
        }
    }

    override fun toast(m: String) {
        toastObj?.cancel()
        toastObj = Toast.makeText(this, m, Toast.LENGTH_SHORT).also { it.show() }
    }
}
