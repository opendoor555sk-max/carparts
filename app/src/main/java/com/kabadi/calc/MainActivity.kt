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
            if (LOGIN_ON) Thread { try { Relay.ping(this) } catch (_: Exception) {} }.start()
            if (LOGIN_ON && !Account.isAdmin(this)) Thread {
                val r = try { Unlock.check(this) } catch (_: Exception) { emptyList() }
                if (r.isNotEmpty()) ui.post {
                    Store.save(this)
                    toast(r.joinToString("\n") { (_, ok) -> if (ok) "✅ " + L.t("unl_yes") else "❌ " + L.t("unl_no") })
                    if (screen == "home") showHome()
                }
            }.start()
            Thread {
                try { Share.republish(this) } catch (_: Exception) {}
                val n = try { Share.fetch(this) } catch (_: Exception) { 0 }
                if (n > 0) ui.post { Store.save(this); toast("👁 $n " + L.t("sh_new")); if (screen == "home") showHome() }
            }.start()
            if (LOGIN_ON && Account.REQUIRED && !Account.isAdmin(this) && Account.verified(this)) {
                val m = Account.mobile(this); val d = Account.device(this)
                Thread {
                    if (Relay.status(m, d) == "block") ui.post { Account.unverify(this); toast("🚫 " + L.t("blocked")); route() }
                }.start()
            }
            autoBackup()
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 9)
            if (intent?.getBooleanExtra("remind", false) == true) showReminders()
            else if (toKhata) showKhata()
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
    /** login / OTP switched off for now: the app opens straight away. Set true to bring it back. */
    private val LOGIN_ON = Account.ENABLED

    /** this phone's own contact number (10 digits, "" if not given yet) */
    private fun myMobile() = Account.mobile(this).ifBlank { Store.mobile }.filter { it.isDigit() }.takeLast(10)

    private fun route() {
        if (!LOGIN_ON || !Account.REQUIRED) {
            // the phone's own contact number is required, so it shows in the admin's user list
            if (LOGIN_ON && !Account.isAdmin(this) && (myMobile().length != 10 || Account.name(this).ifBlank { Store.owner }.isBlank())) return showNeedMobile()
            return startApp()
        }
        // admin phone: always opens straight away (no sign in / sign out)
        if (Account.isAdmin(this)) { if (!Account.verified(this) && Account.exists(this)) Account.approveAsAdmin(this); return startApp() }
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

    /** first open: user name + own contact number (required), no OTP */
    private fun showNeedMobile() {
        var name = Account.name(this).ifBlank { Store.owner }
        var mob = myMobile()
        val body = loginScreen(L.t("nm_title"))
        body.addView(small(L.t("nm_sub")).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dpi(12f)) }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(langRow { showNeedMobile() }, gap())
        body.addView(small(L.t("u_name").uppercase() + " *", INK).apply { typeface = Typeface.DEFAULT_BOLD })
        body.addView(input(L.t("u_name"), name, false) { name = it }.apply { textSize = 17f }, gap())
        body.addView(small(L.t("u_mobile").uppercase() + " *", INK).apply { typeface = Typeface.DEFAULT_BOLD })
        body.addView(mobileInput("98xxxxxxxx", mob) { mob = it }.apply { textSize = 18f }, gap())
        body.addView(bigButton("✅ " + L.t("nm_go"), GREEN) {
            val m = mob.filter { it.isDigit() }
            when {
                name.isBlank() -> toast(L.t("u_name"))
                m.length != 10 -> toast(L.t("acc_bad_mobile"))
                else -> {
                    Account.create(this, name.trim(), m, "")
                    if (Store.owner.isBlank()) Store.owner = name.trim()
                    if (Store.mobile.isBlank()) Store.mobile = m
                    Store.save(this); hideKeyboard()
                    // tell the admin right away (not after 6 hours)
                    getSharedPreferences("kabadi_acct", MODE_PRIVATE).edit().putLong("ping", 0L).apply()
                    startApp()
                }
            }
        }, gap())
    }

    /** 1) Create new account: user name + contact number → request to the owner on WhatsApp */
    private fun showSignup(prefill: String = "") {
        var name = Account.name(this).ifBlank { Store.owner }
        var mob = prefill.ifBlank { Account.mobile(this).ifBlank { Store.mobile.filter { it.isDigit() }.takeLast(10) } }
        val body = loginScreen(L.t("su_title")) {
            Account.create(this, name.trim().ifBlank { Store.owner.ifBlank { "Admin" } }, mob, ""); Account.approveAsAdmin(this); Reminders.schedule(this); startApp() }
        body.addView(small(L.t("su_sub")).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dpi(12f)) }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(langRow { showSignup(prefill) }, gap())
        body.addView(small(L.t("u_name").uppercase(), INK).apply { typeface = Typeface.DEFAULT_BOLD })
        body.addView(input(L.t("u_name"), name, false) { name = it }.apply { textSize = 17f }, gap())
        body.addView(small(L.t("u_mobile").uppercase(), INK).apply { typeface = Typeface.DEFAULT_BOLD })
        body.addView(mobileInput("98xxxxxxxx", mob) { mob = it }.apply { textSize = 18f }, gap())
        body.addView(bigButton("💬 " + L.t("su_send"), 0xFF25D366.toInt()) {
            when {
                name.isBlank() -> toast(L.t("u_name"))
                mob.length != 10 -> toast(L.t("acc_bad_mobile"))
                else -> {
                    Account.create(this, name.trim(), mob, "")
                    if (Store.owner.isBlank()) Store.owner = name.trim()
                    if (Store.mobile.isBlank()) Store.mobile = mob
                    Store.save(this); hideKeyboard()
                    sendOtpRequest()
                    showOtp(true)
                }
            }
        }, gap())
        body.addView(link(L.t("have_acc")) { showSignin() }, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun otpRequestText() = "🔐 " + L.t("app") + " – OTP request\nName: " + Account.name(this) +
        "\nMobile: " + Account.mobile(this) + "\nCode: #" + Account.device(this)

    private fun sendOtpRequest() {
        val admin = Account.adminNumber(this)
        if (admin.isNotEmpty()) whatsapp(admin, otpRequestText())
        else {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.whatsapp").putExtra(Intent.EXTRA_TEXT, otpRequestText())
            try { startActivity(i) } catch (_: Exception) {
                try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, otpRequestText()), "WhatsApp")) } catch (_: Exception) {}
            }
        }
    }

    /** 2) Waiting for OTP from the owner (comes by itself when he approves, or type it) */
    private fun showOtp(send: Boolean = false) {
        val body = loginScreen(L.t("su_title")) { Account.approveAsAdmin(this); Reminders.schedule(this); startApp() }
        val info = card().apply { background = round(0xFFE8F5E9.toInt(), 12f, 0xFFA5D6A7.toInt()) }
        info.addView(small(Account.name(this) + "  " + Account.mobile(this), INK).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(info, gap())
        val status = small("⏳ " + L.t("wait_t"), ORANGE).apply { gravity = Gravity.CENTER; textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
        body.addView(status, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(small(L.t("wait_h")).apply { gravity = Gravity.CENTER; setPadding(0, dpi(2f), 0, dpi(12f)) }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(small("OTP", INK).apply { typeface = Typeface.DEFAULT_BOLD })
        val e = pinInput("• • • • • •").apply { filters = arrayOf(android.text.InputFilter.LengthFilter(6)); textSize = 24f }
        body.addView(e, gap())
        val open = bigButton(L.t("verify"), GREEN) {
            if (Account.approve(this, e.text.toString())) { hideKeyboard(); toast(L.t("otp_ok")); startApp() }
            else toast(if (e.text.isEmpty()) L.t("wait_t") else L.t("otp_bad"))
        }
        body.addView(open, gap())
        val mob = Account.mobile(this); val dev = Account.device(this); val nm = Account.name(this)
        fun listen() = every(5000L, { Relay.answer(mob, dev) }) { ans ->
            when {
                ans == null -> { status.text = "⏳ " + L.t("wait_t"); true }
                ans.isEmpty() -> { status.text = "❌ " + L.t("otp_rejected"); status.setTextColor(RED); false }
                ans.length == 6 -> { e.setText(ans); status.text = "✅ " + L.t("otp_got"); status.setTextColor(GREEN); false }
                else -> true
            }
        }
        if (send) {
            val g = gen
            Thread { Relay.sendRequest(nm, mob, dev); ui.post { if (g == gen) listen() } }.start()
        } else listen()
        body.addView(link("💬 " + L.t("resend")) { sendOtpRequest(); Thread { Relay.sendRequest(nm, mob, dev) }.start() }, llp(MATCH_PARENT, WRAP_CONTENT))
        Account.adminNumber(this).let { an -> if (an.isNotEmpty()) body.addView(link("📞 " + L.t("call_owner")) {
            try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + an))) } catch (_: Exception) {} }, llp(MATCH_PARENT, WRAP_CONTENT)) }
        body.addView(link("✎ " + L.t("change_acc")) { showSignup() }, llp(MATCH_PARENT, WRAP_CONTENT))
        body.addView(link(L.t("have_acc")) { showSignin() }, llp(MATCH_PARENT, WRAP_CONTENT))
    }

    /** 3) Sign in: contact number (approved on this phone before) */
    private fun showSignin() {
        val body = loginScreen(L.t("si_title")) {
            if (!Account.exists(this)) Account.create(this, Store.owner.ifBlank { "Admin" }, Store.mobile.filter { it.isDigit() }.takeLast(10), "")
            Account.approveAsAdmin(this); Reminders.schedule(this); startApp() }
        var mob = Account.mobile(this)
        body.addView(small(L.t("u_mobile").uppercase(), INK).apply { typeface = Typeface.DEFAULT_BOLD })
        body.addView(mobileInput("98xxxxxxxx", mob) { mob = it }.apply { textSize = 18f }, gap())
        body.addView(bigButton(L.t("si_title"), GREEN) {
            when {
                mob.length != 10 -> toast(L.t("acc_bad_mobile"))
                mob == Account.mobile(this) && Account.verified(this) -> { Account.signIn(this); hideKeyboard(); startApp() }
                mob == Account.mobile(this) -> showOtp()
                else -> { toast(L.t("si_bad")); showSignup(mob) }
            }
        }, gap())
        body.addView(link(L.t("no_acc")) { showSignup() }, llp(MATCH_PARENT, WRAP_CONTENT))
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
        val body = setScreen("admin", if (Account.REQUIRED) "👑 Admin – OTP" else "👑 Admin", { showHome() })
        val rc = card().apply { if (!Account.REQUIRED) visibility = View.GONE }
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
                box.addView(row(bigButton("🔑 " + L.t("gen_otp"), GREEN) { act(true) }.apply { textSize = 15f; setPadding(0, dpi(10f), 0, dpi(10f)) } to 1f,
                    bigButton(L.t("adm_no"), RED) { act(false) }.apply { textSize = 15f; setPadding(0, dpi(10f), 0, dpi(10f)) } to 1f))
                reqList.addView(box, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
            }
        }
        reqList.addView(small("⏳ …"))
        if (Account.REQUIRED) every(10000L, { Relay.pending(this) }) { drawReqs(it); true }
        // ---- users asking to correct a final hisab ----
        val uc0 = card().apply { background = round(0xFFE8F5E9.toInt(), 12f, 0xFFA5D6A7.toInt()) }
        uc0.addView(heading("🔓 " + L.t("unl_admin"), GREEN))
        val unlList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        uc0.addView(unlList)
        body.addView(uc0, cardLp())
        fun drawUnl(list: List<Unlock.Req>?) {
            unlList.removeAllViews()
            if (list == null) { unlList.addView(small("📶 " + L.t("otp_net"), RED)); return }
            if (list.isEmpty()) { unlList.addView(small(L.t("unl_none"))); return }
            list.forEach { r ->
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(Color.WHITE, 10f); setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f)) }
                box.addView(small(r.name.ifBlank { "—" } + "   📞 " + r.mobile, INK).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
                box.addView(small("📋 " + r.title.ifBlank { "#" + r.hid } + "\n🕒 " + Bill.dateText(r.time), INK))
                fun act(ok: Boolean) {
                    box.alpha = 0.4f
                    Thread { val sent = Unlock.decide(this, r, ok); ui.post { toast(if (sent) (if (ok) "✅ " else "❌ ") + r.name else L.t("otp_net")); if (!sent) box.alpha = 1f else box.visibility = View.GONE } }.start()
                }
                box.addView(row(bigButton("✅ " + L.t("unl_give"), GREEN) { act(true) }.apply { textSize = 15f; setPadding(0, dpi(10f), 0, dpi(10f)) } to 1f,
                    bigButton("❌ " + L.t("adm_no"), RED) { act(false) }.apply { textSize = 15f; setPadding(0, dpi(10f), 0, dpi(10f)) } to 1f))
                unlList.addView(box, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
            }
        }
        unlList.addView(small("⏳ …"))
        every(15000L, { Unlock.pending(this) }) { drawUnl(it); true }
        // ---- users' problems: voice + text ----
        val fc = card().apply { background = round(0xFFFFF3E0.toInt(), 12f, 0xFFFFCC80.toInt()) }
        fc.addView(heading("🎤 " + L.t("fb_admin"), 0xFFE65100.toInt()))
        val fList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fc.addView(fList)
        body.addView(fc, cardLp())
        fun drawFb() {
            fList.removeAllViews()
            val l = Feedback.list(this)
            if (l.isEmpty()) { fList.addView(small(L.t("fb_none"))); return }
            l.forEach { o ->
                val id = o.optString("id"); val m = o.optString("m"); val nm = o.optString("n")
                val fresh = !o.optBoolean("heard")
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(if (fresh) 0xFFFFE0B2.toInt() else Color.WHITE, 10f); setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f)) }
                box.addView(row(small((if (fresh) "🆕 " else "") + nm.ifBlank { "—" }, INK).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD } to 1f,
                    small(Bill.dateText(o.optLong("at"))) to 0f))
                box.addView(small("📞 " + m.ifBlank { "—" }))
                if (o.optString("t").isNotBlank()) box.addView(small("✍ " + o.optString("t"), INK).apply { textSize = 15f; setPadding(0, dpi(4f), 0, dpi(2f)) })
                val acts = LinearLayout(this).apply { setPadding(0, dpi(6f), 0, 0) }
                fun act(t: String, color: Int, w: Float, a: () -> Unit) = acts.addView(TextView(this).apply {
                    text = t; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = round(color, 14f)
                    setPadding(dpi(6f), dpi(8f), dpi(6f), dpi(8f)); setOnClickListener { a() }
                }, llp(0, WRAP_CONTENT, w).apply { setMargins(dpi(2f), 0, dpi(2f), 0) })
                if (o.optBoolean("voice")) act("▶ " + L.t("fb_play") + "  " + (o.optLong("ms") / 1000) + " sec", GREEN, 2f) {
                    val f = Feedback.file(this, id)
                    if (!f.exists()) toast("✗") else { play(f); if (fresh) { Feedback.markHeard(this, id); box.background = round(Color.WHITE, 10f) } }
                }
                act("💬", 0xFF25D366.toInt(), 1f) { whatsapp(m, "") }
                act("📞", BLUE, 1f) { try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$m"))) } catch (_: Exception) {} }
                act("🗑", RED, 1f) {
                    AlertDialog.Builder(this).setMessage(L.t("del_q")).setPositiveButton(L.t("yes")) { _, _ -> Feedback.delete(this, id); drawFb() }
                        .setNegativeButton(L.t("no"), null).show()
                }
                box.addView(acts)
                if (fresh && !o.optBoolean("voice")) Feedback.markHeard(this, id)
                fList.addView(box, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
            }
        }
        drawFb()
        Thread { val n = try { Feedback.collect(this) } catch (_: Exception) { 0 }; ui.post { if (screen == "admin" && n > 0) drawFb() } }.start()
        // ---- all users: search, filter, block / approve again ----
        val uc = card()
        uc.addView(heading("👥 " + L.t("adm_users")))
        val uList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var uq = ""; var uf = ""
        val fRow = LinearLayout(this)
        val sumTv = small("", INK).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dpi(6f)) }
        fun ago(t: Long): String {
            if (t <= 0) return "—"
            val d = ((System.currentTimeMillis() - t) / 86400000L).toInt()
            return when (d) { 0 -> L.t("u_today"); 1 -> L.t("u_yday"); else -> d.toString() + " " + L.t("u_days_ago") }
        }
        fun drawUsers() {
            uList.removeAllViews()
            val seen = Relay.seen(this)
            val approved = Account.users(this)
            // everyone: approved / cancelled / blocked + phones that only sent usage
            val all = approved + seen.filterKeys { k -> approved.none { (it.optString("m") + ":" + it.optString("d")) == k } }.values
                .filter { !it.optBoolean("adm") }.map { org.json.JSONObject().put("n", it.optString("n")).put("m", it.optString("m")).put("d", it.optString("d")).put("s", "ok").put("t", it.optLong("first")) }
            val week = System.currentTimeMillis() - 7L * 86400000L
            val today = System.currentTimeMillis() - 86400000L
            sumTv.text = L.t("u_total") + ": " + all.size + "   •   🟢 " + L.t("u_week") + ": " + seen.values.count { !it.optBoolean("adm") && it.optLong("at") > week } +
                "   •   " + L.t("u_today") + ": " + seen.values.count { !it.optBoolean("adm") && it.optLong("at") > today } +
                "\n📋 " + L.t("u_hisab") + ": " + seen.values.filter { !it.optBoolean("adm") }.sumOf { it.optInt("h") }
            fRow.removeAllViews()
            listOf("" to L.t("f_all"), "act" to "🟢 7d", "ok" to "✅", "no" to "❌", "block" to "🚫").forEach { (k, t) ->
                val n = when (k) { "" -> all.size; "act" -> all.count { (seen[it.optString("m") + ":" + it.optString("d")]?.optLong("at") ?: 0L) > week }
                    else -> all.count { (it.optString("s").ifEmpty { if (it.optBoolean("ok")) "ok" else "no" }) == k } }
                fRow.addView(pill("$t ($n)", uf == k, 0xFF6A1B9A.toInt()) { uf = k; drawUsers() }.apply { textSize = 12f; setPadding(dpi(4f), dpi(6f), dpi(4f), dpi(6f)) },
                    llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(2f), 0, dpi(2f), 0) })
            }
            all.filter { u ->
                val st = u.optString("s").ifEmpty { if (u.optBoolean("ok")) "ok" else "no" }
                (uf.isEmpty() || st == uf || (uf == "act" && (seen[u.optString("m") + ":" + u.optString("d")]?.optLong("at") ?: 0L) > week)) && (uq.isBlank() || norm(u.optString("n")).contains(norm(uq)) || u.optString("m").contains(uq.filter { it.isDigit() }.ifEmpty { "~" }))
            }.forEach { u ->
                val st = u.optString("s").ifEmpty { if (u.optBoolean("ok")) "ok" else "no" }
                val nm = u.optString("n"); val m = u.optString("m"); val d = u.optString("d")
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(4f), dpi(6f), dpi(4f), dpi(6f)) }
                box.addView(row(small(nm.ifBlank { "—" }, INK).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD } to 1f,
                    small(when (st) { "ok" -> "✅ " + L.t("f_ok"); "block" -> "🚫 " + L.t("f_block"); else -> "❌ " + L.t("f_no") },
                        when (st) { "ok" -> GREEN; "block" -> RED; else -> MUTED }).apply { typeface = Typeface.DEFAULT_BOLD } to 0f))
                box.addView(small((if (m.length == 10) "📞 $m" else "📞 ⚠ " + L.t("nm_missing")) + "   #" + d + "   •  " + Bill.dateText(u.optLong("t")).substringBefore("  "),
                    if (m.length == 10) MUTED else RED))
                val info = seen["$m:$d"]
                if (info != null) {
                    val act = info.optLong("at") > week
                    box.addView(small((if (act) "🟢 " else "⚪ ") + L.t("u_last") + ": " + ago(info.optLong("at")) + "   •   v" + info.optString("v") + "   •   📱 " + info.optString("ph"),
                        if (act) GREEN else MUTED))
                    box.addView(small("📋 " + L.t("u_hisab") + ": " + info.optInt("h") + "  (🚚 " + info.optInt("g") + "  🔨 " + info.optInt("hr") + "  📦 " + info.optInt("l") + ")" +
                        (if (info.optLong("last") > 0) "   •   " + L.t("u_lasth") + ": " + Bill.dateText(info.optLong("last")).substringBefore("  ") else "") +
                        (if (info.optString("own").isNotBlank() && info.optString("own") != nm) "\n🏪 " + info.optString("own") else ""), INK))
                } else if (st == "ok") box.addView(small("⚪ " + L.t("u_nodata")))
                val acts = LinearLayout(this).apply { setPadding(0, dpi(4f), 0, 0) }
                fun act(t: String, color: Int, a: () -> Unit) = acts.addView(TextView(this).apply {
                    text = t; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = round(color, 14f)
                    setPadding(dpi(6f), dpi(7f), dpi(6f), dpi(7f)); setOnClickListener { a() }
                }, llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(2f), 0, dpi(2f), 0) })
                act("💬", 0xFF25D366.toInt()) { whatsapp(m, "") }
                act("📞", BLUE) { try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$m"))) } catch (_: Exception) {} }
                if (st == "ok") act("🚫 " + L.t("block_btn"), RED) {
                    AlertDialog.Builder(this).setTitle("🚫 " + nm).setMessage(L.t("block_q"))
                        .setPositiveButton(L.t("yes")) { _, _ -> Thread { val ok = Relay.block(this, nm, m, d); ui.post { toast(if (ok) "🚫 $nm" else L.t("otp_net")); drawUsers() } }.start() }
                        .setNegativeButton(L.t("no"), null).show()
                } else if (d.length == 6) act("🔑 " + L.t("gen_otp"), GREEN) {
                    Thread { val ok = Relay.decide(this, Relay.Req("", 0, nm, m, d), true); ui.post { toast(if (ok) "✅ $nm" else L.t("otp_net")); if (ok) whatsapp(m, "✅ " + L.t("app") + "\nOTP: " + Otp.code(m, d)); drawUsers() } }.start()
                }
                box.addView(acts)
                uList.addView(box)
                uList.addView(View(this).apply { setBackgroundColor(0xFFE0E0E0.toInt()) }, llp(MATCH_PARENT, dpi(1f)))
            }
            if (uList.childCount == 0) uList.addView(small(L.t("none")))
        }
        uc.addView(sumTv)
        uc.addView(input("🔍 " + L.t("f_search"), "", false) { uq = it; drawUsers() }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(6f) })
        uc.addView(fRow, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(6f) })
        uc.addView(uList)
        drawUsers()
        // fetch fresh usage in the background
        Thread { if (Relay.collect(this)) ui.post { if (screen == "admin") drawUsers() } }.start()
        uc.addView(bigButton("📤 " + L.t("u_share"), 0xFF455A64.toInt()) {
            val seen = Relay.seen(this)
            val sb = StringBuilder(L.t("app")).append(" – ").append(L.t("adm_users")).append("\n\n")
            Account.users(this).forEach { u ->
                val i = seen[u.optString("m") + ":" + u.optString("d")]
                sb.append("• ").append(u.optString("n")).append("  ").append(u.optString("m")).append("  ").append(u.optString("s").ifEmpty { "ok" })
                if (i != null) sb.append("  v").append(i.optString("v")).append("  ").append(L.t("u_hisab")).append(" ").append(i.optInt("h")).append("  ").append(ago(i.optLong("at")))
                sb.append("\n")
            }
            try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString()), "")) } catch (_: Exception) {}
        }.apply { textSize = 14f }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
        body.addView(uc, cardLp())
        if (!Account.REQUIRED) return
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
        if (i?.getBooleanExtra("remind", false) == true) { autoSave(); showReminders() }
        else if (i?.getBooleanExtra("khata", false) == true) { autoSave(); showKhata() }
        else if (i?.getBooleanExtra("admin", false) == true && Account.isAdmin(this)) { autoSave(); showAdmin() }
    }

    override fun onResume() {
        super.onResume()
        Updater.autoCheck(this) // on every open (at most every 30 min)
    }

    override fun onPause() {
        super.onPause()
        if (rec != null || player != null) { stopAudio(); if (screen == "feedback") showFeedback() }
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
        stopAudio()
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

    // ================= problem by voice (user → admin) =================
    private var rec: android.media.MediaRecorder? = null
    private var player: android.media.MediaPlayer? = null

    private fun stopAudio() {
        try { rec?.stop() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
        rec = null
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    private fun play(f: java.io.File) {
        try { player?.release() } catch (_: Exception) {}
        player = try {
            android.media.MediaPlayer().apply { setDataSource(f.absolutePath); prepare(); start() }
        } catch (_: Exception) { toast("▶ ✗"); null }
    }

    private fun showFeedback() {
        val body = setScreen("feedback", "🎤 " + L.t("fb_title"), { showHome() })
        val f = java.io.File(cacheDir, "fb_me.3gp")
        f.delete()
        var recorded = false
        var startAt = 0L
        var dur = 0L
        var text = ""
        val c = card()
        c.addView(small(L.t("fb_sub"), INK).apply { textSize = 15f; setPadding(0, 0, 0, dpi(10f)) })
        val status = small("", INK).apply { textSize = 17f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, dpi(6f), 0, dpi(6f)) }
        val playBtn = bigButton("▶ " + L.t("fb_play"), BLUE) { if (recorded) play(f) }.apply { visibility = View.GONE }
        lateinit var recBtn: TextView
        fun stopRec() {
            var ok = true
            try { rec?.stop() } catch (_: Exception) { ok = false }
            try { rec?.release() } catch (_: Exception) {}
            rec = null
            dur = System.currentTimeMillis() - startAt
            recorded = ok && f.exists() && f.length() > 0
            recBtn.text = "🎤 " + L.t(if (recorded) "fb_again" else "fb_rec")
            status.text = if (recorded) "✅ " + (dur / 1000) + " sec" else ""
            playBtn.visibility = if (recorded) View.VISIBLE else View.GONE
        }
        recBtn = bigButton("🎤 " + L.t("fb_rec"), RED) {
            if (rec != null) return@bigButton stopRec()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 51)
                return@bigButton toast(L.t("fb_perm"))
            }
            try { player?.release() } catch (_: Exception) {}
            player = null
            f.delete()
            val r = if (Build.VERSION.SDK_INT >= 31) android.media.MediaRecorder(this) else @Suppress("DEPRECATION") android.media.MediaRecorder()
            try {
                r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                r.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                r.setAudioSamplingRate(8000)
                r.setAudioEncodingBitRate(4750)
                r.setMaxDuration(60000)
                r.setOutputFile(f.absolutePath)
                r.setOnInfoListener { _, what, _ -> if (what == android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) ui.post { if (rec === r) stopRec() } }
                r.prepare(); r.start()
            } catch (_: Exception) {
                try { r.release() } catch (_: Exception) {}
                return@bigButton toast("🎤 ✗")
            }
            rec = r; startAt = System.currentTimeMillis(); recorded = false
            recBtn.text = "⏹ " + L.t("fb_stop"); playBtn.visibility = View.GONE
            val g = gen
            fun tick() {
                if (rec !== r || g != gen) return
                status.text = "🔴 " + ((System.currentTimeMillis() - startAt) / 1000) + " / 60 sec"
                ui.postDelayed({ tick() }, 500)
            }
            tick()
        }
        c.addView(recBtn, gap())
        c.addView(status, llp(MATCH_PARENT, WRAP_CONTENT))
        c.addView(playBtn, gap())
        c.addView(small(L.t("fb_write"), INK).apply { typeface = Typeface.DEFAULT_BOLD })
        c.addView(input(L.t("fb_write"), "", false) { text = it }.apply {
            setSingleLine(false); minLines = 3; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }, gap())
        lateinit var sendBtn: TextView
        sendBtn = bigButton("📤 " + L.t("fb_send"), GREEN) {
            if (rec != null) stopRec()
            if (!recorded && text.isBlank()) return@bigButton toast(L.t("fb_empty"))
            val bytes = if (recorded) f.readBytes() else null
            sendBtn.isEnabled = false; sendBtn.alpha = 0.5f
            toast(L.t("fb_sending"))
            val nm = Account.name(this).ifBlank { Store.owner }
            val mob = myMobile()
            val dev = Account.device(this)
            val ms = if (recorded) dur else 0L
            val t = text.trim()
            Thread {
                val ok = try { Feedback.send(nm, mob, dev, t, bytes, ms) } catch (_: Exception) { false }
                ui.post {
                    if (ok) { f.delete(); toast("✅ " + L.t("fb_sent")); if (screen == "feedback") showHome() }
                    else { toast(L.t("otp_net")); sendBtn.isEnabled = true; sendBtn.alpha = 1f }
                }
            }.start()
        }
        c.addView(sendBtn, gap())
        body.addView(c, cardLp())
    }

    private fun showHome() {
        editing = null
        editorBack = null
        val body = setScreen("home", L.t("app"), null)
        val dues = openDues(Store.hisabs)
        val late = dues.count { it.due != null && it.due <= Reminders.endOfToday() }
        // ---- 6 big round buttons: about 80% of the screen ----
        val dm = resources.displayMetrics
        val rowH = ((dm.heightPixels * 0.8f - dp(70f)) / 3f).toInt()
        val d = minOf(rowH - dpi(12f), (dm.widthPixels - dpi(20f)) / 2 - dpi(16f))
        fun circle(icon: String, label: String, color: Int, act: () -> Unit): View {
            val txt = android.text.SpannableString(icon + "\n" + label).apply {
                setSpan(android.text.style.RelativeSizeSpan(2.1f), 0, icon.length, 0)
            }
            val oval = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); setStroke(dpi(4f), 0x33FFFFFF) }
            val b = TextView(this).apply {
                text = txt; textSize = if (d < dpi(130f)) 13.5f else 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                maxLines = 4; setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
                background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x55FFFFFF), oval, null)
                elevation = dp(6f); setOnClickListener { act() }
            }
            return FrameLayout(this).apply { addView(b, FrameLayout.LayoutParams(d, d, Gravity.CENTER)) }
        }
        fun gridRow(a1: View, a2: View) = LinearLayout(this).apply {
            addView(a1, llp(0, rowH, 1f)); addView(a2, llp(0, rowH, 1f))
        }
        body.addView(gridRow(
            circle("🚚", L.t("new_gaadi").removePrefix("+ "), GREEN) { newHisab("gaadi") },
            circle("🔨", L.t("new_haraji").removePrefix("+ "), 0xFF6A1B9A.toInt()) { newHisab("haraji") }))
        body.addView(gridRow(
            circle("📦", L.t("new_lot").removePrefix("+ "), 0xFF1565C0.toInt()) { newHisab("lot") },
            circle("🤝", L.t("party_tile"), 0xFF5D4037.toInt()) { showParties() }))
        body.addView(gridRow(
            circle("📒", L.t("khata_btn").substringAfter(" ") + (if (late > 0) "\n⚠ $late" else ""), if (late > 0) RED else 0xFF455A64.toInt()) { showKhata() },
            circle("📊", L.t("report_btn").substringAfter(" "), 0xFF00695C.toInt()) { showReport() }))
        if (Store.shared.isNotEmpty()) body.addView(bigButton("👁 " + L.t("sh_title") + " (" + Store.shared.size + ")", BLUE) { showShared() }
            .apply { textSize = 15f }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
        val rem = reminders(Store.hisabs)
        if (rem.isNotEmpty()) body.addView(bigButton("🔔 " + rem.map { it.key.ifEmpty { it.name } }.distinct().size + " " + L.t("rem_banner"), 0xFFE65100.toInt()) { showReminders() }
            .apply { textSize = 15f }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
        thisMonth(Store.hisabs)?.let { m ->
            val mc = card().apply { background = round(0xFFE8F5E9.toInt(), 12f, 0xFFA5D6A7.toInt()) }
            mc.addView(row(small("📅 " + L.t("this_month"), INK).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD } to 1f,
                small((if (m.munafa >= 0) L.t("profit") else L.t("loss")) + " " + money(Math.abs(m.munafa)), if (m.munafa >= 0) GREEN else RED).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD } to 0f))
            mc.addView(small(listOfNotNull(m.count.toString() + " " + L.t("vehicles"), L.t("bikri") + " " + money(m.bikri),
                if (m.kg > 0) plain(m.kg) + " kg" else null).joinToString("  •  ")))
            mc.setOnClickListener { val c = Calendar.getInstance(); rf.year = c.get(Calendar.YEAR); rf.month = c.get(Calendar.MONTH) + 1; showReport() }
            body.addView(mc, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f); bottomMargin = dpi(4f) })
        }
        if (dues.isNotEmpty()) body.addView(small("⬇ " + L.t("lena") + " " + money(dues.filter { it.lena }.sumOf { it.left }) +
            "     ⬆ " + L.t("dena") + " " + money(dues.filter { !it.lena }.sumOf { it.left })).apply {
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(4f), 0, dpi(8f))
        }, llp(MATCH_PARENT, WRAP_CONTENT))

        // any user: tell the admin a problem by voice / text
        if (LOGIN_ON && !Account.isAdmin(this)) body.addView(bigButton("🎤 " + L.t("fb_btn"), 0xFFE65100.toInt()) { showFeedback() }
            .apply { textSize = 15f }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
        if (LOGIN_ON && Account.isAdmin(this)) {
            val fbBanner = bigButton("", 0xFFE65100.toInt()) { showAdmin() }.apply { textSize = 15f; visibility = View.GONE }
            body.addView(fbBanner, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
            val unlBanner = bigButton("", GREEN) { showAdmin() }.apply { textSize = 15f; visibility = View.GONE }
            body.addView(unlBanner, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
            every(30000L, { Unlock.pending(this) }) { p ->
                unlBanner.visibility = if (p.isNullOrEmpty()) View.GONE else View.VISIBLE
                unlBanner.text = "🔓 " + (p?.size ?: 0) + " " + L.t("unl_new"); true
            }
            every(60000L, { Feedback.collect(this); Feedback.unheard(this) }) { u ->
                val n = u ?: Feedback.unheard(this)
                fbBanner.visibility = if (n > 0) View.VISIBLE else View.GONE
                fbBanner.text = "🎤 $n " + L.t("fb_new"); true
            }
            val nUsers = Relay.seen(this).values.count { !it.optBoolean("adm") }
            body.addView(bigButton("👑 " + L.t(if (Account.REQUIRED) "adm_btn" else "adm_users_btn") + (if (nUsers > 0) "   •   👥 $nUsers" else ""), 0xFF4A148C.toInt()) { showAdmin() }.apply { textSize = 15f },
                llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
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
                c.addView(small(listOf(if (h.finalAt > 0) "✅" else "", if (h.isLot) "🚚 " + L.t("lot") else if (h.isCo) "🏢 " + L.t("mode_co") else if (h.type == "haraji") "🔨 " + L.t("haraji") else "",
                    h.vehicleInfo(), h.vehicle, Bill.dateText(h.time)).filter { it.isNotBlank() }.joinToString("  •  ")))
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
        // admin phone may delete anything, even a final hisab
        val admin = LOGIN_ON && Account.isAdmin(this)
        if (h.finalAt > 0 && !admin) return toast(L.t("locked_del"))
        AlertDialog.Builder(this).setMessage((if (h.finalAt > 0) "👑 ✅ " + L.t("final_s") + "\n" else "") + L.t("del_q") + "\n" + hTitle(h) + "  " + money(h.munafa()))
            .setPositiveButton(L.t("yes")) { _, _ -> Store.hisabs.remove(h); Store.save(this); after() }
            .setNegativeButton(L.t("no"), null).show()
    }

    private fun newHisab(type: String) {
        val now = System.currentTimeMillis()
        // first choice on every new hisab: who is writing it (mudi malik or khedut)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(18f), dpi(8f), dpi(18f), dpi(4f)) }
        val dlg = AlertDialog.Builder(this).setView(box).setNegativeButton(L.t("back"), null).create()
        box.addView(TextView(this).apply { text = L.t("writer_q"); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(INK); gravity = Gravity.CENTER; setPadding(0, dpi(6f), 0, dpi(14f)) },
            llp(MATCH_PARENT, WRAP_CONTENT))
        listOf("mudi" to ("💰  " + L.t("mudi_h")), "khed" to ("🚚  " + L.t("khed_h"))).forEach { (w, t) ->
            box.addView(bigButton(t, if (w == "mudi") 0xFFE65100.toInt() else GREEN) {
                dlg.dismiss(); showEditor(Hisab(now, now, type = type, writer = w))
            }.apply { textSize = 24f; setPadding(0, dpi(18f), 0, dpi(18f)) }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(12f) })
        }
        dlg.show()
    }

    // ================= EDITOR =================
    private lateinit var sumPrice: TextView
    /** gaadi / haraji: "total purchase" under the other goods bought */
    private var buyTotalTv: TextView? = null
    /** sale lines that come from bought goods: name, sold-qty input, info (bought / shortage) */
    private val linkViews = HashMap<String, Triple<TextView, EditText?, TextView>>()
    /** vehicle-part buttons in the sale card (only when a vehicle was bought) */
    private var partChips: View? = null
    private var redrawMaalFn: (() -> Unit)? = null
    private var syncing = false

    private fun linkInfo(h: Hisab, s: Line): Pair<String, Int> {
        val b = h.boughtOf(s) ?: return "" to MUTED
        val unit = if (b.litre) " L" else " kg"
        val buy = "🛒 " + L.t("bought") + ": " + if (b.fixed) money(b.amount) else plain(b.kg) + unit + (if (b.rate != 0.0) " × " + plain(b.rate) else "")
        val sh = h.shortage(s) ?: return buy to MUTED
        return (buy + "\n⚠ " + L.t("kami") + ": " + plain(sh.first) + unit + " → " + L.t("loss_buy") + " " + money(sh.second)) to RED
    }
    private lateinit var sumKharch: TextView
    private lateinit var sumLagat: TextView
    private lateinit var sumMaal: TextView
    private lateinit var sumKg: TextView
    private lateinit var sumLtr: TextView
    private lateinit var sumResult: TextView
    private lateinit var resultBox: LinearLayout
    private var scrollTo: View? = null

    private fun hasContent(h: Hisab) = h.party.isNotBlank() || h.mudiName.isNotBlank() || h.khedName.isNotBlank() || h.vehicle.isNotBlank() || h.priceText.isNotBlank() ||
        h.kharch.isNotEmpty() || h.maal.isNotEmpty() || h.saleText.isNotBlank() || h.partners.isNotEmpty() ||
        h.lots.any { it.vehicles.isNotEmpty() || it.items.isNotEmpty() || it.priceText.isNotBlank() }

    private fun autoSave() {
        if (viewOnly != null) return
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
            if (Share.targets(this, h).isNotEmpty()) Thread { val n = try { Share.publish(this, h) } catch (_: Exception) { 0 }; if (n > 0) ui.post { toast("🔗 " + L.t("sh_sent") + ": $n") } }.start()
            if (missingMobile(h).isNotEmpty()) toast("📞 " + L.t("mob_need") + ": " + missingMobile(h).size)
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
        // bought goods always appear in the sale list (same name, same qty until changed)
        if (!syncing && viewOnly == null) {
            syncing = true
            try {
                if (h.syncSale()) redrawMaalFn?.invoke()
                else h.maal.forEach { s ->
                    val v = linkViews[s.link] ?: return@forEach
                    v.first.text = "📦 " + s.name
                    if (v.second != null && v.second!!.text.toString() != s.kgText) v.second!!.setText(s.kgText)
                    linkInfo(h, s).let { (t, col) -> v.third.text = t; v.third.setTextColor(col) }
                }
                partChips?.visibility = if (h.hasVehicle()) View.VISIBLE else View.GONE
            } finally { syncing = false }
        }
        buyTotalTv?.text = L.t("buy_total") + ": " + money(h.price)
        lotTotals.forEach { (t, tv) ->
            val parts = listOfNotNull(if (t.vehicles.isNotEmpty()) t.vehicles.size.toString() + " 🚚" else null,
                if (t.kg() > 0) plain(t.kg()) + " kg" else null)
            tv.text = parts.joinToString(" • ").let { if (it.isEmpty()) "" else "$it   " } + L.t("lot") + ": " + money(t.price)
        }
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
            listOf(Triple(h.mudiName.ifBlank { L.t("mudi") }, h.mudiPct, h.mudiMobile), Triple(h.khedName.ifBlank { L.t("khed") }, h.khedPct, h.khedMobile)).forEachIndexed { idx, (n, p, mob) ->
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
                val raw = if (idx == 0) h.mudiName else h.khedName
                if (h.inCompany(raw)) {
                    val co = h.coShareOf(raw)
                    val add = if (idx == 0) h.mudiAddCo else h.khedAddCo
                    splitBox.addView(row(small("   🏢 " + L.t("co_part") + ": " + (if (co >= 0) L.t("profit") else L.t("loss")) + " " + money(Math.abs(co)), if (co >= 0) GREEN else RED) to 1f,
                        pill(if (add) "✓ " + L.t("added_co") else "＋ " + L.t("add_co"), add, 0xFF00695C.toInt()) {
                            if (idx == 0) h.mudiAddCo = !h.mudiAddCo else h.khedAddCo = !h.khedAddCo; refreshTotals()
                        }.apply { textSize = 12f } to 0f))
                    if (add) splitBox.addView(small("   = " + L.t("co_total") + ": " + money(v + co), if (v + co >= 0) GREEN else RED).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD })
                }
            }
            if (Math.abs(h.mudiPct + h.khedPct - 100) > 0.001) splitBox.addView(small(L.t("not100"), RED).apply { typeface = Typeface.DEFAULT_BOLD })
        }
        val bad = h.verify()
        checkTv.text = if (bad.isEmpty()) L.t("check_ok") else L.t("check_bad") + " (" + bad.joinToString() + ")"
        checkTv.setTextColor(if (bad.isEmpty()) GREEN else RED)
        val m = h.munafa()
        sumResult.text = (if (h.isCo) L.t("owner_res") + "\n" else "") + (if (m >= 0) L.t("profit") else L.t("loss")) + "   " + money(Math.abs(m))
        resultBox.background = round(if (m >= 0) GREEN else RED, 12f)
        if (h.type == "haraji" || h.isLot) {
            sumSale.text = money(h.sale)
            sumBikri.text = money(h.bikri())
            sumComm.text = "- " + money(h.commission())
            (sumSale.parent as? View)?.visibility = if (h.isLot && h.sale == 0.0) View.GONE else View.VISIBLE
            (sumBikri.parent as? View)?.visibility = if (h.isLot) View.GONE else View.VISIBLE
            (sumComm.parent as? View)?.visibility = if (h.commission() == 0.0) View.GONE else View.VISIBLE
        }
        if (h.type == "haraji") {
            partnerBox.removeAllViews()
            if (h.isCo) {
                val cr = h.companyResult()
                partnerBox.addView(TextView(this).apply {
                    text = "🏢 " + L.t("co_result") + "\n" + (if (cr >= 0) L.t("profit") else L.t("loss")) + "   " + money(Math.abs(cr))
                    textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                    background = round(if (cr >= 0) 0xFF00897B.toInt() else 0xFFAD1457.toInt(), 12f); setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f))
                }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
            }
            if (h.partners.isNotEmpty()) {
                partnerBox.addView(heading(L.t("company").removePrefix("6. ")).apply { textSize = 15f; setPadding(0, dpi(10f), 0, dpi(4f)) })
                val rows = h.partners.map { p -> Triple(p.name.ifBlank { "—" }, p.share, h.partnerLagat(p) to h.partnerMunafa(p)) } +
                    (if (h.ownerShare() > 0) listOf(Triple(L.t("owner_share"), h.ownerShare(), h.companyLagat() * h.ownerShare() / 100 to h.companyResult() * h.ownerShare() / 100)) else emptyList())
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

    /** showing someone else's hisab (mudi malik / khedut link): nothing can be changed */
    private var viewOnly: Shared? = null

    private fun showEditor(h: Hisab, view: Shared? = null) {
        buyTotalTv = null
        linkViews.clear(); partChips = null; redrawMaalFn = null
        if (view == null) h.syncSale()
        viewOnly = view
        editing = h
        val body = setScreen("edit", hTitle(h), { if (viewOnly != null) { viewOnly = null; editing = null; showShared() } else { autoSave(); Store.save(this); editorBack?.invoke() ?: showHome() } })

        // ---- top: Mudi malik | %   Khedut | %  (profit / loss share) ----
        val top = card()
        val lab = LinearLayout(this)
        fun hl(t: String, w: Float, end: Boolean = false) = lab.addView(small(t).apply {
            typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFF00695C.toInt()); if (end) gravity = Gravity.CENTER
        }, llp(0, WRAP_CONTENT, w).apply { leftMargin = dpi(6f) })
        // ✍ marks who is writing this hisab
        hl((if (h.writer == "mudi") "✍ " else "") + L.t("mudi_h"), 1.5f); hl("%", 0.55f, true)
        hl((if (h.writer == "khed") "✍ " else "") + L.t("khed_h"), 1.5f); hl("%", 0.55f, true)
        top.addView(lab)
        fun nameIn(v: String, set: (String) -> Unit) = input(L.t("name_q"), v, false) { set(it); titleTv.text = hTitle(editing ?: return@input) }.apply { textSize = 15f }
        fun pctIn(v: String, set: (String) -> Unit) = input("%", v, true) { set(it); refreshTotals() }.apply { gravity = Gravity.CENTER; textSize = 15f }
        top.addView(row(nameIn(h.mudiName) { h.mudiName = it } to 1.5f, pctIn(h.mudiPctText) { h.mudiPctText = it } to 0.55f,
            nameIn(h.khedName) { h.khedName = it } to 1.5f, pctIn(h.khedPctText) { h.khedPctText = it } to 0.55f))
        // mobile under each name (required for a new hisab)
        val need = h.writer.isNotBlank()
        fun mobHint(v: String) = if (need && digits10(v).length != 10) "📞 " + L.t("mob_need") else ""
        val mudiWarn = small(mobHint(h.mudiMobile), RED).apply { textSize = 11.5f }
        val khedWarn = small(mobHint(h.khedMobile), RED).apply { textSize = 11.5f }
        val mudiMob = mobileInput(L.t("mudi_h") + " " + L.t("cmobile") + if (need) " *" else "", h.mudiMobile) { h.mudiMobile = it; mudiWarn.text = mobHint(it) }
        val khedMob = mobileInput(L.t("khed_h") + " " + L.t("cmobile") + if (need) " *" else "", h.khedMobile) { h.khedMobile = it; khedWarn.text = mobHint(it) }
        top.addView(row(mudiMob to 2.05f, khedMob to 2.05f))
        if (need) top.addView(row(mudiWarn to 2.05f, khedWarn to 2.05f))
        // then date + time (tap to change) and seller / buyer
        val dt = small("🕒 " + Bill.dateText(h.time) + "  ✎", BLUE).apply { textSize = 14f; setPadding(0, dpi(6f), 0, dpi(6f)) }
        dt.setOnClickListener { pickDate(h) { dt.text = "🕒 " + Bill.dateText(h.time) + "  ✎" } }
        top.addView(row(dt to 1f))
        top.addView(small("🔗 " + L.t("sh_hint"), BLUE).apply { textSize = 12.5f })
        // note only when wanted (anything extra: where the vehicle came from, who sent it, conditions...)
        val more = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawMore(open: Boolean) {
            more.removeAllViews()
            if (!open) {
                more.addView(small("＋ " + L.t("note"), BLUE).apply { setPadding(0, dpi(2f), 0, 0); setOnClickListener { drawMore(true) } })
                return
            }
            more.addView(row(input(L.t("note_hint"), h.note, false) { h.note = it } to 1f))
        }
        drawMore(h.note.isNotBlank())
        top.addView(more)
        body.addView(top, cardLp())

        // ---- haraji / lot: credit time for everything, from the haraji day ----
        if (h.type == "haraji" || h.isLot) {
            val mc0 = card().apply { background = round(0xFFFFF3E0.toInt(), 12f, 0xFFFFCC80.toInt()) }
            mc0.addView(heading(L.t("muddat_h"), ORANGE).apply { textSize = 15f })
            var months = h.muddatText.endsWith("m")
            val due = small("", ORANGE).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 15f }
            fun upd() { due.text = muddatEnd(h.time, h.muddatText)?.let { "→ " + L.t("due") + ": " + java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(java.util.Date(it)) } ?: "" }
            val num = input("0", h.muddatText.removeSuffix("m"), true) { v -> h.muddatText = if (v.isBlank()) "" else v.trim() + (if (months) "m" else ""); upd() }
                .apply { gravity = Gravity.CENTER; textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
            val tog = toggle(listOf(L.t("din"), L.t("mahina")), if (months) 1 else 0, ORANGE) { i ->
                months = i == 1
                val v = num.text.toString().trim()
                h.muddatText = if (v.isBlank()) "" else v + (if (months) "m" else ""); upd()
            }
            mc0.addView(row(num to 0.7f, tog to 1.4f))
            mc0.addView(due)
            mc0.addView(small(L.t("muddat_hint")))
            upd()
            body.addView(mc0, cardLp())
        }

        // ---- lot hisab: one or more lots, each with vehicles + any items, own name and price ----
        lotTotals.clear()
        if (h.isLot) {
            if (h.lots.isEmpty()) h.lots.add(Lot("Lot 1").also { t ->
                if (h.vehicles.isNotEmpty()) { t.vehicles.addAll(h.vehicles); h.vehicles.clear(); t.priceText = h.priceText; h.priceText = "" }
            })
            val lotsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            lateinit var drawLots: () -> Unit
            drawLots = {
                lotsBox.removeAllViews(); lotTotals.clear()
                h.lots.forEachIndexed { li, t ->
                    val lc = card().apply { background = round(Color.WHITE, 12f, 0xFF90CAF9.toInt()) }
                    lc.addView(row(small("${li + 1}.", 0xFF1565C0.toInt()).apply { textSize = 17f; typeface = Typeface.DEFAULT_BOLD } to 0f,
                        input(L.t("lot_name"), t.name, false) { t.name = it }.apply { textSize = 17f; typeface = Typeface.DEFAULT_BOLD } to 1f,
                        xBtn { confirmRemove { h.lots.remove(t); drawLots(); refreshTotals() } } to 0f))
                    // vehicles of this lot
                    val vLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    fun drawV() {
                        vLines.removeAllViews()
                        t.vehicles.forEachIndexed { i, v ->
                            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(0xFFF1F6FC.toInt(), 10f, 0xFFBBDEFB.toInt()); setPadding(dpi(8f), dpi(4f), dpi(4f), dpi(6f)) }
                            val bIn = input(L.t("brand"), v.brand, false) { v.brand = it }.apply { tag = "focus" }
                            val pk = pill("▾", false, 0xFF455A64.toInt()) { AlertDialog.Builder(this).setItems(BRANDS.toTypedArray()) { _, w -> bIn.setText(BRANDS[w]) }.show() }
                            box.addView(row(small("🚚 ${i + 1}", INK).apply { typeface = Typeface.DEFAULT_BOLD } to 0f, bIn to 1.2f, pk to 0f,
                                input(L.t("variant").substringBefore(" ("), v.variant, false) { v.variant = it } to 1f,
                                xBtn { confirmRemove { t.vehicles.remove(v); drawV(); refreshTotals() } } to 0f))
                            box.addView(row(input(L.t("vehicle").substringBefore(" /"), v.no, false) { v.no = it } to 1.3f,
                                input(L.t("year"), v.year, true) { v.year = it } to 0.7f,
                                input(L.t("tyres"), v.tyres, true) { v.tyres = it } to 0.6f))
                            box.addView(row(small(L.t("veh_price")) to 1f,
                                input("₹", v.priceText, true) { v.priceText = it; refreshTotals() }.apply { gravity = Gravity.END } to 1.2f))
                            vLines.addView(box, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(6f) })
                        }
                    }
                    // other things in this lot (scrap, tyres … any name)
                    val iLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    fun drawI() {
                        iLines.removeAllViews()
                        t.items.forEach { l -> iLines.addView(lotItemRow(t, l) { drawI(); refreshTotals() }) }
                    }
                    lc.addView(vLines); lc.addView(iLines)
                    drawV(); drawI()
                    lc.addView(row(pill("＋ 🚚 " + L.t("add_veh1"), false, 0xFF1565C0.toInt()) { t.vehicles.add(Veh()); drawV(); refreshTotals(); focusLast(vLines) }
                            .apply { textSize = 14f; setPadding(dpi(6f), dpi(9f), dpi(6f), dpi(9f)) } to 1f,
                        pill("＋ 📦 " + L.t("add_item"), false, GREEN) {
                            val names = Store.parts.map { it.label() } + ("✎ " + L.t("other"))
                            AlertDialog.Builder(this).setTitle(L.t("add_item")).setItems(names.toTypedArray()) { _, w ->
                                val b = Store.parts.getOrNull(w)
                                val nm = b?.label() ?: ""
                                t.items.add(Line(b?.key ?: "", nm, b?.fixed ?: false, rateText = Store.lastRate[nm] ?: "", litre = b?.litre ?: false))
                                drawI(); refreshTotals(); focusLast(iLines)
                            }.show()
                        }.apply { textSize = 14f; setPadding(dpi(6f), dpi(9f), dpi(6f), dpi(9f)) } to 1f).apply { setPadding(0, dpi(6f), 0, dpi(4f)) })
                    lc.addView(row(TextView(this).apply { text = L.t("this_lot_price"); textSize = 15f; setTextColor(BLUE); typeface = Typeface.DEFAULT_BOLD } to 1f,
                        input(L.t("lot_price_h"), t.priceText, true) { t.priceText = it; refreshTotals() }.apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END } to 1.2f))
                    val tot = small("").apply { gravity = Gravity.END; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xFF1565C0.toInt()) }
                    lc.addView(tot, llp(MATCH_PARENT, WRAP_CONTENT))
                    lotTotals.add(t to tot)
                    lotsBox.addView(lc, cardLp())
                }
            }
            body.addView(lotsBox)
            drawLots()
            body.addView(bigButton(L.t("add_lot"), 0xFF1565C0.toInt()) { h.lots.add(Lot("Lot " + (h.lots.size + 1))); drawLots(); refreshTotals() }
                .apply { textSize = 15f }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(10f) })
            val pc = card()
            pc.addView(row(input(L.t("place").substringBefore(" ("), h.place, false) { h.place = it } to 1f))
            pc.addView(small(L.t("lot_price") + " – " + L.t("udhaar") + " / " + L.t("rokad_s")).apply { setPadding(0, dpi(6f), 0, 0) })
            val lb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun drawLb() { lb.removeAllViews(); payBlock(h, h.buyLine, false, lb) { drawLb() } }
            drawLb(); pc.addView(lb)
            body.addView(pc, cardLp())
        }

        // ---- vehicle details + price ----
        val vc = card()
        vc.addView(heading(if (h.isLot) L.t("vinfo") else L.t("buy_h"), 0xFF455A64.toInt()).apply { textSize = 15f })
        if (!h.isLot) vc.addView(small(L.t("buy_hint")).apply { setPadding(0, 0, 0, dpi(4f)) })
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
        // other goods bought (with the vehicle, or without it): any name, kg × rate or fixed
        val biLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val buyTot = small("", BLUE).apply { gravity = Gravity.END; typeface = Typeface.DEFAULT_BOLD; textSize = 16f }
        fun drawBi() {
            biLines.removeAllViews()
            h.buyItems.forEach { l -> biLines.addView(itemRow(h.buyItems, l) { drawBi(); refreshTotals() }) }
            buyTot.visibility = if (h.buyItems.isEmpty()) View.GONE else View.VISIBLE
        }
        if (!h.isLot) {
            vc.addView(small("📦 " + L.t("buy_other_h"), INK).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(10f), 0, dpi(2f)) })
            vc.addView(biLines)
            vc.addView(pill("＋ 📦 " + L.t("add_item"), false, GREEN) {
                val names = Store.parts.map { it.label() } + ("✎ " + L.t("other"))
                AlertDialog.Builder(this).setTitle(L.t("add_item")).setItems(names.toTypedArray()) { _, w ->
                    val b = Store.parts.getOrNull(w)
                    val nm = b?.label() ?: ""
                    h.buyItems.add(Line(b?.key ?: "", nm, b?.fixed ?: false, rateText = "", litre = b?.litre ?: false))
                    drawBi(); refreshTotals(); focusLast(biLines)
                }.show()
            }.apply { textSize = 14f; setPadding(dpi(6f), dpi(9f), dpi(6f), dpi(9f)) }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(4f) })
            vc.addView(buyTot, llp(MATCH_PARENT, WRAP_CONTENT))
            drawBi()
            buyTotalTv = buyTot
        }
        val buyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawBuy() { buyBox.removeAllViews(); payBlock(h, h.buyLine, false, buyBox) { drawBuy() } }
        if (!h.isLot) { drawBuy(); vc.addView(buyBox); body.addView(vc, cardLp()) }

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
        val mh = heading(if (h.isCo) "🏢 " + L.t("co_maal") else if (h.role == "seller") L.t("sell") else L.t("maal"), GREEN).apply { textSize = 15f }
        if (h.type == "haraji") mc.addView(row(mh to 1f, pill("🔨 " + L.t("haraji_do"), !h.coMode, 0xFF6A1B9A.toInt()) { if (h.coMode) { h.coMode = false; showEditor(h) } }
            .apply { textSize = 13f } to 0f))
        else mc.addView(mh)
        val mLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawMaal() {
            mLines.removeAllViews()
            h.maal.forEach { l -> mLines.addView(maalRow(h, l) { drawMaal(); refreshTotals() }) }
        }
        mc.addView(mLines)
        redrawMaalFn = { drawMaal(); refreshTotals() }
        if (h.boughtItems().isNotEmpty()) mc.addView(small("📦 " + L.t("sale_auto_h")).apply { setPadding(0, dpi(4f), 0, 0) })
        val partBtns = chips(Store.parts.map { it.label() } + L.t("other"), GREEN, 4) { i ->
            val b = Store.parts.getOrNull(i)
            val name = b?.label() ?: ""
            if (b != null) h.maal.indexOfFirst { sameItem(it, b.key, name) }.let { at ->
                if (at >= 0) { toast(L.t("dup") + ": " + name); focusAt(mLines, at); return@chips }
            }
            h.maal.add(Line(b?.key ?: "", name, b?.fixed ?: false, rateText = Store.lastRate[name] ?: "", litre = b?.litre ?: false))
            drawMaal(); refreshTotals()
            focusLast(mLines)
        }
        mc.addView(partBtns, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(6f) })
        partChips = partBtns
        partBtns.visibility = if (h.hasVehicle()) View.VISIBLE else View.GONE
        drawMaal()
        body.addView(mc, cardLp())

        if (h.isLot) {
            val sc = card()
            sc.addView(heading(L.t("lot_sale"), GREEN).apply { textSize = 15f })
            sc.addView(small(L.t("lot_sale_h")))
            sc.addView(input("₹", h.saleText, true) { h.saleText = it; refreshTotals() }, llp(MATCH_PARENT, WRAP_CONTENT))
            val sb2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun drawS() { sb2.removeAllViews(); payBlock(h, h.saleLine, true, sb2) { drawS() } }
            drawS(); sc.addView(sb2)
            body.addView(sc, cardLp())
        }
        if (h.type == "haraji") {
            // 4. how it is sold: auction (Rit A) or the whole vehicle to the company (Rit B)
            val sc = card()
            sc.addView(pill((if (h.coMode) "✓ " else "") + "🏢 " + L.t("mode_co"), h.coMode, 0xFF6A1B9A.toInt()) { h.coMode = !h.coMode; showEditor(h) }
                .apply { textSize = 14f; setPadding(dpi(8f), dpi(9f), dpi(8f), dpi(9f)) }, llp(MATCH_PARENT, WRAP_CONTENT))
            sc.addView(heading(if (h.coMode) L.t("co_give") else L.t("sale"), GREEN).apply { textSize = 15f; setPadding(0, dpi(8f), 0, dpi(4f)) })
            if (h.coMode) sc.addView(small(L.t("co_give_h")))
            sc.addView(input("₹", h.saleText, true) { h.saleText = it; refreshTotals() }, llp(MATCH_PARENT, WRAP_CONTENT))
            val sb3 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun drawS3() { sb3.removeAllViews(); payBlock(h, h.saleLine, true, sb3) { drawS3() } }
            drawS3(); sc.addView(sb3)
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
            if (h.coMode) {
                cc.addView(small(L.t("comm_who")).apply { setPadding(0, dpi(8f), 0, dpi(2f)); typeface = Typeface.DEFAULT_BOLD })
                cc.addView(row(pill("💰 " + L.t("mudi_h"), !h.commByCo, ORANGE) { if (h.commByCo) { h.commByCo = false; showEditor(h) } } to 1f,
                    pill("🏢 " + L.t("comm_co"), h.commByCo, ORANGE) { if (!h.commByCo) { h.commByCo = true; showEditor(h) } } to 1f))
                cc.addView(small(if (h.commByCo) L.t("comm_co_h") else L.t("comm_mudi_h")))
            }
            body.addView(cc, cardLp())

            // 6. company partners
            val pcd = card()
            pcd.addView(heading(L.t("company"), 0xFF6A1B9A.toInt()))
            val pLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun drawP() {
                pLines.removeAllViews()
                h.partners.forEach { p ->
                    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(0xFFF8F3FB.toInt(), 10f, 0xFFD1C4E9.toInt()); setPadding(dpi(6f), dpi(4f), dpi(4f), dpi(4f)) }
                    val pn = input(L.t("partner"), p.name, false) { p.name = it; refreshTotals() }.apply { textSize = 15f; tag = "focus" }
                    box.addView(row(pn to 1.6f,
                        input(L.t("pshare"), p.shareText, true) { p.shareText = it; refreshTotals() }.apply { gravity = Gravity.END } to 1f,
                        xBtn { confirmRemove { h.partners.remove(p); drawP(); refreshTotals() } } to 0f))
                    val pm = reqMobile("📞 " + L.t("cmobile") + " *", p.mobile) { p.mobile = it }
                    box.addView(row(pm to 1f, pill("👥", false, BLUE) { pickBuyer { n, m -> if (pn.text.isBlank()) pn.setText(n); pm.setText(m) } }
                        .apply { textSize = 16f; setPadding(dpi(8f), dpi(4f), dpi(8f), dpi(4f)) } to 0f))
                    pLines.addView(box, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(6f) })
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
        if (h.type == "haraji" || h.isLot) {
            sumSale = sumRow(when { h.isLot -> L.t("lot_sale"); h.isCo -> L.t("co_give"); else -> L.t("sale_s") })
            sumBikri = sumRow(if (h.isCo) L.t("co_sales") else L.t("bikri"), true, GREEN)
            sumComm = sumRow(L.t("comm_s") + if (h.isCo) "  (" + (if (h.commByCo) "🏢" else "💰 " + L.t("mudi_h")) + ")" else "", false, ORANGE)
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

        // ---- Final / lock ----
        val ls = lockState(h)
        if (view != null) {
            freeze(body)
            val ban = card().apply { background = round(0xFFE3F2FD.toInt(), 12f, BLUE) }
            ban.addView(small("👁 " + L.t("sh_view"), BLUE).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
            ban.addView(small(L.t("sh_by") + ": " + view.from.ifBlank { "—" } + "  📞 " + view.fromMobile + "\n" + L.t("sh_upd") + ": " + Bill.dateText(view.t), INK))
            body.addView(ban, 0, cardLp())
        } else if (ls > 0 && h.id !in unlocked) {
            freeze(body)
            val ban = card().apply { background = round(if (ls == 2) 0xFFFFEBEE.toInt() else 0xFFE8F5E9.toInt(), 12f, if (ls == 2) RED else GREEN) }
            ban.addView(small("✅ " + L.t("final_s") + ": " + Bill.dateText(h.finalAt), INK).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD })
            if (ls == 2) ban.addView(small("🔒 " + L.t("lock_forever"), RED).apply { textSize = 14f })
            // normal user: correction only with the admin's permission
            if (LOGIN_ON && !Account.isAdmin(this)) {
                if (Unlock.asked(this, h.id)) {
                    ban.addView(small("⏳ " + L.t("unl_wait"), ORANGE).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(8f), 0, 0) })
                    every(10000L, { Unlock.check(this) }) { r ->
                        if (r.isNullOrEmpty()) return@every true
                        Store.save(this)
                        r.firstOrNull { it.first == h.id }?.let { (_, ok) -> toast(if (ok) "✅ " + L.t("unl_yes") else "❌ " + L.t("unl_no")); showEditor(h); false } ?: true
                    }
                } else ban.addView(pill("🙏 " + L.t("unl_ask"), false, ORANGE) {
                    Thread {
                        val ok = try { Unlock.ask(this, h, hTitle(h).ifBlank { h.vehicleInfo() } + "  " + Bill.dateText(h.time).substringBefore("  ")) } catch (_: Exception) { false }
                        ui.post { toast(if (ok) "📤 " + L.t("unl_sent") else L.t("otp_net")); if (ok && screen == "edit") showEditor(h) }
                    }.start()
                }.apply { textSize = 15f; setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f)) }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
            }
            if (ls != 2) {
                ban.addView(small(L.t("lock_info") + if (h.type == "haraji") "\n⏱ " + L.t("lock_48") + " " + Bill.dateText(h.finalAt + 48L * 3600_000L) else ""))
                // only the admin phone can open a final hisab (normal users: no password asked)
                if (!LOGIN_ON || Account.isAdmin(this)) ban.addView(pill("🔓 " + L.t("lock_open"), false, BLUE) { askAdminPin { unlocked.add(h.id); showEditor(h) } }
                    .apply { textSize = 15f; setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f)) }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(8f) })
            }
            body.addView(ban, 0, cardLp())
        } else if (h.finalAt == 0L) {
            body.addView(bigButton(L.t("final_btn"), 0xFF1B5E20.toInt()) {
                if (!hasContent(h)) return@bigButton toast(L.t("none"))
                val miss = missingMobile(h)
                if (miss.isNotEmpty()) return@bigButton AlertDialog.Builder(this).setTitle("📞 " + L.t("mob_need"))
                    .setMessage(miss.joinToString("\n") { "• $it" }).setPositiveButton("OK", null).show().let { }
                AlertDialog.Builder(this).setTitle(L.t("final_btn")).setMessage(L.t("final_q"))
                    .setPositiveButton(L.t("yes")) { _, _ -> h.finalAt = System.currentTimeMillis(); autoSave(); Store.save(this); unlocked.remove(h.id)
                        Thread { try { Share.publish(this, h) } catch (_: Exception) {} }.start(); showEditor(h) }
                    .setNegativeButton(L.t("no"), null).show()
            }, llp(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(12f) })
        } else {
            // opened by admin: lock again when done
            body.addView(pill("🔒 " + L.t("lock_again"), true, GREEN) { autoSave(); Store.save(this); unlocked.remove(h.id); showEditor(h) }
                .apply { textSize = 15f; setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f)) }, 0, cardLp())
        }

        // action bar
        bottom.visibility = View.VISIBLE
        fun act(t: String, color: Int, a: () -> Unit) = bottom.addView(bigButton(t, color, a).apply { textSize = 15f },
            llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(3f), 0, dpi(3f), 0) })
        if (view == null) act(L.t("save"), BLUE) { saveNow(h) }
        act(L.t("jpg"), ORANGE) { exportJpg(h) }
        act(L.t("pdf"), RED) { exportPdf(h) }
        act(L.t("share"), GREEN) { shareText(h) }
        if (view == null && Store.hisabs.any { it === h } && (h.finalAt == 0L || (LOGIN_ON && Account.isAdmin(this)))) act("🗑", 0xFF78909C.toInt()) { askDelete(h) { editing = null; showHome() } }
    }

    /** each lot's total line in the editor (updated with the totals) */
    private val lotTotals = mutableListOf<Pair<Lot, TextView>>()

    /** a thing inside a lot: name (any), kg × rate / litre × rate / fixed */
    private fun lotItemRow(t: Lot, l: Line, redraw: () -> Unit): View = itemRow(t.items, l, redraw)

    /** one bought item (lot or gaadi / haraji purchase): name, kg / litre / fixed, amount */
    private fun itemRow(list: MutableList<Line>, l: Line, redraw: () -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(0xFFF7FAF7.toInt(), 10f, 0xFFC8E6C9.toInt()); setPadding(dpi(8f), dpi(4f), dpi(4f), dpi(6f)) }
        val mode = when { l.fixed -> L.t("fix"); l.litre -> L.t("ltr"); else -> L.t("kg") }
        box.addView(row(small("📦", INK) to 0f, input(L.t("item_name"), l.name, false) { l.name = it; refreshTotals() }.apply { tag = "focus" } to 1f,
            pill(mode + " ⇄", false, GREEN) {
                when { l.fixed -> { l.fixed = false; l.litre = false }; l.litre -> { l.fixed = true; l.litre = false }; else -> l.litre = true }
                redraw()
            }.apply { textSize = 12f } to 0f,
            xBtn { confirmRemove { list.remove(l); redraw() } } to 0f))
        val res = small("").apply { typeface = Typeface.DEFAULT_BOLD; setTextColor(GREEN); gravity = Gravity.END }
        fun upd() { res.text = "= " + money(l.value()); refreshTotals() }
        if (l.fixed) box.addView(row(small(L.t("fixamt")) to 1f, input("₹", l.amountText, true) { l.amountText = it; upd() }.apply { gravity = Gravity.END } to 1.2f))
        else {
            box.addView(row(input(if (l.litre) L.t("ltr") else L.t("kg"), l.kgText, true) { l.kgText = it; upd() } to 1f, small("×") to 0f,
                input(L.t("rate"), l.rateText, true) { l.rateText = it; upd() } to 1f, res to 1f))
        }
        res.text = "= " + money(l.value())
        return box
    }

    /** hisab ids opened by admin PIN (only while the app is open) */
    private val unlocked = mutableSetOf<Long>()

    /** final hisab: nothing can be typed or tapped */
    private fun freeze(v: View) {
        if (v is EditText) { v.isEnabled = false; v.setTextColor(INK); return }
        if (v.hasOnClickListeners()) { v.setOnClickListener(null); v.isClickable = false }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) freeze(v.getChildAt(i))
    }

    private fun pinHash(p: String) = java.security.MessageDigest.getInstance("SHA-256").digest(("kmh-pin:" + p).toByteArray()).joinToString("") { "%02x".format(it) }

    /** admin PIN (set the first time it is needed) */
    private fun askAdminPin(ok: () -> Unit) {
        val saved = getSharedPreferences("kabadi_calc", MODE_PRIVATE).getString("admPin", "").orEmpty()
        if (saved.isEmpty()) return setAdminPin(ok)
        val e = pinInput(L.t("acc_enter"))
        val box = LinearLayout(this).apply { setPadding(dpi(20f), dpi(8f), dpi(20f), 0); addView(e, llp(MATCH_PARENT, WRAP_CONTENT)) }
        AlertDialog.Builder(this).setTitle("🔐 " + L.t("adm_pin")).setView(box)
            .setPositiveButton(L.t("acc_open")) { _, _ -> if (pinHash(e.text.toString()) == saved) ok() else toast(L.t("acc_wrong")) }
            .setNegativeButton(L.t("back"), null).show()
    }

    private fun setAdminPin(done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20f), dpi(8f), dpi(20f), 0) }
        val p1 = pinInput(L.t("acc_pin")); val p2 = pinInput(L.t("acc_pin2"))
        box.addView(small(L.t("adm_pin_h")).apply { setPadding(0, 0, 0, dpi(8f)) }); box.addView(p1, gap()); box.addView(p2, gap())
        AlertDialog.Builder(this).setTitle("🔐 " + L.t("adm_pin_new")).setView(box)
            .setPositiveButton(L.t("save")) { _, _ ->
                val a = p1.text.toString()
                if (a.length == 4 && a == p2.text.toString()) {
                    getSharedPreferences("kabadi_calc", MODE_PRIVATE).edit().putString("admPin", pinHash(a)).apply(); toast(L.t("saved_ok")); done()
                } else toast(L.t("acc_bad_pin"))
            }.setNegativeButton(L.t("back"), null).show()
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

    /** mobile that must be filled (red border until 10 digits) */
    private fun reqMobile(hint: String, value: String, onChange: (String) -> Unit): EditText {
        lateinit var e: EditText
        fun mark() { val ok = digits10(e.text.toString()).length == 10
            e.background = round(if (ok) 0xFFF7F9FA.toInt() else 0xFFFFEBEE.toInt(), 8f, if (ok) 0xFFCFD8DC.toInt() else RED) }
        e = mobileInput(hint, value) { onChange(it); mark() }
        mark()
        return e
    }

    /** credit days of one line (empty = the haraji's common time) and its due date */
    private fun muddatRow(h: Hisab, l: Line): View {
        val due = small("", ORANGE).apply { typeface = Typeface.DEFAULT_BOLD }
        fun upd() { due.text = Bill.dueOf(h, l).let { if (it.isEmpty()) "" else "→ $it" } }
        val hint = h.muddatText.let { if (it.isBlank()) "30" else if (it.endsWith("m")) it.dropLast(1) + " " + L.t("mahina") else it + " " + L.t("din") }
        val r = row(small(if (h.type == "haraji" || h.isLot) L.t("muddat_own") else L.t("days")) to 1.2f,
            input(hint, l.daysText, true) { l.daysText = it; upd() }.apply { gravity = Gravity.CENTER } to 0.8f, due to 1f)
        upd()
        return r
    }

    /** cash / credit for the vehicle price (we pay) or the sale (we get): name, mobile, credit time, installments */
    private fun payBlock(h: Hisab, l: Line, lena: Boolean, box: LinearLayout, redraw: () -> Unit) {
        box.addView(row(pill(L.t("rokad_s"), !l.udhaar, GREEN) { l.pay = "rokad"; redraw(); refreshTotals() } to 0f,
            pill(L.t("udhaar"), l.udhaar, ORANGE) { l.pay = "udhaar"; redraw(); refreshTotals() } to 0f, View(this) to 1f).apply { setPadding(0, dpi(6f), 0, 0) })
        if (!l.udhaar && !(lena && h.type == "haraji" && !h.isCo)) return
        val req = lena && h.type == "haraji" && !h.isCo
        val pn = input("👤 " + L.t(if (lena) "get_from" else "pay_to"), l.cName, false) { l.cName = it }.apply { textSize = 15f }
        val pm = if (req) reqMobile("📞 " + L.t("cmobile") + " *", l.cMobile) { l.cMobile = it } else mobileInput("📞 " + L.t("cmobile"), l.cMobile) { l.cMobile = it }
        box.addView(row(pn to 1.2f, pm to 1f, pill("👥", false, BLUE) { pickBuyer { n, m -> pn.setText(n); pm.setText(m) } }
            .apply { textSize = 16f; setPadding(dpi(8f), dpi(4f), dpi(8f), dpi(4f)) } to 0f))
        if (!l.udhaar) return
        box.addView(muddatRow(h, l))
        kistBlock(l, box, lena, redraw)
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
        AlertDialog.Builder(this).setTitle(L.ln(l) + "  •  " + L.t("left") + " " + money(l.remaining())).setView(pad)
            .setPositiveButton(L.t("add")) { _, _ ->
                val a = evalExpr(et.text.toString())
                if (a.isFinite() && a > 0) { l.pays.add(Pay(System.currentTimeMillis(), et.text.toString())); done() }
            }
            .setNegativeButton(L.t("no"), null).show()
    }

    // ================= CREDIT BOOK =================
    private fun showKhata() {
        val from = editing.takeIf { viewOnly == null }; if (viewOnly != null) { viewOnly = null; editing = null }
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
                sb.append(if (d.lena) "⬇ " else "⬆ ").append(d.l.cName.ifBlank { if (d.lena) hTitle(d.h) else L.ln(d.l) })
                    .append(" • ").append(L.ln(d.l)).append(" • ").append(money(d.left))
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
                text = (if (d.lena) "⬇ " else "⬆ ") + d.l.cName.ifBlank { if (d.lena) hTitle(d.h) else L.ln(d.l) }.ifBlank { "—" }
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
            c.addView(small(listOf(L.ln(d.l), hTitle(d.h), status).filter { it.isNotBlank() }.joinToString("  •  "),
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
        sb.append(L.ln(d.l)).append(": ").append(money(d.l.value())).append("\n")
        if (d.h.vehicleInfo().isNotBlank()) sb.append(d.h.vehicleInfo()).append("\n")
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
        // name | Kg / Litre / Fix | ✕   (goods that were bought: name / mode come from the purchase, cannot be removed)
        val mode = when { l.fixed -> L.t("fix"); l.litre -> L.t("ltr"); else -> L.t("kg") }
        val linked = l.link.isNotEmpty() && h.boughtOf(l) != null
        val nameTv = TextView(this).apply { text = "📦 " + l.name; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(INK); setPadding(dpi(4f), dpi(6f), 0, dpi(6f)) }
        val infoTv = small("").apply { textSize = 12.5f }
        if (linked) box.addView(row(nameTv to 1f, small("🔗 " + L.t("from_buy"), BLUE) to 0f))
        else box.addView(row(input(L.t("name_q"), l.name, false) { l.name = it }.apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD } to 1f,
            pill("$mode ▾", true, GREEN) {
                when { l.fixed -> { l.fixed = false; l.litre = false }; l.litre -> l.fixed = true; else -> l.litre = true }
                redraw(); refreshTotals()
            } to 0f,
            xBtn { confirmRemove { h.maal.remove(l); redraw() } } to 0f))
        var kgIn: EditText? = null
        if (l.fixed) {
            box.addView(row(input(L.t("amount"), l.amountText, true) { l.amountText = it; refreshTotals() }.apply { tag = "focus"; gravity = Gravity.END } to 1f))
        } else {
            val amt = TextView(this).apply { textSize = 16f; setTextColor(GREEN); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END }
            fun upd() { amt.text = "= " + money(l.value()) }
            val unit = if (l.litre) L.t("ltr") else L.t("kg")
            val rateHint = if (l.litre) L.t("rate_l") else L.t("rate")
            kgIn = input(unit, l.kgText, true) { l.kgText = it; upd(); refreshTotals() }.apply { tag = "focus"; gravity = Gravity.END }
            box.addView(row(kgIn to 1f,
                small("×") to 0f,
                input(rateHint, l.rateText, true) { l.rateText = it; upd(); refreshTotals() }.apply { gravity = Gravity.END } to 1f,
                amt to 1.2f))
            upd()
        }
        if (linked) {
            linkInfo(h, l).let { (t, col) -> infoTv.text = t; infoTv.setTextColor(col) }
            box.addView(infoTv)
            linkViews[l.link] = Triple(nameTv, kgIn, infoTv)
        }
        if (h.role == "seller") sellerBlock(h, l, box, redraw)
        return wrap(box)
    }

    /** seller: who took this item (name, mobile), cash / credit, credit days, installments; auction: guarantor + shop */
    private fun sellerBlock(h: Hisab, l: Line, box: LinearLayout, redraw: () -> Unit) {
        val who = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(2f), 0, 0) }
        val nm = input("👤 " + L.t("cname"), l.cName, false) { l.cName = it }.apply { textSize = 15f }
        val mob = if (h.type == "haraji") reqMobile("📞 " + L.t("cmobile") + " *", l.cMobile) { l.cMobile = it }
            else mobileInput("📞 " + L.t("cmobile"), l.cMobile) { l.cMobile = it }
        val pick = pill("👥", false, BLUE) { pickBuyer { n, m -> nm.setText(n); mob.setText(m) } }.apply { textSize = 16f; setPadding(dpi(8f), dpi(4f), dpi(8f), dpi(4f)) }
        val send = pill("💬", true, 0xFF25D366.toInt()) {
            val k = partyKey(l.cName, l.cMobile)
            if (digits10(l.cMobile).length != 10) return@pill toast(L.t("acc_bad_mobile"))
            autoSave(); Store.save(this)
            whatsapp(l.cMobile, buyerMessage(h, k))
        }.apply { textSize = 16f; setPadding(dpi(8f), dpi(4f), dpi(8f), dpi(4f)) }
        who.addView(row(nm to 1.2f, mob to 1f, pick to 0f, send to 0f))
        who.addView(row(pill(L.t("rokad_s"), !l.udhaar, GREEN) { l.pay = "rokad"; redraw(); refreshTotals() } to 0f,
            pill(L.t("udhaar"), l.udhaar, ORANGE) { l.pay = "udhaar"; redraw(); refreshTotals() } to 0f, View(this) to 1f))
        if (l.udhaar) {
            who.addView(muddatRow(h, l))
            kistBlock(l, who, true, redraw)
        }
        if (h.type == "haraji") {
            who.addView(row(input(L.t("gname"), l.gName, false) { l.gName = it }.apply { textSize = 15f } to 1.2f,
                mobileInput(L.t("gmobile"), l.gMobile) { l.gMobile = it } to 1f))
            who.addView(row(input(L.t("shop"), l.shop, false) { l.shop = it }.apply { textSize = 15f } to 1f))
        }
        box.addView(who)
    }

    /** choose a buyer: from earlier buyers or the phone's contacts */
    private var contactCb: ((String, String) -> Unit)? = null
    private fun pickBuyer(done: (String, String) -> Unit) {
        val ps = parties(Store.hisabs)
        val items = listOf("📇 " + L.t("contacts")) + ps.map { p -> p.name.ifBlank { "—" } + (if (p.mobile.isNotBlank()) "  •  " + p.mobile else "") }
        AlertDialog.Builder(this).setTitle(L.t("pick_buyer")).setItems(items.toTypedArray()) { _, w ->
            if (w == 0) {
                contactCb = done
                try { startActivityForResult(Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 31) }
                catch (_: Exception) { toast("✕") }
            } else ps[w - 1].let { done(it.name, it.mobile) }
        }.show()
    }

    /** WhatsApp message for one buyer: only what he took from us in this hisab (no cost / profit) */
    private fun buyerMessage(h: Hisab, key: String): String {
        val lines = buyerLines(h, key)
        val sb = StringBuilder()
        sb.append("*").append(Store.owner.ifBlank { L.t("app") }).append("*\n")
        sb.append(Bill.dateText(h.time).substringBefore("  ")).append("\n")
        if (h.vehicleInfo().isNotBlank()) sb.append("🚚 ").append(h.vehicleInfo()).append(if (h.vehicle.isNotBlank()) " • " + h.vehicle else "").append("\n")
        lines.firstOrNull()?.cName?.let { if (it.isNotBlank()) sb.append("👤 ").append(it).append("\n") }
        sb.append("\n")
        lines.forEach { l ->
            sb.append("• ").append(L.ln(l)).append(": ")
            if (!l.fixed && l.calc == null) sb.append(plain(l.kg)).append(if (l.litre) " L × ₹" else " kg × ₹").append(plain(l.rate)).append(" = ")
            sb.append(money(l.value()))
            sb.append(if (l.udhaar) "  (" + L.t("udhaar") + ")" else "  (" + L.t("rokad_s") + ")").append("\n")
        }
        val tot = lines.sumOf { it.value() }; val left = lines.sumOf { it.remaining() }
        sb.append("\n*").append(L.t("book_total")).append(": ").append(money(tot)).append("*\n")
        if (left > 0.004) {
            if (tot - left > 0.004) sb.append(L.t("got")).append(": ").append(money(tot - left)).append("\n")
            sb.append("*").append(L.t("left")).append(": ").append(money(left)).append("*\n")
            lines.filter { it.remaining() > 0.004 }.mapNotNull { dueTime(h, it) }.minOrNull()?.let {
                sb.append("⏳ ").append(L.t("due")).append(": ").append(Bill.dateText(it).substringBefore("  ")).append("\n") }
        }
        if (Store.mobile.isNotBlank()) sb.append("\n📞 ").append(Store.mobile)
        return sb.toString()
    }

    /** one message with everything a buyer still owes (all hisab) */
    private fun partyMessage(p: Party): String {
        val sb = StringBuilder()
        sb.append("*").append(Store.owner.ifBlank { L.t("app") }).append("*\n")
        sb.append(L.t("party_stmt")).append(" – ").append(p.name).append("\n\n")
        p.items.filter { it.second.remaining() > 0.004 }.forEach { (h, l) ->
            sb.append("• ").append(Bill.dateText(h.time).substringBefore("  ")).append("  ").append(L.ln(l))
            if (h.vehicleInfo().isNotBlank()) sb.append(" (").append(h.vehicleInfo()).append(")")
            sb.append(": ").append(money(l.remaining()))
            dueTime(h, l)?.let { sb.append("  ⏳ ").append(Bill.dateText(it).substringBefore("  ")) }
            sb.append("\n")
        }
        sb.append("\n*").append(L.t("left")).append(": ").append(money(p.left())).append("*\n")
        if (Store.mobile.isNotBlank()) sb.append("📞 ").append(Store.mobile)
        return sb.toString()
    }

    // ================= SHARED: hisab of others where I am mudi malik / khedut =================
    private fun showShared() {
        autoSave(); editing = null; viewOnly = null
        val body = setScreen("shared", "👁 " + L.t("sh_title"), null)
        val me = Share.myMobile(this)
        val info = card()
        if (me.length != 10) {
            info.addView(small(L.t("sh_need_mob"), RED).apply { textSize = 15f })
            var m = ""
            info.addView(row(mobileInput("98xxxxxxxx", "") { m = it } to 1f, pill(L.t("save"), true, GREEN) {
                if (m.length == 10) { Store.mobile = m; Store.save(this); showShared() } else toast(L.t("acc_bad_mobile")) } to 0f))
        } else info.addView(small("📞 " + L.t("sh_my") + ": " + me + "\n" + L.t("sh_info"), INK).apply { textSize = 14f })
        val st = small("", MUTED)
        info.addView(row(st to 1f, pill("⟳ " + L.t("sh_refresh"), false, BLUE) {
            st.text = "⏳"
            Thread { val n = try { Share.fetch(this) } catch (_: Exception) { -1 }; ui.post { if (n > 0) Store.save(this); if (n >= 0) showShared() else st.text = "📶 " + L.t("otp_net") } }.start()
        } to 0f))
        body.addView(info, cardLp())
        if (Store.shared.isEmpty()) body.addView(small(L.t("none")).apply { gravity = Gravity.CENTER; setPadding(0, dpi(20f), 0, 0) }, llp(MATCH_PARENT, WRAP_CONTENT))
        Store.shared.sortedByDescending { it.h.time }.forEach { sh ->
            val h = sh.h
            val asMudi = Otp.mobile10(h.mudiMobile) == me; val asKhed = Otp.mobile10(h.khedMobile) == me
            val mine = (if (asMudi) h.mudiShare() else 0.0) + (if (asKhed) h.khedShare() else 0.0)
            val c = card()
            c.addView(row(small(hTitle(h), INK).apply { textSize = 17f; typeface = Typeface.DEFAULT_BOLD } to 1f,
                small((if (mine >= 0) L.t("profit") else L.t("loss")) + " " + money(Math.abs(mine)), if (mine >= 0) GREEN else RED).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD } to 0f))
            c.addView(small(listOfNotNull(if (asMudi) "💰 " + L.t("mudi_h") + " " + plain(h.mudiPct) + "%" else null, if (asKhed) "🚚 " + L.t("khed_h") + " " + plain(h.khedPct) + "%" else null,
                h.vehicleInfo().ifBlank { null }, Bill.dateText(h.time).substringBefore("  "), if (h.finalAt > 0) "✅" else null).joinToString("  •  "), INK))
            c.addView(small(L.t("sh_by") + ": " + sh.from.ifBlank { "—" } + "  📞 " + sh.fromMobile))
            c.setOnClickListener { showEditor(h, sh) }
            body.addView(c, cardLp())
        }
    }

    // ================= VASULI: daily reminders from 3 days before the credit time =================
    private fun showReminders() {
        autoSave(); editing = null
        val body = setScreen("remind", "🔔 " + L.t("rem_t"), null)
        val groups = reminders(Store.hisabs).groupBy { it.key.ifEmpty { "x:" + it.name } }
        body.addView(small(L.t("rem_h")).apply { setPadding(dpi(4f), 0, 0, dpi(8f)) })
        if (groups.isEmpty()) body.addView(small(L.t("no_due")).apply { gravity = Gravity.CENTER; setPadding(0, dpi(20f), 0, 0) }, llp(MATCH_PARENT, WRAP_CONTENT))
        val canSms = false  // no SMS permission (Play Protect blocks apps that ask for it)
        if (groups.isNotEmpty() && canSms) body.addView(bigButton("📩 " + L.t("rem_sms_all"), 0xFF455A64.toInt()) {
            val n = Reminders.sendSms(this, force = true); toast("📩 $n")
        }.apply { textSize = 15f }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(10f) })
        val today = Reminders.endOfToday()
        groups.values.forEach { list ->
            val f = list.first()
            val c = card().apply { if (list.any { it.due <= today }) background = round(0xFFFFEBEE.toInt(), 12f, 0xFFEF9A9A.toInt()) }
            c.addView(row(small(f.name.ifBlank { "—" }, INK).apply { textSize = 17f; typeface = Typeface.DEFAULT_BOLD } to 1f,
                small(f.mobile.ifBlank { "📞 ?" }, if (f.mobile.isBlank()) RED else MUTED).apply { textSize = 14f } to 0f))
            list.forEach { r ->
                val what = if (r.partner != null) "🏢 " + L.t("role_co") + " (" + plain(r.partner.share) + "%) – " + (if (r.amount >= 0) L.t("profit") else L.t("loss_pay"))
                    else L.ln(r.line!!) + " – " + L.t("left")
                val days = ((r.due - System.currentTimeMillis()) / 86400000L).toInt()
                val when_ = if (r.due <= today) "⚠ " + L.t("overdue") else "⏳ " + (days + 1) + " " + L.t("din")
                c.addView(small(what + ": " + money(Math.abs(r.amount)) + "   " + when_ + " (" + Bill.dateText(r.due).substringBefore("  ") + ")",
                    if (r.due <= today) RED else INK).apply { textSize = 14f; setPadding(0, dpi(3f), 0, 0) })
                c.addView(small(listOf(r.h.vehicleInfo(), r.h.vehicle).filter { it.isNotBlank() }.joinToString(" • ")))
            }
            val acts = LinearLayout(this).apply { setPadding(0, dpi(6f), 0, 0) }
            fun act(t: String, color: Int, a: () -> Unit) = acts.addView(TextView(this).apply {
                text = t; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = round(color, 14f)
                setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f)); setOnClickListener { a() }
            }, llp(0, WRAP_CONTENT, 1f).apply { setMargins(dpi(3f), 0, dpi(3f), 0) })
            val msg = Reminders.message(list)
            if (f.mobile.length == 10) {
                act("💬 WhatsApp", 0xFF25D366.toInt()) { whatsapp(f.mobile, msg) }
                act("📩 SMS", BLUE) { try { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + f.mobile)).putExtra("sms_body", msg)) } catch (_: Exception) {} }
            }
            if (list.any { it.partner != null }) act("✓ " + L.t("rem_done"), GREEN) {
                list.forEach { it.partner?.done = true }; Store.save(this); showReminders()
            }
            act(L.t("open"), 0xFF78909C.toInt()) { editorBack = { showReminders() }; showEditor(f.h) }
            c.addView(acts)
            body.addView(c, cardLp())
        }
    }

    // ================= PARTY (buyers) =================
    private fun showParties() {
        autoSave(); editing = null
        val body = setScreen("party", "📒 " + L.t("party_t"), null)
        val all = parties(Store.hisabs)
        val tot = card()
        tot.addView(small(L.t("party_n") + ": " + all.size, INK).apply { textSize = 15f })
        tot.addView(TextView(this).apply { text = "⬇ " + L.t("lena") + ": " + money(all.sumOf { it.left() }); textSize = 18f; setTextColor(GREEN); typeface = Typeface.DEFAULT_BOLD })
        tot.addView(TextView(this).apply { text = "⬆ " + L.t("dena") + ": " + money(all.sumOf { it.dena() }); textSize = 18f; setTextColor(RED); typeface = Typeface.DEFAULT_BOLD })
        body.addView(tot, cardLp())
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun fill(q: String) {
            list.removeAllViews()
            val today = Reminders.endOfToday()
            all.filter { q.isBlank() || norm(it.name).contains(norm(q)) || it.mobile.contains(q.filter { c -> c.isDigit() }.ifEmpty { "~" }) }.forEach { p ->
                val c = card()
                val left = p.left(); val dn = p.dena()
                c.addView(row(TextView(this).apply { text = p.name.ifBlank { p.mobile }; textSize = 17f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; maxLines = 1 } to 1f,
                    TextView(this).apply {
                        text = listOfNotNull(if (left > 0.004) "⬇ " + money(left) else null, if (dn > 0.004) "⬆ " + money(dn) else null).joinToString("  ").ifEmpty { "✓" }
                        textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(if (left > 0.004) GREEN else if (dn > 0.004) RED else GREEN) } to 0f))
                val nd = p.nextDue()
                c.addView(small(listOf(p.mobile, (p.items.size + p.buys.size).toString() + " " + L.t("items_s"),
                    if (p.items.isNotEmpty()) L.t("p_sold") + " " + money(p.total()) else "", if (p.buys.isNotEmpty()) L.t("p_bought") + " " + money(p.buyTotal()) else "",
                    nd?.let { (if (it <= today) "⚠ " else "⏳ ") + Bill.dateText(it).substringBefore("  ") } ?: "").filter { it.isNotBlank() }.joinToString("  •  "),
                    if (nd != null && nd <= today) RED else MUTED))
                c.setOnClickListener { showParty(p.key) }
                list.addView(c, cardLp())
            }
            if (list.childCount == 0) list.addView(small(L.t("none")).apply { gravity = Gravity.CENTER; setPadding(0, dpi(20f), 0, 0) }, llp(MATCH_PARENT, WRAP_CONTENT))
        }
        body.addView(input("🔍 " + L.t("f_search"), "", false) { fill(it) }, llp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dpi(8f) })
        body.addView(list)
        fill("")
    }

    private fun showParty(key: String) {
        val p = parties(Store.hisabs).firstOrNull { it.key == key } ?: return showParties()
        editorBack = { showParty(key) }
        val body = setScreen("party1", p.name.ifBlank { p.mobile }, { editorBack = null; showParties() })
        val top = card()
        top.addView(small("📞 " + p.mobile.ifBlank { "—" }, INK).apply { textSize = 15f })
        if (p.items.isNotEmpty()) {
            top.addView(small(L.t("p_sold") + ": " + money(p.total()) + "   •   " + L.t("got") + ": " + money(p.got()), INK).apply { textSize = 15f })
            top.addView(TextView(this).apply { text = "⬇ " + L.t("lena") + ": " + money(p.left()); textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(GREEN) })
        }
        if (p.buys.isNotEmpty()) {
            top.addView(small(L.t("p_bought") + ": " + money(p.buyTotal()) + "   •   " + L.t("gave") + ": " + money(p.buyTotal() - p.dena()), INK).apply { textSize = 15f; setPadding(0, dpi(4f), 0, 0) })
            top.addView(TextView(this).apply { text = "⬆ " + L.t("dena") + ": " + money(p.dena()); textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(RED) })
        }
        if (p.mobile.length == 10) top.addView(row(
            bigButton("💬 " + L.t("party_send"), 0xFF25D366.toInt()) { whatsapp(p.mobile, partyMessage(p)) }.apply { textSize = 15f } to 1f,
            bigButton("📞", BLUE) { try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.mobile))) } catch (_: Exception) {} }.apply { textSize = 15f } to 0f))
        body.addView(top, cardLp())
        (p.items.map { Triple(it.first, it.second, false) } + p.buys.map { Triple(it.first, it.second, true) }).sortedByDescending { it.first.time }.forEach { (h, l, buy) ->
            val c = card()
            c.addView(small(if (buy) "⬆ " + L.t("p_bought") else "⬇ " + L.t("p_sold"), if (buy) RED else GREEN).apply { typeface = Typeface.DEFAULT_BOLD })
            c.addView(row(small(L.ln(l), INK).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD } to 1f,
                small(money(l.value()), INK).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD } to 0f))
            val det = listOfNotNull(Bill.dateText(h.time).substringBefore("  "), h.vehicleInfo().ifBlank { null },
                if (!l.fixed && l.calc == null) plain(l.kg) + (if (l.litre) " L" else " kg") + " × " + plain(l.rate) else null).joinToString("  •  ")
            c.addView(small(det))
            if (l.udhaar) {
                val st = if (l.remaining() < 0.005) "✓ " + L.t("chukaya") else L.t("left") + " " + money(l.remaining()) + (dueTime(h, l)?.let { "  ⏳ " + Bill.dateText(it).substringBefore("  ") } ?: "")
                c.addView(row(small(L.t("udhaar") + " • " + st, if (l.remaining() < 0.005) GREEN else RED).apply { typeface = Typeface.DEFAULT_BOLD } to 1f,
                    if (l.remaining() > 0.004) pill(L.t("kist"), true, BLUE) { askKist(l) { Store.save(this); showParty(key) } } to 0f else View(this) to 0f))
            } else c.addView(small(L.t("rokad_s"), GREEN))
            c.setOnClickListener { showEditor(h) }
            body.addView(c, cardLp())
        }
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
            if (Bill.dueOf(h, l).isNotEmpty()) sb.append(" • ").append(L.t("due")).append(": ").append(Bill.dueOf(h, l))
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
            val types = listOf("gaadi", "haraji", "lot")
            pills.addView(row(
                fbtn(L.t("f_mudi"), rf.mudi) { choose(L.t("f_mudi"), people) { rf.mudi = if (it < 0) "" else people[it] } } to 1f,
                fbtn(L.t("f_brand"), rf.brand) { choose(L.t("f_brand"), brands) { rf.brand = if (it < 0) "" else brands[it] } } to 1f))
            pills.addView(row(
                fbtn(L.t("f_year"), if (rf.year == 0) "" else rf.year.toString()) { choose(L.t("f_year"), ys.map { it.toString() }) { rf.year = if (it < 0) 0 else ys[it] } } to 1f,
                fbtn(L.t("f_month"), if (rf.month == 0) "" else monthNames[rf.month - 1].take(3)) { choose(L.t("f_month"), monthNames) { rf.month = it + 1 } } to 1f))
            pills.addView(row(
                fbtn(L.t("f_tyre"), rf.tyres) { choose(L.t("f_tyre"), tyres.map { it + " " + L.t("tyre_s") }) { rf.tyres = if (it < 0) "" else tyres[it] } } to 1f,
                fbtn(L.t("f_type"), when (rf.type) { "gaadi" -> L.t("gaadi_s"); "haraji" -> L.t("haraji"); "lot" -> L.t("lot"); else -> "" }) {
                    choose(L.t("f_type"), listOf(L.t("gaadi_s"), L.t("haraji"), L.t("lot"))) { rf.type = if (it < 0) "" else types[it] } } to 1f))
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
            if (rf.mudi.isNotBlank()) {
                val book = personBook(items, rf.mudi)
                if (book.isNotEmpty()) {
                    val bc = card().apply { background = round(0xFFFFF8E1.toInt(), 12f, 0xFFFFCC80.toInt()) }
                    bc.addView(heading("👤 " + rf.mudi + " – " + L.t("book_title")))
                    book.forEach { r ->
                        val tag = when (r.role) { "mudi" -> "💰 " + L.t("mudi_h"); "khed" -> "🚚 " + L.t("khed_h"); else -> "🏢 " + L.t("role_co") }
                        val what = listOf(r.h.vehicleInfo(), r.h.vehicle, Bill.dateText(r.h.time)).filter { it.isNotBlank() }.joinToString(" • ")
                        val line = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dpi(4f), 0, dpi(4f)); setOnClickListener { showEditor(r.h) } }
                        line.addView(row(small(tag + " (" + plain(r.pct) + "%)", INK).apply { typeface = Typeface.DEFAULT_BOLD } to 1f,
                            small((if (r.amount >= 0) L.t("profit") else L.t("loss")) + " " + money(Math.abs(r.amount)), if (r.amount >= 0) GREEN else RED).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD } to 0f))
                        line.addView(small(what))
                        bc.addView(line)
                        sb.append("• ").append(tag).append(" ").append(what).append(": ").append(money(r.amount)).append("\n")
                    }
                    val g = book.filter { it.role != "co" }.sumOf { it.amount }; val co = book.filter { it.role == "co" }.sumOf { it.amount }
                    if (g != 0.0 || book.any { it.role != "co" }) bc.addView(small(L.t("book_gaadi") + ": " + money(g), if (g >= 0) GREEN else RED).apply { textSize = 15f })
                    if (book.any { it.role == "co" }) bc.addView(small("🏢 " + L.t("book_co") + ": " + money(co), if (co >= 0) GREEN else RED).apply { textSize = 15f })
                    bc.addView(small(L.t("book_total") + ": " + money(g + co), if (g + co >= 0) GREEN else RED).apply { textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(6f), 0, 0) })
                    list.addView(bc, cardLp())
                    sb.append(L.t("book_total")).append(": ").append(money(g + co)).append("\n")
                }
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

    /** once a day, silently: Downloads/KabadiCalc/KabadiCalc_auto_backup.json (same file, overwritten) */
    private fun autoBackup() {
        if (Build.VERSION.SDK_INT < 29 || Store.hisabs.isEmpty()) return
        val sp = getSharedPreferences("kabadi_calc", MODE_PRIVATE)
        if (System.currentTimeMillis() - sp.getLong("autoBackup", 0) < 20L * 3600_000L) return
        val data = Store.toJson().toString().toByteArray()
        Thread {
            try {
                val name = "KabadiCalc_auto_backup.json"
                val col = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                var uri: Uri? = null
                contentResolver.query(col, arrayOf(android.provider.MediaStore.MediaColumns._ID),
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?", arrayOf(name), null)?.use { c ->
                    if (c.moveToFirst()) uri = android.content.ContentUris.withAppendedId(col, c.getLong(0))
                }
                if (uri == null) uri = contentResolver.insert(col, android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/KabadiCalc")
                })
                uri?.let { u -> contentResolver.openOutputStream(u, "wt")?.use { it.write(data) } }
                sp.edit().putLong("autoBackup", System.currentTimeMillis()).apply()
            } catch (_: Exception) {}
        }.start()
    }

    private fun pickBackup() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try { startActivityForResult(i, 21) } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(req, perms, res)
        if (req == 41 && res.firstOrNull() == PackageManager.PERMISSION_GRANTED) { Reminders.setAutoSms(this, true); if (screen == "settings") showSettings() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(req, res, data)
        if (req == 31 && res == RESULT_OK) {
            val cb = contactCb; contactCb = null
            try {
                data?.data?.let { u ->
                    contentResolver.query(u, arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cur ->
                        if (cur.moveToFirst()) cb?.invoke(cur.getString(0) ?: "", digits10(cur.getString(1) ?: ""))
                    }
                }
            } catch (_: Exception) { toast("✕") }
            return
        }
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
        val from = editing.takeIf { viewOnly == null }; if (viewOnly != null) { viewOnly = null; editing = null }
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

        if (LOGIN_ON && Account.exists(this)) {
            val ac = card()
            ac.addView(heading(L.t("acc_sec")))
            ac.addView(small(Account.name(this) + "   📞 " + Account.mobile(this) + (if (Account.isAdmin(this)) "   👑" else ""), INK).apply { textSize = 15f; setPadding(0, 0, 0, dpi(8f)) })
            fun sp(t: String, on: Boolean, col: Int, top: Boolean, a: () -> Unit) = ac.addView(pill(t, on, col, a).apply { textSize = 15f; setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f)) },
                llp(MATCH_PARENT, WRAP_CONTENT).apply { if (top) topMargin = dpi(8f) })
            if (Account.isAdmin(this)) sp("👑 " + L.t("adm_btn"), true, 0xFF6A1B9A.toInt(), false) { showAdmin() }
            if (!Account.isAdmin(this) && Account.REQUIRED) sp(L.t("signout"), false, RED, false) { signOut() }
            body.addView(ac, cardLp())
        }

        if (LOGIN_ON && !Account.isAdmin(this)) body.addView(small("👑 " + L.t("adm_login"), 0xFF6A1B9A.toInt()).apply {
            gravity = Gravity.CENTER; textSize = 14f; setPadding(0, dpi(4f), 0, dpi(10f))
            setOnClickListener { askAdminCode { if (!Account.exists(this@MainActivity)) Account.create(this@MainActivity, Store.owner.ifBlank { "Admin" }, Store.mobile.filter { c -> c.isDigit() }.takeLast(10), "")
                Account.approveAsAdmin(this@MainActivity); Reminders.schedule(this@MainActivity); showHome() } }
        }, llp(MATCH_PARENT, WRAP_CONTENT))

        val sc = card()
        sc.addView(heading("🔔 " + L.t("rem_t")))
        sc.addView(small(L.t("rem_set_h")).apply { setPadding(0, 0, 0, dpi(8f)) })
        body.addView(sc, cardLp())

        val ap = card()
        ap.addView(heading("🔐 " + L.t("adm_pin")))
        ap.addView(small(L.t("adm_pin_h")).apply { setPadding(0, 0, 0, dpi(8f)) })
        val hasPin = getSharedPreferences("kabadi_calc", MODE_PRIVATE).getString("admPin", "").orEmpty().isNotEmpty()
        ap.addView(pill(if (hasPin) L.t("adm_pin_change") else L.t("adm_pin_new"), false, BLUE) {
            if (hasPin) askAdminPin { setAdminPin { showSettings() } } else setAdminPin { showSettings() }
        }.apply { textSize = 15f; setPadding(dpi(8f), dpi(10f), dpi(8f), dpi(10f)) }, llp(MATCH_PARENT, WRAP_CONTENT))
        if (!LOGIN_ON || Account.isAdmin(this)) body.addView(ap, cardLp())

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
        val from = editing.takeIf { viewOnly == null }; if (viewOnly != null) { viewOnly = null; editing = null }
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
