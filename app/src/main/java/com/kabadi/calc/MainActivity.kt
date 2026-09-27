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
    private lateinit var bar: LinearLayout
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
        // if the app ever closes by itself, keep the reason and show it on next open
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { getSharedPreferences("kabadi_crash", MODE_PRIVATE).edit().putString("e", "v" + Updater.myVersionName(this) + "\n" + android.util.Log.getStackTraceString(e)).commit() } catch (_: Exception) {}
            old?.uncaughtException(t, e)
        }
        Store.load(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); fitsSystemWindows = true }

        bar = LinearLayout(this).apply {
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
        try { Reminders.schedule(this) } catch (_: Exception) {}
        val toKhata = intent?.getBooleanExtra("khata", false) == true
        fun start() {
            bar.visibility = View.VISIBLE
            showHome()
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 9)
            if (toKhata) showKhata()
            else if (intent?.getBooleanExtra("admin", false) == true && Account.isAdmin(this)) showAdmin()
        }
        Account.refreshAdminNumber(this)
        startApp = { try { start() } catch (e: Exception) { showCrash(android.util.Log.getStackTraceString(e)) } }
        val crash = getSharedPreferences("kabadi_crash", MODE_PRIVATE).getString("e", null)
        if (crash != null) showCrash(crash)
        else try { route() } catch (e: Exception) { showCrash(android.util.Log.getStackTraceString(e)) }
    }

    /** the app closed by itself last time: show why, so it can be sent and fixed */
    private fun showCrash(err: String) {
        getSharedPreferences("kabadi_crash", MODE_PRIVATE).edit().remove("e").commit()
        bar.visibility = View.GONE
        val body = setScreen("login", "⚠", null)
        body.addView(heading("⚠ " + L.t("crash_t"), RED))
        body.addView(small(L.t("crash_h"), INK).apply { textSize = 15f; setPadding(0, 0, 0, dpi(10f)) })
        body.addView(bigButton(L.t("crash_send"), 0xFF25D366.toInt()) {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Kabadi Market Hisab error\n" + err.take(3000))
            try { startActivity(Intent.createChooser(i, "WhatsApp")) } catch (_: Exception) {}
        }, gap())
        body.addView(bigButton(L.t("crash_go"), GREEN) { try { route() } catch (e: Exception) { showCrash(android.util.Log.getStackTraceString(e)) } }, gap())
        body.addView(small(err.take(1500)).apply { textSize = 11f; setTextIsSelectable(true) })
    }

    // ================= ACCOUNT: sign up (OTP from admin on WhatsApp) / sign in / sign out =================
    private var startApp: () -> Unit = {}

    /** which screen to show when the app opens */
    private fun route() {
        when {
            !Account.exists(this) -> showSignup()
            !Account.verified(this) -> showOtp()
            !Account.signedIn(this) -> showSignin()
            Account.locked(this) -> showPin()
            else -> startApp()
        }
    }

    private fun loginScreen(title: String, onAdmin: (() -> Unit)? = null): LinearLayout {
        bar.visibility = View.GONE
        val body = setScreen("login", title, null)
        body.gravity = Gravity.CENTER_HORIZONTAL
        body.setPadding(dpi(24f), dpi(32f), dpi(24f), dpi(24f))
        body.addView(TextView(this).apply {
            text = "♻"; textSize = 54f; gravity = Gravity.CENTER; setTextColor(GREEN)
            // hidden: long press = admin code
            if (onAdmin != null) setOnLongClickListener { askAdminCode(onAdmin); true }
        }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(TextView(this).apply { text = L.t("app"); textSize = 22f; gravity = Gravity.CENTER; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(small(title).apply { gravity = Gravity.CENTER; textSize = 15f; setPadding(0, dpi(4f), 0, dpi(18f)) }, llp(MATCH_PARENT, WRAP_CONTENT))
        if (onAdmin != null) ui.post {
            body.addView(small("👑 " + L.t("adm_login"), 0xFF6A1B9A.toInt()).apply {
                gravity = Gravity.CENTER; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(24f), 0, dpi(8f))
                setOnClickListener { askAdminCode(onAdmin) }
            }, llp(MATCH_PARENT, WRAP_CONTENT))
        }
        return body
    }

    private fun askAdminCode(ok: () -> Unit) {
        val e = pinInput("• • • • • •").apply { filters = arrayOf(android.text.InputFilter.LengthFilter(6)) }
        val box = LinearLayout(this).apply { setPadding(dpi(20f), dpi(8f), dpi(20f), 0); addView(e, llp(MATCH_PARENT, WRAP_CONTENT)) }
        AlertDialog.Builder(this).setTitle("👑 Admin").setView(box)
            .setPositiveButton(L.t("acc_open")) { _, _ ->
                if (Otp.isAdminCode(e.text.toString())) { Account.makeAdmin(this); toast("👑 Admin ✓"); ok() } else toast(L.t("acc_wrong"))
            }.setNegativeButton(L.t("back"), null).show()
    }

    private fun pinInput(hint: String) = input(hint, "", true) {}.apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(android.text.InputFilter.LengthFilter(4)); textSize = 20f; gravity = Gravity.CENTER
    }

    private fun gap() = llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(10f) }

    private fun langRow(again: () -> Unit) = row(*listOf("English", "हिंदी", "ગુજરાતી").mapIndexed { i, t ->
        pill(t, L.lang == i, BLUE) { L.lang = i; Store.save(this); again() } to 1f }.toTypedArray())

    private fun link(t: String, act: () -> Unit) = small(t, BLUE).apply {
        gravity = Gravity.CENTER; textSize = 15f; setPadding(0, dpi(14f), 0, 0); setOnClickListener { act() }
    }

    /** 1) first time: name + mobile + PIN → ask OTP */
    private fun showSignup() {
        var name = Account.name(this).ifBlank { Store.owner }
        var mob = Account.mobile(this).ifBlank { Store.mobile.filter { it.isDigit() }.takeLast(10) }
        val p1 = pinInput(L.t("acc_pin")); val p2 = pinInput(L.t("acc_pin2"))
        fun save(): Boolean {
            val a = p1.text.toString()
            return when {
                name.isBlank() -> { toast(L.t("acc_name")); false }
                mob.length != 10 -> { toast(L.t("acc_bad_mobile")); false }
                a.length != 4 || a != p2.text.toString() -> { toast(L.t("acc_bad_pin")); false }
                else -> {
                    Account.create(this, name.trim(), mob, a)
                    if (Store.owner.isBlank()) Store.owner = name.trim()
                    if (Store.mobile.isBlank()) Store.mobile = mob
                    Store.save(this); hideKeyboard(); true
                }
            }
        }
        val body = loginScreen(L.t("acc_new")) { if (save()) { Account.approveAsAdmin(this); Reminders.schedule(this); startApp() } else toast(L.t("adm_fill_first")) }
        body.addView(langRow { showSignup() }, gap())
        body.addView(input(L.t("acc_name"), name, false) { name = it }, gap())
        body.addView(mobileInput("📞 " + L.t("acc_mobile"), mob) { mob = it }.apply { textSize = 18f }, gap())
        body.addView(p1, gap()); body.addView(p2, gap())
        body.addView(bigButton(L.t("otp_ask"), GREEN) { if (save()) showOtp(true) }, gap())
        body.addView(small(L.t("otp_why")).apply { gravity = Gravity.CENTER }, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun otpRequestText() = "🔐 " + L.t("app") + " – OTP request\nName: " + Account.name(this) +
        "\nMobile: " + Account.mobile(this) + "\nCode: #" + Account.device(this)

    private fun sendOtpRequest() {
        val admin = Account.adminNumber(this)
        if (admin.isNotEmpty()) whatsapp(admin, otpRequestText())
        else {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, otpRequestText())
            try { startActivity(Intent.createChooser(i, "WhatsApp")) } catch (_: Exception) {}
        }
    }

    /** 2) request goes to admin online; admin accepts → OTP comes here by itself → "Open app" */
    private fun showOtp(send: Boolean = false) {
        val body = loginScreen(L.t("otp_title")) { Account.approveAsAdmin(this); Reminders.schedule(this); startApp() }
        val info = card()
        info.addView(small(Account.name(this), INK).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
        info.addView(small("📞 " + Account.mobile(this) + "     " + L.t("otp_code") + ": #" + Account.device(this), INK).apply { textSize = 15f })
        body.addView(info, gap())
        val status = small("⏳ " + L.t("otp_wait"), ORANGE).apply { gravity = Gravity.CENTER; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(4f), 0, dpi(12f)) }
        body.addView(status, llp(MATCH_PARENT, WRAP_CONTENT))
        val e = pinInput(L.t("otp_enter")).apply { filters = arrayOf(android.text.InputFilter.LengthFilter(6)); textSize = 24f }
        body.addView(e, gap())
        val open = bigButton(L.t("otp_open"), GREEN) {
            if (Account.approve(this, e.text.toString())) { hideKeyboard(); toast(L.t("otp_ok")); startApp() }
            else toast(if (e.text.isEmpty()) L.t("otp_wait") else L.t("otp_bad"))
        }
        body.addView(open, gap())
        val mob = Account.mobile(this); val dev = Account.device(this); val nm = Account.name(this)
        fun listen() = every(5000L, { Relay.answer(mob, dev) }) { ans ->
            when {
                ans == null -> { status.text = "📶 " + L.t("otp_net"); status.setTextColor(RED); true }
                ans.isEmpty() -> { status.text = "❌ " + L.t("otp_rejected"); status.setTextColor(RED); false }
                ans.length == 6 -> {
                    e.setText(ans); status.text = "✅ " + L.t("otp_got"); status.setTextColor(GREEN)
                    open.background = round(GREEN, 10f); false
                }
                else -> { status.text = "⏳ " + L.t("otp_wait"); status.setTextColor(ORANGE); true }
            }
        }
        fun sendNow() {
            status.text = "⏳ " + L.t("otp_sending"); status.setTextColor(ORANGE)
            val g = gen
            Thread {
                val ok = Relay.sendRequest(nm, mob, dev)
                ui.post {
                    if (g != gen) return@post
                    if (ok) { status.text = "⏳ " + L.t("otp_wait"); listen() } else { status.text = "📶 " + L.t("otp_net"); status.setTextColor(RED) }
                }
            }.start()
        }
        if (send) sendNow() else listen()
        body.addView(link("🔄 " + L.t("otp_again")) { showOtp(true) }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(link("💬 " + L.t("otp_send")) { sendOtpRequest() }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(link(L.t("otp_change")) { showSignup() }, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    /** 3) approved before but signed out → PIN to sign in */
    private fun showSignin() {
        val body = loginScreen(L.t("signin"))
        body.addView(small(Account.name(this) + "\n📞 " + Account.mobile(this), INK).apply { gravity = Gravity.CENTER; textSize = 16f; setPadding(0, 0, 0, dpi(12f)) },
            llp(MATCH_PARENT, WRAP_CONTENT))
        pinBox(body) { Account.signIn(this); startApp() }
        body.addView(link(L.t("other_acc")) { showSignup() }, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    /** 4) signed in, PIN lock on */
    private fun showPin() {
        val body = loginScreen(Account.name(this).ifBlank { L.t("acc_enter") } + "\n📞 " + Account.mobile(this))
        pinBox(body) { startApp() }
    }

    private fun pinBox(body: LinearLayout, ok: () -> Unit) {
        val p = pinInput(L.t("acc_enter"))
        body.addView(p, gap())
        fun tryOpen() {
            if (Account.check(this, p.text.toString())) { hideKeyboard(); ok() }
            else { toast(L.t("acc_wrong")); p.setText("") }
        }
        p.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (s?.length == 4) tryOpen() }
        })
        body.addView(bigButton(L.t("signin_btn"), GREEN) { tryOpen() }, gap())
        // forgot PIN → new PIN needs a new OTP from admin
        body.addView(link(L.t("acc_forgot")) { showSignup() }, llp(MATCH_PARENT, WRAP_CONTENT))
        p.requestFocus()
    }

    /** set a new PIN (change, from settings) */
    private fun askNewPin(done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20f), dpi(8f), dpi(20f), 0) }
        val p1 = pinInput(L.t("acc_pin")); val p2 = pinInput(L.t("acc_pin2"))
        box.addView(p1, gap()); box.addView(p2, gap())
        AlertDialog.Builder(this).setTitle(L.t("acc_change")).setView(box)
            .setPositiveButton(L.t("save")) { _, _ ->
                val a = p1.text.toString()
                if (a.length == 4 && a == p2.text.toString()) { Account.setPin(this, a); toast(L.t("saved_ok")); done() } else toast(L.t("acc_bad_pin"))
            }.setNegativeButton(L.t("back"), null).show()
    }

    private fun signOut() {
        AlertDialog.Builder(this).setMessage(L.t("signout_q"))
            .setPositiveButton(L.t("yes")) { _, _ -> autoSave(); Store.save(this); Account.signOut(this); editing = null; showSignin() }
            .setNegativeButton(L.t("no"), null).show()
    }

    // ================= ADMIN: make OTP / reject =================
    private fun showAdmin() {
        val body = setScreen("admin", "👑 Admin – OTP", { showSettings() })
        val rc = card()
        rc.addView(heading("📥 " + L.t("adm_req")))
        val reqList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rc.addView(reqList)
        body.addView(rc, cardLp())
        fun drawReqs(list: List<Relay.Req>?) {
            reqList.removeAllViews()
            if (list == null) { reqList.addView(small("📶 " + L.t("otp_net"), RED)); return }
            if (list.isEmpty()) { reqList.addView(small(L.t("adm_req_none"))); return }
            list.forEach { r ->
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(0xFFF3E5F5.toInt(), 10f); setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f)) }
                box.addView(small(r.name.ifBlank { "—" }, INK).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
                box.addView(small("📞 " + r.mobile + "   #" + r.dev + "   •  " + Bill.dateText(r.time)))
                fun act(ok: Boolean) {
                    box.alpha = 0.4f
                    Thread { val sent = Relay.decide(this, r, ok); ui.post { toast(if (sent) (if (ok) "✅ " + r.name else "❌ " + r.name) else L.t("otp_net")); if (sent) showAdmin() else box.alpha = 1f } }.start()
                }
                box.addView(row(bigButton(L.t("adm_accept"), GREEN) { act(true) }.apply { textSize = 15f; setPadding(0, dpi(10f), 0, dpi(10f)) } to 1f,
                    bigButton(L.t("adm_no"), RED) { act(false) }.apply { textSize = 15f; setPadding(0, dpi(10f), 0, dpi(10f)) } to 1f))
                reqList.addView(box, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
            }
        }
        reqList.addView(small("⏳ …"))
        every(10000L, { Relay.pending(this) }) { drawReqs(it); true }
        body.addView(small(L.t("adm_manual")).apply { setPadding(dpi(4f), dpi(4f), 0, dpi(6f)) })
        var mob = ""; var dev = ""; var name = ""
        val otpTv = TextView(this).apply { textSize = 40f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(GREEN); letterSpacing = 0.2f }
        lateinit var mobIn: EditText; lateinit var devIn: EditText
        fun show() {
            val ok = mob.length == 10 && dev.length == 6
            otpTv.text = if (ok) Otp.code(mob, dev) else "— — —"
        }
        val c = card()
        c.addView(heading(L.t("adm_paste")))
        c.addView(input(L.t("adm_paste_h"), "", false) { t ->
            Otp.parse(t)?.let { (m, d) -> mob = m; dev = d; mobIn.setText(m); devIn.setText(d) }
            name = Regex("Name:\\s*(.+)").find(t)?.groupValues?.get(1)?.trim().orEmpty()
            show()
        }.apply { setSingleLine(false); minLines = 3; gravity = Gravity.TOP; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }, gap())
        mobIn = mobileInput("📞 " + L.t("acc_mobile"), "") { mob = it; show() }
        devIn = input(L.t("otp_code") + " (#ABC123)", "", false) { dev = it.trim().removePrefix("#").uppercase(); show() }.apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(7), android.text.InputFilter.AllCaps())
        }
        c.addView(row(mobIn to 1.3f, devIn to 1f))
        c.addView(small("OTP").apply { gravity = Gravity.CENTER; setPadding(0, dpi(10f), 0, 0) }, llp(MATCH_PARENT, WRAP_CONTENT))
        c.addView(otpTv, llp(MATCH_PARENT, WRAP_CONTENT))
        c.addView(bigButton(L.t("adm_ok"), GREEN) {
            if (mob.length != 10 || dev.length != 6) return@bigButton toast(L.t("adm_fill"))
            Account.log(this, true, name, mob, dev)
            whatsapp(mob, "✅ " + L.t("app") + "\nOTP: " + Otp.code(mob, dev) + "\n" + L.t("adm_ok_msg"))
        }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(10f) })
        c.addView(bigButton(L.t("adm_no"), RED) {
            if (mob.length != 10) return@bigButton toast(L.t("acc_bad_mobile"))
            Account.log(this, false, name, mob, dev)
            whatsapp(mob, "❌ " + L.t("app") + "\n" + L.t("adm_no_msg"))
        }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
        body.addView(c, cardLp())
        show()

        val n = card()
        n.addView(heading(L.t("adm_no_title")))
        val an = Account.adminNumber(this)
        n.addView(small(if (an.isNotEmpty()) "📞 $an" else L.t("adm_no_set"), if (an.isNotEmpty()) INK else RED).apply { textSize = 15f })
        body.addView(n, cardLp())

        val logs = Account.logs(this)
        if (logs.isNotEmpty()) {
            val lc = card()
            lc.addView(heading(L.t("adm_log")))
            logs.take(100).forEach { o ->
                val ok = o.optBoolean("ok")
                lc.addView(small((if (ok) "✅ " else "❌ ") + (o.optString("n").ifBlank { "—" }) + "  📞 " + o.optString("m") + "  •  " + Bill.dateText(o.optLong("t")),
                    if (ok) GREEN else RED).apply { textSize = 14f; setPadding(0, dpi(3f), 0, dpi(3f)) })
            }
            body.addView(lc, cardLp())
        }
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (screen == "login") return
        if (i?.getBooleanExtra("khata", false) == true) { autoSave(); showKhata() }
        else if (i?.getBooleanExtra("admin", false) == true && Account.isAdmin(this)) { autoSave(); showAdmin() }
    }

    override fun onResume() {
        super.onResume()
        Updater.autoCheck(this) // on every open (at most every 30 min)
    }

    override fun onPause() {
        super.onPause()
        autoSave()
        Store.save(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (screen == "home" || screen == "login") @Suppress("DEPRECATION") super.onBackPressed() else goBack()
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

    private var gen = 0
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    /** repeat [work] in background every [ms] while this screen is open; [show] runs on screen */
    private fun <T> every(ms: Long, work: () -> T, show: (T) -> Boolean) {
        val g = gen
        fun tick() {
            Thread {
                val r = try { work() } catch (_: Exception) { null }
                ui.post {
                    if (g != gen || isFinishing) return@post
                    @Suppress("UNCHECKED_CAST") val again = show(r as T)
                    if (again) ui.postDelayed({ if (g == gen) tick() }, ms)
                }
            }.start()
        }
        tick()
    }

    private fun setScreen(name: String, title: String, back: (() -> Unit)?): LinearLayout {
        gen++
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
        textSize = 16f
        setTextColor(INK)
        setHintTextColor(0xFF9EAAB0.toInt())
        setSingleLine()
        inputType = if (number) InputType.TYPE_CLASS_PHONE else (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        background = round(0xFFF7F9FA.toInt(), 8f, 0xFFCFD8DC.toInt())
        setPadding(dpi(8f), dpi(7f), dpi(8f), dpi(7f))
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

    /** grid of quick-add chips (small, [per] in a row) */
    private fun chips(list: List<String>, color: Int, per: Int = 3, onPick: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var row: LinearLayout? = null
        list.forEachIndexed { i, t ->
            if (i % per == 0) { row = LinearLayout(this); box.addView(row, llp(MATCH_PARENT, WRAP_CONTENT)) }
            row!!.addView(TextView(this).apply {
                text = t; textSize = 12.5f; gravity = Gravity.CENTER; maxLines = 2
                setTextColor(color); background = round(Color.WHITE, 16f, color)
                setPadding(dpi(2f), dpi(6f), dpi(2f), dpi(6f))
                setOnClickListener { onPick(i) }
            }, llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(2f), dpi(2f), dpi(2f), dpi(2f)) })
        }
        val rest = list.size % per
        if (rest != 0) repeat(per - rest) { row!!.addView(View(this), llp(0, 1, 1f).apply { setMargins(dpi(2f), 0, dpi(2f), 0) }) }
        return box
    }

    /** small on/off pill */
    private fun pill(t: String, on: Boolean, color: Int, act: () -> Unit) = TextView(this).apply {
        text = t; textSize = 13f; gravity = Gravity.CENTER; maxLines = 1
        setTextColor(if (on) Color.WHITE else color); background = round(if (on) color else Color.WHITE, 14f, color)
        setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f)); setOnClickListener { act() }
    }

    /** 10-digit mobile box (only numbers) */
    private fun mobileInput(hint: String, value: String, onChange: (String) -> Unit) = input(hint, value, true, onChange).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(android.text.InputFilter.LengthFilter(10))
        textSize = 15f
    }

    private fun row(vararg v: Pair<View, Float>): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(3f), 0, dpi(3f))
        v.forEachIndexed { i, (view, w) ->
            addView(view, if (w > 0) llp(0, WRAP_CONTENT, w).apply { if (i > 0) leftMargin = dpi(6f) }
                      else llp(WRAP_CONTENT, WRAP_CONTENT).apply { if (i > 0) leftMargin = dpi(6f) })
        }
    }

    // ================= HOME =================
    /** where "back" from a hisab goes (report keeps its filter) */
    private var editorBack: (() -> Unit)? = null

    private fun showHome() {
        editing = null
        editorBack = null
        val body = setScreen("home", L.t("app"), null)
        val dues = openDues(Store.hisabs)
        val late = dues.count { it.due != null && it.due <= Reminders.endOfToday() }
        fun tile(t: String, color: Int, a: () -> Unit) = bigButton(t, color, a).apply { textSize = 15f; setPadding(dpi(6f), dpi(16f), dpi(6f), dpi(16f)) }
        body.addView(row(tile(L.t("new_gaadi"), GREEN) { newHisab("gaadi") } to 1f, tile(L.t("new_haraji"), 0xFF6A1B9A.toInt()) { newHisab("haraji") } to 1f))
        body.addView(row(tile(L.t("khata_btn") + (if (late > 0) "  ⚠$late" else ""), if (late > 0) RED else 0xFF455A64.toInt()) { showKhata() } to 1f,
            tile(L.t("report_btn"), 0xFF00695C.toInt()) { showReport() } to 1f))
        if (dues.isNotEmpty()) body.addView(small("⬇ " + L.t("lena") + " " + money(dues.filter { it.lena }.sumOf { it.left }) +
            "     ⬆ " + L.t("dena") + " " + money(dues.filter { !it.lena }.sumOf { it.left })).apply {
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(4f), 0, dpi(8f))
        }, llp(MATCH_PARENT, WRAP_CONTENT))

        if (Account.isAdmin(this)) {
            val banner = bigButton("", 0xFF6A1B9A.toInt()) { showAdmin() }.apply { textSize = 15f; visibility = View.GONE }
            body.addView(banner, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
            every(30000L, { Relay.pending(this) }) { p ->
                banner.visibility = if (p.isNullOrEmpty()) View.GONE else View.VISIBLE
                banner.text = "🔔 " + (p?.size ?: 0) + " " + L.t("adm_req_new"); true
            }
        }

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun fill(q: String) {
            list.removeAllViews()
            val items = Store.hisabs.filter {
                q.isBlank() || it.party.contains(q, true) || it.mudiName.contains(q, true) || it.khedName.contains(q, true) || it.vehicle.contains(q, true) || it.note.contains(q, true)
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
                    text = hTitle(h); textSize = 17f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; maxLines = 1
                }, llp(0, WRAP_CONTENT, 1f))
                val m = h.munafa()
                top.addView(TextView(this).apply { text = money(m); textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(if (m >= 0) GREEN else RED) })
                c.addView(top)
                c.addView(small(listOf(if (h.type == "haraji") "🔨 " + L.t("haraji") else "",
                    if (h.role == "buyer") L.t("buyer_s") else L.t("seller_s"), h.vehicleInfo(), h.vehicle, Bill.dateText(h.time)).filter { it.isNotBlank() }.joinToString("  •  ")))
                c.setOnClickListener { showEditor(h) }
                c.setOnLongClickListener { askDelete(h) { fill(q) }; true }
                list.addView(c, cardLp())
            }
        }
        if (Store.hisabs.size > 3) body.addView(input(L.t("search"), "", false) { fill(it) }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
        body.addView(heading(L.t("saved")))
        body.addView(list)
        fill("")
        body.addView(small("⟳  " + L.t("update") + "  (v" + Updater.myVersionName(this) + ")", BLUE).apply {
            gravity = Gravity.CENTER; textSize = 14f; setPadding(0, dpi(24f), 0, dpi(8f))
            setOnClickListener { Updater.check(this@MainActivity, manual = true) }
        }, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun askDelete(h: Hisab, after: () -> Unit) {
        AlertDialog.Builder(this).setMessage(L.t("del_q") + "\n" + hTitle(h) + "  " + money(h.munafa()))
            .setPositiveButton(L.t("yes")) { _, _ -> Store.hisabs.remove(h); Store.save(this); after() }
            .setNegativeButton(L.t("no"), null).show()
    }

    private fun newHisab(type: String) {
        val now = System.currentTimeMillis()
        AlertDialog.Builder(this).setTitle(L.t("role_q"))
            .setItems(arrayOf(L.t("seller"), L.t("buyer"))) { _, w ->
                showEditor(Hisab(now, now, type = type, role = if (w == 1) "buyer" else "seller"))
            }.show()
    }

    // ================= EDITOR =================
    private lateinit var sumPrice: TextView
    private lateinit var sumKharch: TextView
    private lateinit var sumLagat: TextView
    private lateinit var sumMaal: TextView
    private lateinit var sumKg: TextView
    private lateinit var sumLtr: TextView
    private lateinit var sumResult: TextView
    private lateinit var resultBox: LinearLayout
    private var scrollTo: View? = null

    private fun hasContent(h: Hisab) = h.party.isNotBlank() || h.mudiName.isNotBlank() || h.khedName.isNotBlank() || h.vehicle.isNotBlank() || h.priceText.isNotBlank() ||
        h.kharch.isNotEmpty() || h.maal.isNotEmpty() || h.saleText.isNotBlank() || h.partners.isNotEmpty()

    private fun autoSave() {
        val h = editing ?: return
        if (!hasContent(h)) return
        if (Store.hisabs.none { it === h }) Store.hisabs.add(h)
        h.maal.forEach { if (!it.fixed && it.rateText.isNotBlank()) Store.lastRate[it.name] = it.rateText }
        lastLearned = Store.learn(h)
        h.variant.trim().let { v -> if (v.isNotEmpty() && Store.variants.none { norm(it) == norm(v) }) Store.variants.add(0, v) }
    }

    private var lastLearned: List<String> = emptyList()

    /** explicit Save: warn about the same item written twice, then save */
    private fun saveNow(h: Hisab) {
        val d = duplicates(h.maal) + duplicates(h.kharch)
        fun go() {
            autoSave(); Store.save(this)
            toast(L.t("saved_ok") + if (lastLearned.isNotEmpty()) "\n" + L.t("learned") + ": " + lastLearned.joinToString(", ") else "")
        }
        if (d.isEmpty()) return go()
        AlertDialog.Builder(this).setTitle(L.t("dup_save")).setMessage(d.joinToString("\n") { "• $it" })
            .setPositiveButton(L.t("save")) { _, _ -> go() }
            .setNegativeButton(L.t("back"), null).show()
    }

    private lateinit var splitBox: LinearLayout
    private lateinit var checkTv: TextView
    private lateinit var sumDena: TextView
    private lateinit var sumLena: TextView
    private lateinit var sumSale: TextView
    private lateinit var sumComm: TextView
    private lateinit var sumBikri: TextView
    private lateinit var partnerBox: LinearLayout

    private fun refreshTotals() {
        val h = editing ?: return
        sumPrice.text = money(h.price)
        sumKharch.text = money(h.kharchTotal())
        sumLagat.text = money(h.lagat())
        sumMaal.text = money(h.maalTotal())
        sumKg.text = plain(h.kg()) + " " + L.t("kg")
        sumLtr.text = plain(h.litre()) + " " + L.t("ltr")
        (sumLtr.parent as? View)?.visibility = if (h.maal.any { !it.fixed && it.litre }) View.VISIBLE else View.GONE
        sumDena.text = money(h.denaBaaki())
        sumLena.text = money(h.lenaBaaki())
        (sumDena.parent as? View)?.visibility = if (h.denaBaaki() != 0.0) View.VISIBLE else View.GONE
        (sumLena.parent as? View)?.visibility = if (h.lenaBaaki() != 0.0) View.VISIBLE else View.GONE
        (sumKg.parent as? View)?.visibility = if (h.kg() != 0.0) View.VISIBLE else View.GONE
        splitBox.removeAllViews()
        if (h.hasSplit()) {
            val mm = h.munafa()
            splitBox.addView(heading(L.t("split"), 0xFF00695C.toInt()).apply { textSize = 15f; setPadding(0, dpi(10f), 0, dpi(4f)) })
            listOf(Triple(h.mudiName.ifBlank { L.t("mudi") }, h.mudiPct, h.mudiMobile), Triple(h.khedName.ifBlank { L.t("khed") }, h.khedPct, h.khedMobile)).forEach { (n, p, mob) ->
                val v = mm * p / 100
                val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(3f), 0, dpi(3f)) }
                r.addView(TextView(this).apply { text = n + "  (" + plain(p) + "%)"; textSize = 15f; setTextColor(INK) }, llp(0, WRAP_CONTENT, 1f))
                r.addView(TextView(this).apply {
                    text = (if (v >= 0) L.t("profit") else L.t("loss")) + " " + money(Math.abs(v)); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (v >= 0) GREEN else RED)
                })
                if (mob.isNotBlank()) r.addView(TextView(this).apply {
                    text = "💬"; textSize = 20f; setPadding(dpi(8f), 0, 0, 0)
                    setOnClickListener { whatsapp(mob, Bill.text(h) + "\n" + n + " (" + plain(p) + "%): " + (if (v >= 0) L.t("profit") else L.t("loss")) + " " + money(Math.abs(v))) }
                })
                splitBox.addView(r)
            }
            if (Math.abs(h.mudiPct + h.khedPct - 100) > 0.001) splitBox.addView(small(L.t("not100"), RED).apply { typeface = Typeface.DEFAULT_BOLD })
        }
        val bad = h.verify()
        checkTv.text = if (bad.isEmpty()) L.t("check_ok") else L.t("check_bad") + " (" + bad.joinToString() + ")"
        checkTv.setTextColor(if (bad.isEmpty()) GREEN else RED)
        val m = h.munafa()
        sumResult.text = (if (m >= 0) L.t("profit") else L.t("loss")) + "   " + money(Math.abs(m))
        resultBox.background = round(if (m >= 0) GREEN else RED, 12f)
        if (h.type == "haraji") {
            sumSale.text = money(h.sale)
            sumBikri.text = money(h.bikri())
            sumComm.text = "- " + money(h.commission())
            partnerBox.removeAllViews()
            if (h.partners.isNotEmpty()) {
                partnerBox.addView(heading(L.t("company").removePrefix("6. ")).apply { textSize = 15f; setPadding(0, dpi(10f), 0, dpi(4f)) })
                val rows = h.partners.map { p -> Triple(p.name.ifBlank { "—" }, p.share, h.partnerLagat(p) to h.partnerMunafa(p)) } +
                    (if (h.ownerShare() > 0) listOf(Triple(L.t("owner_share"), h.ownerShare(), h.lagat() * h.ownerShare() / 100 to m * h.ownerShare() / 100)) else emptyList())
                rows.forEach { (n, sh, v) ->
                    val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(4f), 0, dpi(4f)) }
                    r.addView(TextView(this).apply { text = n + "  (" + plain(sh) + " " + L.t("pshare").substringBefore(" (") + ")"; textSize = 15f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD })
                    r.addView(small(L.t("invest") + ": " + money(v.first) + "   •   " + (if (v.second >= 0) L.t("profit") else L.t("loss")) + ": " + money(Math.abs(v.second)) +
                        "   •   " + L.t("gets") + ": " + money(v.first + v.second), if (v.second >= 0) GREEN else RED))
                    partnerBox.addView(r)
                }
                if (h.sharesTotal() > 100) partnerBox.addView(small(L.t("over100"), RED).apply { typeface = Typeface.DEFAULT_BOLD })
            }
        }
    }

    /** screen / list title: mudi malik & khedut (old hisab: party name) */
    private fun hTitle(h: Hisab) = h.party.ifBlank { listOf(h.mudiName, h.khedName).filter { it.isNotBlank() }.joinToString(" / ") }
        .ifBlank { h.vehicleInfo().ifBlank { L.t("hisab") } }

    private fun showEditor(h: Hisab) {
        editing = h
        val body = setScreen("edit", hTitle(h), { autoSave(); Store.save(this); editorBack?.invoke() ?: showHome() })

        // ---- top: Mudi malik | %   Khedut | %  (profit / loss share) ----
        val top = card()
        val lab = LinearLayout(this)
        fun hl(t: String, w: Float, end: Boolean = false) = lab.addView(small(t).apply {
            typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFF00695C.toInt()); if (end) gravity = Gravity.CENTER
        }, llp(0, WRAP_CONTENT, w).apply { leftMargin = dpi(6f) })
        hl(L.t("mudi_h"), 1.5f); hl("%", 0.55f, true); hl(L.t("khed_h"), 1.5f); hl("%", 0.55f, true)
        top.addView(lab)
        fun nameIn(v: String, set: (String) -> Unit) = input(L.t("name_q"), v, false) { set(it); titleTv.text = hTitle(editing ?: return@input) }.apply { textSize = 15f }
        fun pctIn(v: String, set: (String) -> Unit) = input("%", v, true) { set(it); refreshTotals() }.apply { gravity = Gravity.CENTER; textSize = 15f }
        top.addView(row(nameIn(h.mudiName) { h.mudiName = it } to 1.5f, pctIn(h.mudiPctText) { h.mudiPctText = it } to 0.55f,
            nameIn(h.khedName) { h.khedName = it } to 1.5f, pctIn(h.khedPctText) { h.khedPctText = it } to 0.55f))
        // date + seller / buyer in one line
        val dt = small("🕒 " + Bill.dateText(h.time) + "  ✎", BLUE).apply { textSize = 14f; setPadding(0, dpi(6f), 0, dpi(6f)) }
        dt.setOnClickListener { pickDate(h) { dt.text = "🕒 " + Bill.dateText(h.time) + "  ✎" } }
        top.addView(row(dt to 1f,
            pill(L.t("seller_s"), h.role != "buyer", BLUE) { if (h.role == "buyer") { h.role = "seller"; showEditor(h) } } to 0f,
            pill(L.t("buyer_s"), h.role == "buyer", BLUE) { if (h.role != "buyer") { h.role = "buyer"; showEditor(h) } } to 0f))
        // mobiles + note only when wanted
        val more = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawMore(open: Boolean) {
            more.removeAllViews()
            if (!open) {
                more.addView(small("＋ " + L.t("more"), BLUE).apply { setPadding(0, dpi(2f), 0, 0); setOnClickListener { drawMore(true) } })
                return
            }
            more.addView(row(mobileInput(L.t("mudi_h") + " " + L.t("cmobile"), h.mudiMobile) { h.mudiMobile = it } to 1f,
                mobileInput(L.t("khed_h") + " " + L.t("cmobile"), h.khedMobile) { h.khedMobile = it } to 1f))
            more.addView(row(input(L.t("note"), h.note, false) { h.note = it } to 1f))
        }
        drawMore(h.mudiMobile.isNotBlank() || h.khedMobile.isNotBlank() || h.note.isNotBlank())
        top.addView(more)
        body.addView(top, cardLp())

        // ---- vehicle details + price ----
        val vc = card()
        vc.addView(heading(L.t("vinfo"), 0xFF455A64.toInt()).apply { textSize = 15f })
        val brandIn = input(L.t("brand"), h.brand, false) { h.brand = it }
        val pick = pill("▾", false, 0xFF455A64.toInt()) {
            AlertDialog.Builder(this).setItems(BRANDS.toTypedArray()) { _, w -> brandIn.setText(BRANDS[w]) }.show()
        }
        vc.addView(row(brandIn to 1.2f, pick to 0f, input(L.t("variant").substringBefore(" ("), h.variant, false) { h.variant = it } to 1f))
        val ty = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(3f), 0, dpi(3f)) }
        ty.addView(small(L.t("tyres") + "  "))
        ty.addView(toggle(TYRES, TYRES.indexOf(h.tyres), 0xFF455A64.toInt()) { i -> h.tyres = TYRES[i] }, llp(0, WRAP_CONTENT, 1f))
        vc.addView(ty)
        vc.addView(row(input(L.t("year"), h.year, true) { h.year = it } to 0.7f, input(L.t("vehicle").substringBefore(" /"), h.vehicle, false) { h.vehicle = it } to 1.3f))
        vc.addView(row(input(L.t("place").substringBefore(" ("), h.place, false) { h.place = it } to 1f))
        val priceIn = input("₹", h.priceText, true) { h.priceText = it; refreshTotals() }.apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END }
        vc.addView(row(TextView(this).apply {
            text = (if (h.type == "haraji") L.t("buy_price") else L.t("price")).removePrefix("1. ")
            textSize = 15f; setTextColor(BLUE); typeface = Typeface.DEFAULT_BOLD
        } to 1f, priceIn to 1.2f).apply { setPadding(0, dpi(8f), 0, 0) })
        body.addView(vc, cardLp())

        // 2. expenses
        val kc = card()
        kc.addView(heading(L.t("kharch"), ORANGE).apply { textSize = 15f })
        val kLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawKharch() {
            kLines.removeAllViews()
            h.kharch.forEach { l -> kLines.addView(kharchRow(h, l) { drawKharch(); refreshTotals() }) }
        }
        kc.addView(kLines)
        kc.addView(chips(Store.expenses.map { it.label() } + L.t("other"), ORANGE, 3) { i ->
            val b = Store.expenses.getOrNull(i)
            if (b != null) h.kharch.indexOfFirst { sameItem(it, b.key, b.label()) }.let { at ->
                if (at >= 0) { toast(L.t("dup") + ": " + b.label()); focusAt(kLines, at); return@chips }
            }
            h.kharch.add(Line(b?.key ?: "", b?.label() ?: "", true))
            drawKharch(); refreshTotals()
            focusLast(kLines)
        }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
        drawKharch()
        body.addView(kc, cardLp())

        // 3. parts
        val mc = card()
        mc.addView(heading(if (h.role == "seller") L.t("sell") else L.t("maal"), GREEN).apply { textSize = 15f })
        val mLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawMaal() {
            mLines.removeAllViews()
            h.maal.forEach { l -> mLines.addView(maalRow(h, l) { drawMaal(); refreshTotals() }) }
        }
        mc.addView(mLines)
        mc.addView(chips(Store.parts.map { it.label() } + L.t("other"), GREEN, 4) { i ->
            val b = Store.parts.getOrNull(i)
            val name = b?.label() ?: ""
            if (b != null) h.maal.indexOfFirst { sameItem(it, b.key, name) }.let { at ->
                if (at >= 0) { toast(L.t("dup") + ": " + name); focusAt(mLines, at); return@chips }
            }
            h.maal.add(Line(b?.key ?: "", name, b?.fixed ?: false, rateText = Store.lastRate[name] ?: "", litre = b?.litre ?: false))
            drawMaal(); refreshTotals()
            focusLast(mLines)
        }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
        drawMaal()
        body.addView(mc, cardLp())

        if (h.type == "haraji") {
            // 4. sold in auction
            val sc = card()
            sc.addView(heading(L.t("sale"), GREEN))
            sc.addView(input("₹", h.saleText, true) { h.saleText = it; refreshTotals() }, llp(MATCH_PARENT, WRAP_CONTENT))
            body.addView(sc, cardLp())

            // 5. market commission: % or fixed
            val cc = card()
            cc.addView(heading(L.t("comm"), ORANGE))
            val cr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val pctB = TextView(this)
            val fixB = TextView(this)
            fun paint() {
                listOf(pctB to h.commPct, fixB to !h.commPct).forEach { (v, on) ->
                    v.setTextColor(if (on) Color.WHITE else ORANGE); v.background = round(if (on) ORANGE else Color.WHITE, 14f, ORANGE)
                }
            }
            pctB.apply { text = L.t("pct"); textSize = 15f; gravity = Gravity.CENTER; setPadding(dpi(16f), dpi(8f), dpi(16f), dpi(8f)); setOnClickListener { h.commPct = true; paint(); refreshTotals() } }
            fixB.apply { text = L.t("fixamt"); textSize = 15f; gravity = Gravity.CENTER; setPadding(dpi(16f), dpi(8f), dpi(16f), dpi(8f)); setOnClickListener { h.commPct = false; paint(); refreshTotals() } }
            paint()
            cr.addView(input("0", h.commText, true) { h.commText = it; refreshTotals() }, llp(0, WRAP_CONTENT, 1f))
            cr.addView(pctB, llp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(6f) })
            cr.addView(fixB, llp(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(4f) })
            cc.addView(cr)
            body.addView(cc, cardLp())

            // 6. company partners
            val pcd = card()
            pcd.addView(heading(L.t("company"), 0xFF6A1B9A.toInt()))
            val pLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun drawP() {
                pLines.removeAllViews()
                h.partners.forEach { p ->
                    val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(3f), 0, dpi(3f)) }
                    r.addView(input(L.t("partner"), p.name, false) { p.name = it; refreshTotals() }.apply { textSize = 15f; tag = "focus" }, llp(0, WRAP_CONTENT, 1.6f))
                    r.addView(input(L.t("pshare"), p.shareText, true) { p.shareText = it; refreshTotals() }.apply { gravity = Gravity.END },
                        llp(0, WRAP_CONTENT, 1f).apply { leftMargin = dpi(6f) })
                    r.addView(xBtn { confirmRemove { h.partners.remove(p); drawP(); refreshTotals() } })
                    pLines.addView(r)
                }
            }
            pcd.addView(pLines)
            pcd.addView(bigButton(L.t("add_partner"), 0xFF6A1B9A.toInt()) {
                h.partners.add(Partner("")); drawP(); refreshTotals(); focusLast(pLines)
            }.apply { textSize = 15f }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
            pcd.addView(small("1 " + L.t("pshare").substringBefore(" (") + " = 1%"))
            drawP()
            body.addView(pcd, cardLp())
        }

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
        sumLtr = sumRow(L.t("sum_ltr"))
        sumMaal = sumRow(L.t("sum_maal"), true, GREEN)
        sumDena = sumRow(L.t("dena_baaki"), false, RED)
        sumLena = sumRow(L.t("lena_baaki"), false, ORANGE)
        if (h.type == "haraji") {
            sumSale = sumRow(L.t("sale_s"))
            sumBikri = sumRow(L.t("bikri"), true, GREEN)
            sumComm = sumRow(L.t("comm_s"), false, ORANGE)
        }
        resultBox = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dpi(8f), dpi(14f), dpi(8f), dpi(14f)) }
        sumResult = TextView(this).apply { textSize = 22f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        resultBox.addView(sumResult)
        tc.addView(resultBox, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
        partnerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(partnerBox)
        splitBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(splitBox)
        checkTv = small("").apply { gravity = Gravity.CENTER; setPadding(0, dpi(8f), 0, 0) }
        tc.addView(checkTv)
        body.addView(tc, cardLp())
        body.addView(small(L.t("tip")).apply { gravity = Gravity.CENTER })

        refreshTotals()

        // action bar
        bottom.visibility = View.VISIBLE
        fun act(t: String, color: Int, a: () -> Unit) = bottom.addView(bigButton(t, color, a).apply { textSize = 15f },
            llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(3f), 0, dpi(3f), 0) })
        act(L.t("save"), BLUE) { saveNow(h) }
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

    private fun confirmRemove(act: () -> Unit) {
        AlertDialog.Builder(this).setMessage(L.t("del_line"))
            .setPositiveButton(L.t("yes")) { _, _ -> act() }
            .setNegativeButton(L.t("no"), null).show()
    }

    private fun focusAt(lines: LinearLayout, i: Int) {
        val v = lines.getChildAt(i) ?: return
        v.post {
            (v.findViewWithTag<View>("focus") ?: v).requestFocus()
            (content.getChildAt(0) as? ScrollView)?.let { sv ->
                val loc = IntArray(2); v.getLocationInWindow(loc)
                val sl = IntArray(2); sv.getLocationInWindow(sl)
                sv.smoothScrollBy(0, loc[1] - sl[1] - dpi(120f))
            }
        }
    }

    private fun xBtn(act: () -> Unit) = TextView(this).apply {
        text = "✕"; textSize = 18f; setTextColor(RED); gravity = Gravity.CENTER
        setPadding(dpi(10f), dpi(4f), dpi(4f), dpi(4f)); setOnClickListener { act() }
    }

    private fun kharchRow(h: Hisab, l: Line, redraw: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(0xFFFFFAF3.toInt(), 10f, 0xFFFFE0B2.toInt())
            setPadding(dpi(6f), dpi(4f), dpi(4f), dpi(4f))
        }
        box.addView(row(input(L.t("name_q"), l.name, false) { l.name = it }.apply { textSize = 15f } to 1.4f,
            input("₹", l.amountText, true) { l.amountText = it; refreshTotals() }.apply { tag = "focus"; gravity = Gravity.END } to 1f,
            pill(L.t("udhaar"), l.udhaar, ORANGE) { l.pay = if (l.udhaar) "rokad" else "udhaar"; redraw(); refreshTotals() } to 0f,
            xBtn { confirmRemove { h.kharch.remove(l); redraw() } } to 0f))
        if (l.udhaar) {
            box.addView(row(pill(L.t("baaki"), !l.paid, RED) { l.paid = false; redraw() } to 0f,
                pill(L.t("chukaya"), l.paid, GREEN) { l.paid = true; redraw() } to 0f, View(this) to 1f))
            if (!l.paid) kistBlock(l, box, false, redraw)
        }
        return wrap(box)
    }

    /** single-choice pill row; returns the view */
    private fun toggle(opts: List<String>, sel: Int, color: Int, onPick: (Int) -> Unit): View {
        val r = LinearLayout(this)
        var cur = sel
        val views = mutableListOf<TextView>()
        fun paint() = views.forEachIndexed { i, v ->
            v.setTextColor(if (i == cur) Color.WHITE else color); v.background = round(if (i == cur) color else Color.WHITE, 14f, color)
        }
        opts.forEachIndexed { i, o ->
            val v = TextView(this).apply {
                text = o; textSize = 13.5f; gravity = Gravity.CENTER; maxLines = 1
                setPadding(dpi(4f), dpi(7f), dpi(4f), dpi(7f))
                setOnClickListener { cur = i; paint(); onPick(i) }
            }
            views.add(v)
            r.addView(v, llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(2f), 0, dpi(2f), 0) })
        }
        paint()
        return r
    }

    /** installments on a credit line: list, + Kist, received / left */
    private fun kistBlock(l: Line, box: LinearLayout, lena: Boolean, redraw: () -> Unit) {
        val k = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(Color.WHITE, 8f, 0xFFB0BEC5.toInt())
            setPadding(dpi(8f), dpi(6f), dpi(8f), dpi(6f))
        }
        l.pays.forEachIndexed { i, p ->
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(small("${i + 1}. " + Bill.dateText(p.time).substringBefore("  ")), llp(0, WRAP_CONTENT, 1f))
            r.addView(TextView(this).apply { text = money(p.amount); textSize = 15f; setTextColor(INK) })
            r.addView(xBtn { confirmRemove { l.pays.remove(p); redraw(); refreshTotals() } })
            k.addView(r)
        }
        val sumRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(4f), 0, 0) }
        sumRow.addView(small((if (lena) L.t("got") else L.t("gave")) + ": " + money(l.received()) + "   •   " + L.t("left") + ": " + money(l.remaining()),
            if (l.remaining() > 0.004) RED else GREEN).apply { typeface = Typeface.DEFAULT_BOLD }, llp(0, WRAP_CONTENT, 1f))
        sumRow.addView(TextView(this).apply {
            text = L.t("kist"); textSize = 14f; setTextColor(Color.WHITE); background = round(BLUE, 14f)
            setPadding(dpi(12f), dpi(6f), dpi(12f), dpi(6f))
            setOnClickListener { askKist(l) { redraw(); refreshTotals() } }
        })
        k.addView(sumRow)
        box.addView(k, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
    }

    private fun askKist(l: Line, done: () -> Unit) {
        val et = input(L.t("kist_amt"), plain(l.remaining()), true) {}
        val pad = LinearLayout(this).apply { setPadding(dpi(20f), dpi(8f), dpi(20f), 0); addView(et, llp(MATCH_PARENT, WRAP_CONTENT)) }
        AlertDialog.Builder(this).setTitle(l.name + "  •  " + L.t("left") + " " + money(l.remaining())).setView(pad)
            .setPositiveButton(L.t("add")) { _, _ ->
                val a = evalExpr(et.text.toString())
                if (a.isFinite() && a > 0) { l.pays.add(Pay(System.currentTimeMillis(), et.text.toString())); done() }
            }
            .setNegativeButton(L.t("no"), null).show()
    }

    // ================= CREDIT BOOK =================
    private fun showKhata() {
        val from = editing
        autoSave()
        editing = null
        val body = setScreen("khata", L.t("khata"), { if (from != null) showEditor(from) else showHome() })
        val dues = openDues(Store.hisabs)
        val tot = card()
        tot.addView(TextView(this).apply { text = "⬇ " + L.t("lena") + ": " + money(dues.filter { it.lena }.sumOf { it.left }); textSize = 18f; setTextColor(GREEN); typeface = Typeface.DEFAULT_BOLD })
        tot.addView(TextView(this).apply { text = "⬆ " + L.t("dena") + ": " + money(dues.filter { !it.lena }.sumOf { it.left }); textSize = 18f; setTextColor(RED); typeface = Typeface.DEFAULT_BOLD })
        body.addView(tot, cardLp())
        if (dues.isNotEmpty()) body.addView(bigButton(L.t("send_list"), GREEN) {
            val sb = StringBuilder(Store.owner.ifBlank { L.t("app") }).append("\n").append(L.t("khata")).append("\n\n")
            dues.forEach { d ->
                sb.append(if (d.lena) "⬇ " else "⬆ ").append(if (d.lena) d.l.cName.ifBlank { hTitle(d.h) } else d.l.name)
                    .append(" • ").append(d.l.name).append(" • ").append(money(d.left))
                d.due?.let { sb.append(" • ").append(L.t("due")).append(" ").append(Bill.dateText(it).substringBefore("  ")) }
                sb.append("\n")
            }
            sb.append("\n⬇ ").append(L.t("lena")).append(": ").append(money(dues.filter { it.lena }.sumOf { it.left }))
            sb.append("\n⬆ ").append(L.t("dena")).append(": ").append(money(dues.filter { !it.lena }.sumOf { it.left }))
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString())
            try { startActivity(Intent.createChooser(i, L.t("share"))) } catch (_: Exception) {}
        }.apply { textSize = 15f }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(10f) })
        if (dues.isEmpty()) body.addView(small(L.t("no_due")).apply { gravity = Gravity.CENTER; setPadding(0, dpi(20f), 0, 0) })
        val today = Reminders.endOfToday()
        dues.forEach { d ->
            val c = card()
            val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            top.addView(TextView(this).apply {
                text = (if (d.lena) "⬇ " else "⬆ ") + (if (d.lena) d.l.cName.ifBlank { hTitle(d.h) } else d.l.name).ifBlank { "—" }
                textSize = 17f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; maxLines = 1
            }, llp(0, WRAP_CONTENT, 1f))
            top.addView(TextView(this).apply { text = money(d.left); textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(if (d.lena) GREEN else RED) })
            c.addView(top)
            val status = when {
                d.due == null -> ""
                d.due < today - 86400000L + 1 -> "⚠ " + L.t("overdue") + " (" + Bill.dateText(d.due).substringBefore("  ") + ")"
                d.due <= today -> "⚠ " + L.t("today")
                else -> L.t("due") + ": " + Bill.dateText(d.due).substringBefore("  ")
            }
            c.addView(small(listOf(d.l.name, hTitle(d.h), status).filter { it.isNotBlank() }.joinToString("  •  "),
                if (status.startsWith("⚠")) RED else MUTED))
            val acts = LinearLayout(this).apply { setPadding(0, dpi(6f), 0, 0) }
            fun act(t: String, color: Int, a: () -> Unit) = acts.addView(TextView(this).apply {
                text = t; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = round(color, 14f)
                setPadding(dpi(8f), dpi(7f), dpi(8f), dpi(7f)); setOnClickListener { a() }
            }, llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(3f), 0, dpi(3f), 0) })
            act(L.t("kist"), BLUE) { askKist(d.l) { Store.save(this); showKhata() } }
            if (d.lena && d.l.cMobile.isNotBlank()) act(L.t("remind"), GREEN) { whatsapp(d.l.cMobile, reminderText(d)) }
            if (d.lena && d.l.gMobile.isNotBlank()) act("💬 " + L.t("gname").substringBefore(" ("), 0xFF6A1B9A.toInt()) { whatsapp(d.l.gMobile, reminderText(d)) }
            act(L.t("open"), 0xFF78909C.toInt()) { showEditor(d.h) }
            c.addView(acts)
            body.addView(c, cardLp())
        }
    }

    private fun reminderText(d: Due): String {
        val sb = StringBuilder()
        sb.append(Store.owner.ifBlank { L.t("app") }).append("\n")
        sb.append(d.l.name).append(": ").append(money(d.l.value())).append("\n")
        if (d.l.pays.isNotEmpty()) sb.append(L.t("got")).append(": ").append(money(d.l.received())).append("\n")
        sb.append("*").append(L.t("left")).append(": ").append(money(d.left)).append("*\n")
        d.due?.let { sb.append(L.t("due")).append(": ").append(Bill.dateText(it).substringBefore("  ")).append("\n") }
        if (Store.mobile.isNotBlank()) sb.append(Store.mobile)
        return sb.toString()
    }

    private fun whatsapp(num: String, text: String) {
        val d = num.filter { it.isDigit() }.let { if (it.length == 10) "91$it" else it }
        if (d.isEmpty()) return toast(L.t("cmobile") + " ?")
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + d + "?text=" + Uri.encode(text)))) } catch (_: Exception) {}
    }

    private fun maalRow(h: Hisab, l: Line, redraw: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(0xFFF7FAF7.toInt(), 10f, 0xFFC8E6C9.toInt())
            setPadding(dpi(6f), dpi(4f), dpi(4f), dpi(6f))
        }
        // name | Kg / Litre / Fix | ✕
        val mode = when { l.fixed -> L.t("fix"); l.litre -> L.t("ltr"); else -> L.t("kg") }
        box.addView(row(input(L.t("name_q"), l.name, false) { l.name = it }.apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD } to 1f,
            pill("$mode ▾", true, GREEN) {
                when { l.fixed -> { l.fixed = false; l.litre = false }; l.litre -> l.fixed = true; else -> l.litre = true }
                redraw(); refreshTotals()
            } to 0f,
            xBtn { confirmRemove { h.maal.remove(l); redraw() } } to 0f))
        if (l.fixed) {
            box.addView(row(input(L.t("amount"), l.amountText, true) { l.amountText = it; refreshTotals() }.apply { tag = "focus"; gravity = Gravity.END } to 1f))
        } else {
            val amt = TextView(this).apply { textSize = 16f; setTextColor(GREEN); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END }
            fun upd() { amt.text = "= " + money(l.value()) }
            val unit = if (l.litre) L.t("ltr") else L.t("kg")
            val rateHint = if (l.litre) L.t("rate_l") else L.t("rate")
            box.addView(row(input(unit, l.kgText, true) { l.kgText = it; upd(); refreshTotals() }.apply { tag = "focus"; gravity = Gravity.END } to 1f,
                small("×") to 0f,
                input(rateHint, l.rateText, true) { l.rateText = it; upd(); refreshTotals() }.apply { gravity = Gravity.END } to 1f,
                amt to 1.2f))
            upd()
        }
        if (h.role == "seller") sellerBlock(h, l, box, redraw)
        return wrap(box)
    }

    /** seller: who took this item (name, mobile), cash / credit, credit days, installments; auction: guarantor + shop */
    private fun sellerBlock(h: Hisab, l: Line, box: LinearLayout, redraw: () -> Unit) {
        val who = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(2f), 0, 0) }
        who.addView(row(input("👤 " + L.t("cname"), l.cName, false) { l.cName = it }.apply { textSize = 15f } to 1.2f,
            mobileInput("📞 " + L.t("cmobile"), l.cMobile) { l.cMobile = it } to 1f,
            pill(L.t("udhaar"), l.udhaar, ORANGE) { l.pay = if (l.udhaar) "rokad" else "udhaar"; redraw(); refreshTotals() } to 0f))
        if (l.udhaar) {
            who.addView(row(small(L.t("days")) to 1f, input("30", l.daysText, true) { l.daysText = it }.apply { gravity = Gravity.CENTER } to 0.6f))
            kistBlock(l, who, true, redraw)
        }
        if (h.type == "haraji") {
            who.addView(row(input(L.t("gname"), l.gName, false) { l.gName = it }.apply { textSize = 15f } to 1.2f,
                mobileInput(L.t("gmobile"), l.gMobile) { l.gMobile = it } to 1f))
            who.addView(row(input(L.t("shop"), l.shop, false) { l.shop = it }.apply { textSize = 15f } to 1f))
        }
        box.addView(who)
    }

    private fun sellMessage(h: Hisab, l: Line): String {
        val sb = StringBuilder()
        sb.append(Store.owner.ifBlank { L.t("app") }).append("\n")
        sb.append(Bill.dateText(h.time)).append("\n")
        if (h.vehicleInfo().isNotBlank()) sb.append(h.vehicleInfo()).append("\n")
        sb.append(l.name).append(": ")
        if (!l.fixed) sb.append(plain(l.kg)).append(if (l.litre) " litre × ₹" else " kg × ₹").append(plain(l.rate)).append(" = ")
        sb.append(money(l.value())).append("\n")
        if (l.udhaar) {
            sb.append(L.t("udhaar"))
            if (l.daysText.isNotBlank()) sb.append(" • ").append(L.t("due")).append(": ").append(Bill.dueDate(h.time, l.daysText))
            sb.append("\n")
        } else sb.append(L.t("rokad")).append("\n")
        return sb.toString()
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
    // ================= REPORT =================
    /** report filter stays while the app is open (so back from a hisab keeps it) */
    private val rf = RFilter()

    private fun showReport() {
        autoSave()
        editing = null
        editorBack = { showReport() }
        val body = setScreen("report", L.t("report"), { editorBack = null; showHome() })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var text = ""
        val monthNames = java.text.DateFormatSymbols(java.util.Locale.US).months.take(12)

        // ---- filter card ----
        val fc = card()
        val pills = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lateinit var fill: () -> Unit
        fun choose(title: String, items: List<String>, pick: (Int) -> Unit) {
            AlertDialog.Builder(this).setTitle(title).setItems((listOf(L.t("f_all")) + items).toTypedArray()) { _, w -> pick(w - 1); fill() }.show()
        }
        fun fbtn(label: String, value: String, act: () -> Unit) = pill(label + ": " + value.ifBlank { L.t("f_all") } + " ▾", value.isNotBlank(), 0xFF00695C.toInt(), act)
            .apply { textSize = 13.5f; setPadding(dpi(6f), dpi(9f), dpi(6f), dpi(9f)) }
        fun drawPills() {
            pills.removeAllViews()
            val people = peopleNames(Store.hisabs)
            val ys = years(Store.hisabs)
            val brands = (Store.hisabs.map { it.brand.trim() }.filter { it.isNotEmpty() } + BRANDS).distinctBy { norm(it) }
            val tyres = (TYRES + Store.hisabs.map { it.tyres.trim() }.filter { it.isNotEmpty() }).distinct()
            val types = listOf("gaadi", "haraji")
            pills.addView(row(
                fbtn(L.t("f_mudi"), rf.mudi) { choose(L.t("f_mudi"), people) { rf.mudi = if (it < 0) "" else people[it] } } to 1f,
                fbtn(L.t("f_brand"), rf.brand) { choose(L.t("f_brand"), brands) { rf.brand = if (it < 0) "" else brands[it] } } to 1f))
            pills.addView(row(
                fbtn(L.t("f_year"), if (rf.year == 0) "" else rf.year.toString()) { choose(L.t("f_year"), ys.map { it.toString() }) { rf.year = if (it < 0) 0 else ys[it] } } to 1f,
                fbtn(L.t("f_month"), if (rf.month == 0) "" else monthNames[rf.month - 1].take(3)) { choose(L.t("f_month"), monthNames) { rf.month = it + 1 } } to 1f))
            pills.addView(row(
                fbtn(L.t("f_tyre"), rf.tyres) { choose(L.t("f_tyre"), tyres.map { it + " " + L.t("tyre_s") }) { rf.tyres = if (it < 0) "" else tyres[it] } } to 1f,
                fbtn(L.t("f_type"), when (rf.type) { "gaadi" -> L.t("gaadi_s"); "haraji" -> L.t("haraji"); else -> "" }) {
                    choose(L.t("f_type"), listOf(L.t("gaadi_s"), L.t("haraji"))) { rf.type = if (it < 0) "" else types[it] } } to 1f))
            if (rf.active()) pills.addView(small(L.t("f_clear"), RED).apply {
                textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dpi(8f), 0, dpi(2f))
                setOnClickListener { rf.who = ""; rf.mudi = ""; rf.year = 0; rf.month = 0; rf.brand = ""; rf.tyres = ""; rf.type = ""; showReport() }
            }, llp(MATCH_PARENT, WRAP_CONTENT))
        }
        fc.addView(input("🔍 " + L.t("f_search"), rf.who, false) { rf.who = it; fill() }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(4f) })
        fc.addView(pills)
        body.addView(fc, cardLp())

        fill = {
            drawPills()
            list.removeAllViews()
            val items = Store.hisabs.filter { rf.matches(it) }.sortedByDescending { it.time }
            val months = monthly(Store.hisabs, rf)
            val fdesc = listOf(rf.mudi, rf.brand, if (rf.tyres.isNotBlank()) rf.tyres + " " + L.t("tyre_s") else "",
                if (rf.month != 0) monthNames[rf.month - 1] else "", if (rf.year != 0) rf.year.toString() else "", rf.who).filter { it.isNotBlank() }.joinToString(" • ")
            val sb = StringBuilder(Store.owner.ifBlank { L.t("app") }).append("\n").append(L.t("report")).append(if (fdesc.isNotBlank()) " – $fdesc" else "").append("\n")
            fun monthCard(title: String, m: MonthSum, strong: Boolean) {
                val c = card()
                if (strong) c.background = round(0xFFE0F2F1.toInt(), 12f, 0xFF80CBC4.toInt())
                val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                top.addView(TextView(this).apply { text = title; textSize = if (strong) 18f else 16f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD }, llp(0, WRAP_CONTENT, 1f))
                top.addView(TextView(this).apply {
                    text = (if (m.munafa >= 0) L.t("profit") else L.t("loss")) + " " + money(Math.abs(m.munafa)); textSize = if (strong) 17f else 15f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (m.munafa >= 0) GREEN else RED)
                })
                c.addView(top)
                val lines = listOfNotNull(
                    L.t("vehicles") + ": " + m.count, L.t("kharidi") + ": " + money(m.kharidi), L.t("sum_kharch") + ": " + money(m.kharch),
                    L.t("bikri") + ": " + money(m.bikri) + (if (m.commission > 0) "   (" + L.t("comm_s") + " " + money(m.commission) + ")" else ""),
                    if (m.kg > 0) L.t("sum_kg") + ": " + plain(m.kg) + " " + L.t("kg") else null
                )
                lines.forEach { c.addView(small(it)) }
                list.addView(c, cardLp())
                sb.append("\n*").append(title).append("*\n")
                lines.forEach { sb.append(it).append("\n") }
                sb.append(if (m.munafa >= 0) L.t("profit") else L.t("loss")).append(": ").append(money(Math.abs(m.munafa))).append("\n")
            }
            if (items.isEmpty()) list.addView(small(L.t("none")).apply { gravity = Gravity.CENTER; setPadding(0, dpi(20f), 0, 0) }, llp(MATCH_PARENT, WRAP_CONTENT))
            else {
                val all = MonthSum(L.t("all_months"))
                months.forEach { all.count += it.count; all.kharidi += it.kharidi; all.kharch += it.kharch; all.bikri += it.bikri; all.commission += it.commission; all.munafa += it.munafa; all.kg += it.kg }
                monthCard(if (fdesc.isNotBlank()) fdesc else L.t("all_months"), all, true)
                if (months.size > 1) months.forEach { m -> monthCard(monthNames[m.key.substring(5).toInt() - 1] + " " + m.key.substring(0, 4), m, false) }
                // every vehicle that matches — tap to open
                list.addView(heading(L.t("f_list") + " (" + items.size + ")").apply { setPadding(dpi(4f), dpi(6f), 0, dpi(6f)) })
                sb.append("\n*").append(L.t("f_list")).append("*\n")
                items.forEach { h ->
                    val c = card().apply { setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f)) }
                    val m = h.munafa()
                    val name = listOf(h.vehicleInfo(), h.vehicle).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { hTitle(h).ifBlank { L.t("gaadi_s") } }
                    c.addView(row(TextView(this).apply { text = name; textSize = 15f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; maxLines = 2 } to 1f,
                        TextView(this).apply { text = money(m); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(if (m >= 0) GREEN else RED) } to 0f))
                    val sub = listOf(listOf(h.mudiName, h.khedName).filter { it.isNotBlank() }.joinToString(" / "), h.place, Bill.dateText(h.time)).filter { it.isNotBlank() }.joinToString("  •  ")
                    c.addView(small(sub))
                    c.setOnClickListener { showEditor(h) }
                    list.addView(c, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(6f) })
                    sb.append("• ").append(name).append(" (").append(Bill.dateText(h.time)).append("): ").append(money(m)).append("\n")
                }
            }
            text = sb.toString()
        }
        body.addView(bigButton(L.t("share"), GREEN) {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            try { startActivity(Intent.createChooser(i, L.t("share"))) } catch (_: Exception) {}
        }.apply { textSize = 15f }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(10f) })
        body.addView(list)
        fill()
    }

    // ================= BACKUP =================
    private fun makeBackup() {
        autoSave(); Store.save(this)
        if (needsStoragePermission()) return
        val name = "KabadiCalc_backup_" + java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date()) + ".json"
        val data = Store.toJson().toString(1).toByteArray()
        try {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 29) {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/KabadiCalc")
                }
                contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)?.also { u ->
                    contentResolver.openOutputStream(u)?.use { it.write(data) }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "KabadiCalc")
                dir.mkdirs(); java.io.File(dir, name).writeBytes(data); null
            }
            getSharedPreferences("kabadi_calc", MODE_PRIVATE).edit().putLong("lastBackup", System.currentTimeMillis()).apply()
            toast(L.t("backup_ok"))
            if (uri != null) shareUri(uri, "application/json")
        } catch (e: Exception) { toast("Backup ✕ " + e.message) }
    }

    private fun pickBackup() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try { startActivityForResult(i, 21) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(req, res, data)
        if (req != 21 || res != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val txt = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
            val n = Store.restore(txt)
            Store.save(this)
            toast(L.t("restored") + ": " + n)
            showHome()
        } catch (e: Exception) { toast(L.t("bad_file")) }
    }

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

        if (Account.exists(this)) {
            val ac = card()
            ac.addView(heading(L.t("acc_sec")))
            ac.addView(small(Account.name(this) + "   📞 " + Account.mobile(this) + (if (Account.isAdmin(this)) "   👑" else ""), INK).apply { textSize = 15f; setPadding(0, 0, 0, dpi(8f)) })
            fun sp(t: String, on: Boolean, col: Int, top: Boolean, a: () -> Unit) = ac.addView(pill(t, on, col, a).apply { textSize = 15f; setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f)) },
                llp(MATCH_PARENT, WRAP_CONTENT).apply { if (top) topMargin = dpi(8f) })
            if (Account.isAdmin(this)) sp("👑 " + L.t("adm_btn"), true, 0xFF6A1B9A.toInt(), false) { showAdmin() }
            val on = Account.locked(this)
            sp(L.t(if (on) "acc_lock_on" else "acc_lock_off"), on, GREEN, Account.isAdmin(this)) { Account.setLock(this, !on); showSettings() }
            sp(L.t("acc_change"), false, BLUE, true) { askNewPin {} }
            sp(L.t("signout"), false, RED, true) { signOut() }
            body.addView(ac, cardLp())
        }

        val lc = card()
        lc.addView(heading(L.t("lists")))
        lc.addView(listEditor(L.t("exp_list"), { Store.expenses }, { Store.expenses = it }, { Store.defaultExpenses() }, true))
        lc.addView(listEditor(L.t("parts_list"), { Store.parts }, { Store.parts = it }, { Store.defaultParts() }, false))
        body.addView(lc, cardLp())

        val u = card()
        u.addView(bigButton(L.t("update") + "  (v" + Updater.myVersionName(this) + ")", BLUE) { Updater.check(this, manual = true) })
        val bk = card()
        bk.addView(heading(L.t("backup"), 0xFF455A64.toInt()))
        val last = getSharedPreferences("kabadi_calc", MODE_PRIVATE).getLong("lastBackup", 0)
        bk.addView(small(L.t("last_backup") + ": " + (if (last > 0) Bill.dateText(last) else "—"), if (last > 0) MUTED else RED))
        bk.addView(bigButton(L.t("backup_make"), GREEN) { makeBackup() }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
        bk.addView(bigButton(L.t("backup_load"), 0xFF78909C.toInt()) { pickBackup() }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
        body.addView(bk, cardLp())
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
                    text = b.label() + (if (!allFixed) "   (" + (if (b.fixed) L.t("fix") else if (b.litre) L.t("ltrrate") else L.t("kgrate")) + ")" else "")
                    textSize = 15f; setTextColor(INK); setPadding(0, dpi(6f), 0, dpi(6f))
                    if (!allFixed) setOnClickListener { l[i] = b.nextMode(); Store.save(this@MainActivity); draw() }
                }, llp(0, WRAP_CONTENT, 1f))
                if (i > 0) r.addView(TextView(this).apply {
                    text = "▲"; textSize = 16f; setTextColor(BLUE); setPadding(dpi(10f), 0, dpi(10f), 0)
                    setOnClickListener { val x = l.removeAt(i); l.add(i - 1, x); Store.save(this@MainActivity); draw() }
                })
                r.addView(xBtn { Store.remove(!allFixed, i); Store.save(this@MainActivity); draw() })
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
                text = "↺ " + L.t("restore"); textSize = 14f; setTextColor(BLUE); setPadding(0, dpi(8f), 0, 0)
                setOnClickListener { Store.restore(!allFixed); Store.save(this@MainActivity); draw() }
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
