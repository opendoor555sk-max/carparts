package com.merahisab.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
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
        b.setBackground(Ui.rr(Ui.SURFACE2, 0, 22));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44));
        p.setMargins(Ui.dp(4), 0, 0, 0);
        b.setLayoutParams(p);
        Ui.tap(b, r);
        return b;
    }

    View top(String title, String sub, Runnable back, View... right) {
        LinearLayout l = Ui.h(c);
        l.setBackgroundColor(Ui.TOP);
        Ui.pad(l, 10, 10, 10, 10);
        if (back != null) l.addView(ib("←", back));
        LinearLayout mid = Ui.v(c);
        mid.addView(Ui.t(c, title, 19, Ui.TEXT, true));
        if (sub != null && !sub.isEmpty()) mid.addView(Ui.t(c, sub, 12, Ui.MUTED, false));
        LinearLayout.LayoutParams mp = Ui.weight(1);
        mp.setMargins(Ui.dp(10), 0, 0, 0);
        l.addView(mid, mp);
        for (View v : right) l.addView(v);
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
        t.setBackground(Ui.rr(Ui.SURFACE, Ui.LINE, 16));
        Ui.pad(t, 16, 22, 16, 22);
        LinearLayout.LayoutParams p = Ui.fillW();
        p.setMargins(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(6));
        t.setLayoutParams(p);
        return t;
    }

    LinearLayout listBox() {
        LinearLayout l = Ui.v(c);
        l.setBackground(Ui.rr(Ui.SURFACE, Ui.LINE, 16));
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

        LinearLayout hero = Ui.v(c);
        hero.setBackground(Ui.grad(Color.parseColor("#8B3FE0"), Color.parseColor("#4B1D8F"), 24));
        Ui.pad(hero, 18, 16, 18, 16);
        LinearLayout.LayoutParams hp = Ui.fillW();
        hp.setMargins(Ui.dp(12), Ui.dp(12), Ui.dp(12), Ui.dp(8));
        hero.setLayoutParams(hp);

        LinearLayout htop = Ui.h(c);
        LinearLayout hl = Ui.v(c);
        hl.addView(Ui.t(c, Fmt.greeting(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)), 13, Color.parseColor("#E5D4FF"), false));
        hl.addView(Ui.t(c, db.settings.owner.isEmpty() ? "Mera Hisab" : db.settings.owner, 22, Color.WHITE, true));
        hl.addView(Ui.t(c, Fmt.longDate(Fmt.today()), 12, Color.parseColor("#E5D4FF"), false));
        htop.addView(hl, Ui.weight(1));
        TextView bell = ib(hasAlert ? "🔔•" : "🔔", new Runnable() { @Override public void run() { a.sheets.bellSheet(); } });
        bell.setBackground(Ui.rr(Color.parseColor("#33FFFFFF"), 0, 22)); bell.setTextColor(Color.WHITE);
        TextView gear = ib("⚙", new Runnable() { @Override public void run() { a.go("more"); } });
        gear.setBackground(Ui.rr(Color.parseColor("#33FFFFFF"), 0, 22)); gear.setTextColor(Color.WHITE);
        TextView dots = ib("⋮", new Runnable() { @Override public void run() { a.sheets.homeMenuSheet(); } });
        dots.setBackground(Ui.rr(Color.parseColor("#33FFFFFF"), 0, 22)); dots.setTextColor(Color.WHITE);
        htop.addView(bell); htop.addView(gear); htop.addView(dots);
        hero.addView(htop);

        LinearLayout lr = Ui.h(c);
        lr.setPadding(0, Ui.dp(14), 0, 0);
        lr.addView(Ui.t(c, lbl + "  ", 14, Color.parseColor("#E5D4FF"), false));
        TextView ch = Ui.t(c, chip, 12, Color.WHITE, true);
        ch.setBackground(Ui.rr(Color.parseColor("#44FFFFFF"), 0, 12));
        Ui.pad(ch, 10, 2, 10, 2);
        Ui.tap(ch, new Runnable() { @Override public void run() { a.balMode = a.balMode.equals("full") ? "cash" : (a.balMode.equals("cash") ? "bank" : "full"); a.render(); } });
        lr.addView(ch);
        hero.addView(lr);
        hero.addView(Ui.t(c, Fmt.money(bal), 38, Color.WHITE, true));
        TextView mh = Ui.t(c, "આ મહિને", 12, Color.WHITE, true);
        mh.setBackground(Ui.rr(Color.parseColor("#44FFFFFF"), 0, 12));
        Ui.pad(mh, 10, 2, 10, 2);
        LinearLayout.LayoutParams mhp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mhp.gravity = Gravity.END;
        mhp.setMargins(0, Ui.dp(8), 0, Ui.dp(6));
        mh.setLayoutParams(mhp);
        hero.addView(mh);

        LinearLayout mrow = Ui.h(c);
        mrow.setGravity(Gravity.TOP);
        String[] tags = {"+ ઉધાર વેચાણ", "− ઉધાર ખરીદી", "↓ આવક", "↑ ખર્ચ"};
        String[] vals = {"+" + Fmt.money(mt.gave), "−" + Fmt.money(mt.took), "+" + Fmt.money(mt.inc), "−" + Fmt.money(mt.exp)};
        int[] cols = {Color.parseColor("#9FF0C0"), Color.parseColor("#FFC9A0"), Color.parseColor("#9FF0C0"), Color.parseColor("#FFC9A0")};
        for (int i = 0; i < 4; i++) {
            LinearLayout col = Ui.v(c);
            col.addView(Ui.t(c, tags[i], 11, cols[i], true));
            col.addView(Ui.t(c, vals[i], 14, cols[i], true));
            mrow.addView(col, Ui.weight(1));
        }
        hero.addView(mrow);
        root.addView(hero);

        // action tiles
        LinearLayout acts = Ui.h(c);
        Ui.pad(acts, 12, 4, 12, 4);
        if (this.a.db.settings.biz.equals("service")) {
            acts.addView(tile("🛠", "સેવા નોંધ", false, new Runnable() { @Override public void run() { Sheets.Form f = new Sheets.Form(); f.type = "gave"; a.sheets.openEntry(f); } }), tileLp());
        }
        acts.addView(tile("＋", "આવક ઉમેરો", false, new Runnable() { @Override public void run() { a.sheets.askIncome(); } }), tileLp());
        acts.addView(tile("−", "ખર્ચ ઉમેરો", false, new Runnable() { @Override public void run() { a.sheets.askExpense(); } }), tileLp());
        acts.addView(tile("🎤", "અવાજ", true, new Runnable() { @Override public void run() { a.sheets.voiceSheet(null); } }), tileLp());
        root.addView(acts);

        // two cards
        LinearLayout two = Ui.h(c);
        Ui.pad(two, 12, 4, 12, 4);
        two.setGravity(Gravity.TOP);
        LinearLayout c1 = Ui.card(c);
        c1.addView(Ui.t(c, "👥 ઉધાર આપ્યો", 13, Ui.MUTED, true));
        c1.addView(Ui.t(c, Fmt.money(due.recv), 22, Ui.GREEN, true));
        String s1 = "જમા: " + Fmt.money(mt.got) + (od.recv > 0 ? "\nમુદતવીતી: " + Fmt.money(od.recv) : "");
        c1.addView(Ui.t(c, s1, 12, od.recv > 0 ? Ui.RED : Ui.MUTED, false));
        LinearLayout c2 = Ui.card(c);
        c2.addView(Ui.t(c, "🏪 લેણદાર બાકી", 13, Ui.MUTED, true));
        c2.addView(Ui.t(c, Fmt.money(due.pay), 22, Ui.RED, true));
        c2.addView(Ui.t(c, "મુદતવીતી: " + Fmt.money(od.pay), 12, od.pay > 0 ? Ui.RED : Ui.MUTED, false));
        LinearLayout.LayoutParams l1 = Ui.weight(1); l1.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
        LinearLayout.LayoutParams l2 = Ui.weight(1); l2.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
        two.addView(c1, l1);
        two.addView(c2, l2);
        Ui.tap(c1, new Runnable() { @Override public void run() { a.kf = "customer"; a.kq = ""; a.go("khata"); } });
        Ui.tap(c2, new Runnable() { @Override public void run() { a.kf = "creditor"; a.kq = ""; a.go("khata"); } });
        root.addView(two);

        // In-Review: spoken entries waiting for confirmation
        if (!db.review.isEmpty()) {
            root.addView(sectionHead("✦ ઇન-રિવ્યુ વ્યવહારો (" + db.review.size() + ")", null, null));
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
                mid.addView(Ui.t(c, "વૉઇસ ટ્રાન્ઝેક્શન", 15, Ui.TEXT, true));
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

        root.addView(sectionHead("તાજેતરના વ્યવહારો", "બધા જુઓ ›", new Runnable() { @Override public void run() { a.go("txn"); } }));
        if (recent.isEmpty()) {
            root.addView(empty("હજી કોઈ ટ્રાન્ઝેક્શન નથી.\nમાઇક દબાવીને બોલો, જેમ કે “હનીફ ભાઈને 5000 નો માલ ઉધાર આપ્યો”."));
        } else {
            LinearLayout lb = listBox();
            for (int i = 0; i < recent.size(); i++) {
                if (i > 0) lb.addView(divider());
                lb.addView(txRow(recent.get(i), true));
            }
            root.addView(lb);
        }
        // quick collection
        TextView q = Ui.t(c, "₹  ઝડપી ઉઘરાણી", 16, Color.WHITE, true);
        q.setGravity(Gravity.CENTER);
        q.setBackground(Ui.grad(Color.parseColor("#9A57D6"), Color.parseColor("#6D28B8"), 28));
        Ui.pad(q, 22, 12, 22, 12);
        LinearLayout.LayoutParams qp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qp.gravity = Gravity.CENTER_HORIZONTAL;
        qp.setMargins(0, Ui.dp(16), 0, Ui.dp(8));
        q.setLayoutParams(qp);
        Ui.tap(q, new Runnable() { @Override public void run() { a.sheets.quickSheet(""); } });
        root.addView(q);
        return root;
    }

    private LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(112), 1f);
        p.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
        return p;
    }

    private View tile(String glyph, String label, boolean voice, Runnable r) {
        LinearLayout t = Ui.v(c);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(Ui.SURFACE, voice ? Ui.BLUE : Ui.LILAC, 18));
        TextView g = Ui.t(c, glyph, 22, voice ? Ui.BLUE : Ui.ACCENT, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Ui.rr(Ui.SOFT, 0, 14));
        g.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
        t.addView(g);
        TextView l = Ui.t(c, label, 14, Ui.TEXT, true);
        l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = Ui.fillW();
        lp.setMargins(0, Ui.dp(8), 0, 0);
        t.addView(l, lp);
        Ui.tap(t, r);
        return t;
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
        String none = a.period.equals("day") ? "આ તારીખે કોઈ વ્યવહારો નથી" : (a.period.equals("month") ? "આ મહિને કોઈ વ્યવહારો નથી" : "કોઈ વ્યવહારો નથી");

        TextView fb = Ui.t(c, "⏷ ફિલ્ટર" + (extra > 0 ? " " + extra : ""), 14, Ui.ACCENT, true);
        fb.setBackground(Ui.rr(Ui.SOFT, 0, 18));
        Ui.pad(fb, 12, 8, 12, 8);
        Ui.tap(fb, new Runnable() { @Override public void run() { a.sheets.filterSheet(); } });
        root.addView(top("વ્યવહારો (" + items.size() + ")", a.db.settings.shop.isEmpty() ? (a.db.settings.owner.isEmpty() ? "Mera Hisab" : a.db.settings.owner) : a.db.settings.shop,
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
        root.addView(top("ખાતા", "કુલ " + a.db.parties.size() + " ખાતા", new Runnable() { @Override public void run() { a.go("home"); } }, bell, sort, mic));
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
        pb.setBackground(Ui.grad(Ui.dark ? Color.parseColor("#2C1F52") : Color.parseColor("#EFE4FF"), Ui.dark ? Color.parseColor("#1B1530") : Color.parseColor("#E4D6FB"), 20));
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
        b.setBackground(primary ? Ui.grad(Color.parseColor("#8B3FE0"), Ui.PURPLE, 14) : Ui.rr(Ui.SURFACE, Ui.LINE, 14));
        Ui.tap(b, r);
        return b;
    }

    // ---------------- REPORTS ----------------
    LinearLayout reports() {
        if ("daily".equals(a.sub)) return dailyReport();
        if ("monthly".equals(a.sub)) return monthlyReport();
        LinearLayout root = Ui.v(c);
        TextView mic = ib("🎤", new Runnable() { @Override public void run() { a.sheets.voiceSheet(null); } });
        root.addView(top("રિપોર્ટ્સ", null, new Runnable() { @Override public void run() { a.go("home"); } }, mic));
        root.addView(sectionHead("દૈનિક રિપોર્ટ્સ", null, null));
        LinearLayout l1 = listBox();
        l1.addView(row("▤", Ui.ACCENT, "દૈનિક સારાંશ", "રોજની રોકડ અને આવક/જાવક ટ્રેક કરો", null, 0, null,
                new Runnable() { @Override public void run() { a.sub = "daily"; a.render(); } }));
        root.addView(l1);
        root.addView(sectionHead("ગ્રાહક અને લેણદાર", null, null));
        LinearLayout l2 = listBox();
        l2.addView(row("👥", Ui.ACCENT, "માસિક સારાંશ અહેવાલ", "ફિલ્ટર્સ સાથે ગ્રાહક અને લેણદાર માસિક સારાંશ", null, 0, null,
                new Runnable() { @Override public void run() { a.sub = "monthly"; a.render(); } }));
        l2.addView(divider());
        l2.addView(row("⏳", Ui.ACCENT, "બાકી ચૂકવણી (Pending)", "કોણ તમને આપશે અને કોને તમારે આપવાના, તાકીદ મુજબ", null, 0, null,
                new Runnable() { @Override public void run() { a.sheets.pendingSheet(); } }));
        root.addView(l2);
        root.addView(sectionHead("રોકડ અને નફો", null, null));
        LinearLayout l3 = listBox();
        l3.addView(row("₹", Ui.ACCENT, "રોકડ સારાંશ", "ફક્ત રોકડ નોંધ, શરૂઆત અને અંતના બેલેન્સ સાથે", null, 0, null,
                new Runnable() { @Override public void run() { a.sheets.cashSheet(); } }));
        l3.addView(divider());
        l3.addView(row("◔", Ui.ACCENT, "નફો-નુકસાન (P&L)", "ચોખ્ખી આવક, ખર્ચ અને નફાનું વિશ્લેષણ", null, 0, null,
                new Runnable() { @Override public void run() { a.sheets.pnlSheet(); } }));
        l3.addView(divider());
        l3.addView(row("▥", Ui.ACCENT, "વાર્ષિક રિપોર્ટ", "આખા વર્ષની મહિના મુજબ આવક અને ખર્ચ", null, 0, null,
                new Runnable() { @Override public void run() { a.sheets.annualSheet(); } }));
        root.addView(l3);
        return root;
    }

    private LinearLayout dailyReport() {
        LinearLayout root = Ui.v(c);
        List<Model.Txn> items = new ArrayList<>();
        for (Model.Txn t : a.db.txns) if (t.date.equals(a.rday)) items.add(t);
        Collections.sort(items, Model.SORT_NEWEST);
        double cin = 0, cout = 0;
        for (Model.Txn t : items) {
            int cs = Model.type(t.type).cash;
            if ("writeoff".equals(t.mode)) continue;
            if (cs > 0) cin += t.amount; else if (cs < 0) cout += t.amount;
        }
        root.addView(top("દૈનિક સારાંશ", null, new Runnable() { @Override public void run() { a.sub = null; a.render(); } }));
        root.addView(periodBar(a.rday.equals(Fmt.today()) ? "આજ" : Fmt.weekday(a.rday), Fmt.shortDate(a.rday),
                new Runnable() { @Override public void run() { a.rday = Fmt.addDays(a.rday, -1); a.render(); } },
                new Runnable() { @Override public void run() { a.rday = Fmt.addDays(a.rday, 1); a.render(); } }, true));
        root.addView(strip("રોકડ/બેંક આવક", cin, Ui.GREEN, "જાવક", cout, Ui.RED, "ચોખ્ખી રકમ", cin - cout, cin - cout >= 0 ? Ui.GREEN : Ui.RED));
        root.addView(Ui.space(c, 10));
        if (items.isEmpty()) root.addView(empty("આ તારીખે કોઈ નોંધ નથી."));
        else {
            LinearLayout lb = listBox();
            boolean first = true;
            for (Model.Txn t : items) { if (!first) lb.addView(divider()); first = false; lb.addView(txRow(t, false)); }
            root.addView(lb);
        }
        return root;
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
        String key = a.rmonth;
        root.addView(top("માસિક સારાંશ", null, new Runnable() { @Override public void run() { a.sub = null; a.render(); } }));
        root.addView(periodBar(Fmt.monthLabel(key), "",
                new Runnable() { @Override public void run() { a.rmonth = Fmt.monthShift(a.rmonth, -1); a.render(); } },
                new Runnable() { @Override public void run() { a.rmonth = Fmt.monthShift(a.rmonth, 1); a.render(); } }, true));
        root.addView(chipsRow(new String[][]{{"all", "બધા"}, {"customer", "ગ્રાહક"}, {"creditor", "લેણદાર"}}, a.rkind, new java.util.function.Consumer<String>() {
            @Override public void accept(String s) { a.rkind = s; a.render(); }
        }));
        Map<String, double[]> per = new LinkedHashMap<>(); // gave, got, took, paid
        for (Model.Txn t : a.db.txns) {
            if (t.partyId == null || !t.date.startsWith(key)) continue;
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
        root.addView(sectionHead("ખાતા મુજબ", null, null));
        LinearLayout tbl = listBox();
        int n = 0;
        tbl.addView(tr("નામ", "ઉધાર", "જમા/ચૂકવણી", "કુલ બાકી", true, 0));
        for (Map.Entry<String, double[]> e : per.entrySet()) {
            Model.Party p = a.db.party(e.getKey());
            if (p == null) continue;
            if (!a.rkind.equals("all") && !a.db.kindOf(p).equals(a.rkind)) continue;
            double bal = a.db.partyBal(p.id);
            double[] x = e.getValue();
            tbl.addView(tr(p.name, Fmt.plain(x[0] + x[2]), Fmt.plain(x[1] + x[3]), Fmt.plain(Math.abs(bal)), false, bal > 0 ? Ui.GREEN : (bal < 0 ? Ui.RED : Ui.TEXT)));
            n++;
        }
        if (n == 0) root.addView(empty("આ મહિને ખાતાઓમાં કોઈ નોંધ નથી.")); else root.addView(tbl);

        List<Model.Txn> exp = new ArrayList<>();
        double total = 0;
        Map<String, Double> cats = new LinkedHashMap<>();
        for (Model.Txn t : a.db.txns) {
            if (!t.type.equals("expense") || !t.date.startsWith(key)) continue;
            exp.add(t);
            total += t.amount;
            String cat = t.cat.isEmpty() ? "અન્ય" : t.cat;
            Double cur = cats.get(cat);
            cats.put(cat, (cur == null ? 0 : cur) + t.amount);
        }
        Collections.sort(exp, Model.SORT_NEWEST);
        root.addView(sectionHead("ખર્ચ  " + Fmt.money(total), null, null));
        if (!cats.isEmpty()) {
            LinearLayout cb = listBox();
            List<Map.Entry<String, Double>> es = new ArrayList<>(cats.entrySet());
            Collections.sort(es, new Comparator<Map.Entry<String, Double>>() {
                @Override public int compare(Map.Entry<String, Double> x, Map.Entry<String, Double> y) { return Double.compare(y.getValue(), x.getValue()); }
            });
            for (Map.Entry<String, Double> e : es) cb.addView(tr(e.getKey(), null, null, Fmt.money(e.getValue()), false, Ui.RED));
            root.addView(cb);
        }
        if (exp.isEmpty()) root.addView(empty("આ મહિને કોઈ ખર્ચ નથી."));
        else {
            LinearLayout lb = listBox();
            boolean first = true;
            for (Model.Txn t : exp) { if (!first) lb.addView(divider()); first = false; lb.addView(txRow(t, true)); }
            root.addView(lb);
        }
        return root;
    }

    // ---------------- SETTINGS ----------------
    private View srow(String glyph, String title, String sub, String right, Runnable r) {
        LinearLayout l = Ui.h(c);
        Ui.pad(l, 14, 10, 14, 10);
        TextView ic = Ui.t(c, glyph, 18, Ui.ACCENT, true);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.rr(Ui.SOFT, 0, 12));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
        ip.setMargins(0, 0, Ui.dp(12), 0);
        l.addView(ic, ip);
        LinearLayout tx = Ui.v(c);
        tx.addView(Ui.t(c, title, 15, Ui.TEXT, true));
        tx.addView(Ui.t(c, sub, 12, Ui.MUTED, false));
        l.addView(tx, Ui.weight(1));
        l.addView(Ui.t(c, right == null ? "›" : right, 16, right != null && right.startsWith("✔") ? Ui.GREEN : Ui.MUTED, true));
        Ui.tap(l, r);
        return l;
    }

    private String tgl(boolean on) { return on ? "● ચાલુ" : "○ બંધ"; }

    private View acc(final String id, String glyph, String title, String sub, List<View> rows) {
        LinearLayout box = Ui.v(c);
        box.setBackground(Ui.rr(Ui.SURFACE, Ui.LINE, 16));
        LinearLayout.LayoutParams bp = Ui.fillW();
        bp.setMargins(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(6));
        box.setLayoutParams(bp);
        boolean open = a.setOpen.equals(id);
        LinearLayout head = Ui.h(c);
        Ui.pad(head, 14, 12, 14, 12);
        TextView ic = Ui.t(c, glyph, 18, Ui.ACCENT, true);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.rr(Ui.SOFT, 0, 12));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
        ip.setMargins(0, 0, Ui.dp(12), 0);
        head.addView(ic, ip);
        LinearLayout tx = Ui.v(c);
        tx.addView(Ui.t(c, title, 16, Ui.TEXT, true));
        tx.addView(Ui.t(c, sub, 12, Ui.MUTED, false));
        head.addView(tx, Ui.weight(1));
        head.addView(Ui.t(c, open ? "⌃" : "⌄", 18, Ui.MUTED, true));
        Ui.tap(head, new Runnable() { @Override public void run() { a.setOpen = a.setOpen.equals(id) ? "" : id; a.render(); } });
        box.addView(head);
        if (open) for (View v : rows) { box.addView(divider()); box.addView(v); }
        return box;
    }

    LinearLayout more() {
        final Model.Settings s = a.db.settings;
        LinearLayout root = Ui.v(c);
        root.addView(top("સેટિંગ્સ", s.shop.isEmpty() ? (s.owner.isEmpty() ? "Mera Hisab" : s.owner) : s.shop, new Runnable() { @Override public void run() { a.go("home"); } }));
        root.addView(Ui.space(c, 6));
        String themeName = s.theme.equals("light") ? "લાઇટ" : (s.theme.equals("dark") ? "ડાર્ક" : "સિસ્ટમ");
        boolean hasPin = !s.pinHash.isEmpty();
        final Sheets sh = a.sheets;

        List<View> biz = new ArrayList<>();
        biz.add(srow("💼", "વ્યવસાયનો પ્રકાર", s.biz.equals("service") ? "સર્વિસ પ્રોવાઈડર (સેવા નોંધ ચાલુ)" : "દુકાન / સ્ટોર · સર્વિસ માટે અહીં બદલો", null, new Runnable() { @Override public void run() { sh.bizSheet(); } }));
        biz.add(srow("👤", "બિઝનેસ પ્રોફાઇલ", "તમારા બિઝનેસની માહિતી અપડેટ કરો", null, new Runnable() { @Override public void run() { sh.profileSheet(); } }));
        biz.add(srow("👛", "ઓપનિંગ બેલેન્સ", "ઓપનિંગ કેશ બેલેન્સ સેટ કરો", null, new Runnable() { @Override public void run() { sh.openBalSheet(); } }));
        biz.add(srow("▦", "બિઝનેસ સેટઅપ", "UPI વિગતો (ચૂકવણી Link માટે)", null, new Runnable() { @Override public void run() { sh.bizSetupSheet(); } }));

        List<View> app = new ArrayList<>();
        app.add(srow("🌐", "ભાષા અને વૉઇસ", (s.lang.equals("hi-IN") ? "હિન્દી" : (s.lang.equals("en-IN") ? "English" : "ગુજરાતી")) + " · બોલીને જવાબ " + (s.speak ? "ચાલુ" : "બંધ"), null, new Runnable() { @Override public void run() { sh.voiceSetSheet(); } }));
        app.add(srow("🎨", "એપ થીમ", themeName, null, new Runnable() { @Override public void run() { sh.themeSheet(); } }));
        app.add(srow("📦", "પ્રોડક્ટ પ્રમાણે એન્ટ્રી", "એન્ટ્રીમાં પ્રોડક્ટ સિલેક્શન અને રેટ", tgl(s.productMode), new Runnable() {
            @Override public void run() { s.productMode = !s.productMode; a.save(); a.render(); if (s.productMode && a.db.products.isEmpty()) sh.prodSheet(); }
        }));
        if (s.productMode) app.add(srow("▤", "પ્રોડક્ટ યાદી", a.db.products.size() + " પ્રોડક્ટ · રેટ સેટ કરો", null, new Runnable() { @Override public void run() { sh.prodSheet(); } }));

        List<View> sec = new ArrayList<>();
        sec.add(srow("🔒", "સિક્યુરિટી પિન", hasPin ? "પિન સક્રિય છે (બદલવા માટે ટેપ કરો)" : "એપ ખોલવા માટે 4 અંકનો પિન મૂકો", tgl(hasPin), new Runnable() { @Override public void run() { sh.pinToggle(); } }));
        sec.add(srow("☝", "ફિંગરપ્રિન્ટ લૉક", "અનલૉક કરવા માટે બાયોમેટ્રિક્સ વાપરો", tgl(!s.bio.isEmpty()), new Runnable() { @Override public void run() { sh.bioToggle(); } }));

        List<View> bk = new ArrayList<>();
        bk.add(srow("☁", "ડેટા બેકઅપ", a.backupTimeText(), a.backupFresh() ? "✔" : null, new Runnable() { @Override public void run() { sh.backupSheet(); } }));
        bk.add(srow("⬆", "બેકઅપ પાછું લાવો", "પહેલાં સેવ કરેલી ફાઇલ પસંદ કરો", null, new Runnable() { @Override public void run() { a.pickRestoreFile(); } }));
        bk.add(srow("⟳", "એપ અપડેટ ચકાસો", "નવા વર્ઝન માટે તપાસો", null, new Runnable() { @Override public void run() { a.checkUpdate(); } }));

        List<View> sup = new ArrayList<>();
        sup.add(srow("?", "FAQs", "વારંવાર પૂછાતા પ્રશ્નો", null, new Runnable() { @Override public void run() { sh.faqSheet(); } }));
        sup.add(srow("🛡", "પ્રાઇવસી", "ડેટા વપરાશ અને સુરક્ષા માર્ગદર્શિકા", null, new Runnable() { @Override public void run() { sh.privacySheet(); } }));

        root.addView(acc("biz", "🏪", "વ્યવસાય વ્યવસ્થાપન", "દુકાન, પ્રોફાઇલ અને ઓપનિંગ બેલેન્સ સેટઅપ", biz));
        root.addView(acc("app", "⚙", "એપ્લિકેશન", "ભાષા, થીમ અને એન્ટ્રી ઓપ્શન્સ", app));
        root.addView(acc("sec", "🛡", "સુરક્ષા", "એપ PIN અને બાયોમેટ્રિક સિક્યુરિટી", sec));
        root.addView(acc("bk", "☁", "બેકઅપ અને એપ અપડેટ્સ", "બેકઅપ અને એપ અપડેટ", bk));
        root.addView(acc("sup", "?", "સપોર્ટ અને લીગલ", "મદદ, FAQs અને નીતિઓ", sup));

        TextView wipe = Ui.t(c, "🗑  બધો ડેટા કાઢી નાખો", 15, Ui.RED, true);
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
        root.addView(wipe);
        TextView n1 = Ui.t(c, "ડેટા ફક્ત આ ફોનમાં સેવ થાય છે. ફોન બદલતા પહેલાં બેકઅપ લઈ લો.", 12, Ui.MUTED, false);
        n1.setGravity(Gravity.CENTER);
        Ui.pad(n1, 24, 10, 24, 2);
        root.addView(n1);
        TextView n2 = Ui.t(c, "Mera Hisab · વર્ઝન " + a.versionName(), 12, Ui.MUTED, false);
        n2.setGravity(Gravity.CENTER);
        Ui.pad(n2, 24, 2, 24, 12);
        root.addView(n2);
        return root;
    }
}
