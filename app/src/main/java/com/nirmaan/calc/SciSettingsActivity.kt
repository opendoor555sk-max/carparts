package com.nirmaan.calc

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray

/** saving / loading the calculator settings */
object SciStore {
    fun load(ctx: Context): SciOpts {
        val sp = ctx.getSharedPreferences("sci", Context.MODE_PRIVATE)
        return SciOpts().apply {
            lang = sp.getInt("lang", ctx.getSharedPreferences("units", Context.MODE_PRIVATE).getInt("lang", 1))
            angle = sp.getInt("angle", 0); mode = sp.getInt("mode", 0); dec = sp.getInt("dec", -1)
            exact = sp.getBoolean("exact", true); expStyle = sp.getInt("expStyle", 0); comma = sp.getBoolean("comma", false)
            group = sp.getInt("group", 0); mul = sp.getInt("mul", 0); div = sp.getInt("div", 0)
            keep = sp.getBoolean("keep", true); negExp = sp.getBoolean("negExp", true); abbrev = sp.getBoolean("abbrev", true)
            spacious = sp.getBoolean("spacious", true); isolate = sp.getBoolean("isolate", true); vibrate = sp.getBoolean("vibrate", true)
        }
    }

    fun save(ctx: Context, o: SciOpts) {
        ctx.getSharedPreferences("sci", Context.MODE_PRIVATE).edit()
            .putInt("lang", o.lang).putInt("angle", o.angle).putInt("mode", o.mode).putInt("dec", o.dec)
            .putBoolean("exact", o.exact).putInt("expStyle", o.expStyle).putBoolean("comma", o.comma)
            .putInt("group", o.group).putInt("mul", o.mul).putInt("div", o.div)
            .putBoolean("keep", o.keep).putBoolean("negExp", o.negExp).putBoolean("abbrev", o.abbrev)
            .putBoolean("spacious", o.spacious).putBoolean("isolate", o.isolate).putBoolean("vibrate", o.vibrate)
            .apply()
    }
}

class SciSettingsActivity : Activity() {

    companion object {
        /** list of past sums; tap one to use it again */
        fun historyDialog(act: Activity, o: SciOpts, onPick: (String) -> Unit) {
            fun t(en: String, hi: String, gu: String) = when (o.lang) { 1 -> hi; 2 -> gu; else -> en }
            val sp = act.getSharedPreferences("sci", Context.MODE_PRIVATE)
            val arr = try { JSONArray(sp.getString("hist", "[]")) } catch (e: Exception) { JSONArray() }
            if (arr.length() == 0) {
                AlertDialog.Builder(act).setMessage(t("No history yet", "अभी कोई हिसाब नहीं", "હજી કોઈ હિસાબ નથી")).setPositiveButton("OK", null).show()
                return
            }
            val exprs = ArrayList<String>()
            val items = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val ob = arr.optJSONObject(i) ?: continue
                exprs.add(ob.optString("e")); items.add(ob.optString("e") + "\n" + ob.optString("r"))
            }
            AlertDialog.Builder(act).setTitle(t("History", "पुराने हिसाब", "જૂના હિસાબ"))
                .setItems(items.toTypedArray()) { _, w -> onPick(exprs[w]) }
                .setNegativeButton(t("Clear all", "सब मिटाओ", "બધું ભૂંસો")) { _, _ ->
                    sp.edit().putString("hist", "[]").putBoolean("histCleared", true).apply()
                }
                .setPositiveButton(t("Close", "बंद", "બંધ"), null)
                .show()
        }
    }

    private lateinit var o: SciOpts
    private lateinit var content: LinearLayout
    private lateinit var titleTv: TextView

    private fun dp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, resources.displayMetrics)
    private fun dpi(x: Float) = dp(x).toInt()
    private fun c(hex: Long) = hex.toInt()
    private fun t(en: String, hi: String, gu: String) = when (o.lang) { 1 -> hi; 2 -> gu; else -> en }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        o = SciStore.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(c(SciActivity.BG)); fitsSystemWindows = true
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(4f)) }
        head.addView(TextView(this).apply {
            text = "←"; textSize = 24f; setTextColor(c(SciActivity.TEXT)); gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dpi(44f), dpi(44f)))
        val title = TextView(this).apply { textSize = 20f; setTextColor(c(SciActivity.TEXT)); typeface = Typeface.DEFAULT_BOLD }
        head.addView(title)
        titleTv = title
        root.addView(head)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(18f), 0, dpi(18f), dpi(30f)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        build()
    }

    private fun save() { SciStore.save(this, o) }

    private fun build() {
        content.removeAllViews()
        titleTv.text = "⚙ " + t("Settings", "सेटिंग", "સેટિંગ")

        header(t("General", "सामान्य", "સામાન્ય"))
        item(t("History", "पुराने हिसाब", "જૂના હિસાબ"), null) {
            historyDialog(this, o) { picked ->
                getSharedPreferences("sci", Context.MODE_PRIVATE).edit().putString("pick", picked).apply()
                finish()
            }
        }
        choice(t("Select language", "भाषा", "ભાષા"), arrayOf("English", "हिन्दी", "ગુજરાતી"), { o.lang }) { o.lang = it; build() }
        choice(t("Decimal separator", "दशमलव का निशान", "દશાંશ ચિહ્ન"), arrayOf(t("Dot  1.5", "बिंदु  1.5", "ટપકું  1.5"), t("Comma  1,5", "कॉमा  1,5", "અલ્પવિરામ  1,5")),
            { if (o.comma) 1 else 0 }) { o.comma = it == 1 }
        choice(t("Digit grouping", "अंकों के बीच कॉमा", "અંકો વચ્ચે અલ્પવિરામ"), arrayOf(t("Indian  12,34,567", "भारतीय  12,34,567", "ભારતીય  12,34,567"),
            t("International  1,234,567", "अंतरराष्ट्रीय  1,234,567", "આંતરરાષ્ટ્રીય  1,234,567"), t("None  1234567", "कोई नहीं  1234567", "કંઈ નહીં  1234567")), { o.group }) { o.group = it }
        choice(t("Multiplication sign", "गुणा का निशान", "ગુણાકારનું ચિહ્ન"), arrayOf("×", "·", "*"), { o.mul }) { o.mul = it }
        choice(t("Division sign", "भाग का निशान", "ભાગાકારનું ચિહ્ન"), arrayOf("÷", "/"), { o.div }) { o.div = it }
        switch(t("Vibrate on key press", "बटन दबाने पर कंपन", "બટન દબાવતા ધ્રુજારી"), { o.vibrate }) { o.vibrate = it }

        header(t("Calculation", "हिसाब", "હિસાબ"))
        choice(t("Angle unit", "कोण की यूनिट", "ખૂણાનો યુનિટ"), arrayOf("DEG  (90° = " + t("right angle", "समकोण", "કાટખૂણો") + ")", "RAD  (π/2)", "GRA  (100 grad)"), { o.angle }) { o.angle = it }
        switch(t("Preserve structure", "Enter के बाद हिसाब दिखता रहे", "Enter પછી હિસાબ દેખાતો રહે"), { o.keep },
            t("On: the sum stays, answer below. Off: the answer replaces the sum.", "चालू: हिसाब रहेगा, जवाब नीचे. बंद: जवाब हिसाब की जगह आएगा.", "ચાલુ: હિસાબ રહેશે, જવાબ નીચે. બંધ: જવાબ હિસાબની જગ્યાએ આવશે.")) { o.keep = it }
        switch(t("Exact answers (3/4, √2/2, π/4)", "सटीक जवाब (3/4, √2/2, π/4)", "ચોક્કસ જવાબ (3/4, √2/2, π/4)"), { o.exact }) { o.exact = it }

        header(t("Output", "जवाब कैसे दिखे", "જવાબ કેવી રીતે દેખાય"))
        switch(t("Negative exponents", "माइनस घात (m·s⁻¹)", "માઇનસ ઘાત (m·s⁻¹)"), { o.negExp },
            t("On: m·s⁻¹   Off: m/s", "चालू: m·s⁻¹   बंद: m/s", "ચાલુ: m·s⁻¹   બંધ: m/s")) { o.negExp = it }
        switch(t("Abbreviate names", "यूनिट छोटे नाम से", "યુનિટ ટૂંકા નામે"), { o.abbrev },
            t("On: ft   Off: foot", "चालू: ft   बंद: फुट", "ચાલુ: ft   બંધ: ફૂટ")) { o.abbrev = it }
        switch(t("Spacious output", "खुली लिखावट", "ખુલ્લું લખાણ"), { o.spacious },
            t("On: 2 + 3   Off: 2+3", "चालू: 2 + 3   बंद: 2+3", "ચાલુ: 2 + 3   બંધ: 2+3")) { o.spacious = it }
        switch(t("Isolate units", "यूनिट अलग रंग में", "યુનિટ અલગ રંગમાં"), { o.isolate }) { o.isolate = it }
        choice(t("Number format", "नंबर का तरीका", "નંબરની રીત"), arrayOf(t("Normal  12,500", "साधारण  12,500", "સામાન્ય  12,500"),
            t("Scientific  1.25×10⁴", "साइंटिफिक  1.25×10⁴", "સાયન્ટિફિક  1.25×10⁴"), t("Engineering  12.5×10³", "इंजीनियरिंग  12.5×10³", "એન્જિનિયરિંગ  12.5×10³")), { o.mode }) { o.mode = it }
        choice(t("Exp display", "घात कैसे दिखे", "ઘાત કેવી રીતે દેખાય"), arrayOf("2×10³", "2E3", "2e3"), { o.expStyle }) { o.expStyle = it }
        choice(t("Decimal places", "दशमलव के बाद अंक", "દશાંશ પછી અંક"),
            arrayOf(t("Auto", "अपने आप", "આપમેળે")) + (0..10).map { if (it == 0) "0" else "0." + "0".repeat(it) }, { o.dec + 1 }) { o.dec = it - 1 }

        header(t("About app", "ऐप के बारे में", "એપ વિશે"))
        item(t("How to use", "कैसे चलाएं", "કેવી રીતે વાપરવું"), null) { help() }
        item(t("Share app", "ऐप भेजो", "એપ મોકલો"), null) {
            val msg = "Nirmaan Calc — construction + scientific calculator + world units\nhttps://github.com/opendoor555sk-max/carparts/releases/download/calc-latest/NirmaanCalc.apk"
            try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Share")) } catch (_: Exception) {}
        }
        item(t("Check for update", "अपडेट देखो", "અપડેટ જુઓ"), null) { Updater.check(this, true) }
        item(t("Privacy policy", "प्राइवेसी", "પ્રાઇવસી"), null) {
            AlertDialog.Builder(this).setTitle(t("Privacy policy", "प्राइवेसी", "પ્રાઇવસી"))
                .setMessage(t("Nirmaan Calc keeps all your sums only on this phone. It asks for no account and sends nothing out. It only checks the internet for a new version of the app.",
                    "निर्माण कैलक्युलेटर आपके सारे हिसाब सिर्फ इसी फोन में रखता है. न खाता, न कोई जानकारी बाहर भेजता है. सिर्फ नया वर्ज़न देखने के लिए इंटरनेट देखता है.",
                    "નિર્માણ કેલ્ક્યુલેટર તમારા બધા હિસાબ ફક્ત આ ફોનમાં રાખે છે. ન ખાતું, ન કોઈ માહિતી બહાર મોકલે છે. ફક્ત નવું વર્ઝન જોવા ઇન્ટરનેટ જુએ છે."))
                .setPositiveButton("OK", null).show()
        }
        item(t("Version", "वर्ज़न", "વર્ઝન") + " " + Updater.myVersionName(this), null, arrow = false) {}
    }

    private fun help() {
        AlertDialog.Builder(this).setTitle(t("How to use", "कैसे चलाएं", "કેવી રીતે વાપરવું")).setMessage(t(
            "• Units: 12 ft × 10 ft = 120 ft²  ·  5 ft 3 in → cm\n• Meter = pick a unit, Kilo = k/M/m… before a unit, → = change unit\n• X Y Z: long-press to save the answer, tap to use it\n• Solve: 2X + 3 = 11 → X = 4\n• ∫(X², 0, 3) · d/dx(X³, 2) · Σ(X, 1, 100)\n• 200 + 10% = 220  ·  5! = 120  ·  √(−4) = 2i\n• Small yellow text on a key = long-press\n• Tap the grey line on top = history",
            "• यूनिट: 12 ft × 10 ft = 120 ft²  ·  5 ft 3 in → cm\n• Meter = यूनिट चुनो, Kilo = यूनिट से पहले k/M/m…, → = यूनिट बदलो\n• X Y Z: देर तक दबाओ = जवाब रखो, दबाओ = इस्तेमाल करो\n• समीकरण: 2X + 3 = 11 → X = 4\n• ∫(X², 0, 3) · d/dx(X³, 2) · Σ(X, 1, 100)\n• 200 + 10% = 220  ·  5! = 120  ·  √(−4) = 2i\n• बटन पर छोटा पीला लिखा = देर तक दबाओ\n• ऊपर की ग्रे लाइन दबाओ = पुराने हिसाब",
            "• યુનિટ: 12 ft × 10 ft = 120 ft²  ·  5 ft 3 in → cm\n• Meter = યુનિટ પસંદ કરો, Kilo = યુનિટ પહેલાં k/M/m…, → = યુનિટ બદલો\n• X Y Z: લાંબું દબાવો = જવાબ રાખો, દબાવો = વાપરો\n• સમીકરણ: 2X + 3 = 11 → X = 4\n• ∫(X², 0, 3) · d/dx(X³, 2) · Σ(X, 1, 100)\n• 200 + 10% = 220  ·  5! = 120  ·  √(−4) = 2i\n• બટન પર નાનું પીળું લખાણ = લાંબું દબાવો\n• ઉપરની ગ્રે લાઇન દબાવો = જૂના હિસાબ"))
            .setPositiveButton("OK", null).show()
    }

    // ================= rows =================
    private fun header(s: String) {
        content.addView(TextView(this).apply {
            text = s; textSize = 13f; setTextColor(c(SciActivity.ACCENT)); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpi(22f), 0, dpi(6f))
        })
    }

    private fun divider() = View(this).apply { setBackgroundColor(c(SciActivity.KEY)) }

    private fun item(title: String, sub: String?, arrow: Boolean = true, onClick: () -> Unit): TextView? {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(12f), 0, dpi(12f))
            setOnClickListener { onClick() }
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(c(SciActivity.TEXT)) })
        var subTv: TextView? = null
        if (sub != null) {
            subTv = TextView(this).apply { text = sub; textSize = 13f; setTextColor(c(SciActivity.SUB)) }
            left.addView(subTv)
        }
        row.addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (arrow) row.addView(TextView(this).apply { text = "›"; textSize = 22f; setTextColor(c(SciActivity.SUB)) })
        content.addView(row)
        content.addView(divider(), LinearLayout.LayoutParams(MATCH_PARENT, 1))
        return subTv
    }

    private fun choice(title: String, items: Array<String>, get: () -> Int, set: (Int) -> Unit) {
        var subTv: TextView? = null
        subTv = item(title, items.getOrElse(get()) { items[0] }) {
            AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(items, get()) { d, w ->
                    set(w); save()
                    subTv?.text = items[w]
                    d.dismiss()
                }.show()
        }
    }

    private fun switch(title: String, get: () -> Boolean, sub: String? = null, set: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(10f), 0, dpi(10f))
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(c(SciActivity.TEXT)) })
        if (sub != null) left.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(c(SciActivity.SUB)) })
        row.addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val sw = Switch(this).apply {
            isChecked = get()
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(c(SciActivity.ACCENT), c(0xFFBDBDBD)))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(c(0x88FF9500), c(0xFF555555)))
            setOnCheckedChangeListener { _, on -> set(on); save() }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        content.addView(row)
        content.addView(divider(), LinearLayout.LayoutParams(MATCH_PARENT, 1))
    }
}
