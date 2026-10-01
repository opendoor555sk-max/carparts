package com.nirmaan.calc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import kotlin.Triple;

/** Scientific calculator with units (dark keys, light display, orange long-press labels). */
public class ScientificActivity extends Activity {

    // ---- colours of this screen ----
    private static final int TOP = 0xFF1B1D21;       // top bar + keypad background
    private static final int CHIP = 0xFF2E3136;
    private static final int DISP = 0xFFE6E7E3;      // light display
    private static final int INK = 0xFF1C1F24;
    private static final int INK2 = 0xFF6B6F75;
    private static final int BAR = 0xFF26292E;       // ⌨ ◀ ▶ ↶ ↷ bar
    private static final int ICON = 0xFF9AA0A6;
    private static final int FKEY = 0xFF2B2E33, FKEY_LINE = 0xFF464A51;
    private static final int NKEY = 0xFF4B4F55, NKEY_LINE = 0xFF5D6168;
    private static final int ORANGE = 0xFFF57C00;
    private static final int DEL_BG = 0xFF3B2226, DEL_LINE = 0xFFE53935;
    private static final int AC_BG = 0xFF1D3A24, AC_LINE = 0xFF2ECC40, AC_TXT = 0xFF34E05A;

    // key list: main, long-press label (shown above the key), row type (0 function, 1 digit)
    private static final String[][] ROWS = {
        {"X", "x", "Y", "y", "Z", "z", "Kilo", "", "Meter", "", "∫", ""},
        {"dx", "", "Σ", "Π", "i", "∠", "Real", "", "ln", "log", "∞", "!"},
        {"sin", "sin⁻¹", "cos", "cos⁻¹", "tan", "tan⁻¹", "√", "", "xʸ", "", "π", "e"},
        {"(", "[", ")", "]", "±", "", "_", ";", "%", "", "=", ","},
        {"7", "x⁷", "8", "x⁸", "9", "x⁹", "⌫", "", "AC", ""},
        {"4", "x⁴", "5", "x⁵", "6", "x⁶", "−", "", "÷", ""},
        {"1", "x¹", "2", "x²", "3", "x³", "+", "", "×", ""},
        {"0", "x⁰", ".", "␣", "E", "", "Enter", "ans"}
    };
    private static final String SUPS = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    // the "Kilo ▲▼" and "Meter ▲▼" keys
    private static final String[][] PREFIXES = {
        {"Kilo", "k"}, {"Mega", "M"}, {"Giga", "G"}, {"Tera", "T"}, {"Hecto", "h"}, {"Deci", "d"},
        {"Centi", "c"}, {"Milli", "m"}, {"Micro", "µ"}, {"Nano", "n"}, {"Pico", "p"}};
    private static final String[][] BASEUNITS = {
        {"Meter", "m"}, {"Gram", "g"}, {"Second", "s"}, {"Litre", "L"}, {"Newton", "N"}, {"Pascal", "Pa"},
        {"Joule", "J"}, {"Watt", "W"}, {"Volt", "V"}, {"Ampere", "A"}, {"Ohm", "Ω"}, {"Hertz", "Hz"},
        {"Byte", "B"}, {"Kelvin", "K"}, {"Foot", "ft"}, {"Inch", "in"}};

    private SciOpts o;
    private final CalculatorLogic logic = new CalculatorLogic();
    private TextView resTv, infoTv;
    private EditText exprEt;
    private TextView angleChip, fmtChip, decChip, exactChip;
    private TextView kiloTv, meterTv, realTv, mulTv, divTv, dotTv;
    private boolean justEval = false, settingText = false, afterPrefix = false, kbOn = false;
    private final ArrayList<String[]> undo = new ArrayList<>(), redo = new ArrayList<>();

    private static final String[] TOKENS = {"d/dx(", "asin(", "acos(", "atan(", "sin(", "cos(", "tan(",
        "log(", "ln(", "Re(", "Im(", "∫(", "Σ(", "Π(", "√(", "∛(", "Ans", " → "};

    private int dp(float x) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        o = SciStore.INSTANCE.load(this);
        getWindow().setStatusBarColor(TOP);
        getWindow().setNavigationBarColor(TOP);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(TOP);
        root.setFitsSystemWindows(true);
        root.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(60)));
        root.addView(displayBox(), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams tb = new LinearLayout.LayoutParams(-1, dp(50));
        tb.setMargins(dp(14), dp(12), dp(14), dp(4));
        root.addView(toolBar(), tb);
        root.addView(keypad(), new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        loadState();
        refreshLabels();
        preview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        o = SciStore.INSTANCE.load(this);
        refreshLabels();
        SharedPreferences sp = getSharedPreferences("sci", MODE_PRIVATE);
        String pick = sp.getString("pick", null);
        if (pick != null) { sp.edit().remove("pick").apply(); setExpr(pick, true); }
        else if (justEval) showAns(); else preview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    // ================= top bar: ⚙  GRA ENG 0.00 EXACT  🕘 =================
    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        IconView gear = new IconView(this, IconView.GEAR, ICON, TOP);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(gear, new LinearLayout.LayoutParams(dp(60), dp(52)));

        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER);
        angleChip = chip(); fmtChip = chip(); decChip = chip(); exactChip = chip();
        angleChip.setOnClickListener(v -> { o.setAngle((o.getAngle() + 1) % 3); modeChanged(); });
        fmtChip.setOnClickListener(v -> { o.setMode((o.getMode() + 1) % 3); modeChanged(); });
        decChip.setOnClickListener(v -> decimalsDialog());
        exactChip.setOnClickListener(v -> { o.setExact(!o.getExact()); modeChanged(); });
        for (TextView c : new TextView[]{angleChip, fmtChip, decChip, exactChip}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.setMargins(dp(4), 0, dp(4), 0);
            chips.addView(c, lp);
        }
        bar.addView(chips, new LinearLayout.LayoutParams(0, -1, 1f));

        IconView hist = new IconView(this, IconView.HIST, ORANGE, TOP);
        hist.setOnClickListener(v -> SettingsActivity.historyDialog(this, o, e -> setExpr(e, true)));
        bar.addView(hist, new LinearLayout.LayoutParams(dp(60), dp(52)));
        return bar;
    }

    private TextView chip() {
        TextView t = new TextView(this);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), 0, dp(12), 0);
        t.setBackground(ripple(CHIP, 0, dp(10)));
        return t;
    }

    // ================= light display =================
    @SuppressLint("ClickableViewAccessibility")
    private View displayBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(DISP);
        box.setPadding(dp(20), dp(14), dp(20), dp(12));

        resTv = new TextView(this);
        resTv.setGravity(Gravity.END);
        resTv.setTextColor(INK);
        resTv.setTypeface(Typeface.DEFAULT_BOLD);
        resTv.setTextSize(46);
        resTv.setMaxLines(2);
        resTv.setText("0");
        resTv.setOnLongClickListener(v -> { copyResult(); return true; });
        box.addView(resTv, new LinearLayout.LayoutParams(-1, -2));

        exprEt = new EditText(this);
        exprEt.setShowSoftInputOnFocus(false);
        exprEt.setBackground(null);
        exprEt.setTextColor(INK);
        exprEt.setTextSize(24);
        exprEt.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        exprEt.setPadding(0, dp(10), 0, dp(8));
        exprEt.setMaxLines(3);
        exprEt.setMinHeight(dp(52));
        exprEt.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                int n = s.length();
                exprEt.setTextSize(n < 22 ? 24 : n < 40 ? 20 : 17);
                if (!settingText) { justEval = false; preview(); }
            }
        });
        box.addView(exprEt, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(this);
        line.setBackgroundColor(0xFFC9CBC6);
        box.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        infoTv = new TextView(this);
        infoTv.setTextColor(INK2);
        infoTv.setTextSize(15);
        infoTv.setPadding(0, dp(8), 0, 0);
        infoTv.setText("0");
        box.addView(infoTv, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    // ================= ⌨ ◀ ▶ ↶ ↷ =================
    private View toolBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(BAR);
        g.setCornerRadius(dp(16));
        bar.setBackground(g);
        bar.setPadding(dp(30), 0, dp(30), 0);
        int[] types = {IconView.KEYBOARD, IconView.LEFT, IconView.RIGHT, IconView.UNDO, IconView.REDO};
        for (int tpe : types) {
            IconView iv = new IconView(this, tpe, ICON, BAR);
            iv.setBackground(ripple(0x00000000, 0, dp(12)));
            iv.setOnClickListener(v -> tool(tpe, iv));
            bar.addView(iv, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return bar;
    }

    private void tool(int tpe, IconView iv) {
        switch (tpe) {
            case IconView.KEYBOARD -> {
                kbOn = !kbOn;
                exprEt.setShowSoftInputOnFocus(kbOn);
                iv.setColor(kbOn ? ORANGE : ICON);
                InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                exprEt.requestFocus();
                if (kbOn) im.showSoftInput(exprEt, InputMethodManager.SHOW_IMPLICIT);
                else im.hideSoftInputFromWindow(exprEt.getWindowToken(), 0);
            }
            case IconView.LEFT -> moveCursor(-1);
            case IconView.RIGHT -> moveCursor(1);
            case IconView.UNDO -> undoRedo(undo, redo);
            case IconView.REDO -> undoRedo(redo, undo);
        }
    }

    private void moveCursor(int dir) {
        exprEt.requestFocus();
        int c = exprEt.getSelectionStart();
        CharSequence s = exprEt.getText();
        int n = c + dir;
        if (n < 0 || n > s.length()) return;
        if (dir < 0 && n > 0 && Character.isLowSurrogate(s.charAt(n))) n--;
        if (dir > 0 && n < s.length() && Character.isLowSurrogate(s.charAt(n))) n++;
        exprEt.setSelection(n);
    }

    private void snapshot() {
        undo.add(new String[]{exprEt.getText().toString(), String.valueOf(exprEt.getSelectionStart())});
        if (undo.size() > 100) undo.remove(0);
        redo.clear();
    }

    private void undoRedo(ArrayList<String[]> from, ArrayList<String[]> to) {
        if (from.isEmpty()) return;
        to.add(new String[]{exprEt.getText().toString(), String.valueOf(exprEt.getSelectionStart())});
        String[] st = from.remove(from.size() - 1);
        settingText = true;
        exprEt.setText(st[0]);
        int c = Math.min(st[0].length(), Math.max(0, Integer.parseInt(st[1])));
        exprEt.setSelection(c);
        settingText = false;
        justEval = false;
        preview();
    }

    // ================= keypad =================
    private View keypad() {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(8), 0, dp(8), dp(8));
        for (int r = 0; r < ROWS.length; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            String[] keys = ROWS[r];
            for (int i = 0; i < keys.length; i += 2) {
                String main = keys[i], alt = keys[i + 1];
                float w = main.equals("Enter") ? 2f : 1f;
                row.addView(cell(main, alt, r >= 4), new LinearLayout.LayoutParams(0, -1, w));
            }
            pad.addView(row, new LinearLayout.LayoutParams(-1, 0, r >= 4 ? 1.22f : 1f));
        }
        return pad;
    }

    @SuppressLint("ClickableViewAccessibility")
    private View cell(String main, String alt, boolean digitRow) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(4), 0, dp(4), dp(5));

        TextView altTv = new TextView(this);
        altTv.setText(alt.equals("␣") ? "␣" : alt);
        altTv.setTextColor(ORANGE);
        altTv.setTextSize(alt.length() > 3 ? 14 : 16);
        altTv.setGravity(Gravity.CENTER);
        altTv.setIncludeFontPadding(false);
        cell.addView(altTv, new LinearLayout.LayoutParams(-1, dp(digitRow ? 22 : 24)));

        View key;
        boolean cycler = main.equals("Kilo") || main.equals("Meter") || main.equals("Real");
        int bg = digitRow ? NKEY : FKEY, line = digitRow ? NKEY_LINE : FKEY_LINE;
        if (main.equals("⌫")) {
            IconView iv = new IconView(this, IconView.BACKSPACE, DEL_LINE, DEL_BG);
            iv.setBackground(ripple(DEL_BG, DEL_LINE, dp(8)));
            key = iv;
        } else if (cycler) {
            LinearLayout k = new LinearLayout(this);
            k.setOrientation(LinearLayout.VERTICAL);
            k.setGravity(Gravity.CENTER);
            k.setBackground(ripple(bg, line, dp(8)));
            k.addView(arrow("▲"));
            TextView t = keyText(main, 16, 0xFFFFFFFF);
            k.addView(t, new LinearLayout.LayoutParams(-2, -2));
            k.addView(arrow("▼"));
            if (main.equals("Kilo")) kiloTv = t;
            if (main.equals("Meter")) meterTv = t;
            if (main.equals("Real")) realTv = t;
            key = k;
        } else {
            int fg = 0xFFFFFFFF;
            float size = digitRow ? 32 : main.length() >= 3 ? 21 : 23;
            int kbg = bg, kline = line;
            if (main.equals("AC")) { kbg = AC_BG; kline = AC_LINE; fg = AC_TXT; size = 30; }
            TextView t = keyText(main, size, fg);
            if (main.equals("Enter")) {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFF8F1F, 0xFFF0600C});
                g.setCornerRadius(dp(8));
                t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), g, null));
                t.setTextSize(30);
            } else t.setBackground(ripple(kbg, kline, dp(8)));
            if (main.equals("×")) mulTv = t;
            if (main.equals("÷")) divTv = t;
            if (main.equals(".")) dotTv = t;
            key = t;
        }
        key.setOnClickListener(v -> onKey(main));
        key.setOnLongClickListener(v -> onLong(main));
        if (cycler) key.setOnTouchListener(swipe(main));
        cell.addView(key, new LinearLayout.LayoutParams(-1, 0, 1f));
        return cell;
    }

    private TextView keyText(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    private TextView arrow(String a) {
        TextView t = new TextView(this);
        t.setText(a);
        t.setTextSize(7);
        t.setTextColor(0xFF8A8F96);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        return t;
    }

    private RippleDrawable ripple(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(dp(1.2f), stroke);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(radius);
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), fill == 0 ? null : g, mask);
    }

    /** swipe up / down on Kilo, Meter, Real to change them */
    private View.OnTouchListener swipe(String main) {
        final float[] y0 = new float[1];
        return (v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> y0[0] = e.getY();
                case MotionEvent.ACTION_UP -> {
                    float dy = e.getY() - y0[0];
                    if (Math.abs(dy) > dp(18)) {
                        v.setPressed(false);
                        v.cancelLongPress();
                        cycle(main, dy < 0 ? 1 : -1);
                        return true;
                    }
                }
            }
            return false;
        };
    }

    private void cycle(String main, int dir) {
        switch (main) {
            case "Kilo" -> o.setPrefixIdx(Math.floorMod(o.getPrefixIdx() + dir, PREFIXES.length));
            case "Meter" -> o.setUnitIdx(Math.floorMod(o.getUnitIdx() + dir, BASEUNITS.length));
            case "Real" -> o.setComplex(!o.getComplex());
        }
        SciStore.INSTANCE.save(this, o);
        refreshLabels();
        if (main.equals("Real")) preview();
    }

    // ================= what each key does =================
    private void onKey(String k) {
        switch (k) {
            case "AC" -> { snapshot(); setExpr("", false); justEval = false; resTv.setText("0"); resTv.setTextColor(INK); infoTv.setText("0"); }
            case "⌫" -> { snapshot(); backspace(); }
            case "Enter" -> evaluate();
            case "Kilo" -> {
                String pre = needSpace() ? " " : "";
                ins(pre + PREFIXES[o.getPrefixIdx()][1]);
                afterPrefix = true;
            }
            case "Meter" -> insUnitSym(BASEUNITS[o.getUnitIdx()][1]);
            case "Real" -> cycle("Real", 1);
            case "_" -> { ins(" → "); unitDialog(dimsBeforeArrow(), false); }
            case "±" -> { snapshot(); toggleSign(); }
            case "sin", "cos", "tan", "ln" -> ins(k + "(");
            case "√" -> ins("√(");
            case "xʸ" -> ins("^");
            case "∫" -> ins("∫(");
            case "dx" -> ins("d/dx(");
            case "Σ" -> ins("Σ(");
            case "−", "+" -> ins(spaced(k));
            case "=" -> ins(spaced("="));
            case "×" -> ins(spaced(o.getMulSign()));
            case "÷" -> ins(spaced(o.getDivSign()));
            case "." -> ins(o.getComma() ? "," : ".");
            default -> ins(k);
        }
    }

    private boolean onLong(String k) {
        if (k.length() == 1 && Character.isDigit(k.charAt(0))) { ins(String.valueOf(SUPS.charAt(k.charAt(0) - '0'))); return true; }
        switch (k) {
            case "X", "Y", "Z" -> ins(k.toLowerCase());
            case "Σ" -> ins("Π(");
            case "i" -> ins("∠");
            case "ln" -> ins("log(");
            case "∞" -> ins("!");
            case "sin", "cos", "tan" -> ins("a" + k + "(");
            case "π" -> ins("e");
            case "(" -> ins("[");
            case ")" -> ins("]");
            case "_" -> ins("; ");
            case "=" -> ins(o.getComma() ? "; " : ", ");
            case "." -> ins(" ");
            case "√" -> ins("∛(");
            case "Enter" -> ins("Ans");
            case "Kilo" -> prefixDialog();
            case "Meter" -> unitDialog(null, false);
            case "⌫" -> { snapshot(); setExpr("", false); }
            default -> { return false; }
        }
        return true;
    }

    private String spaced(String op) { return o.getSpacious() ? " " + op + " " : op; }

    private void setExpr(String s, boolean evalPreview) {
        settingText = true;
        exprEt.setText(s);
        exprEt.setSelection(s.length());
        settingText = false;
        justEval = false;
        if (evalPreview) preview();
    }

    private void ins(String s) {
        snapshot();
        if (justEval) {
            String tr = s.trim();
            boolean op = tr.startsWith("+") || tr.startsWith("−") || tr.startsWith("×") || tr.startsWith("÷")
                || tr.startsWith("·") || tr.startsWith("*") || tr.startsWith("/") || tr.startsWith("^")
                || tr.startsWith("%") || tr.startsWith("!") || tr.startsWith("→") || tr.startsWith("∠")
                || (tr.length() > 0 && SUPS.indexOf(tr.charAt(0)) >= 0);
            if (op) { if (o.getKeep()) setExpr("Ans", false); }
            else setExpr("", false);
            justEval = false;
        }
        Editable txt = exprEt.getText();
        int st = Math.max(0, Math.min(exprEt.getSelectionStart(), exprEt.getSelectionEnd()));
        int en = Math.max(0, Math.max(exprEt.getSelectionStart(), exprEt.getSelectionEnd()));
        if (s.startsWith(" ") && st > 0 && txt.charAt(st - 1) == ' ') s = s.substring(1);
        afterPrefix = false;
        txt.replace(st, en, s);
        exprEt.setSelection(Math.min(st + s.length(), exprEt.getText().length()));
    }

    /** unit from the Meter key: right after Kilo it joins (k + m = km) */
    private void insUnitSym(String sym) {
        boolean glue = afterPrefix;
        String pre = !glue && needSpace() ? " " : "";
        ins(pre + sym);
    }

    private boolean needSpace() {
        int c = exprEt.getSelectionStart();
        if (c <= 0 || justEval) return false;
        char ch = exprEt.getText().charAt(c - 1);
        return Character.isLetterOrDigit(ch) || ch == ')' || ch == ']' || ch == 'π' || SUPS.indexOf(ch) >= 0;
    }

    private void backspace() {
        justEval = false;
        Editable txt = exprEt.getText();
        int st = Math.min(exprEt.getSelectionStart(), exprEt.getSelectionEnd());
        int en = Math.max(exprEt.getSelectionStart(), exprEt.getSelectionEnd());
        if (st < 0) return;
        if (st != en) { txt.delete(st, en); return; }
        if (st == 0) return;
        String before = txt.subSequence(0, st).toString();
        int cut = 1;
        for (String tk : TOKENS) if (before.endsWith(tk)) { cut = tk.length(); break; }
        if (cut == 1) {
            int n = before.length();
            if (before.endsWith("]") && before.lastIndexOf('[') >= 0 && before.lastIndexOf('[') < n - 2
                && !before.substring(before.lastIndexOf('[')).matches(".*[0-9(].*")) cut = n - before.lastIndexOf('[');
            else if (n >= 3 && before.charAt(n - 1) == ' ' && before.charAt(n - 3) == ' ') cut = 3;
            else if (Character.isLowSurrogate(before.charAt(n - 1)) && n >= 2) cut = 2;
        }
        txt.delete(Math.max(0, st - cut), st);
    }

    private void toggleSign() {
        Editable txt = exprEt.getText();
        if (justEval) { setExpr("−(Ans)", true); return; }
        int cur = exprEt.getSelectionStart();
        int p = cur;
        while (p > 0) {
            char ch = txt.charAt(p - 1);
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == ',' || ch == 'π') p--; else break;
        }
        if (p > 0 && txt.charAt(p - 1) == '−') {
            String b = txt.subSequence(0, p - 1).toString().trim();
            char last = b.isEmpty() ? '(' : b.charAt(b.length() - 1);
            if ("(+−×÷·*/^,;=→[".indexOf(last) >= 0) { txt.delete(p - 1, p); return; }
        }
        txt.insert(p, "−");
    }

    // ================= results =================
    private CharSequence styled(String num, String unit, String pre, int color) {
        SpannableStringBuilder b = new SpannableStringBuilder(pre + num);
        b.setSpan(new ForegroundColorSpan(color), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (unit != null && !unit.isEmpty()) {
            int st = b.length();
            b.append(" ").append(unit);
            b.setSpan(new ForegroundColorSpan(o.getIsolate() ? ORANGE : color), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (o.getIsolate()) b.setSpan(new RelativeSizeSpan(0.62f), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
    }

    private void fitResult(int len) {
        resTv.setTextSize(len < 9 ? 46 : len < 14 ? 38 : len < 20 ? 30 : 24);
    }

    /** live answer while typing */
    private void preview() {
        String s = exprEt.getText().toString();
        if (s.trim().isEmpty()) {
            resTv.setTextColor(INK); fitResult(1); resTv.setText("0"); infoTv.setText("0");
            return;
        }
        if (s.contains("=")) { infoTv.setText(t("Press Enter to solve", "हल करने के लिए Enter दबाओ", "ઉકેલવા Enter દબાવો")); return; }
        CalculatorLogic.Result r = logic.peek(s, o);
        if (r.error || r.truth != null) { infoTv.setText(r.error && !r.info.isEmpty() ? r.info : ""); return; }
        fitResult(r.num.length() + r.unit.length());
        resTv.setText(styled(r.num, r.unit, "", 0xFF4A4D52));
        infoTv.setText(r.info.isEmpty() ? "" : r.info);
    }

    private void evaluate() {
        String expr = exprEt.getText().toString();
        if (expr.trim().isEmpty()) return;
        CalculatorLogic.Result r = logic.eval(expr, o);
        if (r.error) {
            resTv.setTextColor(0xFFD32F2F);
            fitResult(5);
            resTv.setText(t("Error", "गलती", "ભૂલ"));
            infoTv.setText(r.info);
            return;
        }
        if (r.truth != null) {
            resTv.setText(styled(r.truth ? "✔ " + t("True", "सही", "સાચું") : "✘ " + t("False", "गलत", "ખોટું"), "", "", r.truth ? 0xFF1E8E3E : 0xFFD32F2F));
            infoTv.setText(expr);
            addHistory(expr, r.truth ? "True" : "False");
            justEval = true;
            return;
        }
        String pre = r.assign != null ? r.assign + " = " : "";
        fitResult(pre.length() + r.num.length() + r.unit.length());
        resTv.setText(styled(r.num, r.unit, pre, INK));
        infoTv.setText(r.info.isEmpty() ? " " : r.info);
        addHistory(expr, (pre + r.num + " " + r.unit).trim());
        if (!o.getKeep()) { snapshot(); setExpr(r.raw, false); }
        justEval = true;
        saveState();
    }

    private void showAns() {
        SciFmt.Shown sh = logic.showAns(o);
        if (sh == null) return;
        fitResult(sh.getNum().length() + sh.getUnit().length());
        resTv.setText(styled(sh.getNum(), sh.getUnit(), "", INK));
        infoTv.setText(sh.getApprox() != null ? sh.getApprox() : " ");
    }

    private void modeChanged() {
        SciStore.INSTANCE.save(this, o);
        refreshLabels();
        if (justEval) showAns(); else preview();
    }

    private void refreshLabels() {
        if (angleChip == null) return;
        angleChip.setText(o.getAngleName().equals("GRA") ? "GRA" : o.getAngleName());
        fmtChip.setText(o.getModeName());
        decChip.setText(o.getDec() < 0 ? "0.00" : o.getDecName());
        decChip.setTextColor(o.getDec() < 0 ? 0xFFB0B4BA : 0xFFFFFFFF);
        exactChip.setText("EXACT");
        exactChip.setTextColor(o.getExact() ? ORANGE : 0xFF80858C);
        kiloTv.setText(PREFIXES[Math.floorMod(o.getPrefixIdx(), PREFIXES.length)][0]);
        meterTv.setText(BASEUNITS[Math.floorMod(o.getUnitIdx(), BASEUNITS.length)][0]);
        realTv.setText(o.getComplex() ? "Cmplx" : "Real");
        mulTv.setText(o.getMulSign());
        divTv.setText(o.getDivSign());
        dotTv.setText(o.getComma() ? "," : ".");
    }

    private void decimalsDialog() {
        String[] items = new String[12];
        items[0] = t("Auto", "अपने आप", "આપમેળે");
        for (int i = 0; i <= 10; i++) {
            StringBuilder z = new StringBuilder(i == 0 ? "0" : "0.");
            for (int j = 0; j < i; j++) z.append('0');
            items[i + 1] = z.toString();
        }
        new AlertDialog.Builder(this).setTitle(t("Decimal places", "दशमलव के बाद अंक", "દશાંશ પછી અંક"))
            .setSingleChoiceItems(items, o.getDec() + 1, (d, w) -> { o.setDec(w - 1); modeChanged(); d.dismiss(); })
            .show();
    }

    // ================= unit lists (long-press Kilo / Meter, and _ ) =================
    private void prefixDialog() {
        List<Triple<String, Double, String>> list = new ArrayList<>();
        for (Triple<String, Double, String> p : SciMath.INSTANCE.getPREFIX()) {
            String s = p.getFirst();
            if (!s.equals("u") && !s.equals("da") && !s.equals("Y") && !s.equals("Z")) list.add(p);
        }
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Triple<String, Double, String> p = list.get(i);
            items[i] = p.getFirst() + "   —   " + p.getThird() + "   (10" + SciFmt.INSTANCE.sup((int) Math.round(Math.log10(p.getSecond()))) + ")";
        }
        new AlertDialog.Builder(this).setTitle("Kilo ▲▼")
            .setItems(items, (d, w) -> {
                String pre = needSpace() ? " " : "";
                ins(pre + list.get(w).getFirst());
                afterPrefix = true;
            }).show();
    }

    private Dim dimsBeforeArrow() {
        String s = exprEt.getText().toString();
        int i = s.lastIndexOf('→');
        String head = (i >= 0 ? s.substring(0, i) : s).trim();
        if (head.isEmpty()) return logic.ans == null ? null : logic.ans.getD();
        try {
            return SciMath.INSTANCE.run(head, new SciMath.Env(logic.vars, o.getAngle(), logic.ans), o.getComma()).getQ().getD();
        } catch (Exception e) {
            return null;
        }
    }

    private void unitDialog(Dim dims, boolean onlyPlain) {
        List<UCat> cats = new ArrayList<>();
        for (UCat c : Units.INSTANCE.getCATS()) {
            if (dims == null || (Dim.Companion.ofCat(c.getId()).equals(dims) && !c.getId().equals("count") && !c.getId().equals("conc"))) cats.add(c);
        }
        if (cats.isEmpty()) cats.addAll(Units.INSTANCE.getCATS());
        if (cats.size() == 1) { unitList(cats.get(0), onlyPlain); return; }
        String[] items = new String[cats.size()];
        for (int i = 0; i < cats.size(); i++) items[i] = cats.get(i).getIcon() + "  " + cats.get(i).name(o.getLang());
        new AlertDialog.Builder(this).setTitle(t("Which kind of unit?", "किस चीज़ की यूनिट?", "શેનો યુનિટ?"))
            .setItems(items, (d, w) -> unitList(cats.get(w), onlyPlain))
            .show();
    }

    private void unitList(UCat cat, boolean onlyPlain) {
        List<UDef> us = new ArrayList<>();
        for (UDef u : Units.INSTANCE.inCat(cat.getId()))
            if (!onlyPlain || (!u.getSym().isEmpty() && u.getOff() == 0.0 && !u.getInv() && u.getK() == 1.0)) us.add(u);
        if (us.isEmpty()) us.addAll(Units.INSTANCE.inCat(cat.getId()));
        us.sort((a, b) -> b.getPop() - a.getPop());
        String[] items = new String[us.size()];
        for (int i = 0; i < us.size(); i++) {
            UDef u = us.get(i);
            String nm = u.name(o.getLang());
            items[i] = u.getFlag() + "  " + nm + (!u.getSym().isEmpty() && !u.getSym().equals(nm) ? "  (" + u.getSym() + ")" : "");
        }
        new AlertDialog.Builder(this).setTitle(cat.getIcon() + " " + cat.name(o.getLang()))
            .setItems(items, (d, w) -> {
                UDef u = us.get(w);
                String pre = needSpace() ? " " : "";
                ins(pre + SciMath.INSTANCE.unitText(u));
            }).show();
    }

    // ================= history & memory =================
    private void addHistory(String e, String r) {
        SharedPreferences sp = getSharedPreferences("sci", MODE_PRIVATE);
        try {
            JSONArray old = new JSONArray(sp.getString("hist", "[]"));
            JSONArray n = new JSONArray();
            n.put(new JSONObject().put("e", e).put("r", r));
            for (int i = 0; i < Math.min(old.length(), 99); i++) {
                JSONObject ob = old.optJSONObject(i);
                if (ob == null || (i == 0 && e.equals(ob.optString("e")))) continue;
                n.put(ob);
            }
            sp.edit().putString("hist", n.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void copyResult() {
        if (logic.ans == null) return;
        String txt = SciFmt.INSTANCE.rawOf(logic.ans, logic.ansUnit, o);
        if (txt.equals("Ans")) txt = SciFmt.INSTANCE.raw(logic.ans.getRe(), o);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("result", txt));
        Toast.makeText(this, t("Copied: ", "कॉपी: ", "કૉપી: ") + txt, Toast.LENGTH_SHORT).show();
    }

    private void loadState() {
        SharedPreferences sp = getSharedPreferences("sci", MODE_PRIVATE);
        logic.ans = SQ.Companion.parse(sp.getString("ans", null));
        String uid = sp.getString("ansUnit", null);
        logic.ansUnit = uid == null ? null : Units.INSTANCE.byId(uid);
        for (String v : SciMath.INSTANCE.getVARS()) {
            SQ q = SQ.Companion.parse(sp.getString("var" + v, null));
            if (q != null) logic.vars.put(v, q);
        }
        setExpr(sp.getString("input", ""), false);
    }

    private void saveState() {
        SharedPreferences.Editor e = getSharedPreferences("sci", MODE_PRIVATE).edit();
        e.putString("input", exprEt.getText().toString());
        if (logic.ans != null) e.putString("ans", logic.ans.toString());
        e.putString("ansUnit", logic.ansUnit == null ? null : logic.ansUnit.getId());
        for (String v : logic.vars.keySet()) e.putString("var" + v, logic.vars.get(v).toString());
        e.apply();
    }

    private String t(String en, String hi, String gu) {
        return o.getLang() == 1 ? hi : o.getLang() == 2 ? gu : en;
    }
}
