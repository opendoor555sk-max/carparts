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
import java.util.Map;

/** Every bottom-sheet / dialog in the app. */
final class Sheets {
    private final MainActivity a;
    private final Context c;
    private Ui.Sheet cur;

    Sheets(MainActivity a) { this.a = a; this.c = a; }

    /** Entry form state. */
    static final class Form {
        String id = null, type = "income", partyId = null, name = "", amount = "", note = "", date = Fmt.today(), mode = "cash",
                cat = "અન્ય", said = "", due = "", prodId = "", qty = "", pendingId = null, svc = "", paid = "";
        List<String[]> parts = new ArrayList<>();
        boolean lock = false;
    }

    private Form form;
    private EditText fName, fAmt, fNote, fQty, fSvc, fPaid;
    private final List<EditText> partNames = new ArrayList<>(), partAmts = new ArrayList<>();
    private TextView tvTot;
    private boolean svcOn, paidOn;
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
        if (fSvc != null) form.svc = fSvc.getText().toString();
        if (fPaid != null) form.paid = fPaid.getText().toString();
        if (!partNames.isEmpty() && partNames.size() == form.parts.size()) {
            for (int i = 0; i < partNames.size(); i++) form.parts.set(i, new String[]{partNames.get(i).getText().toString(), partAmts.get(i).getText().toString()});
        }
    }

    private double partsSum(Form f) {
        double t = 0;
        for (String[] p : f.parts) t += parseD(p[1]);
        return t;
    }

    private void refreshTotals() {
        if (tvTot == null || form == null) return;
        double lab = fAmt == null ? 0 : parseD(fAmt.getText().toString());
        double ps = 0;
        for (EditText e : partAmts) ps += parseD(e.getText().toString());
        double total = lab + (svcOn ? ps : 0);
        double paid = fPaid == null ? 0 : parseD(fPaid.getText().toString());
        StringBuilder sb = new StringBuilder();
        if (svcOn) sb.append("કુલ: ").append(Fmt.money(total));
        if (paidOn) {
            if (sb.length() > 0) sb.append("   ·   ");
            double rest = Math.max(0, total - paid);
            sb.append(form.type.equals("took") ? "ચૂકવવાના બાકી: " : "બાકી (ઉધાર): ").append(Fmt.money(rest));
        }
        tvTot.setText(I18n.tr(sb.toString()));
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
        final Ui.Sheet s = open(f.pendingId != null ? "રિવ્યુ: અવાજની નોંધ" : (f.id != null ? "નોંધમાં ફેરફાર" : "નોંધ ઉમેરો"));
        fName = null; fNote = null; fQty = null; suggBox = null; fSvc = null; fPaid = null; tvTot = null;
        partNames.clear(); partAmts.clear();
        svcOn = a.db.settings.biz.equals("service") && f.type.equals("gave") && f.id == null;
        paidOn = (f.type.equals("gave") || f.type.equals("took")) && f.id == null;

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
        if (svcOn) {
            s.add(Ui.label(c, "સેવા / કામનું નામ"));
            fSvc = Ui.fld(c, "જેમ કે મશીન રિપેર", f.svc, Ui.IN_PLAIN);
            s.add(fSvc);
        }
        s.add(Ui.label(c, svcOn ? "વધારાની નોંધ (જરૂરી નથી)" : "નોંધ (જરૂરી નથી)" + (T.party ? " · માલ/ભાડું શું હતું" : "")));
        fNote = Ui.fld(c, "જેમ કે પેડલ ભાડું", f.note, Ui.IN_PLAIN);
        s.add(fNote);
        s.add(Ui.label(c, svcOn ? "સેવાની રકમ / લેબર (₹)" : "રકમ (₹)"));
        fAmt = Ui.fld(c, "0", f.amount, Ui.IN_NUM);
        fAmt.setTextSize(24);
        s.add(fAmt);
        final Runnable refresh = new Runnable() { @Override public void run() { refreshTotals(); } };
        Ui.onText(fAmt, refresh);
        if (svcOn) {
            s.add(Ui.label(c, "પાર્ટ / માલ (જરૂરી નથી)"));
            for (int i = 0; i < f.parts.size(); i++) {
                final int idx = i;
                LinearLayout row = Ui.h(c);
                EditText pn = Ui.fld(c, "પાર્ટનું નામ", f.parts.get(i)[0], Ui.IN_PLAIN);
                EditText pa = Ui.fld(c, "₹", f.parts.get(i)[1], Ui.IN_NUM);
                row.addView(pn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
                row.addView(pa, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView x = Ui.t(c, "✕", 18, Ui.RED, true);
                Ui.pad(x, 12, 8, 6, 8);
                Ui.tap(x, new Runnable() { @Override public void run() { syncForm(); form.parts.remove(idx); renderForm(); } });
                row.addView(x);
                partNames.add(pn); partAmts.add(pa);
                Ui.onText(pa, refresh);
                s.add(row);
            }
            s.add(Ui.btn(c, "＋ પાર્ટ ઉમેરો", "ghost", new Runnable() { @Override public void run() { syncForm(); form.parts.add(new String[]{"", ""}); renderForm(); } }));
        }
        if (paidOn) {
            s.add(Ui.label(c, f.type.equals("took") ? "અત્યારે કેટલા ચૂકવ્યા (₹)" : "અત્યારે કેટલા મળ્યા (₹)"));
            fPaid = Ui.fld(c, "0 = બધું ઉધાર", f.paid, Ui.IN_NUM);
            s.add(fPaid);
            Ui.onText(fPaid, refresh);
            LinearLayout pc = Ui.h(c);
            pc.addView(Ui.chip(c, "બધું ઉધાર", false, new Runnable() { @Override public void run() { syncForm(); form.paid = ""; renderForm(); } }));
            pc.addView(Ui.chip(c, "બધા રોકડ", false, new Runnable() {
                @Override public void run() {
                    syncForm();
                    double tot = parseD(form.amount) + (svcOn ? partsSum(form) : 0);
                    form.paid = tot > 0 ? Fmt.plain(tot).replace(",", "") : "";
                    renderForm();
                }
            }));
            s.add(pc);
        }
        if (svcOn || paidOn) {
            tvTot = Ui.t(c, "", 15, Ui.TEXT, true);
            Ui.pad(tvTot, 4, 10, 4, 4);
            s.add(tvTot);
            refreshTotals();
        }
        s.add(Ui.label(c, "તારીખ"));
        dateBtn = dateField(f.date, "તારીખ પસંદ કરો", false);
        s.add(dateBtn);
        if (f.type.equals("gave") || f.type.equals("took")) {
            s.add(Ui.label(c, "ચૂકવણીની મુદત (જરૂરી નથી)"));
            dueBtn = dateField(f.due, "મુદત પસંદ કરો", true);
            s.add(dueBtn);
        }
        if (f.pendingId != null) {
            s.add(Ui.btn(c, "સેવ અને આગળ", "primary", new Runnable() { @Override public void run() { syncForm(); saveEntry(); } }));
            s.add(Ui.btn(c, "છોડી દો", "ghost", new Runnable() { @Override public void run() { discardPending(form.pendingId); } }));
            s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        } else {
            s.add(Ui.btn(c, f.said.isEmpty() ? "નોંધ કરો" : "હા, સેવ કરો", "primary", new Runnable() { @Override public void run() { syncForm(); saveEntry(); } }));
            if (!f.said.isEmpty()) {
                s.add(Ui.btn(c, "🎤 બોલીને “હા” કે “ના” કહો", "ghost", new Runnable() { @Override public void run() { yesNoVoice(); } }));
            }
            s.add(Ui.btn(c, "રદ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        }
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
        double total = amt;
        String noteTxt = f.note.trim();
        if (svcOn && f.id == null) {
            StringBuilder sv = new StringBuilder(f.svc.trim().isEmpty() ? "સેવા" : f.svc.trim());
            if (amt > 0) sv.append(" ").append(Fmt.money(amt));
            for (String[] p : f.parts) {
                double pv = parseD(p[1]);
                if (pv <= 0 && p[0].trim().isEmpty()) continue;
                total += pv;
                sv.append(" · પાર્ટ: ").append(p[0].trim().isEmpty() ? "પાર્ટ" : p[0].trim()).append(" ").append(Fmt.money(pv));
            }
            if (!noteTxt.isEmpty()) sv.append(" · ").append(noteTxt);
            noteTxt = sv.toString();
        }
        if (!(total > 0)) { a.toast("રકમ લખો"); return; }
        amt = total;
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
        rec.by = a.who();
        rec.ts = System.currentTimeMillis();
        rec.upd = rec.ts;
        rec.date = f.date;
        rec.type = f.type;
        rec.partyId = pid;
        rec.amount = amt;
        rec.note = noteTxt;
        rec.mode = (f.type.equals("gave") || f.type.equals("took")) ? "" : f.mode;
        rec.cat = f.type.equals("expense") ? f.cat : "";
        rec.said = f.said;
        rec.due = ((f.type.equals("gave") || f.type.equals("took")) && Fmt.validIso(f.due)) ? f.due : "";
        boolean replaced = false;
        if (f.id != null) {
            for (int i = 0; i < a.db.txns.size(); i++) {
                if (a.db.txns.get(i).id.equals(f.id)) { rec.ts = a.db.txns.get(i).ts; if (!a.db.txns.get(i).by.isEmpty()) rec.by = a.db.txns.get(i).by; a.db.txns.set(i, rec); replaced = true; break; }
            }
        }
        if (!replaced) a.db.txns.add(rec);
        double paidNow = (paidOn && f.id == null) ? Math.min(parseD(f.paid), amt) : 0;
        if (paidNow > 0 && pid != null) {
            Model.Txn pr = new Model.Txn();
            pr.id = a.uid();
            pr.ts = rec.ts + 1; pr.upd = pr.ts; pr.by = rec.by;
            pr.date = f.date;
            pr.type = f.type.equals("took") ? "paid" : "got";
            pr.partyId = pid;
            pr.amount = paidNow;
            pr.mode = "cash";
            pr.note = f.type.equals("took") ? "સાથે ચૂકવ્યા" : "સાથે મળ્યા";
            a.db.txns.add(pr);
        }
        if (f.pendingId != null) removePending(f.pendingId);
        a.save();
        close();
        a.render();
        a.toast("નોંધ થઈ ગઈ" + (T.party && a.db.party(pid) != null ? " · " + a.db.party(pid).name : "") + (paidNow > 0 && paidNow < amt ? " · બાકી " + Fmt.money(amt - paidNow) : ""));
        if (f.pendingId != null && !a.db.review.isEmpty()) openReview(a.db.review.get(0));
    }

    // ---------- In-Review (spoken entries waiting for confirmation) ----------
    private void removePending(String id) {
        for (int i = 0; i < a.db.review.size(); i++) if (a.db.review.get(i).id.equals(id)) { a.db.review.remove(i); break; }
    }

    void openReview(Model.Pending q) {
        Form f = new Form();
        f.type = q.type;
        f.amount = q.amount > 0 ? Fmt.plain(q.amount).replace(",", "") : "";
        f.name = q.name;
        f.partyId = (q.partyId != null && a.db.party(q.partyId) != null) ? q.partyId : null;
        f.date = Fmt.validIso(q.date) ? q.date : Fmt.today();
        f.said = q.said;
        f.note = q.note;
        f.cat = q.cat.isEmpty() ? "અન્ય" : q.cat;
        f.pendingId = q.id;
        f.svc = q.svc;
        if (q.paid > 0) f.paid = Fmt.plain(q.paid).replace(",", "");
        if (!q.parts.isEmpty()) for (String pp : q.parts.split(";")) {
            int k = pp.lastIndexOf(':');
            if (k > 0) f.parts.add(new String[]{pp.substring(0, k), pp.substring(k + 1)});
        }
        openEntry(f);
    }

    void openReviewById(String id) {
        for (Model.Pending q : a.db.review) if (q.id.equals(id)) { openReview(q); return; }
    }

    private void discardPending(String id) {
        removePending(id);
        a.save();
        close();
        a.render();
        a.toast("ચકાસવાની યાદી રદ કર્યું");
        if (!a.db.review.isEmpty()) openReview(a.db.review.get(0));
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
        if (!t.by.isEmpty()) { TextView d = Ui.t(c, "નોંધ કરનાર: " + t.by, 12, Ui.MUTED, false); d.setGravity(Gravity.CENTER); Ui.pad(d, 0, 6, 0, 0); card.addView(d); }
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
        if (!a.isStaff()) s.add(Ui.btn(c, "🗑 કાઢી નાખો", "danger", new Runnable() {
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
        if (p != null && !a.isStaff()) {
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
        s.add(srow("ઝડપી ઉઘરાણી", "ગ્રાહકને ચૂકવણી Link મોકલો", null, new Runnable() { @Override public void run() { quickSheet(""); } }));
        s.add(srow("રિમાઇન્ડર મોકલો", "ગ્રાહકને બાકી રકમની યાદ અપાવો", null, new Runnable() { @Override public void run() { remSel.clear(); reminderSheet(); } }));
        s.add(srow("રિમાઇન્ડર ઇતિહાસ", "કોને કોને મોકલ્યું", null, new Runnable() { @Override public void run() { reminderHistorySheet(); } }));
        s.add(srow("અવાજ ટેસ્ટ લૉગ", "શું બોલ્યા, એપ શું સમજ્યું", null, new Runnable() { @Override public void run() { voiceLogSheet(); } }));
        s.add(srow("એપે શીખેલું", a.db.learn.size() + " નામ શીખ્યા", null, new Runnable() { @Override public void run() { learnedSheet(); } }));
        s.add(srow("વ્યવસાયનો પ્રકાર", a.db.settings.biz.equals("service") ? "સર્વિસ પ્રોવાઈડર" : "દુકાન / સ્ટોર", null, new Runnable() { @Override public void run() { bizSheet(); } }));
        s.add(srow("સેટિંગ્સ", "થીમ, પિન, બેકઅપ", null, new Runnable() { @Override public void run() { close(); a.go("more"); } }));
        s.add(srow("બેકઅપ લો", a.backupTimeText(), null, new Runnable() { @Override public void run() { backupSheet(); } }));
        if (!a.db.settings.pinHash.isEmpty()) s.add(srow("હમણાં લૉક કરો", "એપ ફરી ખોલવા પિન જોઈશે", null, new Runnable() { @Override public void run() { close(); a.showPin("unlock", null); } }));
        s.add(srow("મદદ (FAQs)", "કેવી રીતે વાપરવું", null, new Runnable() { @Override public void run() { faqSheet(); } }));
        s.show();
    }

    // ---------- risk traffic light + report sheets ----------
    /** 🟢 recent / 🟡 older than 30 days / 🔴 overdue, for customers who owe money. */
    String light(Model.Party p) {
        double b = a.db.partyBal(p.id);
        if (b <= 0) return "";
        String today = Fmt.today();
        if (a.db.overdueFor(p.id, today).recv > 0) return "🔴";
        String last = "";
        for (Model.Txn t : a.db.txns) if (p.id.equals(t.partyId) && t.date.compareTo(last) > 0) last = t.date;
        if (!last.isEmpty()) {
            try {
                long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(last), java.time.LocalDate.parse(today));
                if (days > 30) return "🟡";
            } catch (Exception e) { /* ignore */ }
        }
        return "🟢";
    }

    void pendingSheet() {
        Ui.Sheet s = open("બાકી ચૂકવણી (Pending)");
        final List<Object[]> rows = new ArrayList<>();
        String today = Fmt.today();
        for (Model.Party p : a.db.parties) {
            double b = a.db.partyBal(p.id);
            if (Math.abs(b) < 0.005) continue;
            Model.Due ov = a.db.overdueFor(p.id, today);
            boolean od = ov.recv > 0 || ov.pay > 0;
            rows.add(new Object[]{p, b, od ? 0 : 1});
        }
        Collections.sort(rows, new Comparator<Object[]>() {
            @Override public int compare(Object[] x, Object[] y) {
                int c1 = (Integer) x[2] - (Integer) y[2];
                return c1 != 0 ? c1 : Double.compare(Math.abs((Double) y[1]), Math.abs((Double) x[1]));
            }
        });
        if (rows.isEmpty()) {
            TextView e = Ui.t(c, "કોઈ બાકી નથી", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER); Ui.pad(e, 10, 24, 10, 24); s.add(e);
        } else {
            LinearLayout box = Ui.v(c);
            for (Object[] r : rows) {
                final Model.Party p = (Model.Party) r[0];
                double b = (Double) r[1];
                String lt = light(p);
                box.addView(rowItem(p.name, (lt.isEmpty() ? "" : lt + " ") + p.name, b > 0 ? "લેવાના" : "દેવાના", Fmt.money(Math.abs(b)), b > 0 ? Ui.GREEN : Ui.RED, null,
                        new Runnable() { @Override public void run() { close(); a.openParty(p.id); } }));
            }
            s.add(scrollBox(box, 380));
        }
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private void statLine(Ui.Sheet s, String label, double v, int color) {
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 4, 8, 4, 8);
        l.addView(Ui.t(c, label, 15, Ui.TEXT, false), Ui.weight(1));
        l.addView(Ui.t(c, Fmt.money(v), 16, color, true));
        s.add(l);
    }

    void cashSheet() {
        String ym = Fmt.today().substring(0, 7);
        double open = a.db.settings.openCash, in = 0, out = 0;
        for (Model.Txn t : a.db.txns) {
            double e = Model.type(t.type).cash * t.amount;
            if (e == 0 || "writeoff".equals(t.mode) || "upi".equals(t.mode)) continue;
            String m = t.date.length() >= 7 ? t.date.substring(0, 7) : "";
            if (m.compareTo(ym) < 0) open += e;
            else if (m.equals(ym)) { if (e > 0) in += e; else out -= e; }
        }
        Ui.Sheet s = open("રોકડ સારાંશ");
        TextView mt = Ui.t(c, Fmt.monthLabel(ym), 14, Ui.MUTED, true);
        mt.setGravity(Gravity.CENTER); s.add(mt);
        statLine(s, "શરૂઆતનું બેલેન્સ", open, Ui.TEXT);
        statLine(s, "રોકડ/બેંક આવક", in, Ui.GREEN);
        statLine(s, "જાવક", out, Ui.RED);
        statLine(s, "અંતનું બેલેન્સ", open + in - out, Ui.ACCENT);
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void pnlSheet() {
        String ym = Fmt.today().substring(0, 7);
        Model.Totals t = a.db.monthTotals(ym);
        Ui.Sheet s = open("નફો-નુકસાન (P&L)");
        TextView mt = Ui.t(c, Fmt.monthLabel(ym), 14, Ui.MUTED, true);
        mt.setGravity(Gravity.CENTER); s.add(mt);
        statLine(s, "કુલ આવક", t.inc, Ui.GREEN);
        statLine(s, "ખર્ચ", t.exp, Ui.RED);
        statLine(s, "ચોખ્ખો નફો", t.inc - t.exp, t.inc - t.exp >= 0 ? Ui.GREEN : Ui.RED);
        statLine(s, "ઉધાર વેચાણ", t.gave, Ui.TEXT);
        statLine(s, "ઉધાર ખરીદી", t.took, Ui.TEXT);
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void annualSheet() {
        String yr = Fmt.today().substring(0, 4);
        Ui.Sheet s = open("વાર્ષિક રિપોર્ટ");
        TextView mt = Ui.t(c, yr, 14, Ui.MUTED, true);
        mt.setGravity(Gravity.CENTER); s.add(mt);
        LinearLayout box = Ui.v(c);
        double ti = 0, te = 0;
        for (int m = 1; m <= 12; m++) {
            String key = yr + "-" + (m < 10 ? "0" : "") + m;
            Model.Totals t = a.db.monthTotals(key);
            ti += t.inc; te += t.exp;
            if (t.inc == 0 && t.exp == 0) continue;
            box.addView(rowItem(I18n.month(m - 1), I18n.month(m - 1), Fmt.money(t.exp) + " ↑", Fmt.money(t.inc), Ui.GREEN, null, null));
        }
        s.add(scrollBox(box, 300));
        statLine(s, "કુલ આવક", ti, Ui.GREEN);
        statLine(s, "ખર્ચ", te, Ui.RED);
        statLine(s, "ચોખ્ખો નફો", ti - te, ti - te >= 0 ? Ui.GREEN : Ui.RED);
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    // ---------- send reminder (one by one, from this phone) ----------
    private final java.util.LinkedHashSet<String> remSel = new java.util.LinkedHashSet<>();
    private final List<String> remQueue = new ArrayList<>();

    private List<Model.Party> dueCustomers() {
        List<Model.Party> out = new ArrayList<>();
        for (Model.Party p : a.db.parties) if (a.db.kindOf(p).equals("customer") && a.db.partyBal(p.id) > 0) out.add(p);
        Collections.sort(out, new Comparator<Model.Party>() {
            @Override public int compare(Model.Party x, Model.Party y) { return Double.compare(a.db.partyBal(y.id), a.db.partyBal(x.id)); }
        });
        return out;
    }

    void reminderSheet() {
        final List<Model.Party> list = dueCustomers();
        double total = 0;
        for (Model.Party p : list) total += a.db.partyBal(p.id);
        Ui.Sheet s = open("રિમાઇન્ડર મોકલો");
        TextView hd = Ui.t(c, list.size() + " ગ્રાહકો • " + Fmt.money(total) + " બાકી", 14, Ui.TEXT, true);
        s.add(hd);
        if (list.isEmpty()) {
            TextView e = Ui.t(c, "હમણાં બાકી ગ્રાહકો નથી", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER); Ui.pad(e, 10, 18, 10, 18); s.add(e);
        } else {
            TextView all = Ui.t(c, "બધા પસંદ કરો", 14, Ui.ACCENT, true);
            all.setGravity(Gravity.END);
            Ui.tap(all, new Runnable() { @Override public void run() {
                if (remSel.size() == list.size()) remSel.clear(); else for (Model.Party p : list) remSel.add(p.id);
                reminderSheet();
            } });
            s.add(all);
            LinearLayout box = Ui.v(c);
            double selAmt = 0;
            for (final Model.Party p : list) {
                final boolean on = remSel.contains(p.id);
                double b = a.db.partyBal(p.id);
                if (on) selAmt += b;
                box.addView(rowItem(p.name, (on ? "☑  " : "☐  ") + p.name, p.phone.isEmpty() ? "ફોન નથી" : p.phone, Fmt.money(b), Ui.RED, null, new Runnable() {
                    @Override public void run() { if (on) remSel.remove(p.id); else remSel.add(p.id); reminderSheet(); }
                }));
            }
            s.add(scrollBox(box, 280));
            final int n = remSel.size();
            TextView go = Ui.btn(c, "ચાલુ રાખો (" + n + " પસંદ કર્યા • " + Fmt.money(selAmt) + ")", n > 0 ? "primary" : "ghost", new Runnable() {
                @Override public void run() {
                    if (remSel.isEmpty()) return;
                    remQueue.clear();
                    remQueue.addAll(remSel);
                    remNext();
                }
            });
            s.add(go);
        }
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private void remNext() {
        while (!remQueue.isEmpty() && a.db.party(remQueue.get(0)) == null) remQueue.remove(0);
        if (remQueue.isEmpty()) { close(); a.toast("મોકલ્યું"); return; }
        final Model.Party p = a.db.party(remQueue.remove(0));
        final double b = a.db.partyBal(p.id);
        Ui.Sheet s = open("રિમાઇન્ડર મોકલો");
        LinearLayout card = Ui.card(c);
        TextView nm = Ui.t(c, p.name, 20, Ui.TEXT, true); nm.setGravity(Gravity.CENTER); card.addView(nm);
        TextView am = Ui.t(c, Fmt.money(b), 28, Ui.RED, true); am.setGravity(Gravity.CENTER); card.addView(am);
        TextView ph = Ui.t(c, p.phone.isEmpty() ? "ફોન નથી" : p.phone, 13, Ui.MUTED, false); ph.setGravity(Gravity.CENTER); card.addView(ph);
        s.add(card);
        s.add(Ui.btn(c, "WhatsApp ખોલો", "green", new Runnable() {
            @Override public void run() {
                String msg = a.db.settings.upiId.isEmpty()
                        ? "નમસ્તે " + p.name + ", તમારા " + Fmt.money(b) + " બાકી છે. કૃપા કરીને ચૂકવણી કરશો." + (a.db.settings.shop.isEmpty() ? "" : "\n— " + a.db.settings.shop)
                        : a.payMsg(p, b);
                Model.RemLog rl = new Model.RemLog();
                rl.partyId = p.id; rl.name = p.name; rl.amount = b; rl.ts = System.currentTimeMillis();
                a.db.remLog.add(rl);
                a.save();
                a.openUrl(a.waLink(p.phone, msg));
                remNext();
            }
        }));
        s.add(Ui.btn(c, "છોડો", "ghost", new Runnable() { @Override public void run() { remNext(); } }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { remQueue.clear(); close(); } }));
        s.show();
    }

    void reminderHistorySheet() {
        Ui.Sheet s = open("રિમાઇન્ડર ઇતિહાસ");
        if (a.db.remLog.isEmpty()) {
            TextView e = Ui.t(c, "કોઈ ઇતિહાસ નથી", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER); Ui.pad(e, 10, 24, 10, 24); s.add(e);
        } else {
            LinearLayout box = Ui.v(c);
            for (int i = a.db.remLog.size() - 1; i >= 0; i--) {
                Model.RemLog r = a.db.remLog.get(i);
                String when = new java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.US).format(new java.util.Date(r.ts));
                box.addView(rowItem(r.name, r.name, "WhatsApp · " + when, Fmt.money(r.amount), Ui.RED, null, null));
            }
            s.add(scrollBox(box, 360));
        }
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
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
            @Override public void accept(String v) { st.lang = v; I18n.set(v); a.save(); a.render(); voiceSetSheet(); }
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

    // ---------- budget / expense categories / about ----------
    static double budgetOf(String budgets, String cat) {
        if (budgets == null) return 0;
        for (String part : budgets.split(";")) {
            int i = part.lastIndexOf('=');
            if (i > 0 && part.substring(0, i).equals(cat)) { try { return Double.parseDouble(part.substring(i + 1)); } catch (Exception e) { return 0; } }
        }
        return 0;
    }

    private Map<String, Double> monthExpenseByCat() {
        Map<String, Double> m = new java.util.LinkedHashMap<>();
        String month = Fmt.today().substring(0, 7);
        for (Model.Txn t : a.db.txns) {
            if (!t.type.equals("expense") || t.date.length() < 7 || !t.date.substring(0, 7).equals(month) || "writeoff".equals(t.mode)) continue;
            String k = (t.cat == null || t.cat.isEmpty()) ? "અન્ય" : t.cat;
            Double o = m.get(k);
            m.put(k, (o == null ? 0 : o) + t.amount);
        }
        return m;
    }

    private View meter(double frac) {
        LinearLayout bar = Ui.h(c);
        bar.setBackground(Ui.rr(Ui.SURFACE2, 0, 4));
        double f = Math.max(0, Math.min(1, frac));
        View fill = new View(c);
        fill.setBackground(Ui.rr(frac > 1 ? Ui.RED : (frac > 0.8 ? Ui.AMBER : Ui.GREEN), 0, 4));
        bar.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(7), (float) Math.max(f, 0.001)));
        bar.addView(new View(c), new LinearLayout.LayoutParams(0, Ui.dp(7), (float) Math.max(1 - f, 0.001)));
        LinearLayout.LayoutParams p = Ui.fillW();
        p.setMargins(0, Ui.dp(4), 0, Ui.dp(2));
        bar.setLayoutParams(p);
        return bar;
    }

    void expCatSheet() {
        Ui.Sheet s = open("ખર્ચ વર્ગ · " + Fmt.monthLabel(Fmt.today().substring(0, 7)));
        Map<String, Double> m = monthExpenseByCat();
        double tot = 0;
        for (double v : m.values()) tot += v;
        LinearLayout list = Ui.v(c);
        for (String cat : Fmt.CATS) {
            Double v = m.get(cat);
            double x = v == null ? 0 : v;
            LinearLayout r = Ui.v(c);
            Ui.pad(r, 4, 8, 4, 8);
            LinearLayout line = Ui.h(c);
            line.addView(Ui.t(c, cat, 15, Ui.TEXT, true), Ui.weight(1));
            line.addView(Ui.t(c, Fmt.money(x), 15, x > 0 ? Ui.RED : Ui.MUTED, true));
            r.addView(line);
            r.addView(meter(tot > 0 ? x / tot : 0));
            list.addView(r);
        }
        s.add(scrollBox(list, 380));
        s.add(Ui.t(c, "કુલ ખર્ચ: " + Fmt.money(tot), 15, Ui.TEXT, true));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void budgetSheet() {
        final Model.Settings st = a.db.settings;
        Ui.Sheet s = open("બજેટ");
        s.add(said("દરેક ખર્ચ વર્ગ માટે મહિનાની હદ લખો. ખાલી એટલે બજેટ નહીં."));
        Map<String, Double> spent = monthExpenseByCat();
        LinearLayout list = Ui.v(c);
        final List<EditText> fields = new ArrayList<>();
        for (String cat : Fmt.CATS) {
            double b = budgetOf(st.budgets, cat);
            Double v = spent.get(cat);
            double x = v == null ? 0 : v;
            LinearLayout r = Ui.v(c);
            Ui.pad(r, 2, 6, 2, 6);
            LinearLayout line = Ui.h(c);
            line.addView(Ui.t(c, cat, 15, Ui.TEXT, true), Ui.weight(1));
            line.addView(Ui.t(c, "ખર્ચ " + Fmt.money(x), 12, x > b && b > 0 ? Ui.RED : Ui.MUTED, true));
            r.addView(line);
            EditText e = Ui.fld(c, "બજેટ (₹)", b > 0 ? Fmt.plain(b).replace(",", "") : "", Ui.IN_NUM);
            fields.add(e);
            r.addView(e);
            if (b > 0) r.addView(meter(x / b));
            list.addView(r);
        }
        s.add(scrollBox(list, 380));
        s.add(Ui.btn(c, "સેવ કરો", "primary", new Runnable() {
            @Override public void run() {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Fmt.CATS.length; i++) {
                    double v = parseD(fields.get(i).getText().toString());
                    if (v > 0) sb.append(sb.length() > 0 ? ";" : "").append(Fmt.CATS[i]).append("=").append(v);
                }
                st.budgets = sb.toString();
                a.save(); close(); a.render(); a.toast("બજેટ સેવ થયું");
            }
        }));
        s.add(Ui.btn(c, "રદ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void aboutSheet() {
        Ui.Sheet s = open("એપ વિશે");
        TextView t = Ui.t(c, "Mera Hisab", 24, Ui.ACCENT, true);
        t.setGravity(Gravity.CENTER);
        s.add(t);
        TextView v = Ui.t(c, "Version " + a.versionName(), 13, Ui.MUTED, false);
        v.setGravity(Gravity.CENTER);
        s.add(v);
        s.add(said("બોલીને હિસાબ લખો: ખાતા, ઉધાર, જમા, આવક અને ખર્ચ. ડેટા તમારા ફોનમાં સુરક્ષિત રહે છે."));
        s.add(srow("પ્રાઈવસી પોલીસી", "ડેટા વપરાશ અને સુરક્ષા", null, new Runnable() { @Override public void run() { privacySheet(); } }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

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
                if (!a.isStaff()) {
                    TextView del = Ui.t(c, "🗑", 20, Ui.RED, true);
                    Ui.tap(del, new Runnable() { @Override public void run() { a.db.products.remove(p); a.save(); prodSheet(); a.render(); } });
                    row.addView(del);
                }
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
            @Override public void accept(String v) { st.lang = v; I18n.set(v); a.save(); a.stopListen(); a.render(); voiceSheet(null); }
        }));
        micBtn = Ui.t(c, "🎤", 34, Color.WHITE, true);
        micBtn.setGravity(Gravity.CENTER);
        micBtn.setBackground(Ui.grad(Color.parseColor("#14B8A6"), Ui.PURPLE, 44));
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
        s.add(Ui.btn(c, "રદ કરો", "ghost", new Runnable() { @Override public void run() { a.stopListen(); close(); } }));
        s.dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface d) { a.stopListen(); voiceOpen = false; }
        });
        s.show();
        if (msg == null) {
            micBtn.post(new Runnable() { @Override public void run() { if (voiceOpen && !a.isListening()) startVoice(); } });
        }
    }

    private void micState(boolean on) {
        if (micBtn == null) return;
        micBtn.setText(on ? "■" : "🎤");
        micBtn.setBackground(on ? Ui.grad(Color.parseColor("#F05252"), Color.parseColor("#C81E1E"), 44) : Ui.grad(Color.parseColor("#14B8A6"), Ui.PURPLE, 44));
        micBtn.setAlpha(1f);
    }

    private void startVoice() {
        boolean ok = a.listen(new MainActivity.SpeechCb() {
            @Override public void partial(String t) { if (vText != null) vText.setText(t + " …"); }
            @Override public void fin(String t) { if (vText != null) vText.setText(t); handleCommand(t); }
            @Override public void end(boolean got) {
                micState(false);
                if (vStat != null && !got) vStat.setText(I18n.tr("ફરી માઇક દબાવીને બોલો"));
            }
            @Override public void error(String kind) { if (vStat != null) vStat.setText(I18n.tr(a.speechError(kind))); a.toast(a.speechError(kind)); }
        });
        if (ok) {
            if (vStat != null) vStat.setText(I18n.tr("સાંભળી રહ્યો છું… બોલો"));
            micState(true);
        }
    }

    void handleCommand(String text) {
        final String orig = text;
        text = Parser.applyAliases(text, a.db.aliasPairs());
        Parser.Result r = Parser.parseCommand(text, a.db.parties);
        logVoice(orig, r);
        if ("entry".equals(r.kind)) {
            a.stopListen();
            Model.Pending q = new Model.Pending();
            q.id = a.uid();
            q.said = text;
            q.type = r.type;
            q.amount = r.amount == null ? 0 : r.amount;
            q.name = r.name == null ? "" : r.name;
            q.partyId = r.partyId;
            q.date = Fmt.addDays(Fmt.today(), r.dateOffset);
            q.note = Parser.leftover(text, r.name);
            q.ts = System.currentTimeMillis();
            q.cat = "";
            if ("expense".equals(r.type)) {
                q.cat = "અન્ય";
                String first = q.note.isEmpty() ? "" : q.note.split(" ")[0];
                for (String cat : Fmt.CATS) {
                    for (String w : cat.split("/")) if (!first.isEmpty() && Parser.normText(w).equals(Parser.normText(first))) q.cat = cat;
                }
            }
            if (a.db.settings.biz.equals("service") && "gave".equals(r.type)) {
                double[] sv = Parser.serviceVoice(text);
                if (sv[1] > 0) {
                    q.amount = sv[0];
                    q.parts = "પાર્ટ:" + Fmt.plain(sv[1]).replace(",", "");
                }
                if (sv[2] > 0) q.paid = q.amount + sv[1];
            }
            a.db.review.add(q);
            a.save();
            close();
            a.go("home");
            a.toast("ચકાસવાની યાદીમાં ઉમેર્યું" + (q.amount > 0 ? " · " + Fmt.money(q.amount) : ""));
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

    // ---------- voice test log ----------
    private void logVoice(String text, Parser.Result r) {
        Model.VLog v = new Model.VLog();
        v.ts = System.currentTimeMillis();
        v.heard = text;
        v.alts = a.lastAlts; a.lastAlts = "";
        String kind = r.kind == null ? "?" : r.kind;
        StringBuilder sb = new StringBuilder(kind);
        if (r.type != null) sb.append(" · ").append(r.type);
        if (r.amount != null) sb.append(" · ").append(Fmt.plain(r.amount));
        if (r.name != null) sb.append(" · ").append(r.name).append(r.partyId != null ? " ✓" : " ✗");
        if (r.what != null) sb.append(" · ").append(r.what);
        v.res = sb.toString();
        a.db.vlog.add(v);
        while (a.db.vlog.size() > 200) a.db.vlog.remove(0);
        a.save();
    }

    /** Remember that the spoken spelling `heardName` means khata `p` (used on every next voice command). */
    void learnAlias(String heardName, Model.Party p) {
        if (heardName == null || p == null) return;
        String f = heardName.trim().toLowerCase();
        if (f.length() < 2 || f.equalsIgnoreCase(p.name.trim())) return;
        for (Model.Learn x : a.db.learn) if (x.from.equals(f)) { x.to = p.name; a.save(); return; }
        Model.Learn l = new Model.Learn();
        l.from = f; l.to = p.name; l.ts = System.currentTimeMillis();
        a.db.learn.add(l);
        a.save();
    }

    /** From a ✗ correction: if the correct sentence names an existing khata, learn what the app heard for it. */
    private void learnFromFix(Model.VLog v) {
        if (v.fix.isEmpty()) return;
        Parser.Result bad = Parser.parseCommand(Parser.applyAliases(v.heard, a.db.aliasPairs()), a.db.parties);
        Parser.Result good = Parser.parseCommand(v.fix, a.db.parties);
        if (good.partyId == null || bad.name == null) return;
        if (good.partyId.equals(bad.partyId)) return;
        learnAlias(bad.name, a.db.party(good.partyId));
    }

    void learnedSheet() {
        Ui.Sheet s = open("એપે શીખેલું");
        TextView h = Ui.t(c, "તમારા સુધારા પરથી એપ આ નામ શીખ્યું. ખોટું હોય તો ✕ દબાવી કાઢી નાખો.", 13, Ui.MUTED, false);
        Ui.pad(h, 8, 4, 8, 8); s.add(h);
        if (a.db.learn.isEmpty()) {
            TextView e = Ui.t(c, "હજી કંઈ શીખ્યું નથી. અવાજ ટેસ્ટ લૉગમાં ✗ દબાવી સાચું લખો.", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER); Ui.pad(e, 10, 24, 10, 24); s.add(e);
        } else {
            LinearLayout box = Ui.v(c);
            for (int i = a.db.learn.size() - 1; i >= 0; i--) {
                final Model.Learn l = a.db.learn.get(i);
                box.addView(rowItem(l.from, "“" + l.from + "”  →  " + l.to, "", "✕", Ui.RED, null, new Runnable() {
                    @Override public void run() { a.db.learn.remove(l); a.save(); learnedSheet(); }
                }));
            }
            s.add(scrollBox(box, 340));
        }
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void reportPartySheet() {
        Ui.Sheet s = open("સંપર્ક પસંદ કરો");
        LinearLayout box = Ui.v(c);
        box.addView(rowItem("all", "બધા સંપર્કો", "", "", Ui.TEXT, null, new Runnable() { @Override public void run() { a.rparty = ""; close(); a.render(); } }));
        for (final Model.Party p : a.db.parties) {
            if (!a.rkind.equals("all") && !a.db.kindOf(p).equals(a.rkind)) continue;
            box.addView(rowItem(p.name, p.name, a.db.kindOf(p).equals("creditor") ? "લેણદાર" : "ગ્રાહક", "", Ui.TEXT, null, new Runnable() { @Override public void run() { a.rparty = p.id; close(); a.render(); } }));
        }
        s.add(scrollBox(box, 380));
        s.show();
    }

    void reportRangeSheet() {
        Ui.Sheet s = open("ફિલ્ટર");
        String[][] rs = {{"month", "આ મહિનો"}, {"last", "ગયો મહિનો"}, {"year", "આ વર્ષ"}, {"all", "બધા સમય"}};
        for (final String[] r : rs) {
            s.add(Ui.btn(c, r[1], a.drange.equals(r[0]) ? "primary" : "ghost", new Runnable() { @Override public void run() { a.drange = r[0]; close(); a.render(); } }));
        }
        s.show();
    }

    void monthPickSheet() {
        Ui.Sheet s = open("મહિનો પસંદ કરો");
        String m = Fmt.today().substring(0, 7);
        for (int i = 0; i < 12; i++) {
            final String key = Fmt.monthShift(m, -i);
            s.add(Ui.btn(c, Fmt.monthLabel(key), key.equals(a.rmonth) ? "primary" : "ghost", new Runnable() { @Override public void run() { a.rmonth = key; close(); a.render(); } }));
        }
        s.show();
    }

    void dayDetailSheet(final String date) {
        Ui.Sheet s = open(Fmt.fmtDate(date));
        LinearLayout box = Ui.v(c);
        for (final Model.Txn t : a.db.txns) {
            if (!t.date.equals(date) || !a.screens.reportMatch(t)) continue;
            Model.Party p = a.db.party(t.partyId);
            box.addView(rowItem(t.id, (p != null ? p.name + " · " : "") + Model.type(t.type).label, t.note, Fmt.money(t.amount), Ui.tone(Ui.toneOf(t.type)), null,
                    new Runnable() { @Override public void run() { txSheet(t.id); } }));
        }
        s.add(scrollBox(box, 380));
        s.show();
    }

    void feedbackSheet() {
        Ui.Sheet s = open("એપ પ્રતિસાદ");
        TextView h = Ui.t(c, "તમારા સૂચનો લખો અથવા કોઈ ભૂલ આવી હોય તો જણાવો. પછી WhatsApp/Email થી મોકલી શકો.", 13, Ui.MUTED, false);
        Ui.pad(h, 8, 4, 8, 8); s.add(h);
        final EditText f = Ui.fld(c, "અહીં લખો", "", Ui.IN_PLAIN);
        s.add(f);
        s.add(Ui.btn(c, "મોકલો", "primary", new Runnable() {
            @Override public void run() {
                String t = f.getText().toString().trim();
                if (t.isEmpty()) { a.toast("કંઈક લખો"); return; }
                a.shareText("Mera Hisab feedback (v" + a.versionName() + "):\n" + t);
            }
        }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void bizSheet() {
        Ui.Sheet s = open("વ્યવસાયનો પ્રકાર");
        s.add(Ui.btn(c, "દુકાન / સ્ટોર", a.db.settings.biz.equals("store") ? "primary" : "ghost", new Runnable() { @Override public void run() { a.db.settings.biz = "store"; a.save(); close(); a.render(); } }));
        s.add(Ui.btn(c, "સર્વિસ પ્રોવાઈડર", a.db.settings.biz.equals("service") ? "primary" : "ghost", new Runnable() { @Override public void run() { a.db.settings.biz = "service"; a.save(); close(); a.render(); } }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void voiceLogSheet() {
        Ui.Sheet s = open("અવાજ ટેસ્ટ લૉગ");
        TextView h = Ui.t(c, "બોલેલું વાક્ય, એપ શું સમજ્યું, અને તમારો ચુકાદો. ખોટું હોય તો ✗ દબાવી સાચું લખો.", 13, Ui.MUTED, false);
        Ui.pad(h, 8, 4, 8, 8); s.add(h);
        if (a.db.vlog.isEmpty()) {
            TextView e = Ui.t(c, "હજી કોઈ અવાજ ટેસ્ટ નથી", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER); Ui.pad(e, 10, 24, 10, 24); s.add(e);
        } else {
            int good = 0, bad = 0;
            for (Model.VLog v : a.db.vlog) { if (v.ok > 0) good++; else if (v.ok < 0) bad++; }
            TextView st = Ui.t(c, "✓ " + good + "   ✗ " + bad + "   કુલ " + a.db.vlog.size(), 14, Ui.TEXT, true);
            st.setGravity(Gravity.CENTER); Ui.pad(st, 8, 4, 8, 8); s.add(st);
            LinearLayout box = Ui.v(c);
            for (int i = a.db.vlog.size() - 1; i >= 0; i--) {
                final Model.VLog v = a.db.vlog.get(i);
                String mark = v.ok > 0 ? "✅ " : (v.ok < 0 ? "❌ " : "▫️ ");
                String sub = "→ " + v.res + (v.fix.isEmpty() ? "" : "\nસાચું: " + v.fix) + (v.alts.isEmpty() ? "" : "\nબીજા અંદાજ: " + v.alts);
                LinearLayout row = Ui.v(c);
                row.addView(rowItem(v.heard, mark + v.heard, sub, "", Ui.TEXT, null, null));
                LinearLayout bt = Ui.h(c);
                bt.addView(Ui.btn(c, "✓ સાચું", "ghost", new Runnable() { @Override public void run() { v.ok = 1; a.save(); voiceLogSheet(); } }), new LinearLayout.LayoutParams(0, -2, 1));
                bt.addView(Ui.btn(c, "✗ ખોટું", "ghost", new Runnable() { @Override public void run() { fixSheet(v); } }), new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(bt);
                box.addView(row);
            }
            s.add(scrollBox(box, 380));
        }
        s.add(Ui.btn(c, "📤 બધું શેર કરો (મને મોકલવા)", "primary", new Runnable() { @Override public void run() { a.shareText(voiceLogText()); } }));
        s.add(Ui.btn(c, "લૉગ સાફ કરો", "ghost", new Runnable() { @Override public void run() { a.db.vlog.clear(); a.save(); voiceLogSheet(); } }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private void fixSheet(final Model.VLog v) {
        Ui.Sheet s = open("સાચું શું બોલવું હતું?");
        TextView h = Ui.t(c, "સંભળાયું: “" + v.heard + "”", 14, Ui.MUTED, false);
        Ui.pad(h, 8, 4, 8, 8); s.add(h);
        final EditText f = Ui.fld(c, "સાચું વાક્ય લખો", v.fix, android.text.InputType.TYPE_CLASS_TEXT);
        s.add(f);
        s.add(Ui.btn(c, "સેવ", "primary", new Runnable() {
            @Override public void run() { v.ok = -1; v.fix = f.getText().toString().trim(); a.save(); learnFromFix(v); voiceLogSheet(); }
        }));
        s.show();
    }

    private String voiceLogText() {
        StringBuilder sb = new StringBuilder("Mera Hisab voice test log\n\n");
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US);
        for (Model.VLog v : a.db.vlog) {
            sb.append(df.format(new java.util.Date(v.ts))).append(" [").append(v.ok > 0 ? "OK" : (v.ok < 0 ? "WRONG" : "?")).append("] ");
            sb.append("heard: ").append(v.heard).append("\n   app: ").append(v.res);
            if (!v.fix.isEmpty()) sb.append("\n   should be: ").append(v.fix);
            if (!v.alts.isEmpty()) sb.append("\n   alts: ").append(v.alts);
            sb.append("\n");
        }
        return sb.toString();
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
                    @Override public void run() { learnAlias(name, p); close(); a.openParty(p.id); a.speak(a.partySummary(p)); }
                }));
            }
            s.add(scrollBox(box, 260));
        }
        if (name != null && !name.trim().isEmpty()) {
            final String nn = name.trim();
            s.add(Ui.btn(c, "➕ “" + nn + "” નું નવું ખાતું બનાવો", "primary", new Runnable() {
                @Override public void run() {
                    Model.Party np = a.newParty(nn, "", "customer");
                    a.save();
                    close();
                    a.openParty(np.id);
                }
            }));
        }
        s.add(Ui.btn(c, "🎤 ફરી બોલો", "ghost", new Runnable() { @Override public void run() { voiceSheet(null); } }));
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

    // ================= update / server / staff / admin =================
    void updatePrompt(long remote) {
        Ui.Sheet s = open("નવું વર્ઝન મળ્યું છે");
        TextView t = Ui.t(c, "એક બટન દબાવો — બાકી બધું એપ પોતે કરશે (ડાઉનલોડ અને ઇન્સ્ટોલ).", 15, Ui.TEXT, false);
        t.setGravity(Gravity.CENTER);
        Ui.pad(t, 6, 6, 6, 10);
        s.add(t);
        s.add(Ui.btn(c, "⬇  અપડેટ કરો", "primary", new Runnable() { @Override public void run() { Updater.install(a); } }));
        s.add(Ui.btn(c, "પછી", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    void serverSheet() {
        Ui.Sheet s = open("સર્વર સેટઅપ");
        TextView t = Ui.t(c, Api.configured() ? "સર્વર જોડાયેલ છે ✔" : "સર્વર જોડાયેલ નથી. એડમિને આપેલો સર્વર કોડ (URL અને કી) અહીં પેસ્ટ કરો.", 14, Api.configured() ? Ui.GREEN : Ui.MUTED, true);
        t.setGravity(Gravity.CENTER);
        Ui.pad(t, 6, 4, 6, 6);
        s.add(t);
        final EditText code = Ui.fld(c, "https://....supabase.co  sb_publishable_...", "", Ui.IN_PLAIN);
        s.add(code);
        s.add(Ui.btn(c, "સેવ કરો", "primary", new Runnable() {
            @Override public void run() {
                String v = code.getText().toString();
                if (!Api.parse(v)) { a.toast("કોડ સાચો નથી"); return; }
                Api.save(c, v);
                a.toast("સર્વર જોડાયું");
                close();
                a.rebuild();
            }
        }));
        if (Api.configured()) s.add(Ui.btn(c, "સર્વર કાઢી નાખો", "danger", new Runnable() {
            @Override public void run() { Api.save(c, ""); close(); a.rebuild(); }
        }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private void callAsync(final String fn, final Map<String, Object> args, final java.util.function.Consumer<Map<String, Object>> cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                final Map<String, Object> r = Api.call(fn, args);
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if ("auth".equals(Api.s(r, "err"))) { a.onSessionExpired(); return; }
                        cb.accept(r);
                    }
                });
            }
        }).start();
    }

    private String srvErr(Map<String, Object> r) {
        switch (Api.s(r, "err")) {
            case "net": return "ઇન્ટરનેટ ચાલુ કરો";
            case "exists": return "આ નંબર પહેલેથી નોંધાયેલ છે";
            case "phone": return "૧૦ અંકનો સાચો નંબર લખો";
            case "name": return "નામ લખો";
            case "forbidden": return "આ તમારા માટે નથી";
            default: return "કંઈક ગડબડ થઈ, ફરી પ્રયત્ન કરો";
        }
    }

    /** Shows a freshly made OTP so the owner/admin can pass it on (WhatsApp button). */
    void showOtp(String name, final String phone, final String otp) {
        Ui.Sheet s = open("OTP તૈયાર છે");
        TextView n = Ui.t(c, name + " · " + phone, 14, Ui.MUTED, false);
        n.setGravity(Gravity.CENTER);
        s.add(n);
        TextView o = Ui.t(c, otp, 40, Ui.ACCENT, true);
        o.setGravity(Gravity.CENTER);
        o.setLetterSpacing(0.2f);
        Ui.pad(o, 0, 12, 0, 12);
        s.add(o);
        TextView h = Ui.t(c, "આ OTP ૪૮ કલાક ચાલશે અને એક જ વાર વપરાશે.", 12, Ui.MUTED, false);
        h.setGravity(Gravity.CENTER);
        s.add(h);
        final String msg = "Mera Hisab login OTP: " + otp + "\nNumber: " + phone;
        s.add(Ui.btn(c, "WhatsApp પર મોકલો", "green", new Runnable() { @Override public void run() { a.openUrl(a.waLink(phone, msg)); } }));
        s.add(Ui.btn(c, "શેર કરો", "ghost", new Runnable() { @Override public void run() { a.shareText(msg); } }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private View pill(String text, int color) {
        TextView t = Ui.t(c, text, 11, color, true);
        Ui.pad(t, 8, 2, 8, 2);
        t.setBackground(Ui.rr((color & 0x00FFFFFF) | 0x24000000, 0, 10));
        return t;
    }

    private View miniBtn(String text, String style, Runnable r) {
        TextView b = Ui.btn(c, text, style, r);
        b.setTextSize(13);
        b.setMinHeight(Ui.dp(40));
        Ui.pad(b, 8, 6, 8, 6);
        LinearLayout.LayoutParams p = Ui.weight(1);
        p.setMargins(Ui.dp(3), Ui.dp(8), Ui.dp(3), 0);
        b.setLayoutParams(p);
        return b;
    }

    @SuppressWarnings("unchecked")
    void staffSheet() {
        Ui.Sheet s0 = open("કર્મચારીઓ (સ્ટાફ)");
        s0.add(Ui.t(c, "લોડ થઈ રહ્યું છે...", 15, Ui.MUTED, false));
        s0.show();
        callAsync("mh_staff_list", Api.args("p_tok", a.sync.token), new java.util.function.Consumer<Map<String, Object>>() {
            @Override public void accept(Map<String, Object> r) {
                if (!Api.ok(r)) { close(); a.toast(srvErr(r)); return; }
                renderStaff((List<Object>) r.get("rows"));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void renderStaff(List<Object> rows) {
        Ui.Sheet s = open("કર્મચારીઓ (સ્ટાફ)");
        TextView info = Ui.t(c, "કર્મચારી પોતાના નંબર અને તમે આપેલા OTP થી લૉગિન કરશે અને તમારા જ ખાતામાં નોંધ કરશે. કર્મચારી નોંધ કાઢી શકશે નહીં.", 12, Ui.MUTED, false);
        Ui.pad(info, 4, 0, 4, 8);
        s.add(info);
        LinearLayout list = Ui.v(c);
        if (rows.isEmpty()) {
            TextView e = Ui.t(c, "હજી કોઈ કર્મચારી નથી.", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER);
            Ui.pad(e, 10, 10, 10, 10);
            list.addView(e);
        }
        for (Object ro : rows) {
            final Map<String, Object> m = (Map<String, Object>) ro;
            final String id = Api.s(m, "id"), nm = Api.s(m, "name"), ph = Api.s(m, "phone");
            final boolean blocked = Api.s(m, "status").equals("blocked");
            LinearLayout box = Ui.v(c);
            box.setBackground(Ui.rr(Ui.SURFACE2, 0, 14));
            Ui.pad(box, 12, 10, 12, 10);
            LinearLayout.LayoutParams bp = Ui.fillW();
            bp.setMargins(0, Ui.dp(4), 0, Ui.dp(4));
            box.setLayoutParams(bp);
            LinearLayout top = Ui.h(c);
            top.addView(Ui.t(c, nm, 16, Ui.TEXT, true), Ui.weight(1));
            if (blocked) top.addView(pill("બ્લોક", Ui.RED));
            else if (Boolean.TRUE.equals(m.get("otp_req"))) top.addView(pill("OTP માંગ્યો", Ui.AMBER));
            box.addView(top);
            box.addView(Ui.t(c, ph, 12, Ui.MUTED, false));
            LinearLayout br = Ui.h(c);
            br.addView(miniBtn("OTP બનાવો", "primary", new Runnable() {
                @Override public void run() {
                    callAsync("mh_staff_otp", Api.args("p_tok", a.sync.token, "p_id", id), new java.util.function.Consumer<Map<String, Object>>() {
                        @Override public void accept(Map<String, Object> r) { if (Api.ok(r)) showOtp(nm, ph, Api.s(r, "otp")); else a.toast(srvErr(r)); }
                    });
                }
            }));
            br.addView(miniBtn(blocked ? "ચાલુ કરો" : "બ્લોક", "ghost", new Runnable() {
                @Override public void run() {
                    callAsync("mh_staff_set", Api.args("p_tok", a.sync.token, "p_id", id, "p_action", blocked ? "unblock" : "block"), new java.util.function.Consumer<Map<String, Object>>() {
                        @Override public void accept(Map<String, Object> r) { if (Api.ok(r)) staffSheet(); else a.toast(srvErr(r)); }
                    });
                }
            }));
            br.addView(miniBtn("કાઢો", "danger", new Runnable() {
                @Override public void run() {
                    confirm(nm + " ને કાઢી નાખવા છે?", "હા, કાઢો", new Runnable() {
                        @Override public void run() {
                            callAsync("mh_staff_set", Api.args("p_tok", a.sync.token, "p_id", id, "p_action", "remove"), new java.util.function.Consumer<Map<String, Object>>() {
                                @Override public void accept(Map<String, Object> r) { if (Api.ok(r)) staffSheet(); else a.toast(srvErr(r)); }
                            });
                        }
                    });
                }
            }));
            box.addView(br);
            list.addView(box);
        }
        s.add(scrollBox(list, 260));
        s.add(Ui.label(c, "નવો કર્મચારી ઉમેરો"));
        final EditText nm = Ui.fld(c, "કર્મચારીનું નામ", "", Ui.IN_TEXT);
        final EditText ph = Ui.fld(c, "મોબાઇલ નંબર", "", Ui.IN_PHONE);
        s.add(nm);
        s.add(ph);
        s.add(Ui.btn(c, "ઉમેરો અને OTP બનાવો", "primary", new Runnable() {
            @Override public void run() {
                final String n = nm.getText().toString().trim(), p = ph.getText().toString().trim();
                callAsync("mh_staff_add", Api.args("p_tok", a.sync.token, "p_name", n, "p_phone", p), new java.util.function.Consumer<Map<String, Object>>() {
                    @Override public void accept(Map<String, Object> r) { if (Api.ok(r)) showOtp(n, Api.s(r, "phone"), Api.s(r, "otp")); else a.toast(srvErr(r)); }
                });
            }
        }));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private String adminTab = "req";

    @SuppressWarnings("unchecked")
    void adminSheet() {
        Ui.Sheet s0 = open("એડમિન પેનલ");
        s0.add(Ui.t(c, "લોડ થઈ રહ્યું છે...", 15, Ui.MUTED, false));
        s0.show();
        final boolean req = adminTab.equals("req");
        callAsync(req ? "mh_admin_requests" : "mh_admin_owners", Api.args("p_tok", a.sync.token), new java.util.function.Consumer<Map<String, Object>>() {
            @Override public void accept(Map<String, Object> r) {
                if (!Api.ok(r)) { close(); a.toast(srvErr(r)); return; }
                renderAdmin((List<Object>) r.get("rows"));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void renderAdmin(List<Object> rows) {
        Ui.Sheet s = open("એડમિન પેનલ");
        s.add(seg(new String[][]{{"req", "નવી વિનંતિઓ"}, {"own", "માલિકો"}}, adminTab, new java.util.function.Consumer<String>() {
            @Override public void accept(String v) { adminTab = v; adminSheet(); }
        }));
        LinearLayout list = Ui.v(c);
        if (rows.isEmpty()) {
            TextView e = Ui.t(c, adminTab.equals("req") ? "કોઈ નવી વિનંતિ નથી." : "હજી કોઈ માલિક નથી.", 14, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER);
            Ui.pad(e, 10, 14, 10, 14);
            list.addView(e);
        }
        for (Object ro : rows) {
            final Map<String, Object> m = (Map<String, Object>) ro;
            final String id = Api.s(m, "id"), nm = Api.s(m, "name"), ph = Api.s(m, "phone");
            LinearLayout box = Ui.v(c);
            box.setBackground(Ui.rr(Ui.SURFACE2, 0, 14));
            Ui.pad(box, 12, 10, 12, 10);
            LinearLayout.LayoutParams bp = Ui.fillW();
            bp.setMargins(0, Ui.dp(4), 0, Ui.dp(4));
            box.setLayoutParams(bp);
            LinearLayout top = Ui.h(c);
            top.addView(Ui.t(c, nm, 16, Ui.TEXT, true), Ui.weight(1));
            LinearLayout br = Ui.h(c);
            if (adminTab.equals("req")) {
                box.addView(top);
                box.addView(Ui.t(c, ph + (Api.s(m, "shop").isEmpty() ? "" : " · " + Api.s(m, "shop")), 12, Ui.MUTED, false));
                final boolean known = Api.s(m, "kind").equals("known");
                br.addView(miniBtn(known ? "OTP બનાવો" : "મંજૂર + OTP", "primary", new Runnable() {
                    @Override public void run() {
                        callAsync(known ? "mh_admin_otp" : "mh_admin_approve", Api.args("p_tok", a.sync.token, "p_id", id), new java.util.function.Consumer<Map<String, Object>>() {
                            @Override public void accept(Map<String, Object> r) { if (Api.ok(r)) showOtp(nm, ph, Api.s(r, "otp")); else a.toast(srvErr(r)); }
                        });
                    }
                }));
                if (!known) br.addView(miniBtn("નામંજૂર", "danger", new Runnable() {
                    @Override public void run() {
                        callAsync("mh_admin_reject", Api.args("p_tok", a.sync.token, "p_id", id), new java.util.function.Consumer<Map<String, Object>>() {
                            @Override public void accept(Map<String, Object> r) { adminSheet(); }
                        });
                    }
                }));
            } else {
                final boolean blocked = Api.s(m, "status").equals("blocked");
                final boolean self = Boolean.TRUE.equals(m.get("is_admin"));
                if (blocked) top.addView(pill("બ્લોક", Ui.RED));
                else if (Boolean.TRUE.equals(m.get("otp_req"))) top.addView(pill("OTP માંગ્યો", Ui.AMBER));
                else if (self) top.addView(pill("એડમિન", Ui.GREEN));
                box.addView(top);
                boolean onl = Boolean.TRUE.equals(m.get("online"));
                top.addView(pill(onl ? "ઑનલાઇન" : "ઑફલાઇન", onl ? Ui.GREEN : Ui.MUTED), 0);
                box.addView(Ui.t(c, ph + " · " + Api.s(m, "biz") + " · " + (Api.s(m, "role").equals("staff") ? "કર્મચારી" : "માલિક"), 12, Ui.MUTED, false));
                box.addView(Ui.t(c, "કર્મચારી: " + Api.s(m, "staff") + " · નોંધ: " + Api.s(m, "records") + (Api.s(m, "ver").isEmpty() ? "" : " · " + "વર્ઝન: " + Api.s(m, "ver")), 12, Ui.MUTED, false));
                box.addView(Ui.t(c, "છેલ્લું લૉગિન: " + localTime(Api.s(m, "last_login")) + "\n" + "છેલ્લે જોયું: " + localTime(Api.s(m, "last_seen")) + "\n" + "છેલ્લું લૉગઆઉટ: " + localTime(Api.s(m, "last_logout")), 12, Ui.MUTED, false));
                br.addView(miniBtn("હિસાબ જુઓ", "ghost", new Runnable() { @Override public void run() { adminDataSheet(id, nm); } }));
                br.addView(miniBtn("OTP બનાવો", "primary", new Runnable() {
                    @Override public void run() {
                        callAsync("mh_admin_otp", Api.args("p_tok", a.sync.token, "p_id", id), new java.util.function.Consumer<Map<String, Object>>() {
                            @Override public void accept(Map<String, Object> r) { if (Api.ok(r)) showOtp(nm, ph, Api.s(r, "otp")); else a.toast(srvErr(r)); }
                        });
                    }
                }));
                if (!self) br.addView(miniBtn(blocked ? "ચાલુ કરો" : "બ્લોક", blocked ? "ghost" : "danger", new Runnable() {
                    @Override public void run() {
                        callAsync("mh_admin_block", Api.args("p_tok", a.sync.token, "p_id", id, "p_block", !blocked), new java.util.function.Consumer<Map<String, Object>>() {
                            @Override public void accept(Map<String, Object> r) { adminSheet(); }
                        });
                    }
                }));
            }
            box.addView(br);
            list.addView(box);
        }
        s.add(scrollBox(list, 340));
        s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { close(); } }));
        s.show();
    }

    private static String localTime(String iso) {
        if (iso == null || iso.length() < 19 || iso.equals("null")) return "-";
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date d = in.parse(iso.substring(0, 19));
            return new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US).format(d);
        } catch (Exception e) { return "-"; }
    }

    @SuppressWarnings("unchecked")
    void adminDataSheet(final String id, final String nm) {
        Ui.Sheet s0 = open(nm);
        s0.add(Ui.t(c, "લોડ થઈ રહ્યું છે...", 15, Ui.MUTED, false));
        s0.show();
        callAsync("mh_admin_data", Api.args("p_tok", a.sync.token, "p_id", id), new java.util.function.Consumer<Map<String, Object>>() {
            @Override public void accept(Map<String, Object> r) {
                if (!Api.ok(r)) { close(); a.toast(srvErr(r)); return; }
                Map<String, String> names = new java.util.HashMap<>();
                List<Object> ps = (List<Object>) r.get("parties");
                if (ps != null) for (Object o : ps) { Map<String, Object> pm = (Map<String, Object>) o; names.put(Api.s(pm, "id"), Api.s(pm, "name")); }
                List<Object> ts = (List<Object>) r.get("txns");
                Ui.Sheet s = open(nm + " " + "(ફક્ત જોવા માટે)");
                s.add(Ui.t(c, "ખાતાં: " + (ps == null ? 0 : ps.size()), 13, Ui.MUTED, true));
                LinearLayout list = Ui.v(c);
                if (ts == null || ts.isEmpty()) list.addView(Ui.t(c, "હમણાં કોઈ એન્ટ્રી નથી.", 14, Ui.MUTED, false));
                else for (Object o : ts) {
                    Map<String, Object> t = (Map<String, Object>) o;
                    String pn = names.get(Api.s(t, "partyId"));
                    String line = Api.s(t, "date") + " · " + (pn == null ? "-" : pn) + " · " + Api.s(t, "type") + " · " + (t.get("amount") instanceof Number ? Fmt.money(((Number) t.get("amount")).doubleValue()) : "")
                            + (Api.s(t, "note").isEmpty() ? "" : " · " + Api.s(t, "note")) + (Api.s(t, "by").isEmpty() ? "" : " · " + Api.s(t, "by"));
                    TextView tv = Ui.t(c, line, 13, Ui.TEXT, false);
                    Ui.pad(tv, 4, 6, 4, 6);
                    list.addView(tv);
                }
                s.add(scrollBox(list, 380));
                s.add(Ui.btn(c, "બંધ કરો", "ghost", new Runnable() { @Override public void run() { adminSheet(); } }));
                s.show();
            }
        });
    }
}
