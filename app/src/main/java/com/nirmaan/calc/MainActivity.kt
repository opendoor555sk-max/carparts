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
    private var curRows: List<Row> = emptyList()

    private class KeyView(val r: Int, val c: Int, val btn: TextView, val top: TextView, val blue: TextView)

    private val keys = ArrayList<KeyView>()
    private var touchX = 0f
    private var touchY = 0f

    /** The Conv key cut at 45°: top-left = Conv, bottom-right = Switch (other calculator). */
    private inner class SplitKey(val a: IntArray, val b: IntArray, val lit: Boolean) : Drawable() {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC); textAlign = android.graphics.Paint.Align.CENTER
        }

        override fun draw(cv: android.graphics.Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat() - dp(2f)
            val r = dp(9f)
            p.shader = null; p.style = android.graphics.Paint.Style.FILL
            p.color = if (eng.light) c(0xFF9AA7AE) else Color.BLACK
            cv.drawRoundRect(android.graphics.RectF(0f, dp(2f), w, h + dp(2f)), r, r, p)
            cv.save()
            cv.clipPath(android.graphics.Path().apply { addRoundRect(android.graphics.RectF(0f, 0f, w, h), r, r, android.graphics.Path.Direction.CW) })
            val t1 = android.graphics.Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(0f, h); close() }
            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, a[0], a[1], android.graphics.Shader.TileMode.CLAMP)
            cv.drawPath(t1, p)
            val t2 = android.graphics.Path().apply { moveTo(w, 0f); lineTo(w, h); lineTo(0f, h); close() }
            p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, b[0], b[1], android.graphics.Shader.TileMode.CLAMP)
            cv.drawPath(t2, p)
            p.shader = null; p.color = if (eng.light) Color.WHITE else Color.BLACK; p.strokeWidth = dp(2f); p.style = android.graphics.Paint.Style.STROKE
            cv.drawLine(w, 0f, 0f, h, p)
            cv.restore()
            if (lit) {
                p.color = if (eng.light) c(0xFF0D47A1) else Color.WHITE; p.strokeWidth = dp(2f)
                cv.drawRoundRect(android.graphics.RectF(dp(1f), dp(1f), w - dp(1f), h - dp(1f)), r, r, p)
            }
            p.style = android.graphics.Paint.Style.FILL
            tp.textSize = h * 0.24f; tp.color = c(0xFF222222)
            cv.drawText("Conv", w * 0.30f, h * 0.40f, tp)
            tp.textSize = h * 0.19f; tp.color = Color.WHITE
            cv.drawText("Switch", w * 0.70f, h * 0.88f, tp)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
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
    }

    override fun onResume() {
        super.onResume()
        Updater.autoCheck(this) // on every open (at most every 30 min)
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
        top.addView(circleIcon("ƒ") { startActivity(android.content.Intent(this, SciActivity::class.java)) }.apply {
            (layoutParams as LinearLayout.LayoutParams).leftMargin = dpi(6f) })
        top.addView(circleIcon("🌍") { startActivity(android.content.Intent(this, UnitsActivity::class.java)) }.apply {
            textSize = 15f; (layoutParams as LinearLayout.LayoutParams).leftMargin = dpi(6f) })
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
                if (r == 4 && col == 0) btn.setOnTouchListener { _, ev ->
                    if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_UP) { touchX = ev.x; touchY = ev.y }
                    false
                }
                btn.setOnClickListener { v ->
                    if (swiped) return@setOnClickListener
                    if (eng.keyDef(r, col).cls == "conv" && v.width > 0 && v.height > 0 &&
                        touchX / v.width + touchY / v.height > 1f
                    ) {
                        // bottom-right half = Switch to the other calculator
                        if (eng.haptic) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        eng.mach = !eng.mach
                        eng.conv = false
                        eng.mode = null
                        toast(if (eng.mach) "Machinist Calculator" else "Nirmaan Calc (Construction)")
                        Store.save(this, eng)
                        render()
                        return@setOnClickListener
                    }
                    if (eng.haptic) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (eng.clickSound) (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
                        .playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.6f)
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(45)
                        .withEndAction { v.animate().scaleX(1f).scaleY(1f).setDuration(90).start() }.start()
                    eng.press(r, col)
                    Store.save(this, eng)
                    render()
                }
                btn.setOnLongClickListener {
                    val d = eng.keyDef(r, col)
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(d.main)
                        .setMessage(KeyHelp.text(d.main, d.conv, d.blue))
                        .setPositiveButton("Theek hai", null)
                        .show()
                    true
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
        // (Machinist keypad: the green-row swipe is off, see ACTION_UP below)
        var sx = 0f
        var sy = 0f
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = ev.rawX; sy = ev.rawY }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.rawX - sx
                    val dy = ev.rawY - sy
                    if (abs(dx) > dp(45f) && abs(dy) < dp(35f) && !(eng.mach && r == 2)) {
                        if (r == 2) {
                            eng.trig = !eng.trig
                            toast(if (eng.trig) "SIN / COS / TAN (ek vaar, pachhi Length / Width / Height)" else "Length / Width / Height")
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
            if (d.cls == "conv") {
                k.btn.text = ""
                k.btn.background = SplitKey(colors("conv"), if (eng.light) intArrayOf(c(0xFF9575CD), c(0xFF5E35B1)) else intArrayOf(c(0xFF7E57C2), c(0xFF3F1F8C)), eng.conv)
                k.top.text = ""
                k.blue.text = ""
                continue
            }
            k.btn.text = txt
            k.btn.maxLines = if (txt.length > 7 && txt.contains(' ')) 2 else 1
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
        val save = TextView(this).apply {
            text = "Save"; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c(0xFFFFA726), c(0xFFEF6C00))).apply { cornerRadius = dp(8f) }
            setPadding(dpi(20f), dpi(10f), dpi(20f), dpi(10f))
            setOnClickListener { saveResult() }
        }
        foot.addView(share)
        foot.addView(save, llp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(8f) })
        val pdf = TextView(this).apply {
            text = "PDF"; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c(0xFFE57373), c(0xFFC62828))).apply { cornerRadius = dp(8f) }
            setPadding(dpi(16f), dpi(10f), dpi(16f), dpi(10f))
            setOnClickListener { printPdf() }
        }
        foot.addView(pdf, llp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(8f) })
        foot.addView(View(this), llp(0, 1, 1f))
        foot.addView(done)
        panelRoot.addView(foot, llp(MATCH_PARENT, WRAP_CONTENT))
        return panelRoot
    }

    private fun openPanel(title: String) {
        panelTitle.text = title
        panelBody.removeAllViews()
        shareText = ""
        curRows = emptyList()
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
            if (r.stair != null || r.plan != null) continue
            if (r.section) sb.append("\n— ").append(r.label).append(" —\n")
            else if (r.value.isEmpty()) sb.append(r.label).append("\n")
            else sb.append(r.label).append(": ").append(r.value).append("\n")
        }
        return sb.append("\n— Nirmaan Calc").toString()
    }

    private fun saveResult() {
        val text = if (shareText.isNotEmpty()) shareText else return toast("Save karne ko kuch nahi")
        val et = EditText(this).apply {
            setText(panelTitle.text.toString() + " " + java.text.SimpleDateFormat("dd-MM HH:mm", java.util.Locale.US).format(java.util.Date()))
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Naam dein (jaise: Ramesh bhai ki seedhi)")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                eng.saved.add(0, et.text.toString().ifBlank { panelTitle.text.toString() } to text)
                while (eng.saved.size > 200) eng.saved.removeAt(eng.saved.size - 1)
                Store.save(this, eng)
                toast("Save ho gaya")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savedPanel() {
        openPanel("Saved results (" + eng.saved.size + ")")
        if (eng.saved.isEmpty()) {
            panelBody.addView(rowView(Row("Abhi kuch save nahi hai. Kisi result screen par neeche Save dabayein.")))
            return
        }
        eng.saved.forEachIndexed { i, (lbl, txt) ->
            val v = rowView(Row(lbl, txt.lineSequence().drop(1).firstOrNull { it.isNotBlank() && !it.startsWith("—") } ?: ""))
            v.isClickable = true
            v.setOnClickListener {
                openPanel(lbl)
                panelBody.addView(TextView(this).apply {
                    text = txt; textSize = 15f; setTextColor(c(0xFF222222)); setTextIsSelectable(true)
                    setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(10f))
                })
                shareText = txt
            }
            v.setOnLongClickListener {
                AlertDialog.Builder(this).setMessage("\"$lbl\" delete karein?")
                    .setPositiveButton("Delete") { _, _ -> eng.saved.removeAt(i); Store.save(this, eng); savedPanel() }
                    .setNegativeButton("Nahi", null).show()
                true
            }
            panelBody.addView(v)
        }
        panelBody.addView(rowView(Row("Kholne ke liye tap, delete ke liye dabakar rakhein")))
    }

    /** BuildCalc "Edit Sizes": add, delete, move up. */
    private fun editSizes(cat: String, done: () -> Unit) {
        val single = cat == "oc" || cat == "picket"
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(16f), dpi(8f), dpi(16f), dpi(8f)) }
        val dlg = AlertDialog.Builder(this).setTitle("Sizes").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Done", null)
            .setNeutralButton("Default") { _, _ -> eng.sizes.remove(cat); Store.save(this, eng); done() }
            .create()
        fun list() = eng.sizes.getOrPut(cat) { eng.sizeDefaults(cat).toMutableList() }
        fun draw() {
            box.removeAllViews()
            val l = list()
            l.forEachIndexed { i, sz ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(4f), 0, dpi(4f)) }
                row.addView(TextView(this).apply { text = eng.sizeLabel(sz); textSize = 17f; setTextColor(c(0xFF222222)) }, llp(0, WRAP_CONTENT, 1f))
                fun btn(t: String, act: () -> Unit) = TextView(this).apply {
                    text = t; textSize = 18f; gravity = Gravity.CENTER; setPadding(dpi(14f), dpi(6f), dpi(14f), dpi(6f))
                    setTextColor(c(0xFF1565C0)); setOnClickListener { act(); Store.save(this@MainActivity, eng); draw(); done() }
                }
                if (i > 0) row.addView(btn("▲") { val x = l.removeAt(i); l.add(i - 1, x) })
                row.addView(btn("✕") { l.removeAt(i) })
                box.addView(row)
            }
            val et = EditText(this).apply {
                hint = if (single) (if (eng.metric) "jaise 45" else "jaise 19-1/2\"") else (if (eng.metric) "jaise 100x200" else "jaise 4'x8' ya 6\"x6\"")
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setSingleLine()
            }
            box.addView(et, llp(MATCH_PARENT, WRAP_CONTENT))
            box.addView(TextView(this).apply {
                text = "+ Size jodein"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor(c(0xFF1E88E5)); cornerRadius = dp(8f) }
                setPadding(0, dpi(10f), 0, dpi(10f))
                setOnClickListener {
                    val t = et.text.toString()
                    val sz = if (single) eng.parseLen(t).let { if (it.isFinite() && it > 0) it to 0.0 else null } else eng.parseSize(t)
                    if (sz == null) toast("Size samajh nahi aayi") else { list().add(0, sz); Store.save(this@MainActivity, eng); draw(); done() }
                }
            }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
            box.addView(TextView(this).apply {
                text = if (eng.metric) "Metric mein cm likhein (1000×2000 mm = 100x200)" else "Feet ke liye ' aur inch ke liye \" lagayein"
                textSize = 12f; setTextColor(c(0xFF777777))
            })
        }
        draw()
        dlg.show()
    }

    /** Print / save the open screen (text + drawings) as PDF with Android's print system. */
    private fun printPdf() {
        val title = panelTitle.text.toString()
        val rows = curRows
        val textOnly = if (rows.isEmpty()) shareText else ""
        val pm = getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
        val act = this
        val adapter = object : android.print.PrintDocumentAdapter() {
            var attrs: android.print.PrintAttributes? = null
            override fun onLayout(old: android.print.PrintAttributes?, new: android.print.PrintAttributes,
                                  cancel: android.os.CancellationSignal?, cb: android.print.PrintDocumentAdapter.LayoutResultCallback, extras: Bundle?) {
                attrs = new
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(android.print.PrintDocumentInfo.Builder("NirmaanCalc.pdf")
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }

            override fun onWrite(pages: Array<out android.print.PageRange>?, dest: android.os.ParcelFileDescriptor,
                                 cancel: android.os.CancellationSignal?, cb: android.print.PrintDocumentAdapter.WriteResultCallback) {
                val doc = android.print.pdf.PrintedPdfDocument(act, attrs ?: android.print.PrintAttributes.Builder()
                    .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(android.print.PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                    .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS).build())
                try {
                    var pageNo = 0
                    var page = doc.startPage(pageNo)
                    val pw = page.info.pageWidth.toFloat()
                    val ph = page.info.pageHeight.toFloat()
                    val margin = 36f
                    var y = margin
                    val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }
                    val hp = android.graphics.Paint(tp).apply { textSize = 16f; isFakeBoldText = true }
                    val sp2 = android.graphics.Paint(tp).apply { textSize = 11f; isFakeBoldText = true; color = c(0xFF1565C0) }
                    fun newPage() { doc.finishPage(page); pageNo++; page = doc.startPage(pageNo); y = margin }
                    fun need(h: Float) { if (y + h > ph - margin) newPage() }
                    need(24f); page.canvas.drawText(title, margin, y + 16f, hp); y += 26f
                    val lines = if (textOnly.isNotEmpty()) textOnly.lines().map { Row(it) } else rows
                    for (r in lines) {
                        val st = r.stair
                        val pl = r.plan
                        if (st != null || pl != null) {
                            val w = pw - 2 * margin
                            val dens = resources.displayMetrics.density
                            val v: View = if (st != null) StairView(act, st) else PlanView(act, pl!!)
                            val wpx = (w * dens).toInt()
                            v.measure(View.MeasureSpec.makeMeasureSpec(wpx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
                            val h = v.measuredHeight / dens
                            need(h + 8f)
                            val cv = page.canvas
                            cv.save(); cv.translate(margin, y); cv.scale(1 / dens, 1 / dens); v.draw(cv); cv.restore()
                            y += h + 8f
                        } else if (r.section) {
                            need(22f); y += 6f; page.canvas.drawText(r.label, margin, y + 11f, sp2); y += 16f
                        } else {
                            need(14f)
                            page.canvas.drawText(r.label, margin, y + 10f, tp)
                            if (r.value.isNotEmpty()) {
                                val vp = android.graphics.Paint(tp).apply { textAlign = android.graphics.Paint.Align.RIGHT; if (r.warn) color = c(0xFFC62828) }
                                page.canvas.drawText(r.value, pw - margin, y + 10f, vp)
                            }
                            y += 14f
                        }
                    }
                    need(20f); page.canvas.drawText("— Nirmaan Calc", margin, y + 14f, tp)
                    doc.finishPage(page)
                    java.io.FileOutputStream(dest.fileDescriptor).use { doc.writeTo(it) }
                    cb.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    cb.onWriteFailed(e.message)
                } finally {
                    doc.close()
                }
            }
        }
        try { pm.print(title, adapter, null) } catch (e: Exception) { toast("PDF nahi ban paya: " + e.message) }
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
        if (r.plan != null) return PlanView(this, r.plan).apply {
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
        curRows = rows
    }

    private fun seg(opts: List<String>, sel: Int, onPick: (Int) -> Unit): View {
        val s = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setStroke(1, c(0xFF888888)); cornerRadius = dp(8f); setColor(c(0xFFEEEEEE)) }
            setPadding(1, 1, 1, 1)
        }
        if (opts.size > 4) {
            // many options: rows of 3 so the names stay readable
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            opts.indices.chunked(3).forEachIndexed { ri, idx ->
                val rowSeg = seg(idx.map { opts[it] } + List(3 - idx.size) { "" }, idx.indexOf(sel)) { j -> if (j < idx.size) onPick(idx[j]) }
                col.addView(rowSeg, llp(MATCH_PARENT, WRAP_CONTENT).apply { if (ri > 0) topMargin = dpi(4f) })
            }
            return col
        }
        opts.forEachIndexed { i, o ->
            s.addView(TextView(this).apply {
                text = o; textSize = if (opts.size > 3) 13f else 15f; gravity = Gravity.CENTER; maxLines = 2
                if (o.isEmpty()) visibility = View.INVISIBLE
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
        var segVal: String? = spec.seg?.let { it.getOrNull(spec.segSel) ?: it.firstOrNull() }
        var seg2Val: String? = spec.seg2?.let { it.getOrNull(spec.seg2Sel) ?: it.firstOrNull() }
        val out = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val inputs = LinkedHashMap<String, EditText>()

        fun recompute() {
            val vals = HashMap<String, Double>()
            spec.fields.forEach { f ->
                val s = inputs.getValue(f.key).text.toString()
                if (f.isSize) {
                    eng.parseSize(s)?.let { vals[f.key + "_a"] = it.first; vals[f.key + "_b"] = it.second }
                } else {
                    vals[f.key] = if (f.isPitch) eng.parsePitch(s) else if (f.isLen) eng.parseLen(s, f.bare)
                    else (s.trim().replace(",", ".").replace("₹", "").trim().toDoubleOrNull() ?: Double.NaN)
                }
            }
            out.removeAllViews()
            val combined = if (spec.seg2 != null) (segVal ?: "") + "|" + (seg2Val ?: "") else segVal
            val rows = try { spec.compute(vals, combined) } catch (e: Exception) { listOf(Row("Values check karein", "—", true)) }
            curRows = rows
            rows.forEach { out.addView(rowView(it)) }
            val inp = spec.fields.map { Row(it.label, inputs.getValue(it.key).text.toString()) }
            val opt = listOfNotNull(segVal, seg2Val).joinToString(", ")
            shareText = rowsText(spec.title, listOf(sec("Inputs")) + inp + (if (opt.isNotEmpty()) listOf(Row("Option", opt)) else emptyList()) + rows)
        }

        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12f), dpi(4f), dpi(12f), dpi(4f))
        }
        fun drawSegs() {
            holder.removeAllViews()
            spec.seg?.let { opts ->
                holder.addView(seg(opts, opts.indexOf(segVal)) { i -> segVal = opts[i]; drawSegs(); recompute() },
                    llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(4f) })
            }
            spec.seg2?.let { opts ->
                holder.addView(seg(opts, opts.indexOf(seg2Val)) { i -> seg2Val = opts[i]; drawSegs(); recompute() },
                    llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
            }
        }
        for ((label, go) in spec.links) {
            val lb = LinearLayout(this).apply { setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(2f)) }
            lb.addView(TextView(this).apply {
                text = label; textSize = 15f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c(0xFF42A5F5), c(0xFF1565C0))).apply { cornerRadius = dp(8f) }
                setPadding(dpi(8f), dpi(11f), dpi(8f), dpi(11f))
                setOnClickListener { go() }
            }, llp(MATCH_PARENT, WRAP_CONTENT))
            panelBody.addView(lb)
        }
        if (spec.seg != null || spec.seg2 != null) {
            drawSegs()
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
                inputType = if (f.isLen || f.isPitch || f.isSize) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                if (f.value.isEmpty()) hint = if (f.isSize) "4'x8'" else "0"
                setSelectAllOnFocus(true)
                setSingleLine()
            }
            box.addView(et, llp(MATCH_PARENT, WRAP_CONTENT))
            if (f.hint.isNotEmpty()) box.addView(TextView(this).apply { text = f.hint; textSize = 12f; setTextColor(c(0xFF777777)) })
            inputs[f.key] = et
            panelBody.addView(box)
            panelBody.addView(divider())
        }
        spec.sizeCat?.let { cat ->
            val eb = LinearLayout(this).apply { setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f)) }
            eb.addView(TextView(this).apply {
                text = "Sizes edit karein (jodein / hatayein / upar karein)"; textSize = 15f; gravity = Gravity.CENTER
                setTextColor(c(0xFF1565C0))
                background = GradientDrawable().apply { setStroke(dpi(1f), c(0xFF1565C0)); cornerRadius = dp(8f); setColor(Color.WHITE) }
                setPadding(0, dpi(10f), 0, dpi(10f))
                setOnClickListener { editSizes(cat) { recompute() } }
            }, llp(MATCH_PARENT, WRAP_CONTENT))
            panelBody.addView(eb)
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
        val sv = LinearLayout(this).apply { setPadding(dpi(12f), dpi(12f), dpi(12f), dpi(4f)) }
        sv.addView(TextView(this).apply {
            text = "Saved results (" + eng.saved.size + ")"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c(0xFFFFA726), c(0xFFEF6C00))).apply { cornerRadius = dp(8f) }
            setPadding(0, dpi(12f), 0, dpi(12f))
            setOnClickListener { savedPanel() }
        }, llp(MATCH_PARENT, WRAP_CONTENT))
        panelBody.addView(sv)
        add("Theme", listOf("Dark", "Light"), if (eng.light) 1 else 0) { eng.light = it == 1; render() }
        add("Units System", listOf("Feet-Inch", "Metric"), if (eng.metric) 1 else 0) { eng.metric = it == 1 }
        val resList = listOf(2, 4, 8, 16, 32, 64)
        add("Fraction Resolution", resList.map { "1/$it" }, resList.indexOf(eng.res)) { eng.res = resList[it] }
        add("Fraction mode", listOf("Standard (7/8)", "Constant (14/16)"), if (eng.fracConst) 1 else 0) { eng.fracConst = it == 1 }
        add("Pounds per ton", listOf("2000 (short)", "2240 (long)"), if (eng.lbsPerTon > 2100) 1 else 0) {
            val old = eng.lbsPerTon; eng.lbsPerTon = if (it == 1) 2240.0 else 2000.0
            if (old != eng.lbsPerTon && abs(eng.dens - 1.5 * old * LB / YD3) < 1e-6) eng.dens = 1.5 * eng.lbsPerTon * LB / YD3
        }
        add("Thousands separator (1,00,000)", listOf("Off", "On"), if (eng.thousands) 1 else 0) { eng.thousands = it == 1 }
        add("Button vibration", listOf("On", "Off"), if (eng.haptic) 0 else 1) { eng.haptic = it == 0 }
        add("Button click awaaz", listOf("Off", "On"), if (eng.clickSound) 1 else 0) { eng.clickSound = it == 1 }
        val decList = listOf(-1, 2, 3, 4, 6)
        add("Decimal places", listOf("Auto", "2", "3", "4", "6"), decList.indexOf(eng.decPlaces).coerceAtLeast(0)) { eng.decPlaces = decList[it] }
        add("Arched wall studs", listOf("Outside (bahar)", "Inside (andar)"), if (eng.archInside) 1 else 0) { eng.archInside = it == 1 }
        add("Advanced Function Mode", listOf("ON", "OFF (purane MsnSz, SprAng, TreadW... keys)"), if (eng.advanced) 0 else 1) { eng.advanced = it == 0 }

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
            text = "Tip: green keys (Length/Width/Height) par ungli left-right sarkayein to ek vaar SIN/COS/TAN aayenge, use karte hi wapas Length/Width/Height. " +
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
            "<b>Kisi bhi button ko dabakar rakhein</b> (long press): us button ka poora kaam likha aata hai.",
            "<b>Kharcha (Cost):</b> quantity × rate, phir Conv + 0. Jaise 9 Yards Yards Yards × 5000 Conv 0 = ₹ 45,000. Board feet par rate 1000 BF ka. Sirf Conv + 0 = rate aur GST screen.",
            "<b>wt/vol (density):</b> 1600 Store 0 = density save (Store 0 dobara = unit badlein: T/yd³, lb/yd³, lb/ft³, MT/m³, kg/m³). Phir volume par Conv + kg/lbs/tons = wazan, aur wazan par Feet/Yards/m = volume.",
            "<b>Board feet:</b> 35 Conv 8 = 35 bf. Volume par Conv 8 = board feet.",
            "<b>DMS:</b> 23.16.45 Conv . = 23.28°. Conv . dobara: pitch, % pitch, % slope, radians.",
            "<b>Fraction:</b> Conv + / = 1/2 se 1/64 tak badlein. m dobara dabane par m → cm → mm.",
            "<b>Rafter:</b> Rise/Run/Pitch ke baad Diag dobara = ridge deduction, overhang, birdsmouth, lakdi ki lambai.",
            "<b>Save:</b> har result screen par Save dabakar naam dein. Prefs mein 'Saved results'.",
            "<b>PDF / Print:</b> result screen par neeche PDF dabayein → 'Save as PDF' ya printer chunein. Seedhi ki drawings bhi aati hain.",
            "<b>Sizes edit:</b> Drywall, Roof, Tile, Fence, Qty@oc screen par 'Sizes edit karein' se apni sizes jodein, hatayein, upar karein. Yaad rehti hain.",
            "<b>Hip/V:</b> screen ke andar Regular / Irregular switch, purlin angles, aur building length daalne par chhat ke har hisse ka area.",
            "<b>Purane keys:</b> Prefs mein Advanced Function Mode OFF karein to Store + 4/6/8/9/./+ = MsnSz, SprAng, TreadW, RiserH, FloorH, FtArea.",
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
