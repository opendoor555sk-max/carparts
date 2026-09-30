package com.kabadi.rate

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val INK = 0xFF1C2328.toInt()
    private val MUTED = 0xFF607D8B.toInt()
    private val GREEN = 0xFF2E7D32.toInt()
    private val RED = 0xFFC62828.toInt()
    private val BLUE = 0xFF1565C0.toInt()
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var content: FrameLayout
    private lateinit var title: TextView
    private lateinit var langBtn: TextView
    private var gen = 0
    private var name = ""; private var mobile = ""; private var otpIn = ""
    private val typed = HashMap<String, String>()
    private var shownAlive = false
    private var tick = 0
    private var active = false

    // 0 English, 1 Hindi, 2 Gujarati
    private val S = mapOf(
        "app" to arrayOf("Scrap Rates", "स्क्रैप भाव", "સ્ક્રેપ ભાવ"),
        "welcome" to arrayOf("Register to send your daily metal rates.", "रोज़ के मेटल भाव भेजने के लिए रजिस्टर करें।", "રોજના મેટલ ભાવ મોકલવા નોંધણી કરો."),
        "name" to arrayOf("Your name / firm *", "आपका नाम / फर्म *", "તમારું નામ / પેઢી *"),
        "mobile" to arrayOf("Mobile number *", "मोबाइल नंबर *", "મોબાઇલ નંબર *"),
        "ask" to arrayOf("Ask admin for OTP", "एडमिन से OTP माँगें", "એડમિન પાસે OTP માંગો"),
        "need" to arrayOf("Write name and a 10-digit mobile", "नाम और 10 अंक का मोबाइल लिखें", "નામ અને 10 આંકડાનો મોબાઇલ લખો"),
        "net" to arrayOf("No internet – try again", "इंटरनेट नहीं – फिर कोशिश करें", "ઇન્ટરનેટ નથી – ફરી પ્રયત્ન કરો"),
        "wait" to arrayOf("Request sent. The admin will give you an OTP (call / WhatsApp).", "रिक्वेस्ट भेज दी। एडमिन आपको OTP देगा (फोन / WhatsApp)।", "રિક્વેસ્ટ મોકલી. એડમિન તમને OTP આપશે (ફોન / WhatsApp)."),
        "appr" to arrayOf("✅ Admin approved. Enter the OTP.", "✅ एडमिन ने मंज़ूरी दी। OTP डालें।", "✅ એડમિને મંજૂરી આપી. OTP નાખો."),
        "code" to arrayOf("Your phone code", "आपके फोन का कोड", "તમારા ફોનનો કોડ"),
        "otp" to arrayOf("OTP (6 digits)", "OTP (6 अंक)", "OTP (6 આંકડા)"),
        "otp_ok" to arrayOf("✅ Check OTP", "✅ OTP जाँचें", "✅ OTP તપાસો"),
        "otp_bad" to arrayOf("Wrong OTP", "OTP गलत है", "OTP ખોટો છે"),
        "again" to arrayOf("🔄 Check again", "🔄 फिर जाँचें", "🔄 ફરી તપાસો"),
        "cancel" to arrayOf("↩ Change number", "↩ नंबर बदलें", "↩ નંબર બદલો"),
        "today" to arrayOf("Today's rates", "आज के भाव", "આજના ભાવ"),
        "gone" to arrayOf("Rates disappear every day at 7 PM. Fill the metals you buy.", "भाव रोज़ शाम 7 बजे हट जाते हैं। जो माल लेते हैं उसके भाव भरें।", "ભાવ રોજ સાંજે 7 વાગ્યે હટી જાય છે. તમે જે માલ લો છો તેના ભાવ ભરો."),
        "send" to arrayOf("✅ Send rates", "✅ भाव भेजें", "✅ ભાવ મોકલો"),
        "sent" to arrayOf("✓ Sent", "✓ भेजे गए", "✓ મોકલ્યા"),
        "last" to arrayOf("Last sent", "आख़िरी बार भेजा", "છેલ્લે મોકલ્યા"),
        "pending" to arrayOf("⏳ Not sent yet (will retry)", "⏳ अभी नहीं गया (फिर कोशिश होगी)", "⏳ હજુ ગયા નથી (ફરી પ્રયત્ન થશે)"),
        "none" to arrayOf("Write at least one rate", "कम से कम एक भाव लिखें", "ઓછામાં ઓછો એક ભાવ લખો"),
        "block" to arrayOf("⛔ The admin has closed this app for you permanently.", "⛔ एडमिन ने आपके लिए यह ऐप हमेशा के लिए बंद कर दिया है।", "⛔ એડમિને તમારા માટે આ એપ કાયમ માટે બંધ કરી છે."),
        "upd" to arrayOf("⬆ Check update", "⬆ अपडेट जाँचें", "⬆ અપડેટ તપાસો"),
        "nof" to arrayOf("✅ OTP is correct.\nThe admin is preparing your rate form. It will appear here automatically.", "✅ OTP सही है।\nएडमिन आपका भाव फ़ॉर्म तैयार कर रहे हैं। यहाँ अपने आप आ जाएगा।", "✅ OTP સાચો છે.\nએડમિન તમારું ભાવ ફોર્મ તૈયાર કરી રહ્યા છે. તે અહીં આપોઆપ દેખાશે."),
        "kg" to arrayOf("per kg", "प्रति kg", "દર કિલો")
    )
    private fun t(k: String): String = S[k]?.get(Me.lang(this)) ?: k

    private fun dp(x: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, resources.displayMetrics)
    private fun dpi(x: Float) = dp(x).toInt()
    private fun llp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun round(color: Int, r: Float = 10f, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r); if (stroke != 0) setStroke(dpi(1f), stroke)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF2F4F5.toInt()) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xFF263238.toInt()); setPadding(dpi(14f), dpi(12f), dpi(10f), dpi(12f)) }
        title = TextView(this).apply { textSize = 19f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        langBtn = TextView(this).apply {
            textSize = 14f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = round(0x33FFFFFF, 14f); setPadding(dpi(12f), dpi(6f), dpi(12f), dpi(6f))
            setOnClickListener { Me.setLang(this@MainActivity, (Me.lang(this@MainActivity) + 2) % 3); render() }
        }
        bar.addView(title, llp(0, WRAP_CONTENT, 1f)); bar.addView(langBtn, llp(WRAP_CONTENT, WRAP_CONTENT))
        content = FrameLayout(this)
        root.addView(bar, llp(MATCH_PARENT, WRAP_CONTENT)); root.addView(content, llp(MATCH_PARENT, 0, 1f))
        setContentView(root)
        render()
        Updater.autoCheck(this)
    }

    override fun onResume() {
        super.onResume(); active = true
        Thread { val ch = Me.sync(this); ui.post { if (ch && active) render() } }.start()
        loop()
    }
    override fun onPause() { super.onPause(); active = false; gen++ }

    /** every 20 s: quick check while waiting for the admin, every 2 min otherwise; and re-draw when 7 PM passes */
    private fun loop() {
        val g = ++gen
        ui.postDelayed(object : Runnable {
            override fun run() {
                if (g != gen || !active) return
                tick++
                val st = Me.state(this@MainActivity)
                if (st == "active" && shownAlive && Me.rates(this@MainActivity).isEmpty()) { typed.clear(); render() }
                if (st == "asked" || (st == "active" && (tick % 6 == 0 || !Me.hasForm(this@MainActivity)))) Thread { val ch = Me.sync(this@MainActivity); ui.post { if (ch && g == gen) render() } }.start()
                ui.postDelayed(this, 20000)
            }
        }, 20000)
    }

    // ---------- small views ----------
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = round(Color.WHITE, 12f); setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(14f)); elevation = dp(1.5f)
    }
    private fun small(s: String, color: Int = MUTED, size: Float = 14f) = TextView(this).apply { text = s; textSize = size; setTextColor(color) }
    private fun button(s: String, color: Int, act: () -> Unit) = TextView(this).apply {
        text = s; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        background = round(color, 10f); setPadding(dpi(8f), dpi(14f), dpi(8f), dpi(14f)); setOnClickListener { act() }
    }
    private fun link(s: String, color: Int, act: () -> Unit) = TextView(this).apply {
        text = s; textSize = 14f; setTextColor(color); gravity = Gravity.CENTER; background = round(Color.WHITE, 14f, color)
        setPadding(dpi(10f), dpi(9f), dpi(10f), dpi(9f)); setOnClickListener { act() }
    }
    private fun input(hint: String, value: String, kind: Int, onChange: (String) -> Unit) = EditText(this).apply {
        this.hint = hint; setText(value); textSize = 17f; setTextColor(INK); setHintTextColor(0xFF9EAAB0.toInt()); setSingleLine()
        inputType = kind; background = round(0xFFF7F9FA.toInt(), 8f, 0xFFCFD8DC.toInt()); setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(10f))
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = onChange(s?.toString() ?: "")
        })
    }
    private fun gap(h: Float = 8f) = View(this).also { it.minimumHeight = dpi(h) }

    // ---------- screens ----------
    private fun render() {
        if (!::content.isInitialized) return
        val lg = Me.lang(this)
        langBtn.text = arrayOf("EN", "हिं", "ગુ")[lg]
        title.text = "📊 " + t("app")
        content.removeAllViews()
        shownAlive = false
        val sv = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(12f), dpi(12f), dpi(12f), dpi(28f)) }
        sv.addView(body); content.addView(sv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        when (Me.state(this)) {
            "block" -> blocked(body)
            "active" -> if (Me.hasForm(this)) rates(body) else noForm(body)
            "asked" -> waiting(body)
            else -> register(body)
        }
    }

    private fun blocked(body: LinearLayout) {
        body.addView(card().apply { addView(small(t("block"), RED, 18f).apply { typeface = Typeface.DEFAULT_BOLD }) }, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun register(body: LinearLayout) {
        if (name.isBlank()) name = Me.name(this)
        if (mobile.isBlank()) mobile = Me.mobile(this)
        val c = card()
        c.addView(small(t("welcome"), INK, 16f))
        c.addView(gap())
        c.addView(input(t("name"), name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) { name = it })
        c.addView(gap(6f))
        c.addView(input(t("mobile"), mobile, InputType.TYPE_CLASS_NUMBER) { mobile = it.filter { ch -> ch.isDigit() }.takeLast(10) }.apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(10)) })
        c.addView(gap(12f))
        c.addView(button("📨 " + t("ask"), BLUE) {
            val m = mobile.filter { it.isDigit() }
            if (name.trim().length < 2 || m.length != 10) return@button toast(t("need"))
            val at = System.currentTimeMillis()
            Thread {
                val ok = Relay.post(Scrap.REG, JSONObject().put("n", name.trim()).put("m", m).put("d", Me.dev(this)).put("t", at))
                if (ok) { Me.register(this, name.trim(), m, at); Me.sync(this) }
                ui.post { if (ok) render() else toast("📶 " + t("net")) }
            }.start()
        })
        body.addView(c, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun waiting(body: LinearLayout) {
        val c = card()
        c.addView(small("👤 " + Me.name(this) + "   📞 " + Me.mobile(this), INK, 16f).apply { typeface = Typeface.DEFAULT_BOLD })
        c.addView(gap(6f))
        c.addView(small(if (Me.approved(this)) t("appr") else t("wait"), if (Me.approved(this)) GREEN else INK, 15f))
        c.addView(gap(6f))
        c.addView(small(t("code") + ": " + Me.dev(this), MUTED, 13f))
        c.addView(gap(10f))
        c.addView(input(t("otp"), otpIn, InputType.TYPE_CLASS_NUMBER) { otpIn = it }.apply { filters = arrayOf(android.text.InputFilter.LengthFilter(6)); gravity = Gravity.CENTER; textSize = 24f })
        c.addView(gap(10f))
        c.addView(button(t("otp_ok"), GREEN) {
            if (Scrap.otpOk(Me.mobile(this), Me.dev(this), otpIn)) {
                Me.activate(this); otpIn = ""
                Thread { Me.sync(this); ui.post { render() } }.start()
                render()
            } else toast(t("otp_bad"))
        })
        c.addView(gap(10f))
        c.addView(LinearLayout(this).apply {
            addView(link(t("again"), BLUE) { Thread { val ch = Me.sync(this@MainActivity); ui.post { if (ch) render() else toast("✓") } }.start() }, llp(0, WRAP_CONTENT, 1f).apply { rightMargin = dpi(6f) })
            addView(link(t("cancel"), MUTED) { Me.reset(this@MainActivity); render() }, llp(0, WRAP_CONTENT, 1f))
        })
        body.addView(c, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun noForm(body: LinearLayout) {
        val c = card()
        c.addView(small("👤 " + Me.name(this) + "   📞 " + Me.mobile(this), INK, 16f).apply { typeface = Typeface.DEFAULT_BOLD })
        c.addView(gap(8f))
        c.addView(small(t("nof"), GREEN, 16f))
        c.addView(gap(12f))
        c.addView(link(t("again"), BLUE) { Thread { val ch = Me.sync(this@MainActivity); ui.post { if (ch) render() else toast("✓") } }.start() })
        body.addView(c, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(link(t("upd") + "  (" + Updater.myVersionName(this) + ")", BLUE) { Updater.check(this, true) }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(18f) })
    }

    private fun rates(body: LinearLayout) {
        val lg = Me.lang(this)
        val cur = Me.rates(this)
        shownAlive = cur.isNotEmpty()
        val h = card()
        h.addView(small("👤 " + Me.name(this), INK, 17f).apply { typeface = Typeface.DEFAULT_BOLD })
        h.addView(small("📅 " + SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date()) + "  •  " + t("today"), INK, 15f))
        h.addView(small("⏰ " + t("gone"), MUTED, 13f))
        body.addView(h, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(10f) })
        val vals = HashMap<String, String>()
        val tiles = Me.format(this).map { m ->
            vals[m.id] = typed[m.id] ?: cur[m.id]?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: ""
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = round(Color.WHITE, 12f); setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(12f)); elevation = dp(1.5f)
                addView(small(m.name(lg), INK, 17f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
                addView(small("₹ " + m.unitText(lg), MUTED, 13f).apply { gravity = Gravity.CENTER })
                addView(input("₹", vals[m.id] ?: "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { s -> vals[m.id] = s; typed[m.id] = s }
                    .apply { gravity = Gravity.CENTER; textSize = 22f; typeface = Typeface.DEFAULT_BOLD }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(4f) })
            }
        }
        tiles.chunked(2).forEach { r ->
            val row = LinearLayout(this)
            r.forEach { row.addView(it, llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(4f), dpi(4f), dpi(4f), dpi(4f)) }) }
            if (r.size == 1) row.addView(View(this), llp(0, 1, 1f))
            body.addView(row, llp(MATCH_PARENT, WRAP_CONTENT))
        }
        body.addView(button(t("send"), GREEN) {
            val ids = Me.format(this).map { it.id }
            val out = HashMap<String, Double>()
            ids.forEach { id -> vals[id]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }?.let { out[id] = it } }
            if (out.isEmpty()) return@button toast(t("none"))
            Me.saveRates(this, out); typed.clear()
            Thread { val ok = Me.sendNow(this); ui.post { toast(if (ok) t("sent") else "📶 " + t("net")); render() } }.start()
            render()
        }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(10f) })
        val at = Me.ratesAt(this)
        if (cur.isNotEmpty()) body.addView(small(if (Me.unsent(this)) t("pending") else t("last") + ": " + SimpleDateFormat("HH:mm", Locale.US).format(Date(at)) + "  " + t("sent"),
            if (Me.unsent(this)) RED else GREEN, 14f).apply { gravity = Gravity.CENTER; setPadding(0, dpi(10f), 0, 0) }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(link(t("upd") + "  (" + Updater.myVersionName(this) + ")", BLUE) { Updater.check(this, true) }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(18f) })
    }
}
