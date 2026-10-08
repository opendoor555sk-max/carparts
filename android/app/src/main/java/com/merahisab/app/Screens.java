package com.merahisab.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All main screens. Each method returns a fresh view tree for the content area. */
final class Screens {
    private final MainActivity a;
    private final Context c;

    Screens(MainActivity a) { this.a = a; this.c = a; }

    static final String[][] FGROUPS = {{"all", "બધા"}, {"gave", "ઉધાર વેચાણ"}, {"took", "ઉધાર ખરીદી"}, {"in", "આવક"}, {"out", "ખર્ચ"}, {"cash", "રોકડ"}, {"upi", "UPI / બેંક"}, {"writeoff", "માંડવાળ"}};

    // ---------------- helpers ----------------
    TextView ib(String glyph, final Runnable r) {
        TextView b = Ui.t(c, glyph, 20, Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rr(Ui.SURFACE2, 0, 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44));
        p.setMargins(Ui.dp(4), 0, 0, 0);
        b.setLayoutParams(p);
        Ui.tap(b, r);
        return b;
    }

    View top(String title, String sub, Runnable back, View... right) {
        LinearLayout l = Ui.h(c);
        l.setBackground(Ui.tealBarBottom(22));
        Ui.pad(l, 12, 14, 12, 16);
        if (back != null) {
            TextView bb = ib("←", back);
            bb.setBackground(Ui.rr(Color.parseColor("#33FFFFFF"), 0, 14)); bb.setTextColor(Color.WHITE);
            l.addView(bb);
        }
        LinearLayout mid = Ui.v(c);
        mid.addView(Ui.t(c, title, 20, Color.WHITE, true));
        if (sub != null && !sub.isEmpty()) mid.addView(Ui.t(c, sub, 12, Color.parseColor("#D1FAF5"), false));
        LinearLayout.LayoutParams mp = Ui.weight(1);
        mp.setMargins(Ui.dp(10), 0, 0, 0);
        l.addView(mid, mp);
        for (View v : right) {
            if (v instanceof TextView) { v.setBackground(Ui.rr(Color.parseColor("#33FFFFFF"), 0, 14)); ((TextView) v).setTextColor(Color.WHITE); }
            l.addView(v);
        }
        return l;
    }

    View sectionHead(String title, String actionText, Runnable action) {
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 16, 16, 16, 6);
        TextView t = Ui.t(c, title, 17, Ui.TEXT, true);
        l.addView(t, Ui.weight(1));
        if (actionText != null) {
            TextView b = Ui.t(c, actionText, 14, Ui.ACCENT, true);
            Ui.tap(b, action);
            l.addView(b);
        }
        return l;
    }

    View empty(String msg) {
        TextView t = Ui.t(c, msg, 14, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(Ui.SURFACE, Ui.dark ? Ui.LINE : 0, 20));
        t.setElevation(Ui.dp(Ui.dark ? 0 : 2));
        Ui.pad(t, 16, 22, 16, 22);
        LinearLayout.LayoutParams p = Ui.fillW();
        p.setMargins(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(6));
        t.setLayoutParams(p);
        return t;
    }

    LinearLayout listBox() {
        LinearLayout l = Ui.v(c);
        l.setBackground(Ui.rr(Ui.SURFACE, Ui.dark ? Ui.LINE : 0, 20));
        l.setElevation(Ui.dp(Ui.dark ? 0 : 2));
        LinearLayout.LayoutParams p = Ui.fillW();
        p.setMargins(Ui.dp(16), Ui.dp(4), Ui.dp(16), Ui.dp(4));
        l.setLayoutParams(p);
        return l;
    }

    View divider() {
        View d = new View(c);
        d.setBackgroundColor(Ui.LINE);
        d.setLayoutParams(Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return d;
    }

    TextView avatar(String name, int color) {
        TextView t = Ui.t(c, Fmt.initial(name), 17, color, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(Ui.SOFT, 0, 22));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44));
        p.setMargins(0, 0, Ui.dp(12), 0);
        t.setLayoutParams(p);
        return t;
    }

    /** One list row: avatar, two text lines and a right side amount. */
    View row(String avatarName, int avColor, String t1, String t2, String amt, int amtColor, String amtSub, Runnable r) {
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 12, 10, 12, 10);
        l.addView(avatar(avatarName, avColor));
        LinearLayout mid = Ui.v(c);
        TextView a1 = Ui.t(c, t1, 16, Ui.TEXT, true);
        a1.setMaxLines(1);
        mid.addView(a1);
        if (t2 != null && !t2.isEmpty()) {
            TextView a2 = Ui.t(c, t2, 12, Ui.MUTED, false);
            a2.setMaxLines(2);
            mid.addView(a2);
        }
        l.addView(mid, Ui.weight(1));
        if (amt != null) {
            LinearLayout rt = Ui.v(c);
            rt.setGravity(Gravity.END);
            TextView am = Ui.t(c, amt, 16, amtColor, true);
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

    View txRow(final Model.Txn t, boolean showDate) {
        Model.TypeInfo T = Model.type(t.type);
        Model.Party p = a.db.party(t.partyId);
        String title;
        if (p != null) title = p.name;
        else if (t.type.equals("expense")) title = (!t.cat.isEmpty() && !t.cat.equals("અન્ય")) ? t.cat : (!t.note.isEmpty() ? t.note : "ખર્ચ");
        else title = !t.note.isEmpty() ? t.note : T.label;
        List<String> sub = new ArrayList<>();
        sub.add(T.label);
        if ((p != null || t.type.equals("expense")) && !t.note.isEmpty()) sub.add(t.note);
        if ("upi".equals(t.mode)) sub.add("UPI/બેંક"); else if ("writeoff".equals(t.mode)) sub.add("માંડવાળ");
        String s = String.join(" · ", sub) + (showDate ? " · " + Fmt.fmtDate(t.date) : "");
        String sign = "writeoff".equals(t.mode) ? "" : (T.cash > 0 ? "+" : (T.cash < 0 ? "−" : ""));
        int col = Ui.tone(Ui.toneOf(t.type));
        return row(title, col, title, s, sign + Fmt.money(t.amount), col, null, new Runnable() {
            @Override public void run() { a.sheets.txSheet(t.id); }
        });
    }

    View chipsRow(String[][] items, String cur, final java.util.function.Consumer<String> pick) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 16, 8, 8, 8);
        for (final String[] it : items) {
            l.addView(Ui.chip(c, it[1], it[0].equals(cur), new Runnable() {
                @Override public void run() { pick.accept(it[0]); }
            }));
        }
        hs.addView(l);
        return hs;
    }

    View strip(String l1, double v1, int c1, String l2, double v2, int c2, String l3, double v3, int c3) {
        LinearLayout l = Ui.h(c);
        l.setBackgroundColor(Ui.SURFACE2);
        Ui.pad(l, 8, 8, 8, 8);
        String[] ls = {l1, l2, l3};
        double[] vs = {v1, v2, v3};
        int[] cs = {c1, c2, c3};
        for (int i = 0; i < 3; i++) {
            LinearLayout col = Ui.v(c);
            col.setGravity(Gravity.CENTER);
            col.addView(Ui.t(c, ls[i], 12, Ui.MUTED, false));
            col.addView(Ui.t(c, Fmt.money(vs[i]), 16, cs[i], true));
            l.addView(col, Ui.weight(1));
        }
        return l;
    }

    View periodBar(String label, String sub, final Runnable prev, final Runnable next, boolean arrows) {
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 12, 8, 12, 8);
        TextView p = ib("←", prev);
        TextView n = ib("→", next);
        if (!arrows) { p.setAlpha(0.2f); n.setAlpha(0.2f); p.setClickable(false); n.setClickable(false); }
        l.addView(p);
        LinearLayout mid = Ui.v(c);
        mid.setGravity(Gravity.CENTER);
        mid.addView(Ui.t(c, label, 17, Ui.TEXT, true));
        if (sub != null && !sub.isEmpty()) mid.addView(Ui.t(c, sub, 12, Ui.MUTED, false));
        l.addView(mid, Ui.weight(1));
        l.addView(n);
        return l;
    }

    View badge(String text, boolean bad) {
        TextView t = Ui.t(c, text, 11, bad ? Ui.RED : Ui.ACCENT, true);
        Ui.pad(t, 8, 1, 8, 1);
        t.setBackground(Ui.rr(bad ? Ui.DNGBG : Ui.SOFT, 0, 10));
        LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(8), 0, 0, 0);
        t.setLayoutParams(p);
        return t;
    }

    // ---------------- HOME ----------------
    LinearLayout home() {
        final Model.Db db = a.db;
        LinearLayout root = Ui.v(c);
        Model.Cash cb = db.cashBank();
        String thisMonth = Fmt.today().substring(0, 7);
        Model.Totals mt = db.monthTotals(thisMonth);
        Model.Due due = db.totalsDue(), od = db.overdueTotals(Fmt.today());
        List<Model.Txn> all = db.sorted();
        List<Model.Txn> recent = all.subList(0, Math.min(5, all.size()));
        String bm = a.balMode;
        double bal = bm.equals("cash") ? cb.cash : (bm.equals("bank") ? cb.bank : cb.cash + cb.bank);
        String lbl = bm.equals("cash") ? "રોકડ હાથ પર" : (bm.equals("bank") ? "બેંક બેલેન્સ" : "રોકડ + બેંક");
        String chip = bm.equals("cash") ? "રોકડ" : (bm.equals("bank") ? "બેંક" : "ફુલ");
        boolean hasAlert = od.recv > 0 || od.pay > 0;

        // ---- header band (teal) ----
        final LinearLayout outer = root;
        LinearLayout head = Ui.v(c);
        head.setBackground(Ui.tealBarBottom(0));
        Ui.pad(head, 16, 14, 16, 44);
        LinearLayout htop = Ui.h(c);
        TextView logo = Ui.t(c, "▥", 22, Ui.T2, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.rr(Color.WHITE, 0, 14));
        LinearLayout.LayoutParams lgp = new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46));
        lgp.setMargins(0, 0, Ui.dp(12), 0);
        htop.addView(logo, lgp);
        LinearLayout hl = Ui.v(c);
        android.text.SpannableString nm = new android.text.SpannableString("Mera Hisab");
        nm.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#99F6E4")), 5, 10, 0);
        TextView title = Ui.t(c, "", 21, Color.WHITE, true);
        title.setText(nm);
        hl.addView(title);
        java.time.LocalDate now = java.time.LocalDate.now();
        int fy = now.getMonthValue() >= 4 ? now.getYear() : now.getYear() - 1;
        hl.addView(Ui.t(c, "નાણાકીય વર્ષ " + fy + "-" + String.valueOf(fy + 1).substring(2) + "  ▾", 12, Color.parseColor("#D1FAF5"), false));
        htop.addView(hl, Ui.weight(1));
        TextView bell = ib(hasAlert ? "🔔•" : "🔔", new Runnable() { @Override public void run() { a.sheets.bellSheet(); } });
        bell.setBackground(Ui.rr(Color.parseColor("#33FFFFFF"), 0, 22)); bell.setTextColor(Color.WHITE);
        TextView av = ib(Fmt.initial(db.settings.owner.isEmpty() ? "M" : db.settings.owner), new Runnable() { @Override public void run() { a.sheets.homeMenuSheet(); } });
        av.setBackground(Ui.rr(Color.WHITE, 0, 22)); av.setTextColor(Ui.T2);
        htop.addView(bell); htop.addView(av);
        head.addView(htop);
        outer.addView(head);

        LinearLayout body = Ui.v(c);
        body.setBackground(Ui.topRounded(Ui.BG, 28));
        Ui.pad(body, 0, 14, 0, 0);
        LinearLayout.LayoutParams bp0 = Ui.fillW();
        bp0.setMargins(0, -Ui.dp(28), 0, 0);
        body.setLayoutParams(bp0);
        root = body;

        // ---- 4 money cards in one row ----
        String today = Fmt.today();
        String lastEnd = java.time.LocalDate.parse(thisMonth + "-01").minusDays(1).toString();
        double cashNow = cb.cash + cb.bank, cashPrev = cashAsOf(lastEnd);
        double[] duePrev = dueAsOf(lastEnd);
        Fin td = fin(today, today), yd = fin(Fmt.addDays(today, -1), Fmt.addDays(today, -1));
        LinearLayout stats = Ui.h(c);
        stats.setGravity(Gravity.TOP);
        Ui.pad(stats, 10, 0, 10, 0);
        stats.addView(statCard("💰", Ui.GREEN, "રોકડ + બેંક", cashNow, pct(cashNow, cashPrev), true, "ગયા મહિને"), cardLp());
        stats.addView(statCard("👥", Ui.AMBER, "લેવાના", due.recv, pct(due.recv, duePrev[0]), true, "ગયા મહિને"), cardLp());
        stats.addView(statCard("🏪", Ui.RED, "દેવાના", due.pay, pct(due.pay, duePrev[1]), false, "ગયા મહિને"), cardLp());
        stats.addView(statCard("📈", Ui.BLUE, "આજની આવક", td.sales(), pct(td.sales(), yd.sales()), true, "ગઈકાલ"), cardLp());
        root.addView(stats);

        // ---- 4 big colour buttons ----
        if (this.a.db.settings.biz.equals("service")) {
            LinearLayout sv = Ui.h(c);
            Ui.pad(sv, 12, 12, 12, 0);
            sv.addView(bigBtn("🛠", "+ સેવા નોંધ", "કામની એન્ટ્રી કરો", "#F59E0B", "#B45309", new Runnable() { @Override public void run() { Sheets.Form f = new Sheets.Form(); f.type = "gave"; a.sheets.openEntry(f); } }), Ui.weight(1));
            root.addView(sv);
        }
        LinearLayout acts1 = Ui.h(c), acts2 = Ui.h(c);
        Ui.pad(acts1, 10, 12, 10, 0); Ui.pad(acts2, 10, 8, 10, 0);
        acts1.addView(bigBtn("↗", "+ આવક / વેચાણ", "વેચાણ કે આવક નોંધો", "#22C55E", "#15803D", new Runnable() { @Override public void run() { a.sheets.askIncome(); } }), btnLp(true));
        acts1.addView(bigBtn("🛒", "+ ખર્ચ / ખરીદી", "ખરીદી કે ખર્ચ નોંધો", "#3B82F6", "#1D4ED8", new Runnable() { @Override public void run() { a.sheets.askExpense(); } }), btnLp(false));
        acts2.addView(bigBtn("💸", "+ ચૂકવણી", "લેણદારને પૈસા આપ્યા", "#EF4444", "#B91C1C", new Runnable() { @Override public void run() { a.sheets.pickSheet("creditor", "paid"); } }), btnLp(true));
        acts2.addView(bigBtn("📥", "+ જમા (રસીદ)", "ગ્રાહક પાસેથી પૈસા મળ્યા", "#14B8A6", "#0F766E", new Runnable() { @Override public void run() { a.sheets.pickSheet("customer", "got"); } }), btnLp(false));
        root.addView(acts1);
        root.addView(acts2);

        // ---- reports row ----
        root.addView(sectionHead("રિપોર્ટ", "બધા જુઓ ›", new Runnable() { @Override public void run() { a.go("rep"); } }));
        LinearLayout reps = Ui.h(c);
        reps.setGravity(Gravity.TOP);
        Ui.pad(reps, 10, 0, 10, 0);
        String mLabel = Fmt.monthLabel(thisMonth);
        reps.addView(repCard("⚖", Color.parseColor("#14B8A6"), "સરવૈયું", Fmt.fmtDate(today), "bs"), cardLp());
        reps.addView(repCard("📊", Color.parseColor("#3B82F6"), "નફો-નુકસાન", mLabel, "pnl"), cardLp());
        reps.addView(repCard("💧", Color.parseColor("#F59E0B"), "રોકડ પ્રવાહ", mLabel, "cf"), cardLp());
        reps.addView(repCard("📋", Color.parseColor("#A855F7"), "ખાતાવાર બાકી", Fmt.fmtDate(today), "tb"), cardLp());
        root.addView(reps);

        // ---- charts ----
        LinearLayout charts = Ui.h(c);
        charts.setGravity(Gravity.TOP);
        Ui.pad(charts, 10, 14, 10, 0);
        charts.addView(profitCard(db, now), chartLp(1.25f));
        charts.addView(expenseCard(db, thisMonth, mt), chartLp(1f));
        root.addView(charts);

        // In-Review: spoken entries waiting for confirmation
        if (!db.review.isEmpty()) {
            root.addView(sectionHead("✦ ચકાસવાની એન્ટ્રીઓ (" + db.review.size() + ")", null, null));
            for (final Model.Pending q : db.review) {
                LinearLayout card = Ui.card(c);
                LinearLayout line = Ui.h(c);
                TextView mic = Ui.t(c, "🎤", 18, Ui.ACCENT, true);
                mic.setGravity(Gravity.CENTER);
                mic.setBackground(Ui.rr(Ui.SOFT, 0, 22));
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44));
                mp.setMargins(0, 0, Ui.dp(10), 0);
                line.addView(mic, mp);
                LinearLayout mid = Ui.v(c);
                mid.addView(Ui.t(c, "બોલેલી એન્ટ્રી", 15, Ui.TEXT, true));
                mid.addView(Ui.t(c, q.said, 13, Ui.MUTED, false));
                line.addView(mid, Ui.weight(1));
                LinearLayout rt = Ui.v(c);
                rt.setGravity(Gravity.END);
                TextView am = Ui.t(c, q.amount > 0 ? Fmt.money(q.amount) : "₹ ?", 17, Ui.tone(Ui.toneOf(q.type)), true);
                am.setGravity(Gravity.END);
                rt.addView(am);
                TextView rv = Ui.t(c, "રિવ્યુ ›", 12, Ui.ACCENT, true);
                rv.setBackground(Ui.rr(Ui.SOFT, 0, 10));
                Ui.pad(rv, 10, 4, 10, 4);
                rv.setGravity(Gravity.END);
                rt.addView(rv);
                line.addView(rt);
                card.addView(line);
                Ui.tap(card, new Runnable() { @Override public void run() { a.sheets.openReviewById(q.id); } });
                LinearLayout.LayoutParams cp = Ui.fillW();
                cp.setMargins(Ui.dp(12), Ui.dp(4), Ui.dp(12), Ui.dp(4));
                card.setLayoutParams(cp);
                root.addView(card);
            }
        }

        root.addView(sectionHead("તાજેતરની એન્ટ્રીઓ", "બધા જુઓ ›", new Runnable() { @Override public void run() { a.go("txn"); } }));
        if (recent.isEmpty()) {
            root.addView(empty("હજી કોઈ એન્ટ્રી નથી.\nમાઇક દબાવીને બોલો, જેમ કે “હનીફ ભાઈને 5000 નો માલ ઉધાર આપ્યો”."));
        } else {
            LinearLayout lb = listBox();
            for (int i = 0; i < recent.size(); i++) {
                if (i > 0) lb.addView(divider());
                lb.addView(homeTxRow(recent.get(i)));
            }
            root.addView(lb);
        }
        root.addView(Ui.space(c, 90));
        outer.addView(root);
        return outer;
    }


    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        p.setMargins(Ui.dp(3), 0, Ui.dp(3), 0);
        return p;
    }

    private LinearLayout.LayoutParams chartLp(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w);
        p.setMargins(Ui.dp(3), 0, Ui.dp(3), 0);
        return p;
    }

    private LinearLayout.LayoutParams btnLp(boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
        return p;
    }

    private double cashAsOf(String date) {
        double v = a.db.settings.openCash + a.db.settings.openBank;
        for (Model.Txn t : a.db.txns) {
            if (t.date.compareTo(date) > 0 || "writeoff".equals(t.mode)) continue;
            v += Model.type(t.type).cash * t.amount;
        }
        return v;
    }

    /** {receivable, payable} as it stood at the end of the given date. */
    private double[] dueAsOf(String date) {
        Map<String, Double> bal = new java.util.HashMap<>();
        for (Model.Txn t : a.db.txns) {
            if (t.partyId == null || t.date.compareTo(date) > 0) continue;
            Double o = bal.get(t.partyId);
            bal.put(t.partyId, (o == null ? 0 : o) + Model.type(t.type).bal * t.amount);
        }
        double r = 0, p = 0;
        for (double b : bal.values()) { if (b > 0) r += b; else if (b < 0) p += -b; }
        return new double[]{r, p};
    }

    /** % change, or NaN when there is nothing to compare with. */
    private double pct(double now, double before) {
        if (Math.abs(before) < 0.5) return Double.NaN;
        return (now - before) / Math.abs(before) * 100.0;
    }

    private View statCard(String glyph, int tint, String label, double amount, double change, boolean upGood, String vs) {
        LinearLayout k = Ui.card(c);
        Ui.pad(k, 8, 10, 8, 10);
        TextView g = Ui.t(c, glyph, 13, tint, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(Ui.SOFT, 0, 10));
        k.addView(g, new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(28)));
        TextView l = Ui.t(c, label, 10, Ui.MUTED, true);
        l.setPadding(0, Ui.dp(6), 0, 0);
        l.setSingleLine(true);
        k.addView(l);
        TextView am = Ui.t(c, Fmt.money(amount), 13, Ui.TEXT, true);
        am.setSingleLine(true);
        am.setAutoSizeTextTypeUniformWithConfiguration(8, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        k.addView(am, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(20)));
        TextView ch;
        if (Double.isNaN(change)) {
            ch = Ui.t(c, "—", 10, Ui.MUTED, true);
        } else {
            boolean up = change >= 0;
            boolean good = up == upGood;
            ch = Ui.t(c, (up ? "↑ " : "↓ ") + String.format(java.util.Locale.US, "%.1f", Math.abs(change)) + "%", 10, good ? Ui.GREEN : Ui.RED, true);
        }
        k.addView(ch);
        TextView sub = Ui.t(c, vs, 9, Ui.MUTED, false);
        sub.setSingleLine(true);
        k.addView(sub);
        return k;
    }

    private View bigBtn(String glyph, String title, String sub, String c1, String c2, Runnable r) {
        LinearLayout t = Ui.h(c);
        t.setBackground(Ui.grad(Color.parseColor(c1), Color.parseColor(c2), 16));
        t.setElevation(Ui.dp(3));
        Ui.pad(t, 10, 12, 8, 12);
        TextView g = Ui.t(c, glyph, 18, Color.WHITE, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(Color.parseColor("#33FFFFFF"), 0, 22));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
        gp.setMargins(0, 0, Ui.dp(8), 0);
        t.addView(g, gp);
        LinearLayout tx = Ui.v(c);
        TextView l = Ui.t(c, title, 14, Color.WHITE, true);
        l.setMaxLines(1);
        tx.addView(l);
        TextView sb = Ui.t(c, sub, 10.5f, Color.parseColor("#E6FFFA"), false);
        sb.setMaxLines(2);
        tx.addView(sb);
        t.addView(tx, Ui.weight(1));
        Ui.tap(t, r);
        return t;
    }

    private View repCard(String glyph, int tint, String title, String sub, final String key) {
        LinearLayout k = Ui.card(c);
        Ui.pad(k, 8, 10, 8, 10);
        TextView g = Ui.t(c, glyph, 15, tint, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(Ui.SOFT, 0, 12));
        k.addView(g, new LinearLayout.LayoutParams(Ui.dp(32), Ui.dp(32)));
        TextView l = Ui.t(c, title, 11, Ui.TEXT, true);
        l.setPadding(0, Ui.dp(6), 0, 0);
        l.setMaxLines(2);
        k.addView(l);
        TextView sb = Ui.t(c, sub, 9, Ui.MUTED, false);
        sb.setSingleLine(true);
        k.addView(sb);
        Ui.tap(k, new Runnable() { @Override public void run() { a.go("rep"); openRep(key); } });
        return k;
    }

    private View pillTag(String text) {
        TextView t = Ui.t(c, text, 10, Ui.TEXT, true);
        t.setBackground(Ui.rr(Ui.SURFACE2, Ui.LINE, 10));
        Ui.pad(t, 8, 3, 8, 3);
        return t;
    }

    private View profitCard(Model.Db db, java.time.LocalDate now) {
        LinearLayout k = Ui.card(c);
        Ui.pad(k, 10, 10, 10, 8);
        LinearLayout h = Ui.h(c);
        TextView t = Ui.t(c, "માસિક નફાનો ટ્રેન્ડ", 12, Ui.TEXT, true);
        h.addView(t, Ui.weight(1));
        k.addView(h);
        k.addView(pillTag("આ વર્ષ ▾"));
        int fy = now.getMonthValue() >= 4 ? now.getYear() : now.getYear() - 1;
        List<Double> vals = new ArrayList<>();
        List<String> labs = new ArrayList<>();
        java.time.LocalDate m = java.time.LocalDate.of(fy, 4, 1);
        while (!m.isAfter(now.withDayOfMonth(1))) {
            String key = String.format(java.util.Locale.US, "%04d-%02d", m.getYear(), m.getMonthValue());
            Fin fm = fin(key + "-01", key + "-31");
            vals.add(fm.profit());
            String mn = I18n.month(m.getMonthValue() - 1);
            labs.add(mn.length() > 4 ? mn.substring(0, 4) : mn);
            m = m.plusMonths(1);
        }
        double[] dv = new double[vals.size()];
        for (int i = 0; i < dv.length; i++) dv[i] = vals.get(i);
        k.addView(new ChartViews.Line(c, dv, labs.toArray(new String[0])), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(130)));
        return k;
    }

    private View expenseCard(Model.Db db, String month, Model.Totals mt) {
        LinearLayout k = Ui.card(c);
        Ui.pad(k, 10, 10, 10, 8);
        k.addView(Ui.t(c, "ખર્ચની વહેંચણી", 12, Ui.TEXT, true));
        k.addView(pillTag("આ મહિને ▾"));
        Fin fe = fin(month + "-01", month + "-31");
        Map<String, Double> cat = new java.util.LinkedHashMap<>(fe.cats);
        if (fe.took > 0) cat.put("ઉધાર ખરીદી", fe.took);
        if (fe.badDebt > 0) cat.put("ડૂબેલી રકમ", fe.badDebt);
        List<Map.Entry<String, Double>> es = new ArrayList<>(cat.entrySet());
        java.util.Collections.sort(es, new java.util.Comparator<Map.Entry<String, Double>>() {
            @Override public int compare(Map.Entry<String, Double> x, Map.Entry<String, Double> y) { return Double.compare(y.getValue(), x.getValue()); }
        });
        int[] pal = {Color.parseColor("#3B82F6"), Color.parseColor("#14B8A6"), Color.parseColor("#F59E0B"), Color.parseColor("#A855F7"), Color.parseColor("#94A3B8")};
        List<String> names = new ArrayList<>();
        List<Double> amts = new ArrayList<>();
        double other = 0, tot = 0;
        for (int i = 0; i < es.size(); i++) {
            tot += es.get(i).getValue();
            if (i < 4) { names.add(es.get(i).getKey()); amts.add(es.get(i).getValue()); } else other += es.get(i).getValue();
        }
        if (other > 0) { names.add("અન્ય"); amts.add(other); }
        double[] dv = new double[amts.size()];
        int[] cols = new int[amts.size()];
        for (int i = 0; i < dv.length; i++) { dv[i] = amts.get(i); cols[i] = pal[i]; }
        View donut = new ChartViews.Donut(c, dv, cols, ChartViews.shortMoney(tot), I18n.tr("કુલ ખર્ચ"));
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(96));
        dp.setMargins(0, Ui.dp(6), 0, Ui.dp(4));
        k.addView(donut, dp);
        for (int i = 0; i < names.size(); i++) {
            LinearLayout r = Ui.h(c);
            View dot = new View(c);
            dot.setBackground(Ui.rr(cols[i], 0, 5));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(8), Ui.dp(8));
            dl.setMargins(0, 0, Ui.dp(5), 0);
            r.addView(dot, dl);
            TextView n = Ui.t(c, names.get(i), 10, Ui.TEXT, false);
            n.setSingleLine(true);
            r.addView(n, Ui.weight(1));
            r.addView(Ui.t(c, Math.round(dv[i] / tot * 100) + "%", 10, Ui.MUTED, true));
            k.addView(r);
        }
        if (names.isEmpty()) {
            TextView e = Ui.t(c, "આ મહિને ખર્ચ નથી", 10, Ui.MUTED, false);
            e.setGravity(Gravity.CENTER);
            k.addView(e);
        }
        return k;
    }

    private View homeTxRow(final Model.Txn t) {
        Model.TypeInfo T = Model.type(t.type);
        Model.Party p = a.db.party(t.partyId);
        int col = Ui.tone(Ui.toneOf(t.type));
        String glyph, tag;
        boolean good;
        switch (t.type) {
            case "gave": glyph = "📄"; tag = "વેચાણ"; good = true; break;
            case "got": glyph = "📥"; tag = "જમા"; good = true; break;
            case "took": glyph = "🛒"; tag = "ખરીદી"; good = false; break;
            case "paid": glyph = "💸"; tag = "ચૂકવણી"; good = false; break;
            case "income": glyph = "↗"; tag = "આવક"; good = true; break;
            default: glyph = "🧾"; tag = "ખર્ચ"; good = false;
        }
        String sub;
        if (p != null) sub = (T.kind.equals("creditor") ? "લેણદાર: " : "ગ્રાહક: ") + p.name;
        else if (!t.note.isEmpty()) sub = t.note;
        else sub = (t.cat == null || t.cat.isEmpty()) ? T.label : t.cat;
        String when;
        String d = t.date;
        if (d.equals(Fmt.today())) when = "આજે"; else if (d.equals(Fmt.addDays(Fmt.today(), -1))) when = "ગઈકાલે"; else when = Fmt.fmtDate(d);
        if (t.ts > 0 && (d.equals(Fmt.today()) || d.equals(Fmt.addDays(Fmt.today(), -1)))) when += ", " + new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(new java.util.Date(t.ts));
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 12, 10, 12, 10);
        TextView g = Ui.t(c, glyph, 16, col, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(Ui.SOFT, 0, 12));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
        gp.setMargins(0, 0, Ui.dp(10), 0);
        l.addView(g, gp);
        LinearLayout mid = Ui.v(c);
        TextView t1 = Ui.t(c, T.label, 13, Ui.TEXT, true);
        t1.setMaxLines(1);
        mid.addView(t1);
        TextView t2 = Ui.t(c, sub, 11, Ui.MUTED, false);
        t2.setMaxLines(1);
        mid.addView(t2);
        l.addView(mid, Ui.weight(1));
        LinearLayout rt = Ui.v(c);
        rt.setGravity(Gravity.END);
        String sign = "writeoff".equals(t.mode) ? "" : (T.cash > 0 ? "+" : (T.cash < 0 ? "−" : ""));
        TextView am = Ui.t(c, sign + Fmt.money(t.amount), 14, Ui.TEXT, true);
        am.setGravity(Gravity.END);
        rt.addView(am);
        TextView wh = Ui.t(c, when, 10, Ui.MUTED, false);
        wh.setGravity(Gravity.END);
        rt.addView(wh);
        l.addView(rt);
        TextView tg = Ui.t(c, tag, 10, good ? Ui.GREEN : Ui.RED, true);
        tg.setBackground(Ui.rr(good ? (Ui.dark ? Color.parseColor("#143D2E") : Color.parseColor("#DCFCE7")) : (Ui.dark ? Color.parseColor("#3A1620") : Color.parseColor("#FEE2E2")), 0, 10));
        Ui.pad(tg, 8, 3, 8, 3);
        LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(Ui.dp(8), 0, 0, 0);
        l.addView(tg, tp);
        Ui.tap(l, new Runnable() { @Override public void run() { a.sheets.txSheet(t.id); } });
        return l;
    }

    // ---------------- TRANSACTIONS ----------------
    List<Model.Txn> periodItems() {
        List<Model.Txn> out = new ArrayList<>();
        for (Model.Txn t : a.db.txns) {
            if (a.period.equals("day") && !t.date.equals(a.day)) continue;
            if (a.period.equals("month") && !t.date.startsWith(a.month)) continue;
            if (!a.fparty.isEmpty() && !a.fparty.equals(t.partyId)) continue;
            if (!matchFilter(t, a.filter)) continue;
            out.add(t);
        }
        Collections.sort(out, Model.SORT_NEWEST);
        return out;
    }

    static boolean matchFilter(Model.Txn t, String f) {
        switch (f) {
            case "gave": return t.type.equals("gave");
            case "took": return t.type.equals("took");
            case "in": return t.type.equals("got") || t.type.equals("income");
            case "out": return t.type.equals("paid") || t.type.equals("expense");
            case "cash": return "cash".equals(t.mode);
            case "upi": return "upi".equals(t.mode);
            case "writeoff": return "writeoff".equals(t.mode);
            default: return true;
        }
    }

    LinearLayout txn() {
        LinearLayout root = Ui.v(c);
        List<Model.Txn> items = periodItems();
        double inc = 0, out = 0;
        for (Model.Txn t : items) {
            int cs = Model.type(t.type).cash;
            if ("writeoff".equals(t.mode)) continue;
            if (cs > 0) inc += t.amount; else if (cs < 0) out += t.amount;
        }
        int extra = (a.fparty.isEmpty() ? 0 : 1) + (a.period.equals("day") ? 0 : 1);
        String t0 = Fmt.today();
        String navLabel, navSub = "";
        if (a.period.equals("day")) {
            navLabel = a.day.equals(t0) ? "આજ" : (a.day.equals(Fmt.addDays(t0, -1)) ? "ગઈકાલ" : Fmt.weekday(a.day));
            navSub = Fmt.shortDate(a.day);
        } else if (a.period.equals("month")) {
            navLabel = Fmt.monthLabel(a.month);
            navSub = a.month.equals(t0.substring(0, 7)) ? "આ મહિનો" : "";
        } else navLabel = "બધા સમયના";
        String none = a.period.equals("day") ? "આ તારીખે કોઈ એન્ટ્રીઓ નથી" : (a.period.equals("month") ? "આ મહિને કોઈ એન્ટ્રીઓ નથી" : "કોઈ એન્ટ્રીઓ નથી");

        TextView fb = Ui.t(c, "⏷ ફિલ્ટર" + (extra > 0 ? " " + extra : ""), 14, Ui.ACCENT, true);
        fb.setBackground(Ui.rr(Ui.SOFT, 0, 18));
        Ui.pad(fb, 12, 8, 12, 8);
        Ui.tap(fb, new Runnable() { @Override public void run() { a.sheets.filterSheet(); } });
        root.addView(top("એન્ટ્રીઓ (" + items.size() + ")", a.db.settings.shop.isEmpty() ? (a.db.settings.owner.isEmpty() ? "Mera Hisab" : a.db.settings.owner) : a.db.settings.shop,
                new Runnable() { @Override public void run() { a.go("home"); } }, fb));
        final boolean arrows = !a.period.equals("all");
        root.addView(periodBar(navLabel, navSub,
                new Runnable() { @Override public void run() { a.navPeriod(-1); } },
                new Runnable() { @Override public void run() { a.navPeriod(1); } }, arrows));
        root.addView(strip("આવક", inc, Ui.GREEN, "ખર્ચ", out, Ui.RED, "ચોખ્ખી રકમ", inc - out, inc - out >= 0 ? Ui.GREEN : Ui.RED));
        root.addView(chipsRow(FGROUPS, a.filter, new java.util.function.Consumer<String>() {
            @Override public void accept(String s) { a.filter = s; a.render(); }
        }));
        if (items.isEmpty()) {
            root.addView(empty(none + "\n＋ દબાવીને નોંધ ઉમેરો"));
        } else {
            String last = "";
            LinearLayout lb = null;
            for (Model.Txn t : items) {
                if (!a.period.equals("day") && !t.date.equals(last)) {
                    last = t.date;
                    TextView dh = Ui.t(c, Fmt.longDate(t.date), 13, Ui.MUTED, true);
                    Ui.pad(dh, 18, 10, 16, 2);
                    root.addView(dh);
                    lb = listBox();
                    root.addView(lb);
                } else if (lb == null) {
                    lb = listBox();
                    root.addView(lb);
                } else lb.addView(divider());
                lb.addView(txRow(t, false));
            }
        }
        return root;
    }

    // ---------------- KHATA ----------------
    private static class PRow { Model.Party p; double b; String k; }

    LinearLayout khata() {
        final LinearLayout root = Ui.v(c);
        Model.Due od = a.db.overdueTotals(Fmt.today());
        TextView bell = ib((od.recv > 0 || od.pay > 0) ? "🔔•" : "🔔", new Runnable() { @Override public void run() { a.sheets.bellSheet(); } });
        TextView sort = ib("⇅", new Runnable() { @Override public void run() { a.sheets.sortSheet(); } });
        TextView mic = ib("🎤", new Runnable() { @Override public void run() { a.sheets.voiceSheet(null); } });
        mic.setBackground(Ui.rr(Ui.SOFT, 0, 22));
        root.addView(top("ખાતા", "કુલ " + a.db.parties.size() + " ખાતા", new Runnable() { @Override public void run() { a.go("masters"); } }, bell, sort, mic));
        root.addView(chipsRow(new String[][]{{"all", "બધા"}, {"customer", "ગ્રાહક"}, {"creditor", "લેણદાર"}}, a.kf, new java.util.function.Consumer<String>() {
            @Override public void accept(String s) { a.kf = s; a.render(); }
        }));
        final EditText q = Ui.fld(c, "🔍 નામ અથવા ફોન શોધો", a.kq, Ui.IN_PLAIN);
        LinearLayout.LayoutParams qp = Ui.fillW();
        qp.setMargins(Ui.dp(16), Ui.dp(4), Ui.dp(16), Ui.dp(6));
        q.setLayoutParams(qp);
        root.addView(q);
        final LinearLayout holder = Ui.v(c);
        root.addView(holder);
        fillKhata(holder);
        Ui.onText(q, new Runnable() {
            @Override public void run() {
                String s = q.getText().toString();
                if (!s.equals(a.kq)) { a.kq = s; fillKhata(holder); }
            }
        });
        TextView add = Ui.btn(c, "＋ નવું ખાતું", "primary", new Runnable() { @Override public void run() { a.sheets.partySheet(null, a.kf.equals("creditor") ? "creditor" : "customer"); } });
        LinearLayout.LayoutParams ap = Ui.fillW();
        ap.setMargins(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(8));
        add.setLayoutParams(ap);
        root.addView(add);
        return root;
    }

    private void fillKhata(LinearLayout holder) {
        holder.removeAllViews();
        String q = Parser.normText(a.kq);
        List<PRow> list = new ArrayList<>();
        for (Model.Party p : a.db.parties) {
            PRow r = new PRow();
            r.p = p; r.b = a.db.partyBal(p.id); r.k = a.db.kindOf(p);
            if (a.kf.equals("customer") && !r.k.equals("customer")) continue;
            if (a.kf.equals("creditor") && !r.k.equals("creditor")) continue;
            if (!q.isEmpty() && !Parser.normText(p.name).contains(q) && !p.phone.contains(q)) continue;
            list.add(r);
        }
        final Map<String, String> lastKey = new HashMap<>();
        for (PRow r : list) {
            List<Model.Txn> es = a.db.partyEntries(r.p.id);
            lastKey.put(r.p.id, es.isEmpty() ? "" : es.get(0).date + String.format("%020d", es.get(0).ts));
        }
        if (a.sort.equals("name")) {
            Collections.sort(list, new Comparator<PRow>() { @Override public int compare(PRow x, PRow y) { return x.p.name.compareToIgnoreCase(y.p.name); } });
        } else if (a.sort.equals("recent")) {
            Collections.sort(list, new Comparator<PRow>() { @Override public int compare(PRow x, PRow y) { return lastKey.get(y.p.id).compareTo(lastKey.get(x.p.id)); } });
        } else {
            Collections.sort(list, new Comparator<PRow>() {
                @Override public int compare(PRow x, PRow y) {
                    int cmp = Double.compare(Math.abs(y.b), Math.abs(x.b));
                    return cmp != 0 ? cmp : x.p.name.compareToIgnoreCase(y.p.name);
                }
            });
        }
        if (list.isEmpty()) {
            holder.addView(empty((a.kf.equals("creditor") ? "લેણદારો મળ્યા નથી." : "ગ્રાહકો મળ્યા નથી.") + "\nએન્ટ્રીઓ મેનેજ કરવા માટે ખાતા ઉમેરો"));
            return;
        }
        LinearLayout lb = listBox();
        holder.addView(lb);
        boolean first = true;
        for (final PRow r : list) {
            if (!first) lb.addView(divider());
            first = false;
            Model.Due ov = a.db.overdueFor(r.p.id, Fmt.today());
            List<Model.Txn> es = a.db.partyEntries(r.p.id);
            String sub = !es.isEmpty() ? "છેલ્લી નોંધ " + Fmt.fmtDate(es.get(0).date) : (!r.p.phone.isEmpty() ? r.p.phone : "હજી નોંધ નથી");
            sub = (r.k.equals("creditor") ? "લેણદાર" : "ગ્રાહક") + ((ov.recv + ov.pay) > 0 ? " · મુદતવીતી" : "") + " · " + sub;
            int col = r.b > 0 ? Ui.GREEN : (r.b < 0 ? Ui.RED : Ui.MUTED);
            lb.addView(row(r.p.name, Ui.ACCENT, (a.sheets.light(r.p).isEmpty() ? "" : a.sheets.light(r.p) + " ") + r.p.name, sub, Fmt.money(Math.abs(r.b)), col, r.b > 0 ? "લેવાના" : (r.b < 0 ? "દેવાના" : "બરાબર"),
                    new Runnable() { @Override public void run() { a.openParty(r.p.id); } }));
        }
    }

    // ---------------- PARTY ----------------
    LinearLayout party(final String pid) {
        final Model.Db db = a.db;
        final Model.Party p = db.party(pid);
        if (p == null) { a.partyId = null; return khata(); }
        LinearLayout root = Ui.v(c);
        double b = db.partyBal(pid);
        Model.Due ov = db.overdueFor(pid, Fmt.today());
        double gave = 0, got = 0, took = 0, paid = 0;
        for (Model.Txn t : db.txns) {
            if (!pid.equals(t.partyId)) continue;
            switch (t.type) {
                case "gave": gave += t.amount; break;
                case "got": got += t.amount; break;
                case "took": took += t.amount; break;
                case "paid": paid += t.amount; break;
                default:
            }
        }
        String k = db.kindOf(p);
        TextView ed = ib("✎", new Runnable() { @Override public void run() { a.sheets.partySheet(p, null); } });
        root.addView(top(p.name, (k.equals("creditor") ? "લેણદાર" : "ગ્રાહક") + " · " + (p.phone.isEmpty() ? "ફોન નથી" : p.phone),
                new Runnable() { @Override public void run() { a.partyId = null; a.render(); } }, ed));

        LinearLayout pb = Ui.v(c);
        pb.setBackground(Ui.grad(Ui.dark ? Color.parseColor("#173A37") : Color.parseColor("#CCFBF1"), Ui.dark ? Color.parseColor("#12201F") : Color.parseColor("#E6FFFA"), 20));
        Ui.pad(pb, 16, 14, 16, 14);
        LinearLayout.LayoutParams pp = Ui.fillW();
        pp.setMargins(Ui.dp(12), Ui.dp(12), Ui.dp(12), Ui.dp(8));
        pb.setLayoutParams(pp);
        pb.addView(Ui.t(c, b > 0 ? "કુલ બાકી (તમારે લેવાના)" : (b < 0 ? "કુલ બાકી (તમારે દેવાના)" : "હિસાબ બરાબર"), 13, Ui.MUTED, true));
        pb.addView(Ui.t(c, Fmt.money(Math.abs(b)), 34, b > 0 ? Ui.GREEN : (b < 0 ? Ui.RED : Ui.TEXT), true));
        List<String[]> cells = new ArrayList<>();
        if (gave > 0) cells.add(new String[]{"કુલ ઉધાર આપ્યું (માલ/ભાડું)", Fmt.money(gave)});
        if (got > 0) cells.add(new String[]{"કુલ પૈસા મળ્યા", Fmt.money(got)});
        if (took > 0) cells.add(new String[]{"કુલ ઉધાર લીધું", Fmt.money(took)});
        if (paid > 0) cells.add(new String[]{"કુલ ચૂકવ્યા", Fmt.money(paid)});
        if (ov.recv > 0) cells.add(new String[]{"મુદતવીતી (લેવાના)", Fmt.money(ov.recv)});
        if (ov.pay > 0) cells.add(new String[]{"મુદતવીતી (દેવાના)", Fmt.money(ov.pay)});
        for (int i = 0; i < cells.size(); i += 2) {
            LinearLayout r = Ui.h(c);
            r.setPadding(0, Ui.dp(8), 0, 0);
            for (int j = i; j < Math.min(i + 2, cells.size()); j++) {
                LinearLayout cl = Ui.v(c);
                cl.setBackground(Ui.rr(Ui.dark ? Color.parseColor("#14FFFFFF") : Color.parseColor("#14000000"), 0, 12));
                Ui.pad(cl, 10, 6, 10, 6);
                cl.addView(Ui.t(c, cells.get(j)[0], 11, Ui.MUTED, false));
                cl.addView(Ui.t(c, cells.get(j)[1], 15, Ui.TEXT, true));
                LinearLayout.LayoutParams cp = Ui.weight(1);
                cp.setMargins(j == i ? 0 : Ui.dp(8), 0, 0, 0);
                r.addView(cl, cp);
            }
            pb.addView(r);
        }
        root.addView(pb);

        // action grid
        LinearLayout g1 = Ui.h(c); Ui.pad(g1, 12, 0, 12, 0);
        g1.addView(pbtn("＋ ઉધાર આપ્યું", true, entry(pid, "gave")), pbLp());
        g1.addView(pbtn("↓ પૈસા મળ્યા", true, entry(pid, "got")), pbLp());
        root.addView(g1);
        LinearLayout g2 = Ui.h(c); Ui.pad(g2, 12, 0, 12, 0);
        g2.addView(pbtn("− ઉધાર લીધું", false, entry(pid, "took")), pbLp());
        g2.addView(pbtn("↑ પૈસા ચૂકવ્યા", false, entry(pid, "paid")), pbLp());
        root.addView(g2);
        LinearLayout g3 = Ui.h(c); Ui.pad(g3, 12, 0, 12, 0);
        g3.addView(pbtn("📤 WhatsApp પર મોકલો", false, new Runnable() { @Override public void run() { a.openUrl(a.waLink(p.phone, a.statementText(p))); } }), pbLp());
        g3.addView(pbtn("🔊 બોલીને સંભળાવો", false, new Runnable() { @Override public void run() { a.speak(a.partySummary(p)); } }), pbLp());
        root.addView(g3);
        if (b > 0) {
            LinearLayout g4 = Ui.h(c); Ui.pad(g4, 12, 0, 12, 0);
            g4.addView(pbtn("₹ ચૂકવણી Link મોકલો", false, new Runnable() { @Override public void run() { a.sheets.payLinkSheet(pid); } }), pbLp());
            root.addView(g4);
        }

        List<Model.Txn> asc = new ArrayList<>(db.partyEntries(pid));
        Collections.reverse(asc); // oldest first for running balance
        Collections.sort(asc, new Comparator<Model.Txn>() {
            @Override public int compare(Model.Txn x, Model.Txn y) {
                int cmp = x.date.compareTo(y.date);
                return cmp != 0 ? cmp : Long.compare(x.ts, y.ts);
            }
        });
        Map<String, Double> run = new LinkedHashMap<>();
        double acc = 0;
        for (Model.Txn t : asc) { acc += Model.type(t.type).bal * t.amount; run.put(t.id, acc); }
        List<Model.Txn> rows = new ArrayList<>(asc);
        Collections.reverse(rows);
        root.addView(sectionHead("બધી નોંધ (" + rows.size() + ")", null, null));
        if (rows.isEmpty()) {
            root.addView(empty("આ ખાતામાં હજી કોઈ નોંધ નથી."));
        } else {
            LinearLayout lb = listBox();
            boolean first = true;
            for (final Model.Txn t : rows) {
                if (!first) lb.addView(divider());
                first = false;
                Model.TypeInfo T = Model.type(t.type);
                int col = Ui.tone(Ui.toneOf(t.type));
                LinearLayout l = Ui.h(c);
                Ui.pad(l, 14, 10, 14, 10);
                LinearLayout mid = Ui.v(c);
                mid.addView(Ui.t(c, T.label, 15, col, true));
                String d = Fmt.fmtDate(t.date) + ("upi".equals(t.mode) ? " · UPI/બેંક" : ("writeoff".equals(t.mode) ? " · માંડવાળ" : "")) + (!t.due.isEmpty() ? " · મુદત " + Fmt.fmtDate(t.due) : "");
                mid.addView(Ui.t(c, d, 12, Ui.MUTED, false));
                String nt = !t.note.isEmpty() ? t.note : t.said;
                if (!nt.isEmpty()) mid.addView(Ui.t(c, nt, 13, Ui.TEXT, false));
                l.addView(mid, Ui.weight(1));
                LinearLayout rt = Ui.v(c);
                rt.setGravity(Gravity.END);
                TextView am = Ui.t(c, Fmt.money(t.amount), 16, col, true);
                am.setGravity(Gravity.END);
                rt.addView(am);
                double rb = run.get(t.id);
                TextView rs = Ui.t(c, "બાકી " + Fmt.money(Math.abs(rb)) + (rb > 0 ? " લેવાના" : (rb < 0 ? " દેવાના" : "")), 11, Ui.MUTED, false);
                rs.setGravity(Gravity.END);
                rt.addView(rs);
                l.addView(rt);
                Ui.tap(l, new Runnable() { @Override public void run() { a.sheets.txSheet(t.id); } });
                lb.addView(l);
            }
            root.addView(lb);
        }
        return root;
    }

    private Runnable entry(final String pid, final String type) {
        return new Runnable() {
            @Override public void run() {
                Model.Party p = a.db.party(pid);
                Sheets.Form f = new Sheets.Form();
                f.type = type;
                if (p != null) { f.partyId = p.id; f.name = p.name; f.lock = true; }
                a.sheets.openEntry(f);
            }
        };
    }

    private LinearLayout.LayoutParams pbLp() {
        LinearLayout.LayoutParams p = Ui.weight(1);
        p.setMargins(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        return p;
    }

    private View pbtn(String text, boolean primary, Runnable r) {
        TextView b = Ui.t(c, text, 14, primary ? Color.WHITE : Ui.TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(48));
        Ui.pad(b, 8, 8, 8, 8);
        b.setBackground(primary ? Ui.grad(Color.parseColor("#14B8A6"), Ui.PURPLE, 14) : Ui.rr(Ui.SURFACE, Ui.LINE, 14));
        Ui.tap(b, r);
        return b;
    }

    // ---------------- REPORTS ----------------
    // ================= REPORTS (Phase 2) =================
    private String rpPeriod = "month";
    private String rpQuery = "";

    private static final class Fin {
        double gave, income, discount, took, expense, badDebt;
        Map<String, Double> cats = new LinkedHashMap<>();
        double sales() { return gave + income; }
        double revenue() { return gave + income + discount; }
        double costs() { return took + expense + badDebt; }
        double profit() { return revenue() - costs(); }
    }

    /** Profit & loss figures for dates from..to (ISO strings, inclusive). Credit sales/purchases count when written, not when paid. */
    Fin fin(String from, String to) {
        Fin f = new Fin();
        for (Model.Txn t : a.db.txns) {
            if (t.date.compareTo(from) < 0 || t.date.compareTo(to) > 0) continue;
            boolean wo = "writeoff".equals(t.mode);
            switch (t.type) {
                case "gave": f.gave += t.amount; break;
                case "took": f.took += t.amount; break;
                case "income": f.income += t.amount; break;
                case "expense": {
                    f.expense += t.amount;
                    String k = (t.cat == null || t.cat.isEmpty()) ? "અન્ય" : t.cat;
                    Double o = f.cats.get(k);
                    f.cats.put(k, (o == null ? 0 : o) + t.amount);
                    break;
                }
                case "got": if (wo) f.badDebt += t.amount; break;
                case "paid": if (wo) f.discount += t.amount; break;
                default:
            }
        }
        return f;
    }

    String[] rpRange() {
        java.time.LocalDate n = java.time.LocalDate.now();
        if (rpPeriod.equals("last")) {
            java.time.LocalDate f = n.withDayOfMonth(1).minusMonths(1);
            return new String[]{f.toString(), f.withDayOfMonth(f.lengthOfMonth()).toString()};
        }
        if (rpPeriod.equals("fy")) {
            int y = n.getMonthValue() >= 4 ? n.getYear() : n.getYear() - 1;
            return new String[]{y + "-04-01", (y + 1) + "-03-31"};
        }
        java.time.LocalDate f = n.withDayOfMonth(1);
        return new String[]{f.toString(), f.withDayOfMonth(f.lengthOfMonth()).toString()};
    }

    String rpLabel() {
        String[] r = rpRange();
        if (rpPeriod.equals("fy")) return Fmt.fmtDate(r[0]) + " – " + Fmt.fmtDate(r[1]);
        return Fmt.monthLabel(r[0].substring(0, 7));
    }

    private static final class RRow {
        int kind; String label; double a, b; int col;
        RRow(int kind, String label, double a, double b, int col) { this.kind = kind; this.label = label; this.a = a; this.b = b; this.col = col; }
    }
    private RRow rh(String l) { return new RRow(0, l, 0, 0, 0); }
    private RRow rl(String l, double v, int col) { return new RRow(1, l, v, 0, col); }
    private RRow rt(String l, double v, int col) { return new RRow(2, l, v, 0, col); }
    private RRow rn(String l) { return new RRow(3, l, 0, 0, 0); }
    private RRow r2(String l, double dr, double cr) { return new RRow(4, l, dr, cr, 0); }

    /** Full-screen report page: header, optional period chips, rows, share button. */
    private LinearLayout reportPage(String title, String subtitle, boolean periods, List<RRow> rows) {
        LinearLayout root = Ui.v(c);
        root.addView(top(title, subtitle, new Runnable() { @Override public void run() { a.sub = null; a.render(); } }));
        if (periods) {
            root.addView(chipsRow(new String[][]{{"month", "આ મહિને"}, {"last", "ગયા મહિને"}, {"fy", "આ વર્ષ"}}, rpPeriod, new java.util.function.Consumer<String>() {
                @Override public void accept(String v) { rpPeriod = v; a.render(); }
            }));
        }
        LinearLayout box = listBox();
        Ui.pad(box, 4, 6, 4, 6);
        StringBuilder txt = new StringBuilder(I18n.tr(title)).append("\n").append(subtitle).append("\n");
        for (RRow r : rows) {
            LinearLayout l = Ui.h(c);
            switch (r.kind) {
                case 0: {
                    Ui.pad(l, 12, 12, 12, 4);
                    l.addView(Ui.t(c, r.label, 13, Ui.ACCENT, true));
                    txt.append("\n").append(I18n.tr(r.label)).append("\n");
                    break;
                }
                case 1: case 2: {
                    Ui.pad(l, 12, r.kind == 2 ? 10 : 6, 12, r.kind == 2 ? 10 : 6);
                    if (r.kind == 2) l.setBackground(Ui.rr(Ui.SOFT, 0, 12));
                    l.addView(Ui.t(c, r.label, r.kind == 2 ? 15 : 14, Ui.TEXT, r.kind == 2), Ui.weight(1));
                    TextView v = Ui.t(c, Fmt.money(r.a), r.kind == 2 ? 16 : 14, r.col == 0 ? Ui.TEXT : r.col, true);
                    v.setGravity(Gravity.END);
                    l.addView(v);
                    txt.append(I18n.tr(r.label)).append(": ").append(Fmt.money(r.a)).append("\n");
                    break;
                }
                case 3: {
                    Ui.pad(l, 12, 6, 12, 6);
                    TextView n = Ui.t(c, r.label, 11, Ui.MUTED, false);
                    l.addView(n, Ui.weight(1));
                    break;
                }
                default: {
                    Ui.pad(l, 12, 6, 12, 6);
                    l.addView(Ui.t(c, r.label, 13, Ui.TEXT, false), Ui.weight(1.6f));
                    TextView d = Ui.t(c, r.a == 0 ? "-" : Fmt.money(r.a), 13, Ui.GREEN, true);
                    d.setGravity(Gravity.END);
                    l.addView(d, Ui.weight(1));
                    TextView e = Ui.t(c, r.b == 0 ? "-" : Fmt.money(r.b), 13, Ui.RED, true);
                    e.setGravity(Gravity.END);
                    l.addView(e, Ui.weight(1));
                    txt.append(I18n.tr(r.label)).append(": ").append(r.a == 0 ? "-" : Fmt.money(r.a)).append(" | ").append(r.b == 0 ? "-" : Fmt.money(r.b)).append("\n");
                }
            }
            box.addView(l);
        }
        root.addView(box);
        final String share = txt.toString();
        LinearLayout bw = Ui.v(c);
        Ui.pad(bw, 16, 8, 16, 0);
        bw.addView(Ui.btn(c, "📤  શેર કરો", "ghost", new Runnable() { @Override public void run() { a.shareText(share); } }));
        root.addView(bw);
        return root;
    }

    private LinearLayout profitLossPage() {
        String[] r = rpRange();
        Fin f = fin(r[0], r[1]);
        List<RRow> rows = new ArrayList<>();
        rows.add(rh("આવક"));
        rows.add(rl("ઉધાર વેચાણ", f.gave, Ui.GREEN));
        rows.add(rl("સીધી આવક", f.income, Ui.GREEN));
        if (f.discount > 0) rows.add(rl("માફ મળેલી રકમ (લેણદાર)", f.discount, Ui.GREEN));
        rows.add(rt("કુલ આવક", f.revenue(), Ui.GREEN));
        rows.add(rh("ખર્ચ"));
        rows.add(rl("ઉધાર ખરીદી", f.took, Ui.RED));
        for (Map.Entry<String, Double> e : f.cats.entrySet()) rows.add(rl(e.getKey(), e.getValue(), Ui.RED));
        if (f.badDebt > 0) rows.add(rl("ડૂબેલી રકમ (માંડવાળ)", f.badDebt, Ui.RED));
        rows.add(rt("કુલ ખર્ચ", f.costs(), Ui.RED));
        rows.add(rh("પરિણામ"));
        double p = f.profit();
        rows.add(rt(p >= 0 ? "ચોખ્ખો નફો" : "ચોખ્ખું નુકસાન", Math.abs(p), p >= 0 ? Ui.GREEN : Ui.RED));
        if (f.revenue() > 0) rows.add(rn("નફાનો દર: " + String.format(java.util.Locale.US, "%.1f", p / f.revenue() * 100) + "% (કુલ આવકના)"));
        rows.add(rn("ઉધાર વેચાણ/ખરીદી લખતી વખતે જ ગણાય છે, પૈસા આવે ત્યારે નહીં."));
        return reportPage("નફો-નુકસાન", rpLabel(), true, rows);
    }

    private LinearLayout cashFlowPage() {
        String[] r = rpRange();
        double open = a.db.settings.openCash + a.db.settings.openBank, in = 0, out = 0, inC = 0, inB = 0, outC = 0, outB = 0;
        Map<String, Double> inK = new LinkedHashMap<>(), outK = new LinkedHashMap<>();
        for (Model.Txn t : a.db.txns) {
            if ("writeoff".equals(t.mode)) continue;
            int cs = Model.type(t.type).cash;
            if (cs == 0) continue;
            boolean bank = "upi".equals(t.mode);
            if (t.date.compareTo(r[0]) < 0) { open += cs * t.amount; continue; }
            if (t.date.compareTo(r[1]) > 0) continue;
            String lab = t.type.equals("got") ? "ગ્રાહક પાસેથી જમા" : t.type.equals("income") ? "સીધી આવક" : t.type.equals("paid") ? "લેણદારને ચૂકવણી" : "સીધા ખર્ચ";
            if (cs > 0) { in += t.amount; if (bank) inB += t.amount; else inC += t.amount; Double o = inK.get(lab); inK.put(lab, (o == null ? 0 : o) + t.amount); }
            else { out += t.amount; if (bank) outB += t.amount; else outC += t.amount; Double o = outK.get(lab); outK.put(lab, (o == null ? 0 : o) + t.amount); }
        }
        List<RRow> rows = new ArrayList<>();
        rows.add(rl("શરૂઆતનું બેલેન્સ (રોકડ + બેંક)", open, Ui.TEXT));
        rows.add(rh("પૈસા આવ્યા"));
        for (Map.Entry<String, Double> e : inK.entrySet()) rows.add(rl(e.getKey(), e.getValue(), Ui.GREEN));
        rows.add(rt("કુલ આવ્યા", in, Ui.GREEN));
        rows.add(rh("પૈસા ગયા"));
        for (Map.Entry<String, Double> e : outK.entrySet()) rows.add(rl(e.getKey(), e.getValue(), Ui.RED));
        rows.add(rt("કુલ ગયા", out, Ui.RED));
        rows.add(rh("અંતે"));
        rows.add(rl("રોકડ (આ સમયમાં ફેરફાર)", inC - outC, (inC - outC) >= 0 ? Ui.GREEN : Ui.RED));
        rows.add(rl("બેંક/UPI (આ સમયમાં ફેરફાર)", inB - outB, (inB - outB) >= 0 ? Ui.GREEN : Ui.RED));
        rows.add(rt("અંતિમ બેલેન્સ", open + in - out, Ui.TEXT));
        return reportPage("રોકડ પ્રવાહ", rpLabel(), true, rows);
    }

    private LinearLayout balanceSheetPage() {
        Model.Cash cb = a.db.cashBank();
        Model.Due d = a.db.totalsDue();
        String today = Fmt.today();
        Fin all = fin("0000-01-01", today);
        double assets = cb.cash + cb.bank + d.recv, liab = d.pay;
        double opening = a.db.settings.openCash + a.db.settings.openBank;
        List<RRow> rows = new ArrayList<>();
        rows.add(rh("સંપત્તિ (તમારી પાસે / લેવાના)"));
        rows.add(rl("રોકડ હાથ પર", cb.cash, Ui.TEXT));
        rows.add(rl("બેંક / UPI", cb.bank, Ui.TEXT));
        rows.add(rl("લેવાના બાકી (ગ્રાહકો)", d.recv, Ui.TEXT));
        rows.add(rt("કુલ સંપત્તિ", assets, Ui.GREEN));
        rows.add(rh("દેવું (આપવાના)"));
        rows.add(rl("દેવાના બાકી (લેણદારો)", d.pay, Ui.TEXT));
        rows.add(rt("કુલ દેવું", liab, Ui.RED));
        rows.add(rh("માલિકની મૂડી"));
        rows.add(rl("શરૂઆતની મૂડી (રોકડ + બેંક)", opening, Ui.TEXT));
        rows.add(rl(all.profit() >= 0 ? "અત્યાર સુધીનો નફો" : "અત્યાર સુધીનું નુકસાન", all.profit(), all.profit() >= 0 ? Ui.GREEN : Ui.RED));
        rows.add(rt("કુલ માલિકની મૂડી", assets - liab, Ui.TEXT));
        rows.add(rt("દેવું + મૂડી", liab + (assets - liab), Ui.TEXT));
        rows.add(rn("સંપત્તિ = દેવું + માલિકની મૂડી. આજની તારીખ સુધીના બધા એન્ટ્રીઓ પરથી."));
        return reportPage("સરવૈયું", Fmt.fmtDate(today), false, rows);
    }

    private LinearLayout trialBalancePage() {
        Model.Cash cb = a.db.cashBank();
        Fin all = fin("0000-01-01", Fmt.today());
        double opening = a.db.settings.openCash + a.db.settings.openBank;
        List<RRow> rows = new ArrayList<>();
        double dr = 0, cr = 0;
        rows.add(rh("ખાતું · લેવાના (Dr) · દેવાના (Cr)"));
        rows.add(r2("રોકડ", cb.cash, 0)); dr += cb.cash;
        rows.add(r2("બેંક / UPI", cb.bank, 0)); dr += cb.bank;
        List<Model.Party> ps = new ArrayList<>(a.db.parties);
        Collections.sort(ps, new Comparator<Model.Party>() { @Override public int compare(Model.Party x, Model.Party y) { return x.name.compareToIgnoreCase(y.name); } });
        for (Model.Party p : ps) {
            double b = a.db.partyBal(p.id);
            if (Math.abs(b) < 0.005) continue;
            if (b > 0) { rows.add(r2(p.name, b, 0)); dr += b; } else { rows.add(r2(p.name, 0, -b)); cr += -b; }
        }
        rows.add(r2("ઉધાર ખરીદી", all.took, 0)); dr += all.took;
        rows.add(r2("ખર્ચ", all.expense, 0)); dr += all.expense;
        if (all.badDebt > 0) { rows.add(r2("ડૂબેલી રકમ", all.badDebt, 0)); dr += all.badDebt; }
        rows.add(r2("ઉધાર વેચાણ", 0, all.gave)); cr += all.gave;
        rows.add(r2("સીધી આવક", 0, all.income)); cr += all.income;
        if (all.discount > 0) { rows.add(r2("માફ મળેલી રકમ", 0, all.discount)); cr += all.discount; }
        rows.add(r2("શરૂઆતની મૂડી", 0, opening)); cr += opening;
        rows.add(r2("કુલ", dr, cr));
        rows.add(rn(Math.abs(dr - cr) < 0.5 ? "✔ બંને બાજુ સરખા છે" : "⚠ બંને બાજુ સરખા નથી (જૂની એન્ટ્રીઓમાં ફેર હોઈ શકે)"));
        return reportPage("ખાતાવાર બાકી (Trial Balance)", Fmt.fmtDate(Fmt.today()), false, rows);
    }

    private View repGroup(String glyph, String title, String sub) {
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 16, 16, 16, 6);
        TextView g = Ui.t(c, glyph, 16, Color.WHITE, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(Ui.T3, 0, 22));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
        gp.setMargins(0, 0, Ui.dp(12), 0);
        l.addView(g, gp);
        LinearLayout m = Ui.v(c);
        m.addView(Ui.t(c, title, 16, Ui.TEXT, true));
        m.addView(Ui.t(c, sub, 12, Ui.MUTED, false));
        l.addView(m, Ui.weight(1));
        return l;
    }

    private View repTile(String glyph, int tint, String title, String sub, Runnable r) {
        LinearLayout k = Ui.card(c);
        Ui.pad(k, 10, 10, 10, 10);
        LinearLayout h = Ui.h(c);
        TextView g = Ui.t(c, glyph, 16, tint, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(Ui.SOFT, 0, 12));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36));
        gp.setMargins(0, 0, Ui.dp(8), 0);
        h.addView(g, gp);
        TextView t = Ui.t(c, title, 13, Ui.TEXT, true);
        t.setMaxLines(2);
        h.addView(t, Ui.weight(1));
        h.addView(Ui.t(c, "›", 18, Ui.ACCENT, true));
        k.addView(h);
        TextView sb = Ui.t(c, sub, 10, Ui.MUTED, false);
        sb.setPadding(0, Ui.dp(6), 0, 0);
        k.addView(sb);
        Ui.tap(k, r);
        return k;
    }

    private void openRep(String key) { a.sub = key; a.render(); a.scroll.scrollTo(0, 0); }

    private boolean repShow(String... texts) {
        if (rpQuery.isEmpty()) return true;
        String q = rpQuery.toLowerCase();
        for (String t : texts) if (I18n.tr(t).toLowerCase().contains(q) || t.toLowerCase().contains(q)) return true;
        return false;
    }

    LinearLayout reports() {
        if ("daily".equals(a.sub)) return dailyReport();
        if ("monthly".equals(a.sub)) return monthlyReport();
        if ("pnl".equals(a.sub)) return profitLossPage();
        if ("cf".equals(a.sub)) return cashFlowPage();
        if ("bs".equals(a.sub)) return balanceSheetPage();
        if ("tb".equals(a.sub)) return trialBalancePage();
        LinearLayout root = Ui.v(c);
        TextView mic = ib("🎤", new Runnable() { @Override public void run() { a.sheets.voiceSheet(null); } });
        root.addView(top("રિપોર્ટ", null, new Runnable() { @Override public void run() { a.go("home"); } }, mic));
        final EditText q = Ui.fld(c, "🔍  રિપોર્ટ શોધો...", rpQuery, Ui.IN_PLAIN);
        LinearLayout.LayoutParams qp = Ui.fillW();
        qp.setMargins(Ui.dp(16), Ui.dp(14), Ui.dp(16), 0);
        q.setLayoutParams(qp);
        root.addView(q);
        final LinearLayout list = Ui.v(c);
        root.addView(list);
        fillReports(list);
        Ui.onText(q, new Runnable() { @Override public void run() { rpQuery = q.getText().toString().trim(); list.removeAllViews(); fillReports(list); } });
        return root;
    }

    private void fillReports(LinearLayout list) {
        boolean any = false;
        if (repShow("સરવૈયું", "નફો-નુકસાન", "રોકડ પ્રવાહ", "ખાતાવાર બાકી", "Balance Sheet", "Profit Loss", "Cash Flow", "Trial Balance")) {
            any = true;
            list.addView(repGroup("▥", "નાણાકીય રિપોર્ટ", "મુખ્ય હિસાબ અને સારાંશ"));
            LinearLayout r1 = Ui.h(c), r2 = Ui.h(c);
            r1.setGravity(Gravity.TOP); r2.setGravity(Gravity.TOP);
            Ui.pad(r1, 10, 4, 10, 0); Ui.pad(r2, 10, 8, 10, 0);
            r1.addView(repTile("⚖", Color.parseColor("#14B8A6"), "સરવૈયું", "સંપત્તિ, દેવું અને મૂડી", new Runnable() { @Override public void run() { openRep("bs"); } }), cardLp());
            r1.addView(repTile("📊", Color.parseColor("#3B82F6"), "નફો-નુકસાન", "આવક, ખર્ચ અને નફો", new Runnable() { @Override public void run() { openRep("pnl"); } }), cardLp());
            r2.addView(repTile("💧", Color.parseColor("#F59E0B"), "રોકડ પ્રવાહ", "રોકડ અને બેંકની આવક-જાવક", new Runnable() { @Override public void run() { openRep("cf"); } }), cardLp());
            r2.addView(repTile("📋", Color.parseColor("#A855F7"), "ખાતાવાર બાકી", "બધા ખાતાના બેલેન્સ", new Runnable() { @Override public void run() { openRep("tb"); } }), cardLp());
            list.addView(r1); list.addView(r2);
        }
        if (repShow("દૈનિક સારાંશ", "માસિક સારાંશ", "વાર્ષિક રિપોર્ટ", "રોકડ સારાંશ", "Daily", "Monthly", "Annual")) {
            any = true;
            list.addView(repGroup("🗓", "સમય મુજબ રિપોર્ટ", "દૈનિક, માસિક અને વાર્ષિક"));
            LinearLayout b = listBox();
            b.addView(row("▤", Ui.GREEN, "દૈનિક સારાંશ", "રોજની રોકડ અને આવક/જાવક", null, 0, null, new Runnable() { @Override public void run() { openRep("daily"); } }));
            b.addView(divider());
            b.addView(row("▥", Ui.BLUE, "માસિક સારાંશ", "ગ્રાહક અને લેણદાર માસિક સારાંશ", null, 0, null, new Runnable() { @Override public void run() { openRep("monthly"); } }));
            b.addView(divider());
            b.addView(row("₹", Ui.ACCENT, "રોકડ સારાંશ", "શરૂઆત અને અંતના બેલેન્સ સાથે", null, 0, null, new Runnable() { @Override public void run() { a.sheets.cashSheet(); } }));
            b.addView(divider());
            b.addView(row("◔", Ui.AMBER, "વાર્ષિક રિપોર્ટ", "મહિના મુજબ આવક અને ખર્ચ", null, 0, null, new Runnable() { @Override public void run() { a.sheets.annualSheet(); } }));
            list.addView(b);
        }
        if (repShow("ખાતા રિપોર્ટ", "ખાતાવહી", "બાકી ચૂકવણી", "Party", "Ledger", "Pending")) {
            any = true;
            list.addView(repGroup("👥", "ગ્રાહક અને લેણદાર", "ખાતાની માહિતી"));
            LinearLayout b = listBox();
            b.addView(row("👥", Ui.GREEN, "ખાતાવહી (Party Ledger)", "દરેક ખાતાની એન્ટ્રીઓ અને બાકી", null, 0, null, new Runnable() { @Override public void run() { a.go("khata"); } }));
            b.addView(divider());
            b.addView(row("⏳", Ui.RED, "બાકી ચૂકવણી (Pending)", "કોણ આપશે અને કોને આપવાના", null, 0, null, new Runnable() { @Override public void run() { a.sheets.pendingSheet(); } }));
            list.addView(b);
        }
        if (!any) list.addView(empty("કોઈ રિપોર્ટ મળ્યો નથી"));
        list.addView(Ui.space(c, 20));
    }

    /** Does this transaction belong to the chosen tab (all / customer / creditor) and khata? */
    boolean reportMatch(Model.Txn t) {
        if (!a.rparty.isEmpty() && !a.rparty.equals(t.partyId)) return false;
        if (a.rkind.equals("all")) return true;
        Model.Party p = a.db.party(t.partyId);
        return p != null && a.db.kindOf(p).equals(a.rkind);
    }

    private boolean inRange(String d) {
        String today = Fmt.today(), m = today.substring(0, 7);
        switch (a.drange) {
            case "last": return d.startsWith(Fmt.monthShift(m, -1));
            case "year": return d.startsWith(today.substring(0, 4));
            case "all": return true;
            default: return d.startsWith(m);
        }
    }

    String rangeLabel() {
        switch (a.drange) {
            case "last": return "ગયો મહિનો";
            case "year": return "આ વર્ષ";
            case "all": return "બધા સમય";
            default: return "આ મહિનો";
        }
    }

    private View partyPill() {
        Model.Party p = a.rparty.isEmpty() ? null : a.db.party(a.rparty);
        LinearLayout l = Ui.h(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(Ui.rr(Ui.SURFACE, Ui.LINE, 16));
        Ui.pad(l, 14, 12, 14, 12);
        l.addView(Ui.t(c, "👥  " + (p != null ? p.name : "બધા સંપર્કો"), 15, Ui.TEXT, true), Ui.weight(1));
        l.addView(Ui.t(c, "⌄", 18, Ui.MUTED, true));
        LinearLayout.LayoutParams lp = Ui.fillW();
        lp.setMargins(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        l.setLayoutParams(lp);
        Ui.tap(l, new Runnable() { @Override public void run() { a.sheets.reportPartySheet(); } });
        return l;
    }

    private View statCard(String label, double v, int col) {
        LinearLayout l = Ui.v(c);
        l.setBackground(Ui.rr(Ui.SURFACE, Ui.LINE, 16));
        Ui.pad(l, 14, 12, 14, 12);
        l.addView(Ui.t(c, "● " + label, 13, col, true));
        l.addView(Ui.t(c, Fmt.money(v), 22, col, true));
        LinearLayout.LayoutParams lp = Ui.weight(1);
        lp.setMargins(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        l.setLayoutParams(lp);
        return l;
    }

    private View statRow(View x, View y) {
        LinearLayout r = Ui.h(c);
        Ui.pad(r, 6, 0, 6, 0);
        r.addView(x);
        r.addView(y);
        return r;
    }

    /** {income, expense, udhar-income (gave), udhar-outgoing (took)} for one transaction list. */
    private double[] sums(List<Model.Txn> items) {
        double[] s = new double[4];
        for (Model.Txn t : items) {
            boolean wo = "writeoff".equals(t.mode);
            switch (t.type) {
                case "income": s[0] += t.amount; break;
                case "got": if (!wo) s[0] += t.amount; break;
                case "expense": s[1] += t.amount; break;
                case "paid": if (!wo) s[1] += t.amount; break;
                case "gave": s[2] += t.amount; break;
                case "took": s[3] += t.amount; break;
                default:
            }
        }
        return s;
    }

    private LinearLayout dailyReport() {
        LinearLayout root = Ui.v(c);
        List<Model.Txn> items = new ArrayList<>();
        for (Model.Txn t : a.db.txns) if (inRange(t.date) && reportMatch(t)) items.add(t);
        Collections.sort(items, Model.SORT_NEWEST);
        final double[] tot = sums(items);
        TextView flt = Ui.t(c, "☰ " + rangeLabel(), 13, Ui.TEXT, true);
        flt.setBackground(Ui.rr(Ui.SURFACE2, Ui.LINE, 18));
        Ui.pad(flt, 12, 8, 12, 8);
        Ui.tap(flt, new Runnable() { @Override public void run() { a.sheets.reportRangeSheet(); } });
        TextView dl = ib("⬇", new Runnable() { @Override public void run() { a.shareText(dailyText(a.db.txns)); } });
        root.addView(top("દૈનિક સારાંશ", null, new Runnable() { @Override public void run() { a.sub = null; a.render(); } }, flt, dl));
        root.addView(chipsRow(new String[][]{{"all", "બધા"}, {"customer", "ગ્રાહક"}, {"creditor", "લેણદાર"}}, a.rkind, new java.util.function.Consumer<String>() {
            @Override public void accept(String s) { a.rkind = s; a.rparty = ""; a.render(); }
        }));
        root.addView(partyPill());
        root.addView(statRow(statCard("આવક", tot[0], Ui.GREEN), statCard("ખર્ચ", tot[1], Ui.RED)));
        root.addView(statRow(statCard("ઉધાર આવક", tot[2], Ui.PURPLE), statCard("ઉધાર જાવક", tot[3], Ui.AMBER)));
        root.addView(sectionHead("દૈનિક નોંધો", null, null));
        if (items.isEmpty()) { root.addView(empty("આ સમયગાળામાં કોઈ નોંધ નથી.")); return root; }
        Map<String, List<Model.Txn>> byDay = new LinkedHashMap<>();
        for (Model.Txn t : items) {
            List<Model.Txn> l = byDay.get(t.date);
            if (l == null) { l = new ArrayList<>(); byDay.put(t.date, l); }
            l.add(t);
        }
        List<String> days = new ArrayList<>(byDay.keySet());
        Collections.sort(days, Collections.reverseOrder());
        for (final String d : days) {
            double[] x = sums(byDay.get(d));
            LinearLayout card = Ui.h(c);
            card.setBackground(Ui.rr(Ui.SURFACE, Ui.LINE, 16));
            card.setGravity(Gravity.CENTER_VERTICAL);
            Ui.pad(card, 12, 12, 12, 12);
            LinearLayout box = Ui.v(c);
            box.setGravity(Gravity.CENTER);
            box.setBackground(Ui.rr(Ui.SURFACE2, Ui.LINE, 12));
            Ui.pad(box, 10, 8, 10, 8);
            box.addView(Ui.t(c, d.substring(8), 20, Ui.ACCENT, true));
            box.addView(Ui.t(c, Fmt.monthLabel(d.substring(0, 7)).split(" ")[0], 11, Ui.MUTED, false));
            card.addView(box);
            LinearLayout grid = Ui.v(c);
            Ui.pad(grid, 12, 0, 0, 0);
            LinearLayout r1 = Ui.h(c), r2 = Ui.h(c);
            r1.addView(mini("આવક", x[0], Ui.GREEN), Ui.weight(1));
            r1.addView(mini("ખર્ચ", x[1], Ui.RED), Ui.weight(1));
            r2.addView(mini("ઉધાર આવક", x[2], Ui.PURPLE), Ui.weight(1));
            r2.addView(mini("ઉધાર જાવક", x[3], Ui.AMBER), Ui.weight(1));
            grid.addView(r1);
            grid.addView(r2);
            card.addView(grid, Ui.weight(1));
            card.addView(Ui.t(c, "›", 20, Ui.MUTED, true));
            LinearLayout.LayoutParams lp = Ui.fillW();
            lp.setMargins(Ui.dp(12), Ui.dp(5), Ui.dp(12), Ui.dp(5));
            card.setLayoutParams(lp);
            Ui.tap(card, new Runnable() { @Override public void run() { a.sheets.dayDetailSheet(d); } });
            root.addView(card);
        }
        return root;
    }

    private View mini(String label, double v, int col) {
        LinearLayout l = Ui.v(c);
        Ui.pad(l, 0, 3, 0, 3);
        l.addView(Ui.t(c, "● " + label, 11, col, false));
        l.addView(Ui.t(c, Fmt.money(v), 15, col, true));
        return l;
    }

    String dailyText(List<Model.Txn> all) {
        List<Model.Txn> items = new ArrayList<>();
        for (Model.Txn t : all) if (inRange(t.date) && reportMatch(t)) items.add(t);
        double[] s = sums(items);
        StringBuilder b = new StringBuilder("*" + I18n.tr("દૈનિક સારાંશ") + "* (" + I18n.tr(rangeLabel()) + ")\n");
        b.append(I18n.tr("આવક")).append(": ").append(Fmt.money(s[0])).append("\n");
        b.append(I18n.tr("ખર્ચ")).append(": ").append(Fmt.money(s[1])).append("\n");
        b.append(I18n.tr("ઉધાર આવક")).append(": ").append(Fmt.money(s[2])).append("\n");
        b.append(I18n.tr("ઉધાર જાવક")).append(": ").append(Fmt.money(s[3])).append("\n");
        return b.toString();
    }

    private View tr(String a1, String a2, String a3, String a4, boolean head, int c4) {
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 12, 8, 12, 8);
        if (head) l.setBackgroundColor(Ui.SURFACE2);
        String[] x = {a1, a2, a3, a4};
        for (int i = 0; i < 4; i++) {
            if (x[i] == null) continue;
            TextView t = Ui.t(c, x[i], head ? 12 : 13, i == 3 && !head ? c4 : (head ? Ui.MUTED : Ui.TEXT), head || i == 3);
            if (i > 0) t.setGravity(Gravity.END);
            l.addView(t, Ui.weight(i == 0 ? 1.6f : 1f));
        }
        return l;
    }

    private LinearLayout monthlyReport() {
        LinearLayout root = Ui.v(c);
        final String key = a.rmonth;
        final Map<String, double[]> per = new LinkedHashMap<>(); // gave, got, took, paid
        for (Model.Txn t : a.db.txns) {
            if (t.partyId == null || !t.date.startsWith(key) || !reportMatch(t)) continue;
            double[] x = per.get(t.partyId);
            if (x == null) { x = new double[4]; per.put(t.partyId, x); }
            switch (t.type) {
                case "gave": x[0] += t.amount; break;
                case "got": x[1] += t.amount; break;
                case "took": x[2] += t.amount; break;
                case "paid": x[3] += t.amount; break;
                default:
            }
        }
        double recv = 0, pay = 0;
        for (Model.Party p : a.db.parties) {
            if (!a.rparty.isEmpty() && !a.rparty.equals(p.id)) continue;
            if (!a.rkind.equals("all") && !a.db.kindOf(p).equals(a.rkind)) continue;
            double b = a.db.partyBal(p.id);
            if (b > 0) recv += b; else pay += -b;
        }
        final double fr = recv, fp = pay;
        TextView mp = Ui.t(c, "☰ " + Fmt.monthLabel(key) + " ⌄", 13, Ui.TEXT, true);
        mp.setBackground(Ui.rr(Ui.SURFACE2, Ui.LINE, 18));
        Ui.pad(mp, 12, 8, 12, 8);
        Ui.tap(mp, new Runnable() { @Override public void run() { a.sheets.monthPickSheet(); } });
        final Runnable share = new Runnable() { @Override public void run() { a.shareText(monthlyText(key, per, fr, fp)); } };
        root.addView(top("માસિક સારાંશ", null, new Runnable() { @Override public void run() { a.sub = null; a.render(); } }, mp, ib("⬇", share), ib("▦", share), ib("⤴", share)));
        root.addView(chipsRow(new String[][]{{"all", "બધા"}, {"customer", "ગ્રાહક"}, {"creditor", "લેણદાર"}}, a.rkind, new java.util.function.Consumer<String>() {
            @Override public void accept(String s) { a.rkind = s; a.rparty = ""; a.render(); }
        }));
        root.addView(partyPill());
        LinearLayout hero = Ui.h(c);
        hero.setBackground(Ui.grad(Color.parseColor("#0F766E"), Color.parseColor("#0F766E"), 20));
        Ui.pad(hero, 18, 16, 18, 16);
        LinearLayout h1 = Ui.v(c), h2 = Ui.v(c);
        h1.addView(Ui.t(c, "વસૂલવાની બાકી રકમ", 13, Color.parseColor("#D1FAF5"), false));
        h1.addView(Ui.t(c, Fmt.money(recv), 24, Color.WHITE, true));
        h2.addView(Ui.t(c, "ચૂકવવા માટે કુલ", 13, Color.parseColor("#D1FAF5"), false));
        h2.addView(Ui.t(c, Fmt.money(pay), 24, Color.WHITE, true));
        hero.addView(h1, Ui.weight(1));
        hero.addView(h2, Ui.weight(1));
        LinearLayout.LayoutParams hp = Ui.fillW();
        hp.setMargins(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        hero.setLayoutParams(hp);
        root.addView(hero);
        root.addView(sectionHead("બધા ખાતાઓ", null, null));
        int n = 0;
        LinearLayout lb = listBox();
        for (Map.Entry<String, double[]> e : per.entrySet()) {
            final Model.Party p = a.db.party(e.getKey());
            if (p == null) continue;
            double bal = a.db.partyBal(p.id);
            double[] x = e.getValue();
            if (n > 0) lb.addView(divider());
            lb.addView(row(p.name, Ui.ACCENT, p.name, I18n.tr("ઉધાર") + " " + Fmt.money(x[0] + x[2]) + " · " + I18n.tr("જમા") + " " + Fmt.money(x[1] + x[3]),
                    Fmt.money(Math.abs(bal)), bal > 0 ? Ui.GREEN : (bal < 0 ? Ui.RED : Ui.MUTED), bal > 0 ? "લેવાના" : (bal < 0 ? "દેવાના" : "બરાબર"),
                    new Runnable() { @Override public void run() { a.openParty(p.id); } }));
            n++;
        }
        if (n == 0) root.addView(empty("આ મહિને કોઈ એન્ટ્રીઓ નથી")); else root.addView(lb);
        return root;
    }

    String monthlyText(String key, Map<String, double[]> per, double recv, double pay) {
        StringBuilder b = new StringBuilder("*" + I18n.tr("માસિક સારાંશ") + "* " + Fmt.monthLabel(key) + "\n");
        b.append(I18n.tr("વસૂલવાની બાકી રકમ")).append(": ").append(Fmt.money(recv)).append("\n");
        b.append(I18n.tr("ચૂકવવા માટે કુલ")).append(": ").append(Fmt.money(pay)).append("\n\n");
        for (Map.Entry<String, double[]> e : per.entrySet()) {
            Model.Party p = a.db.party(e.getKey());
            if (p == null) continue;
            double[] x = e.getValue();
            b.append(p.name).append(": ").append(I18n.tr("ઉધાર")).append(" ").append(Fmt.money(x[0] + x[2])).append(", ")
                    .append(I18n.tr("જમા")).append(" ").append(Fmt.money(x[1] + x[3])).append(", ").append(I18n.tr("બાકી")).append(" ").append(Fmt.money(a.db.partyBal(p.id))).append("\n");
        }
        return b.toString();
    }

    // ---------------- SETTINGS ----------------
    private View srow(String glyph, String title, String sub, String right, Runnable r) {
        return srowT(glyph, Ui.ACCENT, title, sub, right, null, r);
    }

    /** Settings row with its own tint, optional text/view on the right. */
    private View srowT(String glyph, int tint, String title, String sub, String right, View rightView, Runnable r) {
        LinearLayout l = Ui.h(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(l, 14, 12, 14, 12);
        TextView ic = Ui.t(c, glyph, 18, tint, true);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.rr((tint & 0x00FFFFFF) | 0x24000000, 0, 14));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44));
        ip.setMargins(0, 0, Ui.dp(14), 0);
        l.addView(ic, ip);
        LinearLayout tx = Ui.v(c);
        tx.addView(Ui.t(c, title, 16, Ui.TEXT, true));
        if (sub != null && !sub.isEmpty()) tx.addView(Ui.t(c, sub, 12, Ui.MUTED, false));
        l.addView(tx, Ui.weight(1));
        if (rightView != null) l.addView(rightView);
        else l.addView(Ui.t(c, right == null ? "›" : right, right == null ? 22 : 16, right != null && right.startsWith("✔") ? Ui.GREEN : Ui.MUTED, true));
        Ui.tap(l, r);
        return l;
    }

    private View switchView(boolean on) {
        LinearLayout pill = Ui.h(c);
        pill.setGravity(on ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.START | Gravity.CENTER_VERTICAL);
        pill.setBackground(Ui.rr(on ? Color.parseColor("#B794E0") : Color.parseColor("#E8E0EA"), on ? 0 : Color.parseColor("#7D737F"), 18));
        Ui.pad(pill, 4, 4, 4, 4);
        TextView knob = new TextView(c);
        knob.setBackground(Ui.rr(on ? Color.parseColor("#6B21A8") : Color.parseColor("#7D737F"), 0, 14));
        pill.addView(knob, new LinearLayout.LayoutParams(Ui.dp(on ? 26 : 20), Ui.dp(on ? 26 : 20)));
        pill.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(58), Ui.dp(34)));
        return pill;
    }

    private View srowSw(String glyph, int tint, String title, String sub, boolean on, Runnable r) {
        return srowT(glyph, tint, title, sub, null, switchView(on), r);
    }

    private String tgl(boolean on) { return on ? "● ચાલુ" : "○ બંધ"; }

    private View acc(final String id, String glyph, String title, String sub, List<View> rows, int tint) {
        LinearLayout box = Ui.v(c);
        box.setBackground(Ui.rr(Ui.SURFACE, a.setOpen.equals(id) ? ((tint & 0x00FFFFFF) | 0x66000000) : Ui.LINE, 18));
        LinearLayout.LayoutParams bp = Ui.fillW();
        bp.setMargins(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(6));
        box.setLayoutParams(bp);
        boolean open = a.setOpen.equals(id);
        LinearLayout head = Ui.h(c);
        Ui.pad(head, 14, 12, 14, 12);
        TextView ic = Ui.t(c, glyph, 20, tint, true);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.rr((tint & 0x00FFFFFF) | 0x2A000000, 0, 14));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46));
        ip.setMargins(0, 0, Ui.dp(12), 0);
        head.addView(ic, ip);
        LinearLayout tx = Ui.v(c);
        tx.addView(Ui.t(c, title, 16, Ui.TEXT, true));
        tx.addView(Ui.t(c, sub, 12, Ui.MUTED, false));
        head.addView(tx, Ui.weight(1));
        TextView chev = Ui.t(c, open ? "⌃" : "⌄", 18, open ? tint : Ui.MUTED, true);
        chev.setGravity(Gravity.CENTER);
        chev.setBackground(Ui.rr(Ui.SURFACE2, 0, 20));
        head.addView(chev, new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(38)));
        Ui.tap(head, new Runnable() { @Override public void run() { a.setOpen = a.setOpen.equals(id) ? "" : id; a.render(); } });
        box.addView(head);
        if (open) for (View v : rows) { box.addView(divider()); box.addView(v); }
        return box;
    }

    // ---------------- MASTERS ----------------
    private View masterTile(String glyph, String title, String sub, Runnable r) {
        LinearLayout t = Ui.v(c);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(Ui.SURFACE, Ui.dark ? Ui.LINE : 0, 20));
        t.setElevation(Ui.dp(Ui.dark ? 0 : 2));
        Ui.pad(t, 8, 16, 8, 16);
        TextView g = Ui.t(c, glyph, 26, Ui.ACCENT, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(Ui.SOFT, 0, 32));
        t.addView(g, new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64)));
        TextView l = Ui.t(c, title, 14, Ui.TEXT, true);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Ui.dp(8), 0, 0);
        t.addView(l);
        TextView sb = Ui.t(c, sub, 10.5f, Ui.MUTED, false);
        sb.setGravity(Gravity.CENTER);
        t.addView(sb);
        Ui.tap(t, r);
        return t;
    }

    LinearLayout masters() {
        final LinearLayout root = Ui.v(c);
        final Sheets sh = a.sheets;
        TextView search = ib("🔍", new Runnable() { @Override public void run() { a.kf = "all"; a.kq = ""; a.go("khata"); } });
        TextView mic = ib("🎤", new Runnable() { @Override public void run() { sh.voiceSheet(null); } });
        root.addView(top("માસ્ટર", "ખાતા, માલ અને સેટઅપ", null, search, mic));
        List<View> tiles = new ArrayList<>();
        tiles.add(masterTile("📒", "ખાતાવહી", "બધા ખાતા અને બાકી", new Runnable() { @Override public void run() { a.kf = "all"; a.kq = ""; a.go("khata"); } }));
        tiles.add(masterTile("👥", "ગ્રાહકો", "જેમની પાસેથી લેવાના", new Runnable() { @Override public void run() { a.kf = "customer"; a.kq = ""; a.go("khata"); } }));
        tiles.add(masterTile("🏪", "લેણદારો", "જેમને આપવાના", new Runnable() { @Override public void run() { a.kf = "creditor"; a.kq = ""; a.go("khata"); } }));
        tiles.add(masterTile("📦", "પ્રોડક્ટ / માલ", a.db.products.size() + " પ્રોડક્ટ", new Runnable() { @Override public void run() { sh.prodSheet(); } }));
        tiles.add(masterTile("🧾", "ખર્ચ વર્ગ", "વર્ગ મુજબ ખર્ચ", new Runnable() { @Override public void run() { sh.expCatSheet(); } }));
        if (!a.isStaff()) {
            tiles.add(masterTile("🎯", "બજેટ", "મહિનાના ખર્ચની હદ", new Runnable() { @Override public void run() { sh.budgetSheet(); } }));
            tiles.add(masterTile("👤", "સ્ટાફ", a.sync.loggedIn() ? "કર્મચારીઓ અને OTP" : "લૉગિન / સર્વર", new Runnable() { @Override public void run() { if (a.sync.loggedIn()) sh.staffSheet(); else sh.serverSheet(); } }));
            tiles.add(masterTile("👛", "ઓપનિંગ બેલેન્સ", "શરૂઆતના પૈસા", new Runnable() { @Override public void run() { sh.openBalSheet(); } }));
        }
        for (int i = 0; i < tiles.size(); i += 2) {
            LinearLayout r = Ui.h(c);
            r.setGravity(Gravity.TOP);
            Ui.pad(r, 12, i == 0 ? 14 : 8, 12, 0);
            LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, Ui.dp(140), 1f);
            lp1.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
            r.addView(tiles.get(i), lp1);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, Ui.dp(140), 1f);
            lp2.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
            if (i + 1 < tiles.size()) r.addView(tiles.get(i + 1), lp2); else r.addView(new View(c), lp2);
            root.addView(r);
        }
        root.addView(Ui.space(c, 24));
        return root;
    }

    // ---------------- MORE ----------------
    private View moreRow(String glyph, int tint, String title, String chip, boolean danger, Runnable r) {
        LinearLayout l = Ui.h(c);
        l.setBackground(Ui.rr(danger ? Ui.DNGBG : Ui.SURFACE, Ui.dark ? Ui.LINE : 0, 16));
        l.setElevation(Ui.dp(Ui.dark ? 0 : 2));
        Ui.pad(l, 12, 11, 14, 11);
        TextView g = Ui.t(c, glyph, 17, danger ? Ui.RED : tint, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(danger ? Ui.SURFACE : Ui.SOFT, 0, 22));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42));
        gp.setMargins(0, 0, Ui.dp(12), 0);
        l.addView(g, gp);
        l.addView(Ui.t(c, title, 15, danger ? Ui.RED : Ui.TEXT, true), Ui.weight(1));
        if (chip != null) {
            TextView ch = Ui.t(c, chip, 11, Ui.ACCENT, true);
            ch.setBackground(Ui.rr(Ui.SOFT, 0, 10));
            Ui.pad(ch, 8, 3, 8, 3);
            LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, Ui.dp(8), 0);
            l.addView(ch, cp);
        }
        l.addView(Ui.t(c, "›", 20, Ui.MUTED, true));
        LinearLayout.LayoutParams lp = Ui.fillW();
        lp.setMargins(Ui.dp(14), Ui.dp(5), Ui.dp(14), Ui.dp(5));
        l.setLayoutParams(lp);
        Ui.tap(l, r);
        return l;
    }

    LinearLayout more() {
        final Model.Settings s = a.db.settings;
        LinearLayout root = Ui.v(c);
        TextView shield = ib("🛡", new Runnable() { @Override public void run() { a.sheets.privacySheet(); } });
        root.addView(top("વધુ", s.shop.isEmpty() ? (s.owner.isEmpty() ? "Mera Hisab" : s.owner) : s.shop, new Runnable() { @Override public void run() { a.go("home"); } }, shield));
        root.addView(Ui.space(c, 8));
        String themeName = s.theme.equals("light") ? "લાઇટ" : (s.theme.equals("dark") ? "ડાર્ક" : "સિસ્ટમ");
        boolean hasPin = !s.pinHash.isEmpty();
        final Sheets sh = a.sheets;

        final int BLUE = Color.parseColor("#2F6FE0"), ORANGE = Color.parseColor("#F59E0B"), TEAL = Color.parseColor("#0D9488"), GREEN = Color.parseColor("#4CAF50"),
                PURP = Color.parseColor("#8B5CF6"), BLUE2 = Color.parseColor("#3F51B5"), PINK = Color.parseColor("#E11D74"), GREY = Color.parseColor("#607D8B");

        // ---- main list (like the prototype) ----
        final boolean signed = a.sync.loggedIn();
        root.addView(moreRow("🏢", Ui.ACCENT, "કંપની માહિતી", null, false, new Runnable() { @Override public void run() { sh.profileSheet(); } }));
        if (!a.isStaff()) root.addView(moreRow("👥", Ui.ACCENT, "સ્ટાફ અને યુઝર", null, false, new Runnable() { @Override public void run() { if (signed) sh.staffSheet(); else sh.serverSheet(); } }));
        if (a.sync.isAdmin()) root.addView(moreRow("🛠", Ui.ACCENT, "એડમિન પેનલ", null, false, new Runnable() { @Override public void run() { sh.adminSheet(); } }));
        root.addView(moreRow("☁", Ui.ACCENT, "બેકઅપ અને રીસ્ટોર", null, false, new Runnable() { @Override public void run() { sh.backupSheet(); } }));
        root.addView(moreRow("⬇", Ui.ACCENT, "એપ અપડેટ", "v" + a.versionName(), false, new Runnable() { @Override public void run() { a.checkUpdate(); } }));
        root.addView(moreRow("🎨", Ui.ACCENT, "થીમ", themeName, false, new Runnable() { @Override public void run() { sh.themeSheet(); } }));
        root.addView(moreRow("🔔", Ui.ACCENT, "સૂચનાઓ", null, false, new Runnable() { @Override public void run() { sh.bellSheet(); } }));
        root.addView(moreRow("🎧", Ui.ACCENT, "મદદ અને સપોર્ટ", null, false, new Runnable() { @Override public void run() { sh.faqSheet(); } }));
        root.addView(moreRow("ℹ", Ui.ACCENT, "એપ વિશે", null, false, new Runnable() { @Override public void run() { sh.aboutSheet(); } }));
        if (signed) root.addView(moreRow("⏻", Ui.RED, "લૉગઆઉટ", null, true, new Runnable() { @Override public void run() { a.logout(); } }));
        root.addView(sectionHead("બધા સેટિંગ", null, null));

        List<View> biz = new ArrayList<>();
        biz.add(srowT("🏪", Color.parseColor("#2196F3"), "વ્યવસાયનો પ્રકાર", s.biz.equals("service") ? "સર્વિસ પ્રોવાઈડર (સેવા નોંધ ચાલુ)" : "દુકાન / સ્ટોર · સર્વિસ માટે અહીં બદલો", null, null, new Runnable() { @Override public void run() { sh.bizSheet(); } }));
        biz.add(srowT("◉", TEAL, "બિઝનેસ પ્રોફાઇલ", s.shop.isEmpty() ? "તમારા બિઝનેસની માહિતી અપડેટ કરો" : s.shop, null, null, new Runnable() { @Override public void run() { sh.profileSheet(); } }));
        biz.add(srowT("👛", GREEN, "ઓપનિંગ બેલેન્સ", "ઓપનિંગ કેશ બેલેન્સ સેટ કરો", null, null, new Runnable() { @Override public void run() { sh.openBalSheet(); } }));
        biz.add(srowT("▦", PURP, "બિઝનેસ સેટઅપ", "UPI અને QR વિગતો", null, null, new Runnable() { @Override public void run() { sh.bizSetupSheet(); } }));

        List<View> app = new ArrayList<>();
        app.add(srowT("文", BLUE2, "ભાષા", s.lang.equals("hi-IN") ? "હિન્દી" : (s.lang.equals("en-IN") ? "English" : "ગુજરાતી"), null, null, new Runnable() { @Override public void run() { sh.voiceSetSheet(); } }));
        app.add(srowT("◐", PURP, "એપ થીમ", themeName, null, null, new Runnable() { @Override public void run() { sh.themeSheet(); } }));
        app.add(srowSw("▤", PURP, "પ્રોડક્ટ પ્રમાણે એન્ટ્રી ચાલુ કરો", "એન્ટ્રીમાં પ્રોડક્ટ સિલેક્શન અને રેટ ચાલુ કરો", s.productMode, new Runnable() {
            @Override public void run() { s.productMode = !s.productMode; a.save(); a.render(); if (s.productMode && a.db.products.isEmpty()) sh.prodSheet(); }
        }));
        app.add(srowT("☰", TEAL, "પ્રોડક્ટ માસ્ટર", "બનાવેલ પ્રોડક્ટ્સ જુઓ", null, null, new Runnable() { @Override public void run() { sh.prodSheet(); } }));

        List<View> svc = new ArrayList<>();
        svc.add(srowT("🎤", PURP, "અવાજ સેટિંગ", "ભાષા અને બોલીને જવાબ", null, null, new Runnable() { @Override public void run() { sh.voiceSetSheet(); } }));
        svc.add(srowT("☑", GREEN, "અવાજ ટેસ્ટ લૉગ", "શું બોલ્યા, એપ શું સમજ્યું", null, null, new Runnable() { @Override public void run() { sh.voiceLogSheet(); } }));
        svc.add(srowT("🧠", PINK, "એપે શીખેલું", a.db.learn.size() + " નામ શીખ્યા", null, null, new Runnable() { @Override public void run() { sh.learnedSheet(); } }));

        List<View> sec = new ArrayList<>();
        sec.add(srowSw("🔒", PURP, "સિક્યુરિટી પિન", "સુરક્ષા માટે પિન સેટ કરો", hasPin, new Runnable() { @Override public void run() { sh.pinToggle(); } }));
        sec.add(srowSw("☝", Color.parseColor("#2196F3"), "ફિંગરપ્રિન્ટ લૉક", "અનલૉક કરવા માટે બાયોમેટ્રિક્સ વાપરો", !s.bio.isEmpty(), new Runnable() { @Override public void run() { sh.bioToggle(); } }));

        List<View> bk = new ArrayList<>();
        bk.add(srowT("☁", Color.parseColor("#2196F3"), "ડેટા બેકઅપ", a.backupTimeText(), a.backupFresh() ? "✔" : null, null, new Runnable() { @Override public void run() { sh.backupSheet(); } }));
        if (!a.isStaff()) bk.add(srowT("⬆", GREEN, "બેકઅપ પાછું લાવો", "પહેલાં સેવ કરેલી ફાઇલ પસંદ કરો", null, null, new Runnable() { @Override public void run() { a.pickRestoreFile(); } }));
        bk.add(srowT("⬇", ORANGE, "એપ અપડેટ ચકાસો", "નવા વર્ઝન માટે તપાસો", null, null, new Runnable() { @Override public void run() { a.checkUpdate(); } }));

        List<View> accn = new ArrayList<>();
        final boolean on = a.sync.loggedIn();
        if (on) {
            accn.add(srowT("👤", BLUE, a.sync.name, (a.sync.isAdmin() ? "એડમિન · " : (a.sync.isStaff() ? "કર્મચારી · " : "માલિક · ")) + a.sync.bizName + " · " + a.sync.phone, null, null, new Runnable() { @Override public void run() { } }));
            accn.add(srowT("⟳", GREEN, "હમણાં સિંક કરો", a.sync.lastOk == 0 ? "હજી સિંક થયું નથી" : "છેલ્લું સિંક: " + a.sync.lastText(), null, null, new Runnable() {
                @Override public void run() {
                    a.toast("સિંક થઈ રહ્યું છે...");
                    a.sync.run(new Sync.Done() { @Override public void done(boolean ok, String err) { a.toast(ok ? "સિંક થઈ ગયું" : "સિંક ન થયું, ઇન્ટરનેટ તપાસો"); a.render(); } });
                }
            }));
            if (!a.sync.isStaff()) accn.add(srowT("👥", ORANGE, "કર્મચારીઓ (સ્ટાફ)", "ઉમેરો, OTP આપો, બ્લોક કરો", null, null, new Runnable() { @Override public void run() { sh.staffSheet(); } }));
            if (a.sync.isAdmin()) accn.add(srowT("🛠", PINK, "એડમિન પેનલ", "નવી વિનંતિઓ, માલિકો અને OTP", null, null, new Runnable() { @Override public void run() { sh.adminSheet(); } }));
            accn.add(srowT("⏻", Color.parseColor("#E11D48"), "લૉગઆઉટ", "આ ફોનમાંથી બહાર નીકળો", null, null, new Runnable() { @Override public void run() { a.logout(); } }));
        } else {
            accn.add(srowT("☁", BLUE, "સર્વર સેટઅપ", Api.configured() ? "સર્વર જોડાયેલ ✔" : "સ્ટાફ લૉગિન માટે સર્વર જોડો", null, null, new Runnable() { @Override public void run() { sh.serverSheet(); } }));
            if (Api.configured()) accn.add(srowT("→", GREEN, "લૉગિન કરો", "સર્વર સાથે જોડાઓ", null, null, new Runnable() { @Override public void run() { a.setSkipLogin(false); a.rebuild(); } }));
        }

        List<View> sup = new ArrayList<>();
        sup.add(srowT("?", PURP, "FAQs", "વારંવાર પૂછાતા પ્રશ્નો", null, null, new Runnable() { @Override public void run() { sh.faqSheet(); } }));
        sup.add(srowT("!", ORANGE, "એપ પ્રતિસાદ", "તમારા સૂચનો શેર કરો અથવા ભૂલ અહેવાલ કરો", null, null, new Runnable() { @Override public void run() { sh.feedbackSheet(); } }));
        sup.add(srowT("🛡", GREY, "પ્રાઈવસી પોલીસી", "ડેટા વપરાશ અને સુરક્ષા માર્ગદર્શિકા", null, null, new Runnable() { @Override public void run() { sh.privacySheet(); } }));

        root.addView(acc("acct", "👤", "એકાઉન્ટ અને સ્ટાફ", on ? a.sync.name + " · " + a.sync.bizName : "લૉગિન, સ્ટાફ અને સર્વર", accn, Color.parseColor("#E11D48")));
        if (!a.isStaff()) root.addView(acc("biz", "▣", "વ્યવસાય વ્યવસ્થાપન", "દુકાન, પ્રોફાઇલ અને સેટઅપ", biz, BLUE));
        root.addView(acc("app", "⚙", "એપ્લિકેશન", "ભાષા, થીમ અને એન્ટ્રી ઓપ્શન્સ", app, PURP));
        root.addView(acc("svc", "🧾", "સર્વિસિસ અને બિલિંગ", "અવાજ સેટિંગ, ટેસ્ટ અને શીખેલું", svc, Color.parseColor("#16A34A")));
        root.addView(acc("sec", "🛡", "સુરક્ષા", "એપ PIN અને બાયોમેટ્રિક સિક્યુરિટી", sec, Color.parseColor("#D97706")));
        root.addView(acc("bk", "☁", "બેકઅપ અને એપ અપડેટ્સ", "બેકઅપ, રીસ્ટોર અને એપ અપડેટ", bk, Color.parseColor("#0EA5E9")));
        root.addView(acc("sup", "🎧", "સપોર્ટ અને લીગલ", "મદદ, FAQs, ફીડબેક અને નીતિઓ", sup, PINK));

        TextView wipe = Ui.t(c, "🗑  એકાઉન્ટ કાઢી નાખો", 15, Ui.RED, true);
        wipe.setGravity(Gravity.CENTER);
        wipe.setBackground(Ui.rr(Ui.DNGBG, 0, 14));
        Ui.pad(wipe, 14, 14, 14, 14);
        LinearLayout.LayoutParams wp = Ui.fillW();
        wp.setMargins(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(6));
        wipe.setLayoutParams(wp);
        Ui.tap(wipe, new Runnable() {
            @Override public void run() {
                sh.confirm("બધું ખાતું અને બધી નોંધ કાઢી નાખવી છે? આ પાછું નહીં આવે.", "હા, બધું કાઢી નાખો", new Runnable() {
                    @Override public void run() {
                        a.db.parties.clear(); a.db.txns.clear(); a.db.products.clear();
                        a.save(); a.render(); a.toast("બધો ડેટા કાઢી નાખ્યો");
                    }
                });
            }
        });
        if (!a.isStaff()) root.addView(wipe);
        TextView n1 = Ui.t(c, on ? "ડેટા સર્વર પર પણ સુરક્ષિત સેવ થાય છે. સ્ટાફની નોંધ પણ આ જ ખાતામાં આવે છે." : "ડેટા ફક્ત આ ફોનમાં સેવ થાય છે. ફોન બદલતા પહેલાં બેકઅપ લઈ લો.", 12, Ui.MUTED, false);
        n1.setGravity(Gravity.CENTER);
        Ui.pad(n1, 24, 10, 24, 2);
        root.addView(n1);
        TextView n2 = Ui.t(c, "Version " + a.versionName(), 12, Ui.MUTED, false);
        n2.setGravity(Gravity.CENTER);
        Ui.pad(n2, 24, 2, 24, 12);
        root.addView(n2);
        return root;
    }
}
