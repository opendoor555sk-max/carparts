package com.nirmaan.calc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Settings screen (doosri image). */
public class SettingsActivity extends Activity {

    private SciOpts o;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        o = SciStore.INSTANCE.load(this);
        setContentView(buildSettings());
    }

    private void save() {
        SciStore.INSTANCE.save(this, o);
    }

    private LinearLayout buildSettings() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(24, 48, 24, 24);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        addHeader(content, "General");
        addItem(content, "History", null, () -> historyDialog(this, o, e -> {
            getSharedPreferences("sci", MODE_PRIVATE).edit().putString("pick", e).apply();
            finish();
        }));
        addChoice(content, "Select language", new String[]{"English", "हिन्दी", "ગુજરાતી"}, o::getLang, o::setLang);
        addChoice(content, "Decimal separator", new String[]{"Dot", "Comma"}, () -> o.getComma() ? 1 : 0, w -> o.setComma(w == 1));
        addChoice(content, "Multiplication sign", new String[]{"Times", "Dot", "Asterisk"}, o::getMul, o::setMul);
        addChoice(content, "Division sign", new String[]{"Division", "Slash"}, o::getDiv, o::setDiv);

        addHeader(content, "Calculation");
        addSwitch(content, "Preserve structure", o.getKeep(), o::setKeep);

        addHeader(content, "Output");
        addSwitch(content, "Negative exponents", o.getNegExp(), o::setNegExp);
        addSwitch(content, "Abbreviate names", o.getAbbrev(), o::setAbbrev);
        addSwitch(content, "Spacious output", o.getSpacious(), o::setSpacious);
        addSwitch(content, "Allow prefix in denominator", o.getPrefixDen(), o::setPrefixDen);
        addSwitch(content, "Isolate units", o.getIsolate(), o::setIsolate);
        addChoice(content, "Exp display", new String[]{"2×10³", "2E3", "2e3"}, o::getExpStyle, o::setExpStyle);
        addChoice(content, "Interval display", new String[]{"Concise", "Plus-minus"}, o::getInterval, o::setInterval);

        addHeader(content, "About app");
        addItem(content, "Rate us", null, () -> toast("Play Store par aane ke baad / Play Store પર આવ્યા પછી"));
        addItem(content, "Share app", null, () -> {
            String msg = "Nirmaan Calc — construction + scientific calculator + world units\n"
                + "https://github.com/opendoor555sk-max/carparts/releases/download/calc-latest/NirmaanCalc.apk";
            try {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Share"));
            } catch (Exception ignored) { }
        });
        addItem(content, "More apps", null, () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/opendoor555sk-max/carparts/releases")));
            } catch (Exception ignored) { }
        });
        addItem(content, "Privacy policy", null, () -> message("Privacy policy",
            "Nirmaan Calc keeps all your sums only on this phone. No account, nothing is sent out. "
                + "The internet is used only to check for a new version of the app."));
        addItem(content, "Terms and conditions", null, () -> message("Terms and conditions",
            "Nirmaan Calc is free to use. Check important measurements and material quantities before buying or building; "
                + "the maker is not responsible for losses from a wrong entry or a wrong result."));
        addItem(content, "Version " + Updater.INSTANCE.myVersionName(this), null, () -> Updater.INSTANCE.check(this, true));

        scroll.addView(content);
        root.addView(scroll);
        return root;
    }

    private void addHeader(LinearLayout p, String t) {
        TextView h = new TextView(this);
        h.setText(t);
        h.setTextColor(Theme.SUB);
        h.setTextSize(13);
        h.setPadding(0, 28, 0, 10);
        p.addView(h);
    }

    private TextView addItem(LinearLayout p, String title, String sub, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 18, 0, 18);
        row.setOnClickListener(v -> onClick.run());

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Theme.TEXT);
        t.setTextSize(16);
        left.addView(t);

        TextView s = null;
        if (sub != null) {
            s = new TextView(this);
            s.setText(sub);
            s.setTextColor(Theme.SUB);
            s.setTextSize(13);
            left.addView(s);
        }
        row.addView(left);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Theme.SUB);
        arrow.setTextSize(22);
        row.addView(arrow);

        p.addView(row);
        p.addView(divider());
        return s;
    }

    /** an item whose small grey line shows the chosen value; tap to change */
    private void addChoice(LinearLayout p, String title, String[] items, IntSupplier get, IntConsumer set) {
        TextView[] sub = new TextView[1];
        int cur = Math.max(0, Math.min(items.length - 1, get.getAsInt()));
        sub[0] = addItem(p, title, items[cur], () ->
            new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(items, get.getAsInt(), (d, w) -> {
                    set.accept(w);
                    save();
                    sub[0].setText(items[w]);
                    d.dismiss();
                }).show());
    }

    private void addSwitch(LinearLayout p, String title, boolean on, Consumer<Boolean> set) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 14, 0, 14);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Theme.TEXT);
        t.setTextSize(16);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        Switch sw = new Switch(this);
        sw.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{Theme.ACCENT, 0xFFBDBDBD}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{0x88FF9500, 0xFF555555}));
        sw.setOnCheckedChangeListener((b, v) -> { set.accept(v); save(); });
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());

        p.addView(row);
        p.addView(divider());
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFF2C2C2E);
        v.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private void message(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** old sums: tap one to use it again */
    public static void historyDialog(Activity act, SciOpts o, Consumer<String> onPick) {
        SharedPreferences sp = act.getSharedPreferences("sci", MODE_PRIVATE);
        JSONArray arr;
        try { arr = new JSONArray(sp.getString("hist", "[]")); } catch (Exception e) { arr = new JSONArray(); }
        if (arr.length() == 0) {
            new AlertDialog.Builder(act).setMessage("No history yet").setPositiveButton("OK", null).show();
            return;
        }
        ArrayList<String> exprs = new ArrayList<>();
        ArrayList<String> items = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject ob = arr.optJSONObject(i);
            if (ob == null) continue;
            exprs.add(ob.optString("e"));
            items.add(ob.optString("e") + "\n= " + ob.optString("r"));
        }
        new AlertDialog.Builder(act).setTitle("History")
            .setItems(items.toArray(new String[0]), (d, w) -> onPick.accept(exprs.get(w)))
            .setNegativeButton("Clear all", (d, w) -> sp.edit().putString("hist", "[]").apply())
            .setPositiveButton("Close", null)
            .show();
    }
}
