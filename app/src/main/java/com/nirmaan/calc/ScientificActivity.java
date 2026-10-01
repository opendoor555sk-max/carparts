package com.nirmaan.calc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import kotlin.Triple;

/** Calculator screen (pehli image). */
public class ScientificActivity extends Activity {

    private TextView display;
    private TextView hist;
    private final StringBuilder input = new StringBuilder();
    private final CalculatorLogic logic = new CalculatorLogic();
    private SciOpts o;
    private Button angleBtn, fmtBtn, decBtn, exactBtn;
    private Button mulKey, divKey, dotKey;
    private boolean justEval = false;
    private boolean afterPrefix = false;

    private static final String[] TOKENS = {"d/dx(", "asin(", "acos(", "atan(", "sin(", "cos(", "tan(",
        "log(", "ln(", "Re(", "Im(", "∫(", "Σ(", "Π(", "√(", "∛(", "Ans", " → "};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        o = SciStore.INSTANCE.load(this);
        loadState();
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Theme.BG);
        sv.addView(buildCalculator());
        setContentView(sv);
        show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        o = SciStore.INSTANCE.load(this);
        refreshLabels();
        SharedPreferences sp = getSharedPreferences("sci", MODE_PRIVATE);
        String pick = sp.getString("pick", null);
        if (pick != null) {
            sp.edit().remove("pick").apply();
            input.setLength(0);
            input.append(pick);
            justEval = false;
            show();
        } else if (justEval) showAns();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    // ========== CALCULATOR SCREEN ==========
    private LinearLayout buildCalculator() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(12, 20, 12, 12);

        // Top mode buttons
        LinearLayout top = row();
        angleBtn = modeBtn("GRA");
        fmtBtn = modeBtn("ENG");
        decBtn = modeBtn("0.00");
        exactBtn = modeBtn("EXACT", true);
        angleBtn.setOnClickListener(v -> { o.setAngle((o.getAngle() + 1) % 3); modeChanged(); });
        fmtBtn.setOnClickListener(v -> { o.setMode((o.getMode() + 1) % 3); modeChanged(); });
        decBtn.setOnClickListener(v -> decimalsDialog());
        exactBtn.setOnClickListener(v -> { o.setExact(!o.getExact()); modeChanged(); });
        top.addView(angleBtn);
        top.addView(fmtBtn);
        top.addView(decBtn);
        top.addView(exactBtn);
        root.addView(top);

        // Display
        display = new TextView(this);
        display.setText("0");
        display.setTextSize(40);
        display.setTextColor(Theme.TEXT);
        display.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        display.setPadding(20, 40, 20, 20);
        display.setOnLongClickListener(v -> { copyResult(); return true; });
        root.addView(display);

        // Small history line
        hist = new TextView(this);
        hist.setText("0");
        hist.setTextColor(Theme.SUB);
        hist.setTextSize(14);
        hist.setGravity(Gravity.START);
        hist.setPadding(20, 0, 20, 12);
        hist.setOnClickListener(v -> SettingsActivity.historyDialog(this, o, this::usePicked));
        root.addView(hist);

        // Keypad
        String[][] keys = {
            {"X", "Y", "Z", "Kilo", "Meter", "∫"},
            {"dx", "Σ", "i", "Real", "ln", "∞"},
            {"sin", "cos", "tan", "√", "xʸ", "π"},
            {"(", ")", "±", "_", "%", "="},
            {"7", "8", "9", "⌫", "AC"},
            {"4", "5", "6", "−", "÷"},
            {"1", "2", "3", "+", "×"},
            {"0", ".", "E", "Enter"}
        };

        for (String[] rowKeys : keys) {
            LinearLayout r = row();
            for (String k : rowKeys) {
                Button b = keyBtn(k);
                b.setOnClickListener(v -> onKey(k));
                b.setOnLongClickListener(v -> onLong(k));
                if (k.equals("×")) mulKey = b;
                if (k.equals("÷")) divKey = b;
                if (k.equals(".")) dotKey = b;
                r.addView(b);
            }
            root.addView(r);
        }

        // Settings button (gear)
        Button settings = new Button(this);
        settings.setText("⚙  Settings");
        settings.setTextColor(Theme.TEXT);
        settings.setBackgroundColor(Theme.CARD);
        settings.setAllCaps(false);
        settings.setStateListAnimator(null);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 100);
        sp.setMargins(6, 16, 6, 6);
        settings.setLayoutParams(sp);
        root.addView(settings);

        return root;
    }

    // ========== KEYS ==========
    private void onKey(String k) {
        switch (k) {
            case "AC" -> {
                input.setLength(0);
                justEval = false;
                display.setText("0");
                display.setTextColor(Theme.TEXT);
                return;
            }
            case "⌫" -> backspace();
            case "Enter" -> { evaluate(); return; }
            case "=" -> {
                // with X / Y / Z in the line "=" makes an equation (2X+3=11), otherwise it gives the answer
                if (hasVar() && !justEval) append(spaced("="));
                else { evaluate(); return; }
            }
            case "Kilo" -> { prefixDialog(); return; }
            case "Meter" -> { unitDialog(null, false); return; }
            case "_" -> { append(" → "); unitDialog(dimsBeforeArrow(), false); }
            case "±" -> toggleSign();
            case "sin", "cos", "tan", "ln" -> append(k + "(");
            case "√" -> append("√(");
            case "xʸ" -> append("^");
            case "∫" -> append("∫(");
            case "dx" -> append("d/dx(");
            case "Σ" -> append("Σ(");
            case "Real" -> append("Re(");
            case "−", "+" -> append(spaced(k));
            case "×" -> append(spaced(o.getMulSign()));
            case "÷" -> append(spaced(o.getDivSign()));
            case "." -> append(o.getComma() ? "," : ".");
            default -> append(k);
        }
        show();
    }

    private boolean onLong(String k) {
        switch (k) {
            case "X", "Y", "Z" -> {
                if (logic.ans == null) { toast(t("Press Enter first", "पहले Enter दबाओ", "પહેલા Enter દબાવો")); return true; }
                logic.vars.put(k, logic.ans);
                SciFmt.Shown sh = logic.showAns(o);
                toast((k + " = " + sh.getNum() + " " + sh.getUnit()).trim());
                saveState();
                return true;
            }
            case "sin", "cos", "tan" -> append("a" + k + "(");
            case "√" -> append("∛(");
            case "ln" -> append("log(");
            case "π" -> append("e");
            case "xʸ" -> append("²");
            case "Σ" -> append("Π(");
            case "Real" -> append("Im(");
            case "i" -> append("Ans");
            case "∞" -> append("!");
            case "." -> append(o.getComma() ? "; " : ", ");
            case "⌫" -> input.setLength(0);
            case "Enter" -> { copyResult(); return true; }
            case "Meter" -> { startActivity(new Intent(this, UnitsActivity.class)); return true; }
            default -> { return false; }
        }
        show();
        return true;
    }

    private String spaced(String op) {
        return o.getSpacious() ? " " + op + " " : op;
    }

    private boolean hasVar() {
        String s = input.toString();
        return s.contains("X") || s.contains("Y") || s.contains("Z");
    }

    private void append(String s) {
        if (justEval) {
            String tr = s.trim();
            boolean op = tr.startsWith("+") || tr.startsWith("−") || tr.startsWith("×") || tr.startsWith("÷")
                || tr.startsWith("·") || tr.startsWith("*") || tr.startsWith("/") || tr.startsWith("^")
                || tr.startsWith("%") || tr.startsWith("!") || tr.startsWith("²") || tr.startsWith("→") || tr.startsWith("=");
            if (!op) input.setLength(0);   // a new sum
            justEval = false;
        }
        if (s.startsWith(" ") && input.length() > 0 && input.charAt(input.length() - 1) == ' ') s = s.substring(1);
        afterPrefix = false;
        input.append(s);
    }

    private void backspace() {
        justEval = false;
        int n = input.length();
        if (n == 0) return;
        String s = input.toString();
        int cut = 1;
        for (String tk : TOKENS) if (s.endsWith(tk)) { cut = tk.length(); break; }
        if (cut == 1) {
            if (s.endsWith("]") && s.lastIndexOf('[') >= 0) cut = n - s.lastIndexOf('[');
            else if (n >= 3 && s.charAt(n - 1) == ' ' && s.charAt(n - 3) == ' ') cut = 3;   // " + "
            else if (Character.isLowSurrogate(s.charAt(n - 1)) && n >= 2) cut = 2;
        }
        input.setLength(Math.max(0, n - cut));
    }

    private void toggleSign() {
        if (justEval) {
            justEval = false;
            String r = input.toString();
            input.setLength(0);
            input.append("−(").append(r).append(")");
            return;
        }
        int p = input.length();
        while (p > 0) {
            char ch = input.charAt(p - 1);
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == ',' || ch == 'π') p--; else break;
        }
        if (p > 0 && input.charAt(p - 1) == '−') {
            String before = input.substring(0, p - 1).trim();
            char last = before.isEmpty() ? '(' : before.charAt(before.length() - 1);
            if ("(+−×÷·*/^,;=→".indexOf(last) >= 0) { input.deleteCharAt(p - 1); return; }
        }
        input.insert(p, "−");
    }

    // ========== SHOWING ==========
    private void show() {
        String s = input.length() == 0 ? "0" : input.toString();
        display.setTextColor(Theme.TEXT);
        int n = s.length();
        display.setTextSize(n < 12 ? 40 : n < 20 ? 32 : n < 30 ? 26 : 20);
        display.setText(s);
    }

    private CharSequence styled(String num, String unit, String pre) {
        SpannableStringBuilder b = new SpannableStringBuilder(pre + num);
        if (unit != null && !unit.isEmpty()) {
            int st = b.length();
            b.append(" ").append(unit);
            if (o.getIsolate()) {
                b.setSpan(new ForegroundColorSpan(Theme.ACCENT), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                b.setSpan(new RelativeSizeSpan(0.7f), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return b;
    }

    private void evaluate() {
        if (input.length() == 0) return;
        String expr = input.toString();
        CalculatorLogic.Result r = logic.eval(expr, o);
        display.setTextSize(36);
        if (r.error) {
            display.setTextColor(Theme.RED);
            display.setText(t("Error", "गलती", "ભૂલ"));
            hist.setText(r.info.isEmpty() ? expr : r.info);
            return;
        }
        display.setTextColor(Theme.TEXT);
        if (r.truth != null) {
            display.setTextColor(r.truth ? Theme.GREEN : Theme.RED);
            display.setText(r.truth ? "✔ " + t("True", "सही", "સાચું") : "✘ " + t("False", "गलत", "ખોટું"));
            hist.setText(expr);
            addHistory(expr, display.getText().toString());
            justEval = true;
            return;
        }
        String pre = r.assign != null ? r.assign + " = " : "";
        display.setText(styled(r.num, r.unit, pre));
        String h = o.getKeep() ? expr + " =" : "";
        if (!r.info.isEmpty()) h = h.isEmpty() ? r.info : h + "   " + r.info;
        hist.setText(h.isEmpty() ? " " : h);
        addHistory(expr, (pre + r.num + " " + r.unit).trim());
        input.setLength(0);
        input.append(r.raw);
        justEval = true;
        saveState();
    }

    private void showAns() {
        SciFmt.Shown sh = logic.showAns(o);
        if (sh == null) return;
        display.setTextColor(Theme.TEXT);
        display.setTextSize(36);
        display.setText(styled(sh.getNum(), sh.getUnit(), ""));
        if (sh.getApprox() != null) hist.setText(sh.getApprox());
    }

    private void modeChanged() {
        SciStore.INSTANCE.save(this, o);
        refreshLabels();
        if (justEval) showAns();
    }

    private void refreshLabels() {
        if (angleBtn == null) return;
        angleBtn.setText(o.getAngleName());
        fmtBtn.setText(o.getModeName());
        decBtn.setText(o.getDec() < 0 ? "0.00" : o.getDecName());
        decBtn.setTextColor(o.getDec() < 0 ? Theme.SUB : Theme.TEXT);
        exactBtn.setBackgroundColor(o.getExact() ? Theme.ACCENT : Theme.KEY);
        mulKey.setText(o.getMulSign());
        divKey.setText(o.getDivSign());
        dotKey.setText(o.getComma() ? "," : ".");
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

    // ========== UNITS ==========
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
        new AlertDialog.Builder(this).setTitle(t("Kilo, Mega, milli … then pick a unit", "पहले का अक्षर, फिर यूनिट चुनो", "આગળનો અક્ષર, પછી યુનિટ પસંદ કરો"))
            .setItems(items, (d, w) -> {
                String pre = needSpace() ? " " : "";
                append(pre + list.get(w).getFirst());
                afterPrefix = true;
                show();
                unitDialog(null, true);
            }).show();
    }

    private boolean needSpace() {
        if (input.length() == 0 || justEval) return false;
        char ch = input.charAt(input.length() - 1);
        return Character.isLetterOrDigit(ch) || ch == ')' || ch == ']' || ch == 'π';
    }

    private Dim dimsBeforeArrow() {
        String s = input.toString();
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
            .setOnCancelListener(d -> afterPrefix = false)
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
                boolean wasPrefix = afterPrefix;
                String pre = !wasPrefix && needSpace() ? " " : "";
                append(pre + (wasPrefix && !u.getSym().isEmpty() ? u.getSym() : SciMath.INSTANCE.unitText(u)));
                show();
            })
            .setOnCancelListener(d -> afterPrefix = false)
            .show();
    }

    // ========== HISTORY / MEMORY ==========
    private void usePicked(String e) {
        input.setLength(0);
        input.append(e);
        justEval = false;
        show();
    }

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
        toast(t("Copied: ", "कॉपी: ", "કૉપી: ") + txt);
    }

    private void loadState() {
        SharedPreferences sp = getSharedPreferences("sci", MODE_PRIVATE);
        logic.ans = SQ.Companion.parse(sp.getString("ans", null));
        String uid = sp.getString("ansUnit", null);
        logic.ansUnit = uid == null ? null : Units.INSTANCE.byId(uid);
        for (String v : new String[]{"X", "Y", "Z"}) {
            SQ q = SQ.Companion.parse(sp.getString("var" + v, null));
            if (q != null) logic.vars.put(v, q);
        }
        input.append(sp.getString("input", ""));
    }

    private void saveState() {
        SharedPreferences.Editor e = getSharedPreferences("sci", MODE_PRIVATE).edit();
        e.putString("input", input.toString());
        if (logic.ans != null) e.putString("ans", logic.ans.toString());
        e.putString("ansUnit", logic.ansUnit == null ? null : logic.ansUnit.getId());
        for (String v : logic.vars.keySet()) e.putString("var" + v, logic.vars.get(v).toString());
        e.apply();
    }

    private String t(String en, String hi, String gu) {
        return o.getLang() == 1 ? hi : o.getLang() == 2 ? gu : en;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ========== UI HELPERS ==========
    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private Button modeBtn(String t) {
        return modeBtn(t, false);
    }

    private Button modeBtn(String t, boolean accent) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(Theme.TEXT);
        b.setBackgroundColor(accent ? Theme.ACCENT : Theme.KEY);
        b.setAllCaps(false);
        b.setTextSize(12);
        flat(b);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 90, 1f);
        p.setMargins(4, 4, 4, 4);
        b.setLayoutParams(p);
        return b;
    }

    private Button keyBtn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(Theme.TEXT);
        b.setAllCaps(false);
        b.setTextSize(t.length() > 2 ? 13 : 18);

        int bg = Theme.KEY;
        if (t.equals("AC")) bg = Theme.GREEN;
        else if (t.equals("⌫")) bg = Theme.RED;
        else if (t.equals("Enter")) bg = Theme.ACCENT;

        b.setBackgroundColor(bg);
        flat(b);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 110, 1f);
        p.setMargins(5, 5, 5, 5);
        b.setLayoutParams(p);
        return b;
    }

    /** no shadow, no forced minimum size, so "Meter" fits in the small key */
    private void flat(Button b) {
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setSingleLine(true);
    }
}
