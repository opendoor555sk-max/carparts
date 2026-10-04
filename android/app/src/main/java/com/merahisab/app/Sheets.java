package com.merahisab.app;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Every bottom-sheet / dialog in the app. */
final class Sheets {
    private final MainActivity a;
    private final Context c;
    private Ui.Sheet cur;

    Sheets(MainActivity a) { this.a = a; this.c = a; }

    /** Entry form state. */
    static final class Form {
        String id = null, type = "income", partyId = null, name = "", amount = "", note = "", date = Fmt.today(), mode = "cash",
                cat = "અન્ય", said = "", due = "", prodId = "", qty = "";
        boolean lock = false;
    }

    private Form form;
    private EditText fName, fAmt, fNote, fQty;
    private LinearLayout suggBox;
    private TextView dateBtn, dueBtn;

    // ---------- plumbing ----------
    Ui.Sheet open(String title) {
        close();
        cur = new Ui.Sheet(c);
        if (title != null) cur.title(title);
        return cur;
    }

    void close() {
        if (cur != null) { cur.dismiss(); cur = null; }
    }

    boolean isOpen() { return cur != null && cur.dlg.isShowing(); }

    private View scrollBox(View inner, int maxDp) {
        ScrollView sv = new ScrollView(c);
        sv.addView(inner);
        sv.setLayoutParams(Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(maxDp)));
        return sv;
    }

    private View lineRow(String l, String r, int rc) {
        LinearLayout row = Ui.h(c);
        Ui.pad(row, 2, 6, 2, 6);
        row.addView(Ui.t(c, l, 15, Ui.TEXT, false), Ui.weight(1));
        row.addView(Ui.t(c, r, 15, rc, true));
        return row;
    }

    private View said(String text) {
        TextView t = Ui.t(c, text, 14, Ui.TEXT, false);
        t.setBackground(Ui.rr(Ui.SOFT, Ui.LILAC, 14));
        Ui.pad(t, 14, 10, 14, 10);
        LinearLayout.LayoutParams p = Ui.fillW();
        p.setMargins(0, Ui.dp(6), 0, Ui.dp(6));
        t.setLayoutParams(p);
        return t;
    }

    private View seg(String[][] items, String cur, final java.util.function.Consumer<String> pick) {
        LinearLayout l = Ui.h(c);
        l.setBackground(Ui.rr(Ui.SURFACE2, Ui.LINE, 14));
        Ui.pad(l, 4, 4, 4, 4);
        for (final String[] it : items) {
            boolean on = it[0].equals(cur);
            TextView b = Ui.t(c, it[1], 14, on ? Color.WHITE : Ui.TEXT, true);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(Ui.dp(42));
            Ui.pad(b, 6, 8, 6, 8);
            if (on) b.setBackground(Ui.rr(Ui.PURPLE, 0, 11));
            Ui.tap(b, new Runnable() { @Override public void run() { pick.accept(it[0]); } });
            l.addView(b, Ui.weight(1));
        }
        LinearLayout.LayoutParams p = Ui.fillW();
        p.setMargins(0, Ui.dp(6), 0, 0);
        l.setLayoutParams(p);
        return l;
    }

    private View srow(String title, String sub, String right, Runnable r) {
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 10, 10, 10, 10);
        LinearLayout tx = Ui.v(c);
        tx.addView(Ui.t(c, title, 15, Ui.TEXT, true));
        if (sub != null && !sub.isEmpty()) tx.addView(Ui.t(c, sub, 12, Ui.MUTED, false));
        l.addView(tx, Ui.weight(1));
        l.addView(Ui.t(c, right == null ? "›" : right, 15, Ui.MUTED, true));
        Ui.tap(l, r);
        return l;
    }

    private View rowItem(String name, String t1, String t2, String amt, int amtColor, String amtSub, Runnable r) {
        LinearLayout l = Ui.h(c);
        l.setBackground(Ui.rr(Ui.SURFACE2, 0, 14));
        Ui.pad(l, 12, 10, 12, 10);
        LinearLayout.LayoutParams lp = Ui.fillW();
        lp.setMargins(0, Ui.dp(3), 0, Ui.dp(3));
        l.setLayoutParams(lp);
        TextView av = Ui.t(c, Fmt.initial(name), 16, Ui.ACCENT, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Ui.rr(Ui.SOFT, 0, 20));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
        ap.setMargins(0, 0, Ui.dp(10), 0);
        l.addView(av, ap);
        LinearLayout mid = Ui.v(c);
        mid.addView(Ui.t(c, t1, 15, Ui.TEXT, true));
        if (t2 != null && !t2.isEmpty()) mid.addView(Ui.t(c, t2, 12, Ui.MUTED, false));
        l.addView(mid, Ui.weight(1));
        if (amt != null) {
            LinearLayout rt = Ui.v(c);
            rt.setGravity(Gravity.END);
            TextView am = Ui.t(c, amt, 15, amtColor, true);
            am.setGravity(Gravity.END);
            rt.addView(am);
            if (amtSub != null && !amtSub.isEmpty()) {
                TextView s = Ui.t(c, amtSub, 11, Ui.MUTED, false);
                s.setGravity(Gravity.END);
                rt.addView(s);
            }
            l.addView(rt);
        }
        if (r != null) Ui.tap(l, r);
        return l;
    }

    void confirm(String msg, String yesLabel, final Runnable onYes) {
        Ui.Sheet s = open(msg);
        s.add(Ui.btn(c, yesLabel, "danger", new Runnable() { @Override public void run() { close(); onYes.run(); } }));
        s.add(Ui.btn(c, "ના, પાછા", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private void ask(String glyph, String q, String yesLabel, Runnable yes, String noLabel, Runnable no) {
        Ui.Sheet s = open(null);
        TextView g = Ui.t(c, glyph, 30, Ui.ACCENT, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(Ui.SOFT, 0, 18));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(60), Ui.dp(60));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        s.add(g);
        g.setLayoutParams(gp);
        TextView t = Ui.t(c, q, 19, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        Ui.pad(t, 0, 12, 0, 6);
        s.add(t);
        s.add(Ui.btn(c, yesLabel, "primary", yes));
        s.add(Ui.btn(c, noLabel, "ghost", no));
        s.show();
    }

    // ---------- new-note flow ----------
    void askIncome() {
        ask("👛", "શું આ ખાતા ગ્રાહકની વસૂલી છે?", "ખાતામાં નોંધો", new Runnable() { @Override public void run() { pickSheet("customer", "got"); } },
                "સીધી વેચાણ", new Runnable() { @Override public void run() { Form f = new Form(); f.type = "income"; f.lock = true; openEntry(f); } });
    }

    void askExpense() {
        ask("🏪", "શું આ લેણદારને ચૂકવણી છે?", "હા, લેણદારને ચૂકવણી", new Runnable() { @Override public void run() { pickSheet("creditor", "paid"); } },
                "ના, સીધો ખર્ચ", new Runnable() { @Override public void run() { Form f = new Form(); f.type = "expense"; f.lock = true; openEntry(f); } });
    }

    // ---------- party picker ----------
    private String pickQ = "";
    private boolean pickAdding = false;

    void pickSheet(final String kind, final String next) {
        pickQ = "";
        pickAdding = false;
        renderPick(kind, next);
    }

    private void renderPick(final String kind, final String next) {
        boolean cr = kind.equals("creditor");
        Ui.Sheet s = open(cr ? "લેણદાર પસંદ કરો" : "ગ્રાહક પસંદ કરો");
        final EditText q = Ui.fld(c, cr ? "🔍 લેણદાર શોધો..." : "🔍 ગ્રાહક શોધો...", pickQ, Ui.IN_PLAIN);
        s.add(q);
        final LinearLayout list = Ui.v(c);
        s.add(scrollBox(list, 260));
        fillPick(list, kind, next);
        Ui.onText(q, new Runnable() {
            @Override public void run() { pickQ = q.getText().toString(); fillPick(list, kind, next); }
        });
        if (pickAdding) {
            s.add(Ui.label(c, "નવા ખાતાનું નામ"));
            final EditText nm = Ui.fld(c, "", pickQ, Ui.IN_TEXT);
            s.add(nm);
            s.add(Ui.label(c, "ફોન નંબર (જરૂરી નથી)"));
            final EditText ph = Ui.fld(c, "", "", Ui.IN_PHONE);
            s.add(ph);
            s.add(Ui.btn(c, "સેવ કરીને આગળ વધો", "primary", new Runnable() {
                @Override public void run() {
                    String name = nm.getText().toString().trim(), phone = ph.getText().toString().trim();
                    if (name.isEmpty()) { a.toast("નામ લખો"); return; }
                    Model.Party ex = a.db.findByName(name);
                    if (ex == null) { ex = a.newParty(name, phone, kind); a.save(); }
                    Form f = new Form();
                    f.type = next; f.partyId = ex.id; f.name = ex.name; f.lock = true;
                    openEntry(f);
                }
            }));
        } else {
            s.add(Ui.btn(c, "＋ નવું ખાતું ઉમેરો", "ghost", new Runnable() {
                @Override public void run() { pickAdding = true; renderPick(kind, next); }
            }));
        }
        s.show();
    }

    private void fillPick(LinearLayout list, final String kind, final String next) {
        list.removeAllViews();
        String q = Parser.normText(pickQ);
        List<Model.Party> ps = new ArrayList<>();
        for (Model.Party p : a.db.parties) {
            if (!q.isEmpty() && !Parser.normText(p.name).contains(q) && !p.phone.contains(q)) continue;
            ps.add(p);
        }
        Collections.sort(ps, new Comparator<Model.Party>() {
            @Override public int compare(Model.Party x, Model.Party y) {
                int kx = a.db.kindOf(x).equals(kind) ? 0 : 1, ky = a.db.kindOf(y).equals(kind) ? 0 : 1;
                if (kx != ky) return kx - ky;
                return Double.compare(Math.abs(a.db.partyBal(y.id)), Math.abs(a.db.partyBal(x.id)));
            }
        });
        if (ps.isEmpty()) {
            TextView e = Ui.t(c, kind.equals("creditor") ? "કોઈ લેણદાર મળ્યા નહીં." : "કોઈ ગ્રાહક મળ્યા નહીં.", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER);
            Ui.pad(e, 10, 18, 10, 18);
            list.addView(e);
            return;
        }
        for (final Model.Party p : ps) {
            double b = a.db.partyBal(p.id);
            list.addView(rowItem(p.name, p.name, (a.db.kindOf(p).equals("creditor") ? "લેણદાર" : "ગ્રાહક") + (p.phone.isEmpty() ? "" : " · " + p.phone),
                    Fmt.money(Math.abs(b)), b > 0 ? Ui.GREEN : (b < 0 ? Ui.RED : Ui.MUTED), b > 0 ? "લેવાના" : (b < 0 ? "દેવાના" : ""), new Runnable() {
                        @Override public void run() {
                            Form f = new Form();
                            f.type = next; f.partyId = p.id; f.name = p.name; f.lock = true;
                            openEntry(f);
                        }
                    }));
        }
    }

    // ---------- entry form ----------
    void openEntry(Form f) { form = f; renderForm(); }

    private void syncForm() {
        if (form == null) return;
        if (fName != null) form.name = fName.getText().toString();
        if (fAmt != null) form.amount = fAmt.getText().toString();
        if (fNote != null) form.note = fNote.getText().toString();
        if (fQty != null) form.qty = fQty.getText().toString();
    }

    private List<Model.Party> similar(String name) {
        String q = Parser.normText(name);
        List<Model.Party> out = new ArrayList<>();
        if (q.isEmpty()) {
            for (int i = 0; i < Math.min(6, a.db.parties.size()); i++) out.add(a.db.parties.get(i));
            return out;
        }
        String first = q.split(" ")[0];
        for (Model.Party p : a.db.parties) {
            String n = Parser.normText(p.name);
            String f = n.split(" ")[0];
            if (n.contains(q) || q.contains(n) || f.equals(first) || (f.length() >= 4 && first.length() >= 4 && Parser.lev(f, first) <= 2)) out.add(p);
            if (out.size() >= 6) break;
        }
        for (Model.Party p : Parser.phonMatches(name, a.db.parties)) if (!out.contains(p) && out.size() < 8) out.add(p);
        return out;
    }

    private void fillSugg() {
        if (suggBox == null || form == null) return;
        suggBox.removeAllViews();
        if (!Model.type(form.type).party) return;
        String n = Parser.normText(form.name);
        boolean exact = false;
        for (Model.Party p : a.db.parties) if (Parser.normText(p.name).equals(n)) exact = true;
        for (final Model.Party p : similar(form.name)) {
            suggBox.addView(Ui.chip(c, p.name, p.id.equals(form.partyId), new Runnable() {
                @Override public void run() {
                    form.partyId = p.id; form.name = p.name;
                    if (fName != null) fName.setText(p.name);
                    fillSugg();
                }
            }));
        }
        if (!form.name.trim().isEmpty() && form.partyId == null && !exact) {
            TextView t = Ui.t(c, "નવું ખાતું બનશે: " + form.name.trim(), 12, Ui.AMBER, false);
            suggBox.addView(t);
        }
    }

    private void pickDate(final boolean due) {
        String cur = due ? form.due : form.date;
        int y, m, d;
        try {
            String[] p = (Fmt.validIso(cur) ? cur : Fmt.today()).split("-");
            y = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]) - 1; d = Integer.parseInt(p[2]);
        } catch (Exception e) { y = 2026; m = 0; d = 1; }
        new DatePickerDialog(c, new DatePickerDialog.OnDateSetListener() {
            @Override public void onDateSet(DatePicker v, int yy, int mm, int dd) {
                String iso = String.format(java.util.Locale.US, "%04d-%02d-%02d", yy, mm + 1, dd);
                syncForm();
                if (due) form.due = iso; else form.date = iso;
                renderForm();
            }
        }, y, m, d).show();
    }

    private TextView dateField(String val, String empty, final boolean due) {
        TextView t = Ui.t(c, Fmt.validIso(val) ? Fmt.fmtDate(val) : empty, 17, Fmt.validIso(val) ? Ui.TEXT : Ui.MUTED, false);
        t.setBackground(Ui.rr(Ui.FLD, Ui.LINE, 14));
        Ui.pad(t, 14, 13, 14, 13);
        LinearLayout.LayoutParams p = Ui.fillW();
        p.setMargins(0, Ui.dp(6), 0, 0);
        t.setLayoutParams(p);
        Ui.tap(t, new Runnable() { @Override public void run() { pickDate(due); } });
        return t;
    }

    private static final String[][] TYPE_SEG = {{"gave", "ઉધાર વેચાણ"}, {"got", "પૈસા મળ્યા"}, {"took", "ઉધાર ખરીદી"}, {"paid", "પૈસા ચૂકવ્યા"}, {"income", "આવક"}, {"expense", "ખર્ચ"}};
    private static final String[][] BANNER = {{"gave", "ઉધાર વેચાણ"}, {"got", "આવક · ખાતામાં જમા"}, {"took", "ઉધાર ખરીદી"}, {"paid", "ખર્ચ · લેણદારને ચૂકવણી"}, {"income", "આવક"}, {"expense", "ખર્ચ"}};

    private void renderForm() {
        final Form f = form;
        final Model.TypeInfo T = Model.type(f.type);
        boolean showMode = !f.type.equals("gave") && !f.type.equals("took");
        String[] modes = T.party ? new String[]{"cash", "upi", "writeoff"} : new String[]{"cash", "upi"};
        boolean okMode = false;
        for (String m : modes) if (m.equals(f.mode)) okMode = true;
        if (!okMode) f.mode = "cash";
        final Ui.Sheet s = open(f.id != null ? "નોંધમાં ફેરફાર" : "નોંધ ઉમેરો");
        fName = null; fNote = null; fQty = null; suggBox = null;

        if (!f.said.isEmpty()) {
            s.add(said("તમે બોલ્યા: “" + f.said + "”\nનીચે બધું બરાબર છે? જોઈ લો અને સેવ કરો."));
        }
        if (f.lock) {
            String bn = "";
            for (String[] b : BANNER) if (b[0].equals(f.type)) bn = b[1];
            TextView b = Ui.t(c, bn, 15, Ui.ACCENT, true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.rr(Ui.SOFT, 0, 12));
            Ui.pad(b, 10, 8, 10, 8);
            s.add(b);
        } else {
            // two rows of three type buttons
            for (int row = 0; row < 2; row++) {
                LinearLayout l = Ui.h(c);
                for (int i = row * 3; i < row * 3 + 3; i++) {
                    final String key = TYPE_SEG[i][0];
                    boolean on = key.equals(f.type);
                    TextView b = Ui.t(c, TYPE_SEG[i][1], 13, on ? Color.WHITE : Ui.TEXT, true);
                    b.setGravity(Gravity.CENTER);
                    b.setMinHeight(Ui.dp(44));
                    Ui.pad(b, 4, 6, 4, 6);
                    b.setBackground(on ? Ui.rr(Ui.PURPLE, 0, 12) : Ui.rr(Ui.SURFACE2, Ui.LINE, 12));
                    Ui.tap(b, new Runnable() { @Override public void run() { syncForm(); form.type = key; renderForm(); } });
                    LinearLayout.LayoutParams p = Ui.weight(1);
                    p.setMargins(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
                    l.addView(b, p);
                }
                s.add(l);
            }
        }
        if (T.party) {
            if (f.lock && a.db.party(f.partyId) != null) {
                s.add(Ui.label(c, "ખાતું"));
                TextView pc = Ui.t(c, "  " + a.db.party(f.partyId).name, 17, Ui.TEXT, true);
                pc.setBackground(Ui.rr(Ui.SOFT, 0, 14));
                Ui.pad(pc, 14, 12, 14, 12);
                s.add(pc);
            } else {
                s.add(Ui.label(c, "ખાતાનું નામ"));
                fName = Ui.fld(c, "જેમ કે હનીફ ભાઈ", f.name, Ui.IN_TEXT);
                s.add(fName);
                android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
                hs.setHorizontalScrollBarEnabled(false);
                suggBox = Ui.h(c);
                Ui.pad(suggBox, 0, 8, 0, 0);
                hs.addView(suggBox);
                s.add(hs);
                fillSugg();
                Ui.onText(fName, new Runnable() {
                    @Override public void run() {
                        if (form == null || fName == null) return;
                        form.name = fName.getText().toString();
                        if (form.partyId != null) {
                            Model.Party p = a.db.party(form.partyId);
                            if (p == null || !Parser.normText(p.name).equals(Parser.normText(form.name))) form.partyId = null;
                        }
                        fillSugg();
                    }
                });
            }
        }
        if (showMode) {
            s.add(Ui.label(c, "ચૂકવણી મોડ"));
            String[][] ms = new String[modes.length][2];
            for (int i = 0; i < modes.length; i++) ms[i] = new String[]{modes[i], modes[i].equals("cash") ? "રોકડ" : (modes[i].equals("upi") ? "UPI/બેંક" : "માંડવાળ")};
            s.add(seg(ms, f.mode, new java.util.function.Consumer<String>() {
                @Override public void accept(String v) { syncForm(); form.mode = v; renderForm(); }
            }));
        }
        if (f.type.equals("expense")) {
            s.add(Ui.label(c, "ખર્ચનો પ્રકાર"));
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout cl = Ui.h(c);
            Ui.pad(cl, 0, 6, 0, 0);
            for (final String cat : Fmt.CATS) {
                cl.addView(Ui.chip(c, cat, cat.equals(f.cat), new Runnable() { @Override public void run() { syncForm(); form.cat = cat; renderForm(); } }));
            }
            hs.addView(cl);
            s.add(hs);
        }
        boolean prodOn = a.db.settings.productMode && !a.db.products.isEmpty() && (f.type.equals("gave") || f.type.equals("took") || f.type.equals("income") || f.type.equals("expense"));
        if (prodOn) {
            s.add(Ui.label(c, "પ્રોડક્ટ"));
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout pl = Ui.h(c);
            Ui.pad(pl, 0, 6, 0, 0);
            for (final Model.Product p : a.db.products) {
                pl.addView(Ui.chip(c, p.name + " " + Fmt.money(p.rate) + "/" + p.unit, p.id.equals(f.prodId), new Runnable() {
                    @Override public void run() { syncForm(); form.prodId = p.id.equals(form.prodId) ? "" : p.id; applyProduct(); renderForm(); }
                }));
            }
            hs.addView(pl);
            s.add(hs);
            s.add(Ui.label(c, "જથ્થો"));
            fQty = Ui.fld(c, "0", f.qty, Ui.IN_NUM);
            s.add(fQty);
            Ui.onText(fQty, new Runnable() {
                @Override public void run() {
                    if (fQty == null || form == null) return;
                    form.qty = fQty.getText().toString();
                    Model.Product p = a.db.product(form.prodId);
                    double q = parseD(form.qty);
                    if (p != null && q > 0) {
                        form.amount = Fmt.plain(p.rate * q).replace(",", "");
                        form.note = p.name + " × " + form.qty + " " + p.unit;
                        if (fAmt != null) fAmt.setText(form.amount);
                        if (fNote != null) fNote.setText(form.note);
                    }
                }
            });
        }
        s.add(Ui.label(c, "નોંધ (જરૂરી નથી)" + (T.party ? " · માલ/ભાડું શું હતું" : "")));
        fNote = Ui.fld(c, "જેમ કે પેડલ ભાડું", f.note, Ui.IN_PLAIN);
        s.add(fNote);
        s.add(Ui.label(c, "રકમ (₹)"));
        fAmt = Ui.fld(c, "0", f.amount, Ui.IN_NUM);
        fAmt.setTextSize(24);
        s.add(fAmt);
        s.add(Ui.label(c, "તારીખ"));
        dateBtn = dateField(f.date, "તારીખ પસંદ કરો", false);
        s.add(dateBtn);
        if (f.type.equals("gave") || f.type.equals("took")) {
            s.add(Ui.label(c, "ચૂકવણીની મુદત (જરૂરી નથી)"));
            dueBtn = dateField(f.due, "મુદત પસંદ કરો", true);
            s.add(dueBtn);
        }
        s.add(Ui.btn(c, f.said.isEmpty() ? "નોંધ કરો" : "હા, સેવ કરો", "primary", new Runnable() { @Override public void run() { syncForm(); saveEntry(); } }));
        if (!f.said.isEmpty()) {
            s.add(Ui.btn(c, "🎤 બોલીને “હા” કે “ના” કહો", "ghost", new Runnable() { @Override public void run() { yesNoVoice(); } }));
        }
        s.add(Ui.btn(c, "રદ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private void applyProduct() {
        Model.Product p = a.db.product(form.prodId);
        double q = parseD(form.qty);
        if (p != null && q > 0) {
            form.amount = Fmt.plain(p.rate * q).replace(",", "");
            form.note = p.name + " × " + form.qty + " " + p.unit;
        }
    }

    static double parseD(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private void yesNoVoice() {
        a.toast("“હા” કે “ના” બોલો");
        a.listen(new MainActivity.SpeechCb() {
            @Override public void partial(String t) {}
            @Override public void fin(String txt) {
                String r = Parser.parseYesNo(txt);
                if ("yes".equals(r)) { syncForm(); saveEntry(); }
                else if ("no".equals(r)) { close(); a.toast("નોંધ રદ કરી"); }
                else a.toast("સમજાયું નહીં: “" + txt + "”");
            }
            @Override public void end(boolean got) {}
            @Override public void error(String kind) { a.toast(a.speechError(kind)); }
        });
    }

    private void saveEntry() {
        Form f = form;
        if (f == null) return;
        Model.TypeInfo T = Model.type(f.type);
        double amt = parseD(f.amount);
        if (!(amt > 0)) { a.toast("રકમ લખો"); return; }
        if (!Fmt.validIso(f.date)) { a.toast("તારીખ પસંદ કરો"); return; }
        String pid = null;
        if (T.party) {
            pid = f.partyId;
            if (pid == null || a.db.party(pid) == null) {
                String n = f.name.trim();
                if (n.isEmpty()) { a.toast("ખાતાનું નામ લખો"); return; }
                Model.Party ex = a.db.findByName(n);
                if (ex == null) ex = a.newParty(n, "", T.kind);
                pid = ex.id;
            }
        }
        Model.Txn rec = new Model.Txn();
        rec.id = f.id != null ? f.id : a.uid();
        rec.ts = System.currentTimeMillis();
        rec.upd = rec.ts;
        rec.date = f.date;
        rec.type = f.type;
        rec.partyId = pid;
        rec.amount = amt;
        rec.note = f.note.trim();
        rec.mode = (f.type.equals("gave") || f.type.equals("took")) ? "" : f.mode;
        rec.cat = f.type.equals("expense") ? f.cat : "";
        rec.said = f.said;
        rec.due = ((f.type.equals("gave") || f.type.equals("took")) && Fmt.validIso(f.due)) ? f.due : "";
        boolean replaced = false;
        if (f.id != null) {
            for (int i = 0; i < a.db.txns.size(); i++) {
                if (a.db.txns.get(i).id.equals(f.id)) { rec.ts = a.db.txns.get(i).ts; a.db.txns.set(i, rec); replaced = true; break; }
            }
        }
        if (!replaced) a.db.txns.add(rec);
        a.save();
        close();
        a.render();
        a.toast("નોંધ થઈ ગઈ" + (T.party && a.db.party(pid) != null ? " · " + a.db.party(pid).name : ""));
    }

    // ---------- transaction detail ----------
    void txSheet(final String id) {
        Model.Txn t = null;
        for (Model.Txn x : a.db.txns) if (x.id.equals(id)) t = x;
        if (t == null) return;
        final Model.Txn tx = t;
        Model.TypeInfo T = Model.type(t.type);
        Model.Party p = a.db.party(t.partyId);
        Ui.Sheet s = open(T.label);
        LinearLayout card = Ui.card(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView am = Ui.t(c, Fmt.money(t.amount), 28, Ui.tone(Ui.toneOf(t.type)), true);
        am.setGravity(Gravity.CENTER);
        card.addView(am);
        String modeL = "cash".equals(t.mode) ? "રોકડ" : ("upi".equals(t.mode) ? "UPI/બેંક" : ("writeoff".equals(t.mode) ? "માંડવાળ" : ""));
        TextView s1 = Ui.t(c, (p != null ? p.name + " · " : "") + Fmt.fmtDate(t.date) + (modeL.isEmpty() ? "" : " · " + modeL), 13, Ui.MUTED, false);
        s1.setGravity(Gravity.CENTER);
        card.addView(s1);
        if (!t.due.isEmpty()) { TextView d = Ui.t(c, "ચૂકવણીની મુદત: " + Fmt.fmtDate(t.due), 13, Ui.MUTED, false); d.setGravity(Gravity.CENTER); card.addView(d); }
        if (!t.cat.isEmpty() && !t.cat.equals("અન્ય")) { TextView d = Ui.t(c, t.cat, 13, Ui.MUTED, false); d.setGravity(Gravity.CENTER); card.addView(d); }
        if (!t.note.isEmpty()) { TextView d = Ui.t(c, t.note, 15, Ui.TEXT, false); d.setGravity(Gravity.CENTER); Ui.pad(d, 0, 8, 0, 0); card.addView(d); }
        if (!t.said.isEmpty()) { TextView d = Ui.t(c, "બોલ્યા હતા: “" + t.said + "”", 12, Ui.MUTED, false); d.setGravity(Gravity.CENTER); Ui.pad(d, 0, 6, 0, 0); card.addView(d); }
        s.add(card);
        s.add(Ui.btn(c, "✎ ફેરફાર કરો", "primary", new Runnable() {
            @Override public void run() {
                Model.Party pp = a.db.party(tx.partyId);
                Form f = new Form();
                f.id = tx.id; f.type = tx.type; f.partyId = tx.partyId; f.name = pp != null ? pp.name : "";
                f.amount = Fmt.plain(tx.amount).replace(",", ""); f.note = tx.note; f.date = tx.date;
                f.mode = tx.mode.isEmpty() ? "cash" : tx.mode; f.cat = tx.cat.isEmpty() ? "અન્ય" : tx.cat; f.due = tx.due; f.lock = true;
                openEntry(f);
            }
        }));
        s.add(Ui.btn(c, "🗑 કાઢી નાખો", "danger", new Runnable() {
            @Override public void run() {
                confirm("આ નોંધ કાઢી નાખવી છે?", "હા, કાઢી નાખો", new Runnable() {
                    @Override public void run() {
                        for (int i = 0; i < a.db.txns.size(); i++) if (a.db.txns.get(i).id.equals(id)) { a.db.txns.remove(i); break; }
                        a.save(); a.render(); a.toast("નોંધ કાઢી નાખી");
                    }
                });
            }
        }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    // ---------- party add / edit ----------
    private String pKind = "customer";

    void partySheet(final Model.Party p, String kind) {
        pKind = p != null ? a.db.kindOf(p) : (kind != null ? kind : "customer");
        renderPartySheet(p, p != null ? p.name : "", p != null ? p.phone : "");
    }

    private void renderPartySheet(final Model.Party p, String name, String phone) {
        Ui.Sheet s = open(p != null ? "ખાતામાં ફેરફાર" : "નવું ખાતું");
        final String[] kindRef = {pKind};
        final EditText nm = Ui.fld(c, "જેમ કે હનીફ ભાઈ", name, Ui.IN_TEXT);
        final EditText ph = Ui.fld(c, "10 અંકનો નંબર", phone, Ui.IN_PHONE);
        s.add(seg(new String[][]{{"customer", "ગ્રાહક"}, {"creditor", "લેણદાર"}}, pKind, new java.util.function.Consumer<String>() {
            @Override public void accept(String v) { pKind = v; renderPartySheet(p, nm.getText().toString(), ph.getText().toString()); }
        }));
        s.add(Ui.label(c, "નામ"));
        s.add(nm);
        s.add(Ui.label(c, "ફોન નંબર (WhatsApp માટે, જરૂરી નથી)"));
        s.add(ph);
        s.add(Ui.btn(c, "સેવ કરો", "primary", new Runnable() {
            @Override public void run() {
                String n = nm.getText().toString().trim(), pn = ph.getText().toString().trim();
                if (n.isEmpty()) { a.toast("નામ લખો"); return; }
                if (p != null) { p.name = n; p.phone = pn; p.kind = pKind; p.upd = System.currentTimeMillis(); }
                else {
                    if (a.db.findByName(n) != null) { a.toast("આ નામનું ખાતું પહેલેથી છે"); return; }
                    a.newParty(n, pn, pKind);
                }
                a.save(); close(); a.render(); a.toast("ખાતું સેવ થયું");
            }
        }));
        if (p != null) {
            s.add(Ui.btn(c, "🗑 આ ખાતું કાઢી નાખો", "danger", new Runnable() {
                @Override public void run() {
                    int n = 0;
                    for (Model.Txn t : a.db.txns) if (p.id.equals(t.partyId)) n++;
                    confirm(p.name + " નું ખાતું અને તેની " + n + " નોંધ કાઢી નાખવી છે?", "હા, કાઢી નાખો", new Runnable() {
                        @Override public void run() {
                            a.db.parties.remove(p);
                            List<Model.Txn> keep = new ArrayList<>();
                            for (Model.Txn t : a.db.txns) if (!p.id.equals(t.partyId)) keep.add(t);
                            a.db.txns = keep;
                            a.save(); a.partyId = null; a.render(); a.toast("ખાતું કાઢી નાખ્યું");
                        }
                    });
                }
            }));
        }
        s.add(Ui.btn(c, "રદ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    // ---------- filter / sort / menu / reminders ----------
    void filterSheet() {
        Ui.Sheet s = open("ફિલ્ટર");
        s.add(Ui.label(c, "સમય"));
        s.add(seg(new String[][]{{"day", "દિવસ"}, {"month", "મહિનો"}, {"all", "બધા"}}, a.period, new java.util.function.Consumer<String>() {
            @Override public void accept(String v) { a.period = v; filterSheet(); }
        }));
        s.add(Ui.label(c, "પ્રકાર / ચૂકવણી મોડ"));
        LinearLayout wrap = Ui.v(c);
        LinearLayout r = null;
        for (int i = 0; i < Screens.FGROUPS.length; i++) {
            if (i % 3 == 0) { r = Ui.h(c); Ui.pad(r, 0, 3, 0, 3); wrap.addView(r); }
            final String key = Screens.FGROUPS[i][0];
            r.addView(Ui.chip(c, Screens.FGROUPS[i][1], key.equals(a.filter), new Runnable() { @Override public void run() { a.filter = key; filterSheet(); } }));
        }
        s.add(wrap);
        s.add(Ui.label(c, "ખાતું"));
        List<Model.Party> ps = new ArrayList<>(a.db.parties);
        Collections.sort(ps, new Comparator<Model.Party>() { @Override public int compare(Model.Party x, Model.Party y) { return x.name.compareToIgnoreCase(y.name); } });
        LinearLayout pl = Ui.v(c);
        pl.addView(Ui.chip(c, "બધા ખાતા", a.fparty.isEmpty(), new Runnable() { @Override public void run() { a.fparty = ""; filterSheet(); } }));
        for (final Model.Party p : ps) {
            pl.addView(spacedChip(p.name, p.id.equals(a.fparty), new Runnable() { @Override public void run() { a.fparty = p.id; filterSheet(); } }));
        }
        s.add(scrollBox(pl, 130));
        s.add(Ui.btn(c, "લાગુ કરો", "primary", new Runnable() { @Override public void run() { close(); a.render(); } }));
        s.add(Ui.btn(c, "સાફ કરો", "ghost", new Runnable() {
            @Override public void run() { a.filter = "all"; a.fparty = ""; a.period = "day"; a.day = Fmt.today(); close(); a.render(); }
        }));
        s.show();
    }

    private View spacedChip(String text, boolean on, Runnable r) {
        TextView ch = Ui.chip(c, text, on, r);
        LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, Ui.dp(3), 0, Ui.dp(3));
        ch.setLayoutParams(p);
        return ch;
    }

    void sortSheet() {
        Ui.Sheet s = open("ગોઠવણી");
        String[][] opts = {{"bal", "સૌથી વધુ બાકી પહેલા"}, {"name", "નામ પ્રમાણે (અ-બ)"}, {"recent", "તાજેતરની નોંધ પહેલા"}};
        for (final String[] o : opts) {
            s.add(Ui.btn(c, o[1], a.sort.equals(o[0]) ? "primary" : "ghost", new Runnable() { @Override public void run() { a.sort = o[0]; close(); a.render(); } }));
        }
        s.show();
    }

    void homeMenuSheet() {
        Ui.Sheet s = open(null);
        s.add(srow("સેટિંગ્સ", "થીમ, પિન, બેકઅપ", null, new Runnable() { @Override public void run() { close(); a.go("more"); } }));
        s.add(srow("બેકઅપ લો", a.backupTimeText(), null, new Runnable() { @Override public void run() { backupSheet(); } }));
        if (!a.db.settings.pinHash.isEmpty()) s.add(srow("હમણાં લૉક કરો", "એપ ફરી ખોલવા પિન જોઈશે", null, new Runnable() { @Override public void run() { close(); a.showPin("unlock", null); } }));
        s.add(srow("મદદ (FAQs)", "કેવી રીતે વાપરવું", null, new Runnable() { @Override public void run() { faqSheet(); } }));
        s.show();
    }

    void bellSheet() {
        Ui.Sheet s = open("રિમાઇન્ડર");
        final List<Object[]> rows = new ArrayList<>(); // party, kind, amt, sort
        String today = Fmt.today();
        for (Model.Party p : a.db.parties) {
            double b = a.db.partyBal(p.id);
            Model.Due ov = a.db.overdueFor(p.id, today);
            if (ov.recv > 0) rows.add(new Object[]{p, "odr", ov.recv, 0});
            if (ov.pay > 0) rows.add(new Object[]{p, "odp", ov.pay, 1});
            if (b > 0 && ov.recv <= 0) rows.add(new Object[]{p, "recv", b, 2});
        }
        Collections.sort(rows, new Comparator<Object[]>() {
            @Override public int compare(Object[] x, Object[] y) {
                int cmp = (Integer) x[3] - (Integer) y[3];
                return cmp != 0 ? cmp : Double.compare((Double) y[2], (Double) x[2]);
            }
        });
        if (rows.isEmpty()) {
            TextView e = Ui.t(c, "કોઈ રિમાઇન્ડર નથી. બધું સમયસર છે.", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER);
            Ui.pad(e, 10, 18, 10, 18);
            s.add(e);
        } else {
            LinearLayout list = Ui.v(c);
            for (Object[] r : rows) {
                final Model.Party p = (Model.Party) r[0];
                final String kind = (String) r[1];
                double amt = (Double) r[2];
                String lab = kind.equals("odr") ? "મુદતવીતી · લેવાના" : (kind.equals("odp") ? "મુદતવીતી · દેવાના" : "બાકી · લેવાના");
                LinearLayout row = Ui.h(c);
                row.setBackground(Ui.rr(Ui.SURFACE2, 0, 14));
                Ui.pad(row, 12, 10, 12, 10);
                LinearLayout.LayoutParams rp = Ui.fillW();
                rp.setMargins(0, Ui.dp(3), 0, Ui.dp(3));
                row.setLayoutParams(rp);
                LinearLayout mid = Ui.v(c);
                mid.addView(Ui.t(c, p.name, 15, Ui.TEXT, true));
                mid.addView(Ui.t(c, lab, 12, Ui.MUTED, false));
                Ui.tap(mid, new Runnable() { @Override public void run() { close(); a.openParty(p.id); } });
                row.addView(mid, Ui.weight(1));
                row.addView(Ui.t(c, Fmt.money(amt), 15, kind.equals("recv") ? Ui.GREEN : Ui.RED, true));
                if (!kind.equals("odp")) {
                    TextView send = Ui.t(c, "  📤", 20, Ui.ACCENT, true);
                    Ui.tap(send, new Runnable() {
                        @Override public void run() {
                            double b = a.db.partyBal(p.id);
                            String msg = "નમસ્તે " + p.name + ", તમારા " + Fmt.money(b) + " બાકી છે. કૃપા કરીને ચૂકવણી કરશો." + (a.db.settings.shop.isEmpty() ? "" : "\n— " + a.db.settings.shop);
                            a.openUrl(a.waLink(p.phone, msg));
                        }
                    });
                    row.addView(send);
                }
                list.addView(row);
            }
            s.add(scrollBox(list, 360));
        }
        s.show();
    }

    // ---------- quick collection / payment link ----------
    void quickSheet(String q0) {
        Ui.Sheet s = open("ઝડપી ઉઘરાણી");
        s.add(Ui.t(c, "ચૂકવણી Link માટે ગ્રાહક પસંદ કરો", 13, Ui.MUTED, false));
        final EditText q = Ui.fld(c, "🔍 ગ્રાહક શોધો...", q0, Ui.IN_PLAIN);
        s.add(q);
        final LinearLayout list = Ui.v(c);
        s.add(scrollBox(list, 300));
        fillQuick(list, q0);
        Ui.onText(q, new Runnable() { @Override public void run() { fillQuick(list, q.getText().toString()); } });
        s.show();
    }

    private void fillQuick(LinearLayout list, String q0) {
        list.removeAllViews();
        String q = Parser.normText(q0);
        List<Model.Party> ps = new ArrayList<>();
        for (Model.Party p : a.db.parties) {
            if (!a.db.kindOf(p).equals("customer")) continue;
            if (!q.isEmpty() && !Parser.normText(p.name).contains(q)) continue;
            ps.add(p);
        }
        Collections.sort(ps, new Comparator<Model.Party>() {
            @Override public int compare(Model.Party x, Model.Party y) { return Double.compare(a.db.partyBal(y.id), a.db.partyBal(x.id)); }
        });
        if (ps.isEmpty()) {
            TextView e = Ui.t(c, "કોઈ ગ્રાહક મળ્યા નહીં.", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER);
            Ui.pad(e, 10, 18, 10, 18);
            list.addView(e);
            return;
        }
        for (final Model.Party p : ps) {
            double b = a.db.partyBal(p.id);
            list.addView(rowItem(p.name, p.name, p.phone.isEmpty() ? "ફોન નથી" : p.phone, Fmt.money(Math.max(b, 0)), b > 0 ? Ui.GREEN : Ui.MUTED, b > 0 ? "લેવાના" : "",
                    new Runnable() { @Override public void run() { payLinkSheet(p.id); } }));
        }
    }

    void payLinkSheet(final String pid) {
        final Model.Party p = a.db.party(pid);
        if (p == null) return;
        final Model.Settings st = a.db.settings;
        Ui.Sheet s = open("ચૂકવણી Link · " + p.name);
        s.add(Ui.label(c, "રકમ (₹)"));
        double b = Math.max(a.db.partyBal(pid), 0);
        final EditText amt = Ui.fld(c, "0", b > 0 ? Fmt.plain(b).replace(",", "") : "", Ui.IN_NUM);
        amt.setTextSize(24);
        s.add(amt);
        if (!st.upiId.isEmpty()) {
            s.add(said("તમારું UPI ID: " + st.upiId));
            s.add(Ui.btn(c, "📤 WhatsApp પર મોકલો", "green", new Runnable() {
                @Override public void run() {
                    double v = parseD(amt.getText().toString());
                    if (!(v > 0)) { a.toast("રકમ લખો"); return; }
                    a.openUrl(a.waLink(p.phone, a.payMsg(p, v)));
                }
            }));
            s.add(Ui.btn(c, "⧉ Link કોપી / શેર કરો", "ghost", new Runnable() {
                @Override public void run() {
                    double v = parseD(amt.getText().toString());
                    if (!(v > 0)) { a.toast("રકમ લખો"); return; }
                    a.shareText(a.payMsg(p, v));
                }
            }));
        } else {
            s.add(said("ચૂકવણી Link બનાવવા માટે પહેલા તમારું UPI ID સેટ કરો."));
            s.add(Ui.btn(c, "UPI વિગતો ભરો", "primary", new Runnable() { @Override public void run() { bizSetupSheet(); } }));
        }
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    // ---------- settings sheets ----------
    void profileSheet() {
        final Model.Settings st = a.db.settings;
        Ui.Sheet s = open("બિઝનેસ પ્રોફાઇલ");
        s.add(Ui.label(c, "તમારું નામ"));
        final EditText o = Ui.fld(c, "", st.owner, Ui.IN_TEXT); s.add(o);
        s.add(Ui.label(c, "દુકાનનું નામ (WhatsApp સંદેશમાં દેખાશે)"));
        final EditText sh = Ui.fld(c, "", st.shop, Ui.IN_TEXT); s.add(sh);
        s.add(Ui.label(c, "ફોન નંબર"));
        final EditText ph = Ui.fld(c, "", st.phone, Ui.IN_PHONE); s.add(ph);
        s.add(Ui.label(c, "સરનામું"));
        final EditText ad = Ui.fld(c, "", st.addr, Ui.IN_TEXT); s.add(ad);
        s.add(Ui.btn(c, "સેવ કરો", "primary", new Runnable() {
            @Override public void run() {
                st.owner = o.getText().toString().trim(); st.shop = sh.getText().toString().trim();
                st.phone = ph.getText().toString().trim(); st.addr = ad.getText().toString().trim();
                a.save(); close(); a.render(); a.toast("સેવ થયું");
            }
        }));
        s.add(Ui.btn(c, "રદ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void openBalSheet() {
        final Model.Settings st = a.db.settings;
        Ui.Sheet s = open("ઓપનિંગ બેલેન્સ");
        s.add(said("શરૂઆતમાં તમારી પાસે કેટલા પૈસા હતા તે લખો. તે હોમ પર કુલ રકમમાં ઉમેરાશે."));
        s.add(Ui.label(c, "ઓપનિંગ રોકડ (₹)"));
        final EditText cash = Ui.fld(c, "0", st.openCash != 0 ? Fmt.plain(st.openCash).replace(",", "") : "", Ui.IN_NUM); s.add(cash);
        s.add(Ui.label(c, "ઓપનિંગ બેંક બેલેન્સ (₹)"));
        final EditText bank = Ui.fld(c, "0", st.openBank != 0 ? Fmt.plain(st.openBank).replace(",", "") : "", Ui.IN_NUM); s.add(bank);
        s.add(Ui.btn(c, "સેવ કરો", "primary", new Runnable() {
            @Override public void run() {
                st.openCash = parseD(cash.getText().toString()); st.openBank = parseD(bank.getText().toString());
                a.save(); close(); a.render(); a.toast("ઓપનિંગ બેલેન્સ સેવ થયું");
            }
        }));
        s.add(Ui.btn(c, "રદ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void bizSetupSheet() {
        final Model.Settings st = a.db.settings;
        Ui.Sheet s = open("બિઝનેસ સેટઅપ");
        s.add(said("ગ્રાહકને ચૂકવણી Link મોકલવા માટે તમારું UPI ID જોઈએ, જેમ કે name@okaxis"));
        s.add(Ui.label(c, "UPI ID"));
        final EditText id = Ui.fld(c, "name@bank", st.upiId, Ui.IN_PLAIN); s.add(id);
        s.add(Ui.label(c, "UPI પર દેખાતું નામ"));
        final EditText nm = Ui.fld(c, "", st.upiName, Ui.IN_TEXT); s.add(nm);
        s.add(Ui.btn(c, "સેવ કરો", "primary", new Runnable() {
            @Override public void run() {
                String v = id.getText().toString().trim();
                if (!v.isEmpty() && !v.matches("[\\w.\\-]{2,}@[\\w.\\-]{2,}")) { a.toast("UPI ID સાચું લખો, જેમ કે name@bank"); return; }
                st.upiId = v; st.upiName = nm.getText().toString().trim();
                a.save(); close(); a.render(); a.toast("UPI વિગતો સેવ થઈ");
            }
        }));
        s.add(Ui.btn(c, "રદ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void voiceSetSheet() {
        final Model.Settings st = a.db.settings;
        Ui.Sheet s = open("ભાષા અને વૉઇસ");
        s.add(Ui.label(c, "બોલવાની ભાષા (માઇક કઈ ભાષા સમજે)"));
        s.add(seg(new String[][]{{"gu-IN", "ગુજરાતી"}, {"hi-IN", "हिन्दी"}, {"en-IN", "English"}}, st.lang, new java.util.function.Consumer<String>() {
            @Override public void accept(String v) { st.lang = v; a.save(); voiceSetSheet(); a.render(); }
        }));
        s.add(srow("બોલીને જવાબ આપો", "હિસાબ પૂછો ત્યારે એપ બોલીને સંભળાવે", st.speak ? "● ચાલુ" : "○ બંધ", new Runnable() {
            @Override public void run() { st.speak = !st.speak; a.save(); voiceSetSheet(); a.render(); }
        }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void themeSheet() {
        final Model.Settings st = a.db.settings;
        Ui.Sheet s = open("એપ થીમ");
        s.add(seg(new String[][]{{"system", "સિસ્ટમ"}, {"light", "લાઇટ"}, {"dark", "ડાર્ક"}}, st.theme, new java.util.function.Consumer<String>() {
            @Override public void accept(String v) { st.theme = v; a.save(); a.applyTheme(); close(); a.rebuild(); }
        }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private static final String[] UNITS = {"નંગ", "કિલો", "દિવસ", "ટન", "ક્વિન્ટલ"};
    private String prodUnit = UNITS[0];

    void prodSheet() {
        Ui.Sheet s = open("પ્રોડક્ટ યાદી");
        if (a.db.products.isEmpty()) {
            TextView e = Ui.t(c, "હજી કોઈ પ્રોડક્ટ નથી.", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER);
            Ui.pad(e, 10, 12, 10, 12);
            s.add(e);
        } else {
            LinearLayout list = Ui.v(c);
            for (final Model.Product p : a.db.products) {
                LinearLayout row = Ui.h(c);
                row.setBackground(Ui.rr(Ui.SURFACE2, 0, 14));
                Ui.pad(row, 12, 10, 12, 10);
                LinearLayout.LayoutParams rp = Ui.fillW();
                rp.setMargins(0, Ui.dp(3), 0, Ui.dp(3));
                row.setLayoutParams(rp);
                LinearLayout mid = Ui.v(c);
                mid.addView(Ui.t(c, p.name, 15, Ui.TEXT, true));
                mid.addView(Ui.t(c, Fmt.money(p.rate) + " / " + p.unit, 12, Ui.MUTED, false));
                row.addView(mid, Ui.weight(1));
                TextView del = Ui.t(c, "🗑", 20, Ui.RED, true);
                Ui.tap(del, new Runnable() { @Override public void run() { a.db.products.remove(p); a.save(); prodSheet(); a.render(); } });
                row.addView(del);
                list.addView(row);
            }
            s.add(scrollBox(list, 200));
        }
        s.add(Ui.label(c, "નવી પ્રોડક્ટ"));
        final EditText nm = Ui.fld(c, "નામ, જેમ કે પેડલ", "", Ui.IN_TEXT); s.add(nm);
        final EditText rate = Ui.fld(c, "રેટ ₹", "", Ui.IN_NUM); s.add(rate);
        LinearLayout ur = Ui.h(c);
        Ui.pad(ur, 0, 8, 0, 0);
        for (final String u : UNITS) ur.addView(Ui.chip(c, u, u.equals(prodUnit), new Runnable() { @Override public void run() { prodUnit = u; prodSheetKeep(nm.getText().toString(), rate.getText().toString()); } }));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.addView(ur);
        s.add(hs);
        s.add(Ui.btn(c, "ઉમેરો", "primary", new Runnable() {
            @Override public void run() {
                String n = nm.getText().toString().trim();
                String rv = rate.getText().toString().trim();
                if (n.isEmpty() || rv.isEmpty()) { a.toast("નામ અને રેટ લખો"); return; }
                Model.Product p = new Model.Product();
                p.id = a.uid(); p.name = n; p.rate = parseD(rv); p.unit = prodUnit;
                a.db.products.add(p);
                a.save(); prodSheet(); a.render();
            }
        }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private void prodSheetKeep(String name, String rate) {
        prodSheet();
        // refill typed values into the freshly built sheet
        if (cur == null) return;
        List<EditText> edits = new ArrayList<>();
        collectEdits(cur.body, edits);
        if (edits.size() >= 2) { edits.get(edits.size() - 2).setText(name); edits.get(edits.size() - 1).setText(rate); }
    }

    private void collectEdits(View v, List<EditText> out) {
        if (v instanceof EditText) { out.add((EditText) v); return; }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectEdits(g.getChildAt(i), out);
        }
    }

    void faqSheet() {
        String[][] q = {
                {"બોલીને નોંધ કેવી રીતે કરવી?", "હોમ પર “અવાજ” દબાવો, માઇક દબાવીને બોલો, જેમ કે “હનીફ ભાઈને 5000 નો માલ ઉધાર આપ્યો”. સ્ક્રીન પર નોંધ ભરાયેલી દેખાશે. સાચી હોય તો “હા, સેવ કરો” દબાવો અથવા બોલો “હા”."},
                {"હિસાબ કેવી રીતે પૂછવો?", "માઇક દબાવીને બોલો “હનીફ ભાઈ નો હિસાબ બતાવો” અથવા “આ મહિનાનો ખર્ચ બતાવો”. ખાતું ખુલશે અને એપ બોલીને પણ સંભળાવશે."},
                {"ગ્રાહક અને લેણદારમાં શું ફરક?", "ગ્રાહક પાસેથી તમારે પૈસા લેવાના હોય છે. લેણદારને તમારે પૈસા આપવાના હોય છે."},
                {"ઉધાર વેચાણ અને પૈસા મળ્યા શું છે?", "માલ કે ભાડું ઉધાર આપો ત્યારે “ઉધાર વેચાણ” થાય છે અને ગ્રાહકનું બાકી વધે છે. પછી પૈસા આવે ત્યારે “પૈસા મળ્યા” નોંધો, બાકી ઘટશે."},
                {"માંડવાળ એટલે શું?", "જે રકમ ગ્રાહક પાસેથી મળવાની નથી, તે માફ કરવી. તેમાં રોકડ કે બેંકમાં કોઈ ફેરફાર થતો નથી, ફક્ત બાકી ઘટે છે."},
                {"મુદતવીતી એટલે શું?", "ઉધાર નોંધતી વખતે “ચૂકવણીની મુદત” લખી હોય અને તે તારીખ નીકળી જાય છતાં પૈસા ન મળ્યા હોય, તો તે મુદતવીતી ગણાય."},
                {"ડેટા ક્યાં સેવ થાય છે?", "આ ફોનમાં. નિયમિત “ડેટા બેકઅપ” લો અને ફાઇલ WhatsApp કે Drive માં સાચવો."},
                {"પિન ભૂલી જાઉં તો?", "લૉક સ્ક્રીન પર “PIN ભૂલ્યા?” દબાવો. બધો ડેટા કાઢીને નવી શરૂઆત થશે, પછી બેકઅપ ફાઇલ પાછી લાવી શકાય. એટલે બેકઅપ લઈ રાખો."}
        };
        Ui.Sheet s = open("FAQs");
        LinearLayout list = Ui.v(c);
        for (String[] x : q) {
            list.addView(Ui.t(c, x[0], 15, Ui.TEXT, true));
            TextView ans = Ui.t(c, x[1], 13, Ui.MUTED, false);
            Ui.pad(ans, 0, 2, 0, 12);
            list.addView(ans);
        }
        s.add(scrollBox(list, 400));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void privacySheet() {
        Ui.Sheet s = open("પ્રાઇવસી");
        TextView t = Ui.t(c, "તમારો બધો હિસાબ ફક્ત આ ફોનમાં સેવ થાય છે. અમે કોઈ સર્વર પર ડેટા મોકલતા નથી.\n\nબોલવાની સુવિધા ફોનની Google સ્પીચ સર્વિસ વાપરે છે, એટલે બોલેલો અવાજ પ્રોસેસ કરવા માટે ઇન્ટરનેટ જોઈએ અને Google પાસે જાય છે.\n\nપિન અને ફિંગરપ્રિન્ટ લૉક ફક્ત બીજા કોઈને એપ ખોલતા રોકવા માટે છે.", 14, Ui.TEXT, false);
        s.add(t);
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    // ---------- security / backup ----------
    void pinToggle() {
        if (a.db.settings.pinHash.isEmpty()) { a.showPin("set1", null); return; }
        Ui.Sheet s = open("સિક્યુરિટી પિન");
        s.add(Ui.btn(c, "પિન બદલો", "primary", new Runnable() {
            @Override public void run() { close(); a.showPin("verify", new Runnable() { @Override public void run() { a.showPin("set1", null); } }); }
        }));
        s.add(Ui.btn(c, "પિન બંધ કરો", "danger", new Runnable() {
            @Override public void run() {
                close();
                a.showPin("verify", new Runnable() {
                    @Override public void run() { a.db.settings.pinHash = ""; a.db.settings.bio = ""; a.save(); a.render(); a.toast("પિન બંધ કર્યો"); }
                });
            }
        }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void bioToggle() {
        Model.Settings st = a.db.settings;
        if (!st.bio.isEmpty()) { st.bio = ""; a.save(); a.render(); a.toast("ફિંગરપ્રિન્ટ લૉક બંધ"); return; }
        if (st.pinHash.isEmpty()) { a.toast("પહેલા સિક્યુરિટી પિન સેટ કરો"); return; }
        if (!a.bioAvailable()) { a.toast("આ ફોનમાં ફિંગરપ્રિન્ટ ઉપલબ્ધ નથી"); return; }
        a.bioAuth("ફિંગરપ્રિન્ટ લૉક ચાલુ કરો", new MainActivity.BoolCb() {
            @Override public void done(boolean ok) {
                if (ok) { a.db.settings.bio = "native"; a.save(); a.render(); a.toast("ફિંગરપ્રિન્ટ લૉક ચાલુ"); }
                else a.toast("ફિંગરપ્રિન્ટ ચકાસણી ન થઈ");
            }
        });
    }

    void backupSheet() {
        Ui.Sheet s = open("ડેટા બેકઅપ");
        s.add(Ui.btn(c, "⬇ Downloads માં સેવ કરો", "primary", new Runnable() { @Override public void run() { close(); a.doBackup(false); } }));
        s.add(Ui.btn(c, "📤 WhatsApp / Drive પર મોકલો", "primary", new Runnable() { @Override public void run() { close(); a.doBackup(true); } }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    // ---------- voice ----------
    private TextView vStat, vText;
    private TextView micBtn;
    private EditText vType;
    private boolean voiceOpen = false;

    void voiceSheet(String msg) {
        final Model.Settings st = a.db.settings;
        Ui.Sheet s = open("બોલીને નોંધ કરો");
        voiceOpen = true;
        s.add(seg(new String[][]{{"gu-IN", "ગુજરાતી"}, {"hi-IN", "हिन्दी"}, {"en-IN", "English"}}, st.lang, new java.util.function.Consumer<String>() {
            @Override public void accept(String v) { st.lang = v; a.save(); a.stopListen(); voiceSheet(null); }
        }));
        micBtn = Ui.t(c, "🎤", 34, Color.WHITE, true);
        micBtn.setGravity(Gravity.CENTER);
        micBtn.setBackground(Ui.grad(Color.parseColor("#8B3FE0"), Ui.PURPLE, 44));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.dp(88), Ui.dp(88));
        mp.gravity = Gravity.CENTER_HORIZONTAL;
        mp.setMargins(0, Ui.dp(16), 0, Ui.dp(8));
        micBtn.setLayoutParams(mp);
        Ui.tap(micBtn, new Runnable() { @Override public void run() { if (a.isListening()) a.stopListen(); else startVoice(); } });
        s.add(micBtn);
        vStat = Ui.t(c, msg != null ? msg : "માઇક દબાવો અને બોલો", 14, Ui.MUTED, false);
        vStat.setGravity(Gravity.CENTER);
        s.add(vStat);
        vText = Ui.t(c, "", 17, Ui.TEXT, true);
        vText.setGravity(Gravity.CENTER);
        Ui.pad(vText, 0, 8, 0, 8);
        s.add(vText);
        s.add(Ui.label(c, "અથવા અહીં લખો"));
        vType = Ui.fld(c, "હનીફ ભાઈને 5000 નો માલ ઉધાર આપ્યો", "", Ui.IN_PLAIN);
        s.add(vType);
        s.add(Ui.btn(c, "મોકલો ➤", "primary", new Runnable() {
            @Override public void run() {
                String t = vType.getText().toString().trim();
                if (t.isEmpty()) { a.toast("પહેલાં લખો"); return; }
                vText.setText(t);
                handleCommand(t);
            }
        }));
        s.dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) { a.stopListen(); voiceOpen = false; }
        });
        s.show();
    }

    private void startVoice() {
        boolean ok = a.listen(new MainActivity.SpeechCb() {
            @Override public void partial(String t) { if (vText != null) vText.setText(t + " …"); }
            @Override public void fin(String t) { if (vText != null) vText.setText(t); handleCommand(t); }
            @Override public void end(boolean got) {
                if (micBtn != null) micBtn.setAlpha(1f);
                if (vStat != null && !got) vStat.setText("ફરી માઇક દબાવીને બોલો");
            }
            @Override public void error(String kind) { if (vStat != null) vStat.setText(a.speechError(kind)); a.toast(a.speechError(kind)); }
        });
        if (ok) {
            if (vStat != null) vStat.setText("સાંભળી રહ્યો છું… બોલો");
            if (micBtn != null) micBtn.setAlpha(0.6f);
        }
    }

    void handleCommand(String text) {
        Parser.Result r = Parser.parseCommand(text, a.db.parties);
        if ("entry".equals(r.kind)) {
            a.stopListen();
            Form f = new Form();
            f.type = r.type;
            f.amount = r.amount == null ? "" : Fmt.plain(r.amount).replace(",", "");
            f.name = r.name == null ? "" : r.name;
            f.partyId = r.partyId;
            f.date = Fmt.addDays(Fmt.today(), r.dateOffset);
            f.said = text;
            f.note = Parser.leftover(text, r.name);
            if ("expense".equals(r.type)) {
                f.cat = "અન્ય";
                String first = f.note.isEmpty() ? "" : f.note.split(" ")[0];
                for (String cat : Fmt.CATS) {
                    for (String w : cat.split("/")) if (!first.isEmpty() && Parser.normText(w).equals(Parser.normText(first))) f.cat = cat;
                }
            }
            openEntry(f);
            if (r.amount == null) a.toast("રકમ સંભળાઈ નહીં, લખો");
            return;
        }
        if ("query".equals(r.kind)) {
            a.stopListen();
            if ("party".equals(r.what)) {
                Model.Party p = r.partyId != null ? a.db.party(r.partyId) : null;
                if (p == null) {
                    List<Model.Party> sm = similar(r.name == null ? "" : r.name);
                    if (sm.size() == 1) p = sm.get(0);
                }
                if (p == null) { partyPicker(text, r.name); return; }
                close();
                a.openParty(p.id);
                a.speak(a.partySummary(p));
                return;
            }
            if ("expense".equals(r.what)) { close(); expenseSheet(r.scope); return; }
            close();
            a.partyId = null; a.kf = "all"; a.kq = ""; a.go("khata");
            return;
        }
        voiceSheet("સમજાયું નહીં: “" + text + "”. ફરી બોલો, જેમ કે “હનીફ ભાઈને 5000 નો માલ ઉધાર આપ્યો”.");
        if (vText != null) vText.setText(text);
    }

    /** No single khata matched the spoken name: show what was heard and let the user tap the right khata. */
    void partyPicker(String heard, String name) {
        List<Model.Party> list = similar(name == null ? "" : name);
        boolean none = list.isEmpty();
        if (none) for (int i = 0; i < Math.min(8, a.db.parties.size()); i++) list.add(a.db.parties.get(i));
        Ui.Sheet s = open("કયું ખાતું ખોલવું?");
        TextView h = Ui.t(c, "સંભળાયું: “" + heard + "”", 14, Ui.MUTED, false);
        h.setGravity(Gravity.CENTER);
        Ui.pad(h, 8, 6, 8, 6);
        s.add(h);
        if (a.db.parties.isEmpty()) {
            TextView e = Ui.t(c, "હજી કોઈ ખાતું નથી. પહેલાં નોંધ કરો, જેમ કે “કાસમ ભાઈને 5000 આપ્યા”.", 14, Ui.TEXT, false);
            e.setGravity(Gravity.CENTER); Ui.pad(e, 10, 10, 10, 10); s.add(e);
        } else {
            TextView q = Ui.t(c, none ? "આ નામનું ખાતું મળ્યું નહીં. તમારા ખાતા:" : "આમાંથી કયું? ટૅપ કરો:", 15, Ui.TEXT, true);
            q.setGravity(Gravity.CENTER); Ui.pad(q, 8, 6, 8, 8); s.add(q);
            LinearLayout box = Ui.v(c);
            for (final Model.Party p : list) {
                box.addView(rowItem(p.name, p.name, "", "", Ui.TEXT, null, new Runnable() {
                    @Override public void run() { close(); a.openParty(p.id); a.speak(a.partySummary(p)); }
                }));
            }
            s.add(scrollBox(box, 260));
        }
        s.add(Ui.btn(c, "🎤 ફરી બોલો", "primary", new Runnable() { @Override public void run() { voiceSheet(null); startVoice(); } }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void expenseSheet(String scope) {
        String t0 = Fmt.today();
        String from, to, title;
        if ("day".equals(scope)) { from = to = t0; title = "આજનો ખર્ચ"; }
        else if ("yesterday".equals(scope)) { from = to = Fmt.addDays(t0, -1); title = "ગઈકાલનો ખર્ચ"; }
        else { from = t0.substring(0, 7) + "-01"; to = t0.substring(0, 7) + "-31"; title = "આ મહિનાનો ખર્ચ"; }
        List<Model.Txn> items = new ArrayList<>();
        double total = 0;
        for (Model.Txn t : a.db.txns) {
            if (t.type.equals("expense") && t.date.compareTo(from) >= 0 && t.date.compareTo(to) <= 0) { items.add(t); total += t.amount; }
        }
        Collections.sort(items, Model.SORT_NEWEST);
        Ui.Sheet s = open(title);
        LinearLayout card = Ui.card(c);
        TextView l = Ui.t(c, "કુલ ખર્ચ", 13, Ui.MUTED, false); l.setGravity(Gravity.CENTER); card.addView(l);
        TextView n = Ui.t(c, Fmt.money(total), 28, Ui.RED, true); n.setGravity(Gravity.CENTER); card.addView(n);
        TextView k = Ui.t(c, items.size() + " નોંધ", 13, Ui.MUTED, false); k.setGravity(Gravity.CENTER); card.addView(k);
        s.add(card);
        if (items.isEmpty()) {
            TextView e = Ui.t(c, "કોઈ ખર્ચ નોંધાયો નથી.", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER);
            Ui.pad(e, 10, 14, 10, 14);
            s.add(e);
        } else {
            LinearLayout list = Ui.v(c);
            for (Model.Txn t : items) {
                list.addView(rowItem(t.cat.equals("અન્ય") || t.cat.isEmpty() ? (t.note.isEmpty() ? "ખર્ચ" : t.note) : t.cat,
                        t.cat.equals("અન્ય") || t.cat.isEmpty() ? (t.note.isEmpty() ? "ખર્ચ" : t.note) : t.cat,
                        Fmt.fmtDate(t.date) + (t.note.isEmpty() ? "" : " · " + t.note), Fmt.money(t.amount), Ui.RED, null, null));
            }
            s.add(scrollBox(list, 220));
        }
        final String say = a.isEnglish() ? ("Total expense " + Fmt.plain(total) + " rupees") : (title + " " + Fmt.plain(total) + " રૂપિયા");
        s.add(Ui.btn(c, "🔊 બોલીને સંભળાવો", "primary", new Runnable() { @Override public void run() { a.speak(say); } }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
        a.speak(say);
    }
}
