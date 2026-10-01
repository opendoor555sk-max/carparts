package com.kabadi.calc

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tape calculator screen: a blank paper, press the mic and speak the whole calculation
 * ("majuri 500 plus diesel 1200 minus advance 300 total") and it is written like on paper, with the total.
 * The key pad is hidden until asked for.
 */
class TapeView(private val act: Activity) {
    private val set = TapeStore.settings(act)
    val calc = TapeCalc(set)
    val root = LinearLayout(act)

    private val PAPER = 0xFFFFFBEF.toInt()
    private val RULE = 0xFFE6DFC8.toInt()
    private val INK = 0xFF1C2328.toInt()
    private val MUTED = 0xFF8A8F94.toInt()
    private val BLUE = 0xFF1565C0.toInt()
    private val RED = 0xFFC62828.toInt()
    private val GREEN = 0xFF2E7D32.toInt()

    private val vCodes = arrayOf("en-IN", "hi-IN", "gu-IN")
    private var vLang = TapeStore.voiceLang(act, L.lang.coerceIn(0, 2)).coerceIn(0, 2)
    private var listening = false
    private var partial = ""
    private var sr: SpeechRecognizer? = null
    private var dead = false

    private val rows = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(8)) }
    private val scroll = ScrollView(act)
    private val totalTv = TextView(act)
    private val micBtn = TextView(act)
    private val micHint = TextView(act)
    private val langChips = ArrayList<TextView>()
    private lateinit var keypad: LinearLayout

    private fun tr(en: String, hi: String, gu: String) = when (L.lang) { 1 -> hi; 2 -> gu; else -> en }
    private fun dp(x: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x.toFloat(), act.resources.displayMetrics).toInt()
    private fun toast(m: String) = Toast.makeText(act, m, Toast.LENGTH_SHORT).show()
    private fun rounded(c: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke) }
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun fmt(v: BigDecimal) = TapeMath.fmt(v, set.places, set.style)
    private fun totalText(l: List<TLine>) = TapeMath.total(l, set.smart)?.let { fmt(it) } ?: "Error"

    private fun build() {
        TapeStore.loadState(act, calc)
        calc.archive = { TapeStore.addHistory(act, it, totalText(it)) }
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFFF2F4F5.toInt())
        buildToolbar()
        buildPaper()
        buildTotal()
        buildMic()
        buildKeypad()
        refresh()
    }

    // ---------------------------------------------------------------- layout
    private fun iconBtn(t: String, act2: () -> Unit) = TextView(act).apply {
        text = t; textSize = 20f; gravity = Gravity.CENTER; setTextColor(INK)
        setPadding(dp(10), dp(6), dp(10), dp(6)); setOnClickListener { act2() }
    }

    private fun buildToolbar() {
        val bar = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(4), 0) }
        arrayOf("English", "हिंदी", "ગુજરાતી").forEachIndexed { i, n ->
            val c = TextView(act).apply {
                text = n; textSize = 13f; gravity = Gravity.CENTER; setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener { vLang = i; TapeStore.saveVoiceLang(act, i); styleChips(); if (listening) { stopListening(); startListening() } }
            }
            langChips.add(c)
            bar.addView(c, lp(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(4) })
        }
        styleChips()
        bar.addView(View(act), lp(0, dp(1), 1f))
        bar.addView(iconBtn("⚙") { settingsDlg() })
        bar.addView(iconBtn("▤") { historyDlg() })
        bar.addView(iconBtn("📤") { share() })
        bar.addView(iconBtn("🗑") { clearTape() })
        root.addView(bar, lp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun styleChips() = langChips.forEachIndexed { i, c ->
        val on = i == vLang
        c.setTextColor(if (on) Color.WHITE else INK)
        c.background = rounded(if (on) BLUE else Color.WHITE, 16, if (on) BLUE else 0xFFCFD8DC.toInt())
        c.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun buildPaper() {
        scroll.addView(rows, lp(MATCH_PARENT, WRAP_CONTENT))
        scroll.background = rounded(PAPER, 10, RULE)
        scroll.isFillViewport = true
        root.addView(scroll, lp(MATCH_PARENT, 0, 1f).apply { setMargins(dp(8), dp(8), dp(8), 0) })
    }

    private fun buildTotal() {
        val strip = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF111111.toInt(), 10); setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        strip.addView(TextView(act).apply { text = tr("TOTAL", "कुल", "કુલ"); textSize = 14f; setTextColor(0xFFB0BEC5.toInt()); typeface = Typeface.DEFAULT_BOLD })
        totalTv.apply { textSize = 30f; setTextColor(0xFF7CFC9A.toInt()); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END; setSingleLine(true) }
        strip.addView(totalTv, lp(0, WRAP_CONTENT, 1f))
        root.addView(strip, lp(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(dp(8), dp(8), dp(8), 0) })
    }

    private fun buildMic() {
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val undo = TextView(act).apply {
            text = "⌫"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(INK); background = rounded(Color.WHITE, 28, 0xFFCFD8DC.toInt())
            setOnClickListener { buzz(); calc.ce(); refresh() }
        }
        val kb = TextView(act).apply {
            text = "⌨"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(INK); background = rounded(Color.WHITE, 28, 0xFFCFD8DC.toInt())
            setOnClickListener { keypad.visibility = if (keypad.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }
        micBtn.apply { textSize = 34f; gravity = Gravity.CENTER; setOnClickListener { toggleMic() } }
        val mid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        mid.addView(micBtn, lp(dp(76), dp(76)))
        micHint.apply { textSize = 12f; setTextColor(MUTED); gravity = Gravity.CENTER }
        mid.addView(micHint, lp(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(2) })
        row.addView(undo, lp(dp(56), dp(56)))
        row.addView(mid, lp(0, WRAP_CONTENT, 1f))
        row.addView(kb, lp(dp(56), dp(56)))
        root.addView(row, lp(MATCH_PARENT, WRAP_CONTENT))
        updateMic()
    }

    private fun updateMic() {
        micBtn.text = if (listening) "⏹" else "🎤"
        micBtn.setTextColor(Color.WHITE)
        micBtn.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (listening) RED else GREEN) }
        micBtn.elevation = dp(4).toFloat()
        micHint.text = if (listening) tr("Listening… speak, then say total", "सुन रहा हूँ… बोलिए, आख़िर में कुल बोलिए", "સાંભળું છું… બોલો, છેલ્લે કુલ બોલો")
        else tr("Tap and speak: labour 500 plus diesel 1200 total", "दबाकर बोलिए: मजदूरी 500 जोड़ डीजल 1200 कुल", "દબાવો અને બોલો: મજૂરી 500 પ્લસ ડીઝલ 1200 કુલ")
    }

    private fun buildKeypad() {
        keypad = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(6), 0, dp(6), dp(6)) }
        val keys = listOf(listOf("AC", "⌫", "%", "÷"), listOf("7", "8", "9", "x"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("( )", "0", ".", "="))
        keys.forEach { r ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            r.forEach { k ->
                val isOp = k in listOf("÷", "x", "−", "+", "%")
                val bg = when { k == "=" -> GREEN; isOp -> BLUE; k == "AC" || k == "⌫" -> RED; else -> Color.WHITE }
                row.addView(TextView(act).apply {
                    text = k; textSize = 22f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (bg == Color.WHITE) INK else Color.WHITE); background = rounded(bg, 10, if (bg == Color.WHITE) 0xFFCFD8DC.toInt() else 0)
                    setOnClickListener { key(k) }
                }, lp(0, dp(50), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            keypad.addView(row, lp(MATCH_PARENT, WRAP_CONTENT))
        }
        root.addView(keypad, lp(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun key(k: String) {
        buzz()
        when (k) {
            "AC" -> { calc.ac(); lastRaw = "" }
            "⌫" -> calc.back()
            "%" -> calc.percent()
            "÷" -> calc.op('/')
            "x" -> calc.op('x')
            "−" -> calc.op('-')
            "+" -> calc.op('+')
            "=" -> calc.equals()
            "." -> calc.dot()
            "( )" -> if (calc.cur.isNotEmpty() && calc.unclosed() > 0) calc.paren(false) else calc.paren(true)
            else -> calc.digit(k)
        }
        refresh()
    }

    private fun buzz() { if (set.vibrate) root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    // ---------------------------------------------------------------- paper
    private fun opColor(c: Char) = when (c) { '-' -> RED; '=' -> GREEN; else -> BLUE }

    private fun row(idx: String, note: String, amount: String, op: String, opCol: Int, onTap: (() -> Unit)?, bg: Int = 0): View {
        val r = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(9)) }
        if (bg != 0) r.setBackgroundColor(bg)
        if (onTap != null) r.setOnClickListener { onTap() }
        r.addView(TextView(act).apply { text = idx; textSize = 12f; setTextColor(MUTED) }, lp(dp(26), WRAP_CONTENT))
        r.addView(TextView(act).apply { text = note; textSize = 17f; setTextColor(INK) }, lp(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        r.addView(TextView(act).apply { text = amount; textSize = 21f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END })
        r.addView(TextView(act).apply { text = op; textSize = 22f; setTextColor(opCol); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }, lp(dp(28), WRAP_CONTENT))
        return r
    }

    private fun rule() = View(act).apply { setBackgroundColor(RULE); layoutParams = lp(MATCH_PARENT, 1) }

    fun refresh() {
        rows.removeAllViews()
        val entry = calc.entryText()
        if (calc.lines.isEmpty() && entry.isEmpty() && partial.isEmpty()) {
            rows.addView(TextView(act).apply {
                text = tr("Blank paper.\n\nTap 🎤 and speak:\n“labour 500 plus diesel 1200 minus advance 300 total”",
                    "कोरा कागज़।\n\n🎤 दबाइए और बोलिए:\n“मजदूरी 500 जोड़ डीजल 1200 घटा एडवांस 300 कुल”",
                    "કોરો કાગળ.\n\n🎤 દબાવો અને બોલો:\n“મજૂરી 500 પ્લસ ડીઝલ 1200 માઇનસ એડવાન્સ 300 કુલ”")
                textSize = 16f; setTextColor(MUTED); gravity = Gravity.CENTER; setPadding(dp(20), dp(40), dp(20), dp(40))
            }, lp(MATCH_PARENT, WRAP_CONTENT))
        }
        calc.lines.forEachIndexed { i, l ->
            rows.addView(row("${i + 1}", l.note, calc.lineText(l), calc.opChar(l), opColor(l.op), { editLine(i) }), lp(MATCH_PARENT, WRAP_CONTENT))
            rows.addView(rule())
        }
        if (entry.isNotEmpty()) rows.addView(row("", "", entry, "", BLUE, null, 0xFFFFF3C4.toInt()), lp(MATCH_PARENT, WRAP_CONTENT))
        if (lastRaw.isNotEmpty() && partial.isEmpty()) rows.addView(TextView(act).apply {
            text = "🗣 $lastRaw"; textSize = 12f; setTextColor(MUTED); setPadding(dp(12), dp(8), dp(12), dp(4))
        }, lp(MATCH_PARENT, WRAP_CONTENT))
        if (partial.isNotEmpty()) rows.addView(TextView(act).apply {
            text = "🎤 $partial"; textSize = 15f; setTextColor(MUTED); typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC); setPadding(dp(12), dp(10), dp(12), dp(10))
        }, lp(MATCH_PARENT, WRAP_CONTENT))
        totalTv.text = if (calc.lines.isEmpty()) "0" else totalText(calc.lines)
        if (calc.closed) totalTv.text = "= " + totalTv.text
        TapeStore.saveState(act, calc)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun editLine(i: Int) {
        val l = calc.lines.getOrNull(i) ?: return
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), 0) }
        val note = EditText(act).apply { setText(l.note); hint = tr("Item", "मद", "વિગત"); setSingleLine(true) }
        val amt = EditText(act).apply {
            setText(l.value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()); hint = tr("Amount", "रकम", "રકમ")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        box.addView(note); box.addView(amt)
        AlertDialog.Builder(act).setTitle("${i + 1}").setView(box)
            .setPositiveButton(tr("Save", "सेव", "સેવ")) { _, _ ->
                val v = try { BigDecimal(amt.text.toString().replace(",", "").trim()) } catch (_: Exception) { null }
                if (v == null) toast("✕") else { l.value = v; l.note = note.text.toString().trim(); refresh() }
            }
            .setNeutralButton(tr("Delete line", "लाइन हटाएं", "લાઇન કાઢો")) { _, _ -> calc.lines.removeAt(i); refresh() }
            .setNegativeButton(tr("Cancel", "रद्द", "રદ"), null).show()
    }

    private fun clearTape() {
        if (calc.lines.isEmpty() && !calc.hasEntry()) return
        AlertDialog.Builder(act).setMessage(tr("Clear this paper? (it stays in history)", "यह कागज़ साफ़ करें? (हिस्ट्री में रहेगा)", "આ કાગળ સાફ કરવો? (હિસ્ટ્રી માં રહેશે)"))
            .setPositiveButton(tr("Clear", "साफ़", "સાફ")) { _, _ -> calc.ac(); lastRaw = ""; refresh() }
            .setNegativeButton(tr("No", "नहीं", "ના"), null).show()
    }

    private fun share() {
        if (calc.lines.isEmpty()) return toast(tr("Nothing written yet", "अभी कुछ नहीं लिखा", "હજી કંઈ લખ્યું નથી"))
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, calc.text("Kabadi Market Hisab"))
        try { act.startActivity(Intent.createChooser(i, null)) } catch (_: Exception) { toast("✕") }
    }

    private fun historyDlg() {
        val h = TapeStore.history(act)
        if (h.isEmpty()) return toast(tr("No earlier paper yet", "अभी कोई पुराना कागज़ नहीं", "હજી કોઈ જૂનો કાગળ નથી"))
        val df = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        val names = h.map { df.format(Date(it.at)) + "   •   " + it.total + "   (" + it.lines.size + ")" }.toTypedArray()
        AlertDialog.Builder(act).setTitle(tr("Earlier papers", "पुराने कागज़", "જૂના કાગળ"))
            .setItems(names) { _, w ->
                if (calc.lines.isNotEmpty() && !calc.closed) TapeStore.addHistory(act, calc.lines.map { it.copy() }, totalText(calc.lines))
                calc.loadLines(h[w].lines); refresh()
            }
            .setNegativeButton(tr("Clear history", "हिस्ट्री साफ़", "હિસ્ટ્રી સાફ")) { _, _ -> TapeStore.clearHistory(act) }
            .setPositiveButton(tr("Close", "बंद", "બંધ"), null).show()
    }

    private fun settingsDlg() {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        val placesList = intArrayOf(0, 1, 2, 3, 4, 6, -1)
        fun styleName() = if (set.style == 3) "12,34,567.89" else "1,234,567.89"
        fun placesName() = if (set.places < 0) tr("Floating", "फ्लोटिंग", "ફ્લોટિંગ") else set.places.toString()
        fun opt(get: () -> String, tap: () -> Unit) = TextView(act).apply {
            textSize = 16f; setTextColor(INK); background = rounded(Color.WHITE, 8, 0xFFCFD8DC.toInt()); setPadding(dp(12), dp(12), dp(12), dp(12))
            text = get(); setOnClickListener { tap(); text = get() }
        }.also { box.addView(it, lp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }) }
        opt({ tr("Number format: ", "नंबर फॉर्मेट: ", "નંબર ફોર્મેટ: ") + styleName() }) { set.style = if (set.style == 3) 0 else 3 }
        opt({ tr("Decimals: ", "दशमलव: ", "દશાંશ: ") + placesName() }) { set.places = placesList[(placesList.indexOf(set.places) + 1) % placesList.size] }
        opt({ tr("Smart % (100 + 10% = 110): ", "स्मार्ट % (100 + 10% = 110): ", "સ્માર્ટ % (100 + 10% = 110): ") + (if (set.smart) "ON" else "OFF") }) { set.smart = !set.smart }
        opt({ tr("Vibration: ", "वाइब्रेशन: ", "વાઇબ્રેશન: ") + (if (set.vibrate) "ON" else "OFF") }) { set.vibrate = !set.vibrate }
        opt({ tr("🎤 Voice without internet: how", "🎤 बिना इंटरनेट आवाज़: कैसे", "🎤 ઇન્ટરનેટ વગર અવાજ: કેવી રીતે") }) { offlineHelp() }
        AlertDialog.Builder(act).setTitle("⚙").setView(box)
            .setPositiveButton("OK") { _, _ -> TapeStore.saveSettings(act, set); refresh() }.show()
    }

    // ---------------------------------------------------------------- voice
    // One tap = the microphone stays on (up to 3 minutes of silence). The phone's recogniser ends after every sentence, so it
    // is restarted straight away on the same object, the "beep" is muted meanwhile, the screen stays on, text that was cut
    // between two sentences is carried over, and it asks for the on-device (offline) language pack first.
    private val audio = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var beepMuted = false
    private var lastActive = 0L
    private var hardErrors = 0
    private var hardSince = 0L
    private var lastPartial = ""
    private var carry = ""
    /** what the phone wrote for the last sentence (shown small under the lines, so a wrong word is easy to see) */
    private var lastRaw = ""
    private var carryAt = 0L
    private var helpShown = false
    /** ask the phone for its offline language pack only after the internet failed (asking for it first fails on some phones) */
    private var offlineFirst = false
    private var triedOther = false

    private fun muteBeep(on: Boolean) {
        try {
            if (on && !beepMuted && !audio.isStreamMute(AudioManager.STREAM_MUSIC)) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0); beepMuted = true }
            else if (!on && beepMuted) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0); beepMuted = false }
        } catch (_: Exception) {}
    }

    private fun toggleMic() { if (listening) stopListening() else startListening() }

    fun startListening() {
        if (dead) return
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 52); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(act)) return fallbackDialog()
        listening = true; offlineFirst = false; triedOther = false; hardErrors = 0; hardSince = 0; lastActive = System.currentTimeMillis(); lastPartial = ""
        muteBeep(true); root.keepScreenOn = true
        updateMic(); newRecognizer(); listenOnce()
    }

    fun stopListening() {
        listening = false
        muteBeep(false); root.keepScreenOn = false
        try { sr?.cancel() } catch (_: Exception) {}
        try { sr?.destroy() } catch (_: Exception) {}
        sr = null
        // what was being said when stopped is not lost
        if (lastPartial.isNotBlank()) { val t = lastPartial; lastPartial = ""; partial = ""; heard(t) }
        partial = ""
        if (!dead) { updateMic(); refresh() }
    }

    fun release() {
        dead = true
        listening = false
        muteBeep(false)
        try { sr?.destroy() } catch (_: Exception) {}
        sr = null
        TapeStore.saveState(act, calc)
    }

    private fun newRecognizer() {
        try { sr?.destroy() } catch (_: Exception) {}
        val r = SpeechRecognizer.createSpeechRecognizer(act)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (dead || !listening || t.isBlank()) return
                lastActive = System.currentTimeMillis(); lastPartial = t; partial = t; refresh()
            }
            override fun onResults(b: Bundle?) {
                if (dead || !listening) return
                partial = ""
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: lastPartial
                lastPartial = ""
                if (!t.isNullOrBlank()) { lastActive = System.currentTimeMillis(); hardErrors = 0; triedOther = false; heard(t) } else refresh()
                again(60)
            }
            override fun onError(code: Int) {
                if (dead || !listening) return
                // the sentence was cut off by an error: keep what had already been heard
                val salv = lastPartial; lastPartial = ""; partial = ""
                if (salv.isNotBlank()) { lastActive = System.currentTimeMillis(); heard(salv) } else refresh()
                when (code) {
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> {
                        if (System.currentTimeMillis() - lastActive > 180_000) {
                            stopListening()
                            toast(tr("Nothing heard for 3 minutes. Tap 🎤 to start again", "3 मिनट कुछ नहीं सुना। फिर 🎤 दबाइए", "3 મિનિટ કંઈ સંભળાયું નહીં. ફરી 🎤 દબાવો"))
                        } else again(60)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_AUDIO, 11 -> {
                        val now = System.currentTimeMillis()
                        if (now - hardSince > 15_000) { hardSince = now; hardErrors = 0 }
                        hardErrors++
                        if (hardErrors > 6) { stopListening(); fallbackDialog() } else { newRecognizerLater(400L * hardErrors) }
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { stopListening(); act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 52) }
                    // internet or language problem: try the other way once (online <-> offline pack), then explain with the code
                    else -> {
                        if (!triedOther) { triedOther = true; offlineFirst = !offlineFirst; newRecognizerLater(300) }
                        else { stopListening(); offlineHelp(code) }
                    }
                }
            }
        })
    }

    private fun newRecognizerLater(ms: Long) {
        root.postDelayed({ if (listening && !dead) { newRecognizer(); listenOnce() } }, ms)
    }

    private fun listenOnce() {
        if (!listening || dead) return
        val r = sr ?: return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, vCodes[vLang])
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offlineFirst)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, act.packageName)
            // wait longer for a pause before closing the sentence
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 20000L)
        try { r.startListening(i) } catch (_: Exception) { newRecognizerLater(500) }
    }

    private fun again(ms: Long) {
        root.postDelayed({ if (listening && !dead) listenOnce() }, ms)
    }

    /** the spoken text becomes lines on the paper; words left without an amount (cut between two sentences) join the next sentence */
    private fun heard(text: String) {
        lastRaw = text
        val now = System.currentTimeMillis()
        val full = (if (carry.isNotBlank() && now - carryAt < 20_000) "$carry " else "") + text
        carry = ""
        val r = TapeSpeech.parseFull(full)
        if (r.rest.isNotBlank()) { carry = r.rest; carryAt = now }
        if (r.items.isEmpty()) {
            if (r.rest.isBlank()) toast(tr("No amount heard. Say item, then amount", "रकम नहीं सुनी। पहले मद, फिर रकम बोलिए", "રકમ સંભળાઈ નહીં. પહેલાં વિગત, પછી રકમ બોલો"))
            else { partial = r.rest; refresh(); partial = "" }
            return
        }
        calc.addSpoken(r.items)
        buzz()
        refresh()
    }

    private fun offlineHelp(code: Int = 0) {
        val msg = tr(
            "Voice needs internet, or the offline language pack. To talk without internet, download it once:\n\nPhone Settings → Google → Settings for Google apps → Search, Assistant & Voice → Voice → Offline speech recognition → download English, Hindi and Gujarati.\n\n(Names differ a little on each phone.)",
            "आवाज़ के लिए इंटरनेट चाहिए, या ऑफ़लाइन भाषा पैक। बिना इंटरनेट बोलने के लिए एक बार डाउनलोड करें:\n\nफ़ोन Settings → Google → Google apps के लिए सेटिंग → Search, Assistant और Voice → Voice → Offline speech recognition → English, हिन्दी, ગુજરાતી डाउनलोड करें।\n\n(हर फ़ोन में नाम थोड़े अलग हो सकते हैं।)",
            "અવાજ માટે ઇન્ટરનેટ જોઈએ, અથવા ઑફલાઇન ભાષા પેક. ઇન્ટરનેટ વગર બોલવા માટે એક વાર download કરો:\n\nફોન Settings → Google → Settings for Google apps → Search, Assistant & Voice → Voice → Offline speech recognition → English, हिन्दी, ગુજરાતી download કરો.\n\n(દરેક ફોનમાં નામ થોડાં જુદાં હોઈ શકે.)")
        AlertDialog.Builder(act).setTitle(if (code != 0) "🎤 (error $code)" else "🎤").setMessage(msg)
            .setPositiveButton(tr("Open voice settings", "वॉइस सेटिंग खोलें", "વૉઇસ સેટિંગ ખોલો")) { _, _ ->
                try { act.startActivity(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)) } catch (_: Exception) { toast("✕") }
            }
            .setNegativeButton(tr("Close", "बंद", "બંધ"), null).show()
    }

    /** phone without the continuous recogniser: the normal Google voice window, one sentence at a time */
    private fun fallbackDialog() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, vCodes[vLang])
            .putExtra(RecognizerIntent.EXTRA_PROMPT, tr("Speak the calculation", "पूरा हिसाब बोलिए", "આખો હિસાબ બોલો"))
        try { act.startActivityForResult(i, 61) } catch (_: Exception) {
            toast(tr("Voice typing is not available on this phone", "इस फ़ोन में वॉइस टाइपिंग नहीं है", "આ ફોનમાં વૉઇસ ટાઇપિંગ નથી"))
        }
    }

    fun onVoiceResult(list: List<String>?) { val t = list?.firstOrNull(); if (!t.isNullOrBlank()) heard(t) }

    fun onPermission(granted: Boolean) {
        if (granted) startListening() else toast(tr("Allow the microphone to speak", "बोलने के लिए माइक की अनुमति दें", "બોલવા માટે માઇક ની પરવાનગી આપો"))
    }

    // must stay LAST: every property above has to exist before the screen is built
    init { build() }
}
